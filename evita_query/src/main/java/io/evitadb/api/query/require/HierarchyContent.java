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

package io.evitadb.api.query.require;

import io.evitadb.api.query.Constraint;
import io.evitadb.api.query.FilterConstraint;
import io.evitadb.api.query.OrderConstraint;
import io.evitadb.api.query.ReferenceConstraint;
import io.evitadb.api.query.RequireConstraint;
import io.evitadb.api.query.descriptor.ConstraintDomain;
import io.evitadb.api.query.descriptor.annotation.AdditionalChild;
import io.evitadb.api.query.descriptor.annotation.Child;
import io.evitadb.api.query.descriptor.annotation.Classifier;
import io.evitadb.api.query.descriptor.annotation.ConstraintDefinition;
import io.evitadb.api.query.descriptor.annotation.Creator;
import io.evitadb.api.query.filter.FilterBy;
import io.evitadb.api.query.order.OrderBy;
import io.evitadb.utils.ArrayUtils;
import io.evitadb.utils.Assert;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * This `hierarchyContent` requirement changes default behaviour of the query engine returning only entity primary keys in the result.
 * When this requirement is used result contains [entity bodies](entity_model.md) along with hierarchyContent with to entities
 * or external objects specified in one or more arguments of this requirement.
 *
 * Example:
 *
 * ```
 * hierarchyContent()
 * hierarchyContent(CATEGORY)
 * hierarchyContent(CATEGORY, 'stocks', entityBody())
 * hierarchyContent(CATEGORY, stopAt(distance(4)), entityBody())
 * ```
 *
 * @author Jan Novotný (novotny@fg.cz), FG Forrest a.s. (c) 2021
 */
@ConstraintDefinition(
	name = "content",
	shortDescription = "The constraint triggers fetching referenced hierarchy entity parent chain and its bodies into returned main entities.",
	supportedIn = ConstraintDomain.ENTITY
)
public class HierarchyContent extends AbstractRequireConstraintContainer implements ReferenceConstraint<RequireConstraint>, SeparateEntityContentRequireContainer, EntityContentRequire {
	@Serial private static final long serialVersionUID = 3374240925555151814L;
	public static final HierarchyContent ALL_hierarchyContent = new HierarchyContent();

	private HierarchyContent(@Nonnull String[] referencedEntityType,
	                         @Nonnull RequireConstraint[] requirements,
	                         @Nonnull Constraint<?>[] additionalChildren) {
		super(referencedEntityType, requirements, additionalChildren);
	}

	@Creator(suffix = "all")
	public HierarchyContent() {
		super();
	}

	public HierarchyContent(@Nonnull String referencedEntityType) {
		super(new String[] { referencedEntityType });
	}

	public HierarchyContent(@Nonnull String... referencedEntityType) {
		super(referencedEntityType);
	}

	public HierarchyContent(@Nonnull String referencedEntityType,
	                        @Nullable EntityFetch entityRequirement) {
		super(new String[] { referencedEntityType }, entityRequirement);
	}

	public HierarchyContent(@Nonnull String referencedEntityType,
	                        @Nullable EntityGroupFetch groupEntityRequirement) {
		super(new String[] { referencedEntityType }, groupEntityRequirement);
	}

	public HierarchyContent(@Nonnull String referencedEntityType,
	                        @Nullable EntityFetch entityRequirement,
	                        @Nullable EntityGroupFetch groupEntityRequirement) {
		super(new String[] { referencedEntityType }, entityRequirement, groupEntityRequirement);
	}

	public HierarchyContent(@Nonnull String[] referencedEntityTypes,
	                        @Nullable EntityFetch entityRequirement) {
		super(referencedEntityTypes, entityRequirement);
	}

	public HierarchyContent(@Nonnull String[] referencedEntityTypes,
	                        @Nullable EntityGroupFetch groupEntityRequirement) {
		super(referencedEntityTypes, groupEntityRequirement);
	}

	public HierarchyContent(@Nonnull String[] referencedEntityTypes,
	                        @Nullable EntityFetch entityRequirement,
	                        @Nullable EntityGroupFetch groupEntityRequirement) {
		super(referencedEntityTypes, entityRequirement, groupEntityRequirement);
	}

	public HierarchyContent(@Nonnull String referencedEntityType,
	                        @Nullable FilterBy filterBy,
	                        @Nullable EntityFetch entityRequirement) {
		super(new String[] { referencedEntityType }, new RequireConstraint[] {entityRequirement}, filterBy);
	}

	public HierarchyContent(@Nonnull String referencedEntityType,
	                        @Nullable FilterBy filterBy,
	                        @Nullable EntityGroupFetch groupEntityRequirement) {
		super(new String[] { referencedEntityType }, new RequireConstraint[] {groupEntityRequirement}, filterBy);
	}

	public HierarchyContent(@Nonnull String referencedEntityType,
	                        @Nullable FilterBy filterBy,
	                        @Nullable EntityFetch entityRequirement,
	                        @Nullable EntityGroupFetch groupEntityRequirement) {
		super(new String[] { referencedEntityType }, new RequireConstraint[] {entityRequirement, groupEntityRequirement}, filterBy);
	}

	public HierarchyContent(@Nonnull String referencedEntityType,
	                        @Nullable FilterBy filterBy) {
		super(new String[] { referencedEntityType }, new RequireConstraint[0], filterBy);
	}

	public HierarchyContent(@Nonnull EntityFetch entityRequirement) {
		super(entityRequirement);
	}

	public HierarchyContent(@Nonnull EntityGroupFetch groupEntityRequirement) {
		super(groupEntityRequirement);
	}

	public HierarchyContent(@Nullable EntityFetch entityRequirement, @Nullable EntityGroupFetch groupEntityRequirement) {
		super(entityRequirement, groupEntityRequirement);
	}

	@Creator
	private HierarchyContent(@Nonnull @Classifier String referencedEntityType,
	                         @Nullable @AdditionalChild FilterBy filterBy,
	                         @Nullable @Child(uniqueChildren = true) EntityRequire... requirements) {
		super(new String[] { referencedEntityType }, requirements, filterBy);
	}

	/**
	 * Returns names of entity types or external entities which hierarchyContent should be loaded along with entity.
	 */
	@Nonnull
	public String[] getReferencedEntityTypes() {
		return Arrays.stream(getArguments())
			.map(String.class::cast)
			.toArray(String[]::new);
	}

	/**
	 * Returns requirements for entities.
	 */
	@Nullable
	public EntityFetch getEntityRequirement() {
		final int childrenLength = getChildren().length;
		if (childrenLength == 2) {
			return (EntityFetch) getChildren()[0];
		} else if (childrenLength == 1) {
			if (getChildren()[0] instanceof final EntityFetch facetEntityRequirement) {
				return facetEntityRequirement;
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	/**
	 * Returns requirements for group entities.
	 */
	@Nullable
	public EntityGroupFetch getGroupEntityRequirement() {
		final int childrenLength = getChildren().length;
		if (childrenLength == 2) {
			return (EntityGroupFetch) getChildren()[1];
		} else if (childrenLength == 1) {
			if (getChildren()[0] instanceof final EntityGroupFetch groupEntityRequirement) {
				return groupEntityRequirement;
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	/**
	 * Returns filter to filter list of returning hierarchyContent.
	 */
	@Nullable
	public FilterBy getFilterBy() {
		return getAdditionalChild(FilterBy.class);
	}

	/**
	 * Returns sorting to order list of returning hierarchyContent.
	 */
	@Nullable
	public OrderBy getOrderBy() {
		return getAdditionalChild(OrderBy.class);
	}

	@Override
	public boolean isApplicable() {
		return true;
	}

	/**
	 * Returns TRUE if all available hierarchyContent were requested to load.
	 */
	public boolean isAllRequested() {
		return ArrayUtils.isEmpty(getArguments());
	}

	@Nonnull
	@SuppressWarnings("unchecked")
	@Override
	public <T extends EntityContentRequire> T combineWith(@Nonnull T anotherRequirement) {
		Assert.isTrue(anotherRequirement instanceof HierarchyContent, "Only hierarchyContent requirement can be combined with this one!");
		if (isAllRequested()) {
			return (T) this;
		} else if (((HierarchyContent) anotherRequirement).isAllRequested()) {
			return anotherRequirement;
		} else {
			return (T) new HierarchyContent(
				Stream.concat(
						Arrays.stream(getArguments()).map(String.class::cast),
						Arrays.stream(anotherRequirement.getArguments()).map(String.class::cast)
					)
					.distinct()
					.toArray(String[]::new)
			);
		}
	}

	@Nonnull
	@Override
	public RequireConstraint getCopyWithNewChildren(@Nonnull RequireConstraint[] children, @Nonnull Constraint<?>[] additionalChildren) {
		if (additionalChildren.length > 2 || (additionalChildren.length == 2 && !FilterConstraint.class.isAssignableFrom(additionalChildren[0].getType()) && !OrderConstraint.class.isAssignableFrom(additionalChildren[1].getType()))) {
			throw new IllegalArgumentException("Expected single or no additional filter and order child query.");
		}
		return new HierarchyContent(getReferencedEntityTypes(), children, additionalChildren);
	}

	@Nonnull
	@Override
	public RequireConstraint cloneWithArguments(@Nonnull Serializable[] newArguments) {
		return new HierarchyContent(
			Arrays.stream(newArguments)
				.map(String.class::cast)
				.toArray(String[]::new),
			getChildren(),
			getAdditionalChildren()
		);
	}
}
