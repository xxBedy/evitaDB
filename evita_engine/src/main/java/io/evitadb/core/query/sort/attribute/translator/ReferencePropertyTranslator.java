/*
 *
 *                         _ _        ____  ____
 *               _____   _(_) |_ __ _|  _ \| __ )
 *              / _ \ \ / / | __/ _` | | | |  _ \
 *             |  __/\ V /| | || (_| | |_| | |_) |
 *              \___| \_/ |_|\__\__,_|____/|____/
 *
 *   Copyright (c) 2023
 *
 *   Licensed under the Business Source License, Version 1.1 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   https://github.com/FgForrest/evitaDB/blob/main/LICENSE
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.evitadb.core.query.sort.attribute.translator;

import io.evitadb.api.query.OrderConstraint;
import io.evitadb.api.query.order.ReferenceProperty;
import io.evitadb.api.requestResponse.data.mutation.reference.ReferenceKey;
import io.evitadb.api.requestResponse.schema.EntitySchemaContract;
import io.evitadb.api.requestResponse.schema.ReferenceSchemaContract;
import io.evitadb.core.exception.ReferenceNotIndexedException;
import io.evitadb.core.query.common.translator.SelfTraversingTranslator;
import io.evitadb.core.query.indexSelection.IndexSelectionVisitor;
import io.evitadb.core.query.indexSelection.TargetIndexes;
import io.evitadb.core.query.sort.OrderByVisitor;
import io.evitadb.core.query.sort.Sorter;
import io.evitadb.core.query.sort.translator.OrderingConstraintTranslator;
import io.evitadb.index.EntityIndex;
import io.evitadb.index.EntityIndexKey;
import io.evitadb.index.EntityIndexType;
import io.evitadb.index.GlobalEntityIndex;
import io.evitadb.index.ReferencedTypeEntityIndex;
import io.evitadb.utils.ArrayUtils;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static io.evitadb.utils.Assert.isTrue;

/**
 * This implementation of {@link OrderingConstraintTranslator} converts {@link ReferenceProperty} to {@link Sorter}.
 *
 * @author Jan Novotný (novotny@fg.cz), FG Forrest a.s. (c) 2021
 */
public class ReferencePropertyTranslator implements OrderingConstraintTranslator<ReferenceProperty>, SelfTraversingTranslator {
	private static final Comparator<EntityIndex> DEFAULT_COMPARATOR = (o1, o2) -> {
		final int o1pk = ((ReferenceKey) o1.getIndexKey().getDiscriminator()).primaryKey();
		final int o2pk = ((ReferenceKey) o2.getIndexKey().getDiscriminator()).primaryKey();
		return Integer.compare(o1pk, o2pk);
	};
	private static final EntityIndex[] EMPTY_INDEXES = new EntityIndex[0];

	/**
	 * Method locates all {@link EntityIndex} instances that are related to the given reference name. The list is
	 * resolved from {@link ReferencedTypeEntityIndex}.
	 */
	@Nonnull
	private static EntityIndex[] selectFullEntityIndexSet(
		@Nonnull OrderByVisitor orderByVisitor,
		@Nonnull String referenceName,
		@Nonnull ReferenceSchemaContract referenceSchema,
		boolean referencedEntityHierarchical
	) {
		final EntityIndexKey entityIndexKey = new EntityIndexKey(EntityIndexType.REFERENCED_ENTITY_TYPE, referenceName);
		final Optional<ReferencedTypeEntityIndex> referencedEntityTypeIndex = orderByVisitor.getIndex(entityIndexKey);

		final Comparator<EntityIndex> indexComparator = referencedEntityHierarchical ?
			getHierarchyComparator(orderByVisitor.getGlobalEntityIndex(referenceSchema.getReferencedEntityType())) :
			DEFAULT_COMPARATOR;

		return referencedEntityTypeIndex.map(
				it -> it.getAllPrimaryKeys()
					.stream()
					.mapToObj(
						refPk -> orderByVisitor.getIndex(
								new EntityIndexKey(
									referencedEntityHierarchical ?
										EntityIndexType.REFERENCED_HIERARCHY_NODE :
										EntityIndexType.REFERENCED_ENTITY,
									new ReferenceKey(referenceName, refPk)
								)
							)
							.orElse(null)
					)
					.map(EntityIndex.class::cast)
					.filter(Objects::nonNull)
					.sorted(indexComparator)
					.toArray(EntityIndex[]::new)
			)
			.orElse(EMPTY_INDEXES);
	}

	/**
	 * Method locates all {@link EntityIndex} from the resolved list of {@link TargetIndexes} which were identified
	 * by the {@link IndexSelectionVisitor}. The list is expected to be much smaller than the full list computed
	 * in {@link #selectFullEntityIndexSet(OrderByVisitor, String, ReferenceSchemaContract, boolean)}.
	 */
	@Nonnull
	private static EntityIndex[] selectReducedEntityIndexSet(
		@Nonnull OrderByVisitor orderByVisitor,
		@Nonnull String referenceName,
		@Nonnull ReferenceSchemaContract referenceSchema,
		boolean referencedEntityHierarchical
	) {
		final Comparator<EntityIndex> indexComparator = referencedEntityHierarchical ?
			getHierarchyComparator(orderByVisitor.getGlobalEntityIndex(referenceSchema.getReferencedEntityType())) :
			DEFAULT_COMPARATOR;

		return orderByVisitor.getTargetIndexes()
			.stream()
			.flatMap(it -> it.getIndexes().stream())
			.filter(it -> it instanceof EntityIndex)
			.map(EntityIndex.class::cast)
			.filter(it -> !(it instanceof ReferencedTypeEntityIndex))
			.filter(
				it -> it.getIndexKey().getDiscriminator() instanceof ReferenceKey referenceKey &&
					referenceKey.referenceName().equals(referenceName)
			)
			.sorted(indexComparator)
			.toArray(EntityIndex[]::new);
	}

	/**
	 * Method creates the comparator that allows to sort referenced primary keys by their presence in the hierarchy
	 * using deep traversal mechanism.
	 */
	@Nonnull
	private static Comparator<EntityIndex> getHierarchyComparator(@Nonnull GlobalEntityIndex entityIndex) {
		final Comparator<Integer> comparator = entityIndex.getHierarchyComparator();
		return (o1, o2) -> comparator.compare(
			((ReferenceKey) o1.getIndexKey().getDiscriminator()).primaryKey(),
			((ReferenceKey) o2.getIndexKey().getDiscriminator()).primaryKey()
		);
	}

	@Nonnull
	@Override
	public Stream<Sorter> createSorter(@Nonnull ReferenceProperty orderConstraint, @Nonnull OrderByVisitor orderByVisitor) {
		final String referenceName = orderConstraint.getReferenceName();
		final EntitySchemaContract entitySchema = orderByVisitor.getSchema();
		final ReferenceSchemaContract referenceSchema = entitySchema.getReferenceOrThrowException(referenceName);
		isTrue(referenceSchema.isIndexed(), () -> new ReferenceNotIndexedException(referenceName, entitySchema));
		final boolean referencedEntityHierarchical = referenceSchema.isReferencedEntityTypeManaged() &&
			orderByVisitor.getSchema(referenceSchema.getReferencedEntityType()).isWithHierarchy();

		final EntityIndex[] reducedEntityIndexSet = selectReducedEntityIndexSet(orderByVisitor, referenceName, referenceSchema, referencedEntityHierarchical);
		final EntityIndex[] referenceIndexes = ArrayUtils.isEmpty(reducedEntityIndexSet) ?
			selectFullEntityIndexSet(orderByVisitor, referenceName, referenceSchema, referencedEntityHierarchical) :
			reducedEntityIndexSet;

		orderByVisitor.executeInContext(
			referenceIndexes,
			referenceSchema.getReferencedEntityType(),
			null,
			orderByVisitor.getProcessingScope().withReferenceSchemaAccessor(referenceName),
			new EntityReferenceAttributeExtractor(referenceName),
			() -> {
				for (OrderConstraint innerConstraint : orderConstraint.getChildren()) {
					innerConstraint.accept(orderByVisitor);
				}
				return null;
			}
		);

		return Stream.empty();
	}

}
