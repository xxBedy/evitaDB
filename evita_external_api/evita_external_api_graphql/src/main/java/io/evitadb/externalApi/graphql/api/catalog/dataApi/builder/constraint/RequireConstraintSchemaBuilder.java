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

package io.evitadb.externalApi.graphql.api.catalog.dataApi.builder.constraint;

import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputType;
import io.evitadb.api.query.Constraint;
import io.evitadb.api.query.descriptor.ConstraintCreator.ChildParameterDescriptor;
import io.evitadb.api.query.descriptor.ConstraintDescriptor;
import io.evitadb.api.query.descriptor.ConstraintDescriptorProvider;
import io.evitadb.api.query.descriptor.ConstraintType;
import io.evitadb.api.query.require.FacetGroupsConjunction;
import io.evitadb.api.query.require.FacetGroupsDisjunction;
import io.evitadb.api.query.require.FacetGroupsNegation;
import io.evitadb.api.query.require.PriceType;
import io.evitadb.api.query.require.Require;
import io.evitadb.api.requestResponse.schema.AttributeSchemaContract;
import io.evitadb.externalApi.api.catalog.dataApi.builder.constraint.ConstraintSchemaBuilder;
import io.evitadb.externalApi.api.catalog.dataApi.constraint.GenericDataLocator;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Implementation of {@link GraphQLConstraintSchemaBuilder} for building require query tree starting from {@link io.evitadb.api.query.require.Require}.
 *
 * @author Lukáš Hornych, FG Forrest a.s. (c) 2022
 */
public class RequireConstraintSchemaBuilder extends GraphQLConstraintSchemaBuilder {

	/**
	 * Because most of require constraints are resolved from client-defined output objects structure we need only
	 * few left constraints that cannot be resolved from output structure because they usually change whole Evita
	 * query behaviour.
	 */
	private static final Set<Class<? extends Constraint<?>>> MAIN_REQUIRE_ALLOWED_CONSTRAINTS = Set.of(
		FacetGroupsConjunction.class,
		FacetGroupsDisjunction.class,
		FacetGroupsNegation.class,
		PriceType.class
	);

	protected RequireConstraintSchemaBuilder(@Nonnull GraphQLConstraintSchemaBuildingContext sharedContext,
	                                         @Nonnull Map<ConstraintType, AtomicReference<? extends ConstraintSchemaBuilder<GraphQLConstraintSchemaBuildingContext, GraphQLInputType, GraphQLInputType, GraphQLInputObjectField>>> additionalBuilders,
	                                         @Nonnull Set<Class<? extends Constraint<?>>> allowedConstraints) {
		super(
			sharedContext, additionalBuilders, allowedConstraints, Set.of());
	}

	/**
	 * Creates schema builder for require container used in main query. This require is limited because rest of requires
	 * are resolved from input fields.
	 */
	public static RequireConstraintSchemaBuilder forMainRequire(@Nonnull GraphQLConstraintSchemaBuildingContext sharedContext,
	                                                            @Nonnull AtomicReference<FilterConstraintSchemaBuilder> filterConstraintSchemaBuilder) {
		return new RequireConstraintSchemaBuilder(
			sharedContext,
			Map.of(ConstraintType.FILTER, filterConstraintSchemaBuilder),
			MAIN_REQUIRE_ALLOWED_CONSTRAINTS
		);
	}

	/**
	 * Creates schema builder for require containers used in many places in extra result fields which often all constraints.
	 */
	public static RequireConstraintSchemaBuilder forExtraResultsRequire(@Nonnull GraphQLConstraintSchemaBuildingContext sharedContext,
	                                                                    @Nonnull AtomicReference<FilterConstraintSchemaBuilder> filterConstraintSchemaBuilder) {
		return new RequireConstraintSchemaBuilder(
			sharedContext,
			Map.of(ConstraintType.FILTER, filterConstraintSchemaBuilder),
			Set.of()
		);
	}

	@Nonnull
	public GraphQLInputType build(@Nonnull String rootEntityType) {
		return build(new GenericDataLocator(rootEntityType));
	}

	@Nonnull
	@Override
	protected ConstraintType getConstraintType() {
		return ConstraintType.REQUIRE;
	}

	@Nonnull
	@Override
	protected ConstraintDescriptor getDefaultRootConstraintContainerDescriptor() {
		return ConstraintDescriptorProvider.getConstraint(Require.class);
	}

	@Nonnull
	@Override
	protected String getContainerObjectTypeName() {
		return "RequireContainer";
	}

	@Nonnull
	@Override
	protected Predicate<AttributeSchemaContract> getAttributeSchemaFilter() {
		return attributeSchema -> true;
	}

	@Override
	protected boolean isChildrenUnique(@Nonnull ChildParameterDescriptor childParameter) {
		// We don't want list of wrapper container because in "require" constraints there are no generic conjunction
		// containers (and also there is currently no need to support that). Essentially, we want require constraints
		// with children to act as if they were `ChildParameterDescriptor#uniqueChildren` as, although they are
		// originally not, in case of GraphQL where classifiers are in keys those fields are in fact unique children.
		return true;
	}
}
