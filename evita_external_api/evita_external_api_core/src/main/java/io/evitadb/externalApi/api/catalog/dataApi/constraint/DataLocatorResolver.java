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

package io.evitadb.externalApi.api.catalog.dataApi.constraint;

import io.evitadb.api.query.descriptor.ConstraintDescriptor;
import io.evitadb.api.query.descriptor.ConstraintDomain;
import io.evitadb.api.query.descriptor.ConstraintPropertyType;
import io.evitadb.api.requestResponse.schema.CatalogSchemaContract;
import io.evitadb.api.requestResponse.schema.ReferenceSchemaContract;
import io.evitadb.externalApi.exception.ExternalApiInternalError;
import io.evitadb.utils.Assert;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.Set;

/**
 * Helper for resolving {@link DataLocator}s usually from other locators.
 *
 * @author Lukáš Hornych, FG Forrest a.s. (c) 2023
 */
@RequiredArgsConstructor
public class DataLocatorResolver {

	@Nonnull private final CatalogSchemaContract catalogSchema;

	/**
	 * Tries to create new {@link DataLocator} that should be used for child constraint based on parent one and desired
	 * domain of child constraints.
	 * Note: not all combinations of parent locators and child domains are possible.
	 */
	@Nonnull
	public DataLocator resolveChildDataLocator(@Nonnull DataLocator parentDataLocator,
	                                           @Nonnull ConstraintDomain desiredChildDomain) {
		if (desiredChildDomain == ConstraintDomain.DEFAULT || parentDataLocator.targetDomain().equals(desiredChildDomain)) {
			return parentDataLocator;
		} else if (Set.of(ConstraintDomain.REFERENCE, ConstraintDomain.HIERARCHY, ConstraintDomain.HIERARCHY_TARGET, ConstraintDomain.FACET).contains(desiredChildDomain)) {
			Assert.isPremiseValid(
				parentDataLocator instanceof DataLocatorWithReference,
				() -> new ExternalApiInternalError("Cannot switch to `" + desiredChildDomain + "` domain because parent domain doesn't contain any reference.")
			);

			final String childEntityType = parentDataLocator.entityType();
			final String childReferenceName = ((DataLocatorWithReference) parentDataLocator).referenceName();
			return switch (desiredChildDomain) {
				case REFERENCE -> {
					Assert.isPremiseValid(
						childReferenceName != null,
						() -> new ExternalApiInternalError("Child domain `" + ConstraintDomain.REFERENCE + "` requires explicit reference name.")
					);
					yield new ReferenceDataLocator(childEntityType, childReferenceName);
				}
				case HIERARCHY -> {
					Assert.isPremiseValid(
						parentDataLocator instanceof HierarchyDataLocator,
						() -> new ExternalApiInternalError("Cannot switch to `" + desiredChildDomain + "` domain because parent domain doesn't locate any hierarchy.")
					);
					yield new HierarchyDataLocator(childEntityType, childReferenceName);
				}
				case HIERARCHY_TARGET -> {
					Assert.isPremiseValid(
						parentDataLocator instanceof HierarchyDataLocator,
						() -> new ExternalApiInternalError("Cannot switch to `" + desiredChildDomain + "` domain because parent domain doesn't locate any hierarchy.")
					);
					if (childReferenceName == null) {
						yield new EntityDataLocator(childEntityType);
					} else {
						yield new ReferenceDataLocator(childEntityType, childReferenceName);
					}
				}
				case FACET -> {
					Assert.isPremiseValid(
						parentDataLocator instanceof FacetDataLocator,
						() -> new ExternalApiInternalError("Cannot switch to `" + desiredChildDomain + "` domain because parent domain doesn't locate any hierarchy.")
					);
					yield new FacetDataLocator(childEntityType, childReferenceName);
				}
				default -> throw new ExternalApiInternalError("Unsupported domain `" + desiredChildDomain + "`.");
			};
		} else {
			if (parentDataLocator instanceof final DataLocatorWithReference dataLocatorWithReference) {
				if (dataLocatorWithReference.referenceName() == null) {
					return switch (desiredChildDomain) {
						case GENERIC -> new GenericDataLocator(dataLocatorWithReference.entityType());
						case ENTITY -> new EntityDataLocator(dataLocatorWithReference.entityType());
						default -> throw new ExternalApiInternalError("Unsupported domain `" + desiredChildDomain + "`.");
					};
				} else {
					final ReferenceSchemaContract referenceSchema = catalogSchema.getEntitySchemaOrThrowException(dataLocatorWithReference.entityType())
						.getReferenceOrThrowException(dataLocatorWithReference.referenceName());

					final String referencedEntityType = referenceSchema.getReferencedEntityType();
					return switch (desiredChildDomain) {
						case GENERIC -> new GenericDataLocator(referencedEntityType);
						case ENTITY -> {
							if (referenceSchema.isReferencedEntityTypeManaged()) {
								yield new EntityDataLocator(referencedEntityType);
							} else {
								yield new ExternalEntityDataLocator(referencedEntityType);
							}
						}
						default -> throw new ExternalApiInternalError("Unsupported domain `" + desiredChildDomain + "`.");
					};
				}
			} else if (parentDataLocator instanceof ExternalEntityDataLocator) {
				return switch (desiredChildDomain) {
					case GENERIC -> new GenericDataLocator(parentDataLocator.entityType());
					case ENTITY -> new ExternalEntityDataLocator(parentDataLocator.entityType());
					default -> throw new ExternalApiInternalError("Unsupported domain `" + desiredChildDomain + "`.");
				};
			} else {
				return switch (desiredChildDomain) {
					case GENERIC -> new GenericDataLocator(parentDataLocator.entityType());
					case ENTITY -> new EntityDataLocator(parentDataLocator.entityType());
					default -> throw new ExternalApiInternalError("Unsupported domain `" + desiredChildDomain + "`.");
				};
			}
		}
	}

	/**
	 * Tries to create new {@link DataLocator} that should be used for child constraint based on parent one and specific
	 * child constraint.
	 * Note: not all combinations of parent locators and child domains are possible.
	 */
	@Nonnull
	public DataLocator resolveChildDataLocator(@Nonnull DataLocator parentDataLocator,
	                                           @Nonnull ConstraintDescriptor constraintDescriptor,
	                                           @Nonnull Optional<String> classifier) {
		return switch (constraintDescriptor.propertyType()) {
			case GENERIC, ATTRIBUTE, ASSOCIATED_DATA, PRICE -> parentDataLocator;
			case ENTITY -> {
				if (parentDataLocator instanceof final DataLocatorWithReference dataLocatorWithReference) {
					if (dataLocatorWithReference.referenceName() == null) {
						yield new EntityDataLocator(dataLocatorWithReference.entityType());
					} else {
						final ReferenceSchemaContract referenceSchema = catalogSchema.getEntitySchemaOrThrowException(parentDataLocator.entityType())
							.getReferenceOrThrowException(dataLocatorWithReference.referenceName());
						if (referenceSchema.isReferencedEntityTypeManaged()) {
							yield new EntityDataLocator(referenceSchema.getReferencedEntityType());
						} else {
							yield new ExternalEntityDataLocator(referenceSchema.getReferencedEntityType());
						}
					}
				} else if (parentDataLocator instanceof ExternalEntityDataLocator) {
					yield parentDataLocator;
				} else {
					yield new EntityDataLocator(parentDataLocator.entityType());
				}
			}
			case REFERENCE, HIERARCHY, FACET -> {
				if (constraintDescriptor.creator().hasClassifier()) {
					// if reference constraint has classifier, it means we need to change context to that reference
					Assert.isPremiseValid(
						!(parentDataLocator instanceof DataLocatorWithReference),
						() -> new ExternalApiInternalError("`" + constraintDescriptor.propertyType() + "` containers cannot have `" + constraintDescriptor.propertyType() + "` constraints with different classifiers.")
					);
					if (constraintDescriptor.creator().hasClassifierParameter() && classifier.isEmpty()) {
						throw new ExternalApiInternalError("Missing required classifier.");
					}
					yield switch (constraintDescriptor.propertyType()) {
						case REFERENCE -> new ReferenceDataLocator(parentDataLocator.entityType(), classifier.orElse(null));
						case HIERARCHY -> new HierarchyDataLocator(parentDataLocator.entityType(), classifier.orElse(null));
						case FACET -> new FacetDataLocator(parentDataLocator.entityType(), classifier.orElse(null));
						default -> throw new ExternalApiInternalError("Unexpected property type `" + constraintDescriptor.propertyType() + "`.");
					};
				} else {
					// if reference constraint doesn't have classifier, it means it is either in another reference container or it is some more general constraint
					if (constraintDescriptor.propertyType().equals(ConstraintPropertyType.REFERENCE)) {
						Assert.isPremiseValid(
							parentDataLocator instanceof ReferenceDataLocator,
							() -> new ExternalApiInternalError("Reference constraints without classifier must be encapsulated into parent reference containers.")
						);
					} else if (constraintDescriptor.propertyType().equals(ConstraintPropertyType.HIERARCHY)) {
						Assert.isPremiseValid(
							parentDataLocator instanceof HierarchyDataLocator,
							() -> new ExternalApiInternalError("Hierarchy constraints without classifier must be encapsulated into parent hierarchy containers.")
						);
					} else if (constraintDescriptor.propertyType().equals(ConstraintPropertyType.FACET)) {
						Assert.isPremiseValid(
							parentDataLocator instanceof GenericDataLocator || parentDataLocator instanceof FacetDataLocator,
							() -> new ExternalApiInternalError("Facet constraints without classifier must be encapsulated into parent generic or facet containers.")
						);
					} else {
						throw new ExternalApiInternalError("Unexpected property type `" + constraintDescriptor.propertyType() + "`.");
					}
					yield parentDataLocator;
				}
			}
			default -> throw new ExternalApiInternalError("Unsupported property type `" + constraintDescriptor.propertyType() + "`.");
		};
	}
}
