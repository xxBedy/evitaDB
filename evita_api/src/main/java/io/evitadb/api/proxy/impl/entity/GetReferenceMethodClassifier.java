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

package io.evitadb.api.proxy.impl.entity;

import io.evitadb.api.exception.EntityClassInvalidException;
import io.evitadb.api.proxy.ProxyFactory;
import io.evitadb.api.proxy.ProxyReferenceFactory;
import io.evitadb.api.proxy.impl.ProxyUtils;
import io.evitadb.api.proxy.impl.ProxyUtils.OptionalProducingOperator;
import io.evitadb.api.proxy.impl.SealedEntityProxyState;
import io.evitadb.api.requestResponse.data.EntityContract;
import io.evitadb.api.requestResponse.data.ReferenceContract;
import io.evitadb.api.requestResponse.data.SealedEntity;
import io.evitadb.api.requestResponse.data.annotation.Entity;
import io.evitadb.api.requestResponse.data.annotation.EntityRef;
import io.evitadb.api.requestResponse.data.annotation.Reference;
import io.evitadb.api.requestResponse.data.annotation.ReferenceRef;
import io.evitadb.api.requestResponse.data.structure.EntityReference;
import io.evitadb.api.requestResponse.data.structure.ReferenceDecorator;
import io.evitadb.api.requestResponse.schema.EntitySchemaContract;
import io.evitadb.api.requestResponse.schema.ReferenceSchemaContract;
import io.evitadb.dataType.EvitaDataTypes;
import io.evitadb.function.ExceptionRethrowingFunction;
import io.evitadb.utils.Assert;
import io.evitadb.utils.ClassUtils;
import io.evitadb.utils.CollectorUtils;
import io.evitadb.utils.NamingConvention;
import io.evitadb.utils.ReflectionLookup;
import one.edee.oss.proxycian.CurriedMethodContextInvocationHandler;
import one.edee.oss.proxycian.DirectMethodClassification;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static io.evitadb.api.proxy.impl.ProxyUtils.getResolvedTypes;
import static java.util.Optional.ofNullable;

/**
 * Identifies methods that are used to get reference from a sealed entity and provides their implementation.
 *
 * @author Jan Novotný (novotny@fg.cz), FG Forrest a.s. (c) 2023
 */
public class GetReferenceMethodClassifier extends DirectMethodClassification<Object, SealedEntityProxyState> {
	/**
	 * We may reuse singleton instance since advice is stateless.
	 */
	public static final GetReferenceMethodClassifier INSTANCE = new GetReferenceMethodClassifier();

	/**
	 * Tries to identify parent from the class field related to the constructor parameter.
	 *
	 * @param expectedType     class the constructor belongs to
	 * @param parameter        constructor parameter
	 * @param reflectionLookup reflection lookup
	 * @return attribute name derived from the annotation if found
	 */
	@Nullable
	public static <T> ExceptionRethrowingFunction<EntityContract, Object> getExtractorIfPossible(
		@Nonnull EntitySchemaContract schema,
		@Nonnull Class<T> expectedType,
		@Nonnull Parameter parameter,
		@Nonnull ReflectionLookup reflectionLookup,
		@Nonnull ProxyFactory proxyFactory,
		@Nonnull ProxyReferenceFactory proxyReferenceFactory
	) {
		// now we need to identify reference schema that is being requested
		final ReferenceSchemaContract referenceSchema = getReferenceSchema(
			expectedType, parameter, reflectionLookup, schema
		);
		// if not found, this method is not classified by this implementation
		if (referenceSchema == null) {
			return null;
		} else {
			// finally provide implementation that will retrieve the reference or reference entity from the entity
			final String referenceName = referenceSchema.getName();
			// now we need to identify the return type
			@SuppressWarnings("rawtypes") final Class returnType = parameter.getType();
			final Class<?>[] resolvedTypes = getResolvedTypes(parameter, expectedType);

			@SuppressWarnings("rawtypes") final Class collectionType;
			@SuppressWarnings("rawtypes") final Class itemType;
			if (Collection.class.equals(resolvedTypes[0]) || List.class.isAssignableFrom(resolvedTypes[0]) || Set.class.isAssignableFrom(resolvedTypes[0])) {
				collectionType = resolvedTypes[0];
				itemType = resolvedTypes.length > 1 ? resolvedTypes[1] : EntityReference.class;
			} else if (resolvedTypes[0].isArray()) {
				collectionType = resolvedTypes[0];
				itemType = returnType.getComponentType();
			} else {
				collectionType = null;
				itemType = resolvedTypes[0];
			}

			// return the appropriate result
			final Entity entityInstance = reflectionLookup.getClassAnnotation(itemType, Entity.class);
			final EntityRef entityRefInstance = reflectionLookup.getClassAnnotation(itemType, EntityRef.class);
			if (itemType.equals(EntityReference.class)) {
				return getEntityReference(referenceName, collectionType);
			} else if (Number.class.isAssignableFrom(EvitaDataTypes.toWrappedForm(itemType))) {
				//noinspection unchecked
				return getEntityId(referenceName, collectionType, itemType);
			} else if (entityInstance != null) {
				return getReferencedEntity(schema, referenceSchema, entityInstance.name(), collectionType, itemType, proxyFactory);
			} else if (entityRefInstance != null) {
				return getReferencedEntity(schema, referenceSchema, entityRefInstance.value(), collectionType, itemType, proxyFactory);
			} else {
				return getReference(referenceName, collectionType, itemType, proxyReferenceFactory);
			}
		}
	}

	/**
	 * Retrieves appropriate reference schema from the annotations on the method. If no Evita annotation is found
	 * it tries to match the attribute name by the name of the method.
	 */
	@Nullable
	private static ReferenceSchemaContract getReferenceSchema(
		@Nonnull Method method,
		@Nonnull ReflectionLookup reflectionLookup,
		@Nonnull EntitySchemaContract entitySchema
	) {
		final Reference referenceInstance = reflectionLookup.getAnnotationInstanceForProperty(method, Reference.class);
		final ReferenceRef referenceRefInstance = reflectionLookup.getAnnotationInstanceForProperty(method, ReferenceRef.class);
		if (referenceInstance != null) {
			return entitySchema.getReferenceOrThrowException(referenceInstance.name());
		} else if (referenceRefInstance != null) {
			return entitySchema.getReferenceOrThrowException(referenceRefInstance.value());
		} else if (!reflectionLookup.hasAnnotationInSamePackage(method, Reference.class) && ClassUtils.isAbstract(method)) {
			final Optional<String> referenceName = ReflectionLookup.getPropertyNameFromMethodNameIfPossible(method.getName());
			return referenceName
				.flatMap(it -> entitySchema.getReferenceByName(it, NamingConvention.CAMEL_CASE))
				.orElse(null);
		} else {
			return null;
		}
	}

	/**
	 * Retrieves appropriate reference schema from the annotations on the parameter. If no Evita annotation is found
	 * it tries to match the attribute name by the name of the parameter.
	 */
	@Nullable
	private static ReferenceSchemaContract getReferenceSchema(
		@Nonnull Class<?> expectedType,
		@Nonnull Parameter parameter,
		@Nonnull ReflectionLookup reflectionLookup,
		@Nonnull EntitySchemaContract entitySchema
	) {
		final String parameterName = parameter.getName();
		final Reference referenceInstance = reflectionLookup.getAnnotationInstanceForProperty(expectedType, parameterName, Reference.class);
		final ReferenceRef referenceRefInstance = reflectionLookup.getAnnotationInstanceForProperty(expectedType, parameterName, ReferenceRef.class);
		if (referenceInstance != null) {
			return entitySchema.getReferenceOrThrowException(referenceInstance.name());
		} else if (referenceRefInstance != null) {
			return entitySchema.getReferenceOrThrowException(referenceRefInstance.value());
		} else {
			final Optional<String> referenceName = ReflectionLookup.getPropertyNameFromMethodNameIfPossible(parameter.getName());
			return referenceName
				.flatMap(it -> entitySchema.getReferenceByName(it, NamingConvention.CAMEL_CASE))
				.orElse(null);
		}
	}

	/**
	 * Method wraps passed {@link ReferenceContract} into a custom proxy instance of particular type.
	 */
	@Nullable
	private static <T> T createProxy(
		@Nonnull String entityName,
		@Nonnull String referenceName,
		@Nonnull Class<T> itemType,
		@Nonnull ProxyFactory proxyFactory,
		@Nonnull ReferenceContract reference,
		@Nonnull Function<ReferenceDecorator, Optional<SealedEntity>> entityExtractor
	) {
		Assert.isTrue(
			reference instanceof ReferenceDecorator,
			() -> "Entity `" + entityName + "` references of type `" +
				referenceName + "` were not fetched with `entityFetch` requirement. " +
				"Related entity body is not available."
		);
		final ReferenceDecorator referenceDecorator = (ReferenceDecorator) reference;
		return entityExtractor.apply(referenceDecorator)
			.map(it -> proxyFactory.createEntityProxy(itemType, it))
			.orElse(null);
	}

	/**
	 * Creates an implementation of the method returning a single referenced entity wrapped into {@link EntityReference} object.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> singleEntityReferenceResult(
		@Nonnull String cleanReferenceName,
		@Nonnull BiFunction<EntityContract, String, Collection<ReferenceContract>> referenceExtractor,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		return (entityClassifier, theMethod, args, theState, invokeSuper) -> {
			final Collection<ReferenceContract> references = referenceExtractor.apply(theState.getEntity(), cleanReferenceName);
			if (references == null || references.isEmpty()) {
				return resultWrapper.apply(null);
			} else {
				final ReferenceContract theReference = references.iterator().next();
				return resultWrapper.apply(new EntityReference(theReference.getReferencedEntityType(), theReference.getReferencedPrimaryKey()));
			}
		};
	}

	/**
	 * Creates an implementation of the method returning a single referenced entity wrapped into {@link EntityReference} object.
	 */
	@Nonnull
	private static ExceptionRethrowingFunction<EntityContract, Object> singleEntityReferenceResult(
		@Nonnull String cleanReferenceName
	) {
		return sealedEntity -> {
			final Collection<ReferenceContract> references = sealedEntity.getReferences(cleanReferenceName);
			if (references.isEmpty()) {
				return null;
			} else {
				final ReferenceContract theReference = references.iterator().next();
				return new EntityReference(theReference.getReferencedEntityType(), theReference.getReferencedPrimaryKey());
			}
		};
	}

	/**
	 * Creates an implementation of the method returning a list of referenced entities wrapped into {@link EntityReference} object.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> listOfEntityReferencesResult(
		@Nonnull String cleanReferenceName,
		@Nonnull BiFunction<EntityContract, String, Collection<ReferenceContract>> referenceExtractor,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		return (entityClassifier, theMethod, args, theState, invokeSuper) ->
			resultWrapper.apply(
				ofNullable(referenceExtractor.apply(theState.getEntity(), cleanReferenceName))
					.map(refs -> refs.stream()
						.map(it -> new EntityReference(it.getReferencedEntityType(), it.getReferencedPrimaryKey()))
						.toList()
					).orElse(Collections.emptyList())
			);
	}

	/**
	 * Creates an implementation of the method returning a list of referenced entities wrapped into {@link EntityReference} object.
	 */
	@Nonnull
	private static ExceptionRethrowingFunction<EntityContract, Object> listOfEntityReferencesResult(
		@Nonnull String referenceName
	) {
		return sealedEntity -> {
			if (sealedEntity.referencesAvailable(referenceName)) {
				return sealedEntity.getReferences(referenceName)
					.stream()
					.map(it -> new EntityReference(it.getReferencedEntityType(), it.getReferencedPrimaryKey()))
					.toList();
			} else {
				return Collections.emptyList();
			}
		};
	}

	/**
	 * Creates an implementation of the method returning a set of referenced entities wrapped into {@link EntityReference} object.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> setOfEntityReferencesResult(
		@Nonnull String cleanReferenceName,
		@Nonnull BiFunction<EntityContract, String, Collection<ReferenceContract>> referenceExtractor,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		return (entityClassifier, theMethod, args, theState, invokeSuper) ->
			resultWrapper.apply(
				ofNullable(referenceExtractor.apply(theState.getEntity(), cleanReferenceName))
					.map(
						refs -> refs.stream()
							.map(it -> new EntityReference(it.getReferencedEntityType(), it.getReferencedPrimaryKey()))
							.collect(CollectorUtils.toUnmodifiableLinkedHashSet())
					).orElse(Collections.emptySet())
			);
	}

	/**
	 * Creates an implementation of the method returning a set of referenced entities wrapped into {@link EntityReference} object.
	 */
	@Nonnull
	private static ExceptionRethrowingFunction<EntityContract, Object> setOfEntityReferencesResult(
		@Nonnull String referenceName
	) {
		return sealedEntity -> {
			if (sealedEntity.referencesAvailable(referenceName)) {
				return sealedEntity.getReferences(referenceName)
					.stream()
					.map(it -> new EntityReference(it.getReferencedEntityType(), it.getReferencedPrimaryKey()))
					.collect(CollectorUtils.toUnmodifiableLinkedHashSet());
			} else {
				return Collections.emptySet();
			}
		};
	}

	/**
	 * Creates an implementation of the method returning an array of referenced entities wrapped into {@link EntityReference} object.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> arrayOfEntityReferencesResult(
		@Nonnull String cleanReferenceName,
		@Nonnull BiFunction<EntityContract, String, Collection<ReferenceContract>> referenceExtractor,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		return (entityClassifier, theMethod, args, theState, invokeSuper) ->
			resultWrapper.apply(
				ofNullable(referenceExtractor.apply(theState.getEntity(), cleanReferenceName))
					.map(refs -> refs.stream()
						.map(it -> new EntityReference(it.getReferencedEntityType(), it.getReferencedPrimaryKey()))
						.toArray(EntityReference[]::new)
					).orElse(null)
			);
	}

	/**
	 * Creates an implementation of the method returning an array of referenced entities wrapped into {@link EntityReference} object.
	 */
	@Nonnull
	private static ExceptionRethrowingFunction<EntityContract, Object> arrayOfEntityReferencesResult(
		@Nonnull String referenceName
	) {
		return sealedEntity -> {
			if (sealedEntity.referencesAvailable(referenceName)) {
				return sealedEntity.getReferences(referenceName)
					.stream()
					.map(it -> new EntityReference(it.getReferencedEntityType(), it.getReferencedPrimaryKey()))
					.toArray(EntityReference[]::new);
			} else {
				return null;
			}
		};
	}

	/**
	 * Creates an implementation of the method returning an integer representing a primary key of referenced entity.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> singleEntityIdResult(
		@Nonnull String cleanReferenceName,
		@Nonnull BiFunction<EntityContract, String, Collection<ReferenceContract>> referenceExtractor,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		return (entityClassifier, theMethod, args, theState, invokeSuper) -> {
			final Collection<ReferenceContract> references = referenceExtractor.apply(theState.getEntity(), cleanReferenceName);
			if (references == null || references.isEmpty()) {
				return resultWrapper.apply(null);
			} else {
				final ReferenceContract theReference = references.iterator().next();
				return resultWrapper.apply(theReference.getReferencedPrimaryKey());
			}
		};
	}

	/**
	 * Creates an implementation of the method returning an integer representing a primary key of referenced entity.
	 */
	@Nonnull
	private static ExceptionRethrowingFunction<EntityContract, Object> singleEntityIdResult(
		@Nonnull String referenceName
	) {
		return sealedEntity -> {
			if (sealedEntity.referencesAvailable(referenceName)) {
				final Collection<ReferenceContract> references = sealedEntity.getReferences(referenceName);
				if (references.isEmpty()) {
					return null;
				} else {
					final ReferenceContract theReference = references.iterator().next();
					return theReference.getReferencedPrimaryKey();
				}
			} else {
				return null;
			}
		};
	}

	/**
	 * Creates an implementation of the method returning a list of integers representing a primary keys of referenced entities.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> listOfEntityIdsResult(
		@Nonnull String cleanReferenceName,
		@Nonnull BiFunction<EntityContract, String, Collection<ReferenceContract>> referenceExtractor,
		@Nonnull Function<Object, Object> resultWrapper
	) {
		return (entityClassifier, theMethod, args, theState, invokeSuper) ->
			resultWrapper.apply(
				ofNullable(referenceExtractor.apply(theState.getEntity(), cleanReferenceName))
					.map(refs -> refs.stream()
						.map(ReferenceContract::getReferencedPrimaryKey)
						.toList()
					).orElse(Collections.emptyList())
			);
	}

	/**
	 * Creates an implementation of the method returning a list of integers representing a primary keys of referenced entities.
	 */
	@Nonnull
	private static ExceptionRethrowingFunction<EntityContract, Object> listOfEntityIdsResult(
		@Nonnull String referenceName
	) {
		return sealedEntity -> {
			if (sealedEntity.referencesAvailable(referenceName)) {
				return sealedEntity.getReferences(referenceName)
					.stream()
					.map(ReferenceContract::getReferencedPrimaryKey)
					.toList();
			} else {
				return Collections.emptyList();
			}
		};
	}

	/**
	 * Creates an implementation of the method returning a set of integers representing a primary keys of referenced entities.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> setOfEntityIdsResult(
		@Nonnull String cleanReferenceName,
		@Nonnull BiFunction<EntityContract, String, Collection<ReferenceContract>> referenceExtractor,
		@Nonnull Function<Object, Object> resultWrapper
	) {
		return (entityClassifier, theMethod, args, theState, invokeSuper) ->
			resultWrapper.apply(
				ofNullable(referenceExtractor.apply(theState.getEntity(), cleanReferenceName))
					.map(refs -> refs.stream()
						.map(ReferenceContract::getReferencedPrimaryKey)
						.collect(CollectorUtils.toUnmodifiableLinkedHashSet())
					).orElse(Collections.emptySet())
			);
	}

	/**
	 * Creates an implementation of the method returning a set of integers representing a primary keys of referenced entities.
	 */
	@Nonnull
	private static ExceptionRethrowingFunction<EntityContract, Object> setOfEntityIdsResult(
		@Nonnull String referenceName
	) {
		return sealedEntity -> {
			if (sealedEntity.referencesAvailable(referenceName)) {
				return sealedEntity.getReferences(referenceName)
					.stream()
					.map(ReferenceContract::getReferencedPrimaryKey)
					.collect(CollectorUtils.toUnmodifiableLinkedHashSet());
			} else {
				return Collections.emptySet();
			}
		};
	}

	/**
	 * Creates an implementation of the method returning an array of integers representing a primary keys of referenced entities.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> arrayOfEntityIdsResult(
		@Nonnull String cleanReferenceName,
		@Nonnull Class<? extends Serializable> itemType,
		@Nonnull BiFunction<EntityContract, String, Collection<ReferenceContract>> referenceExtractor,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		if (itemType.isPrimitive()) {
			Assert.isTrue(
				int.class.equals(itemType),
				() -> "Only array of integers (`int[]`) is supported, but method returns `" + itemType + "[]`!"
			);
			return (entityClassifier, theMethod, args, theState, invokeSuper) ->
				resultWrapper.apply(
					ofNullable(referenceExtractor.apply(theState.getEntity(), cleanReferenceName))
						.map(
							refs -> refs.stream().filter(Objects::nonNull)
								.mapToInt(ReferenceContract::getReferencedPrimaryKey)
								.toArray()
						).orElse(null)
				);
		} else {
			return (entityClassifier, theMethod, args, theState, invokeSuper) ->
				resultWrapper.apply(
					ofNullable(referenceExtractor.apply(theState.getEntity(), cleanReferenceName))
						.map(
							refs -> refs.stream().filter(Objects::nonNull)
								.map(ReferenceContract::getReferencedPrimaryKey)
								.map(it -> EvitaDataTypes.toTargetType(it, itemType))
								.toArray(count -> (Object[]) Array.newInstance(itemType, count))
						).orElse(null)
				);
		}
	}

	/**
	 * Creates an implementation of the method returning an array of integers representing a primary keys of referenced entities.
	 */
	@Nonnull
	private static ExceptionRethrowingFunction<EntityContract, Object> arrayOfEntityIdsResult(
		@Nonnull String referenceName,
		@Nonnull Class<? extends Serializable> itemType
	) {
		if (itemType.isPrimitive()) {
			Assert.isTrue(
				int.class.equals(itemType),
				() -> "Only array of integers (`int[]`) is supported, but method returns `" + itemType + "[]`!"
			);
			return sealedEntity -> {
				if (sealedEntity.referencesAvailable(referenceName)) {
					return sealedEntity.getReferences(referenceName)
						.stream()
						.mapToInt(ReferenceContract::getReferencedPrimaryKey)
						.toArray();
				} else {
					return null;
				}
			};
		} else {
			return sealedEntity -> {
				if (sealedEntity.referencesAvailable(referenceName)) {
					return sealedEntity.getReferences(referenceName)
						.stream()
						.map(ReferenceContract::getReferencedPrimaryKey)
						.map(it -> EvitaDataTypes.toTargetType(it, itemType))
						.toArray(count -> (Object[]) Array.newInstance(itemType, count));
				} else {
					return null;
				}
			};
		}
	}

	/**
	 * Creates an implementation of the method returning a single referenced entity wrapped into a custom proxy instance.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> singleEntityResult(
		@Nonnull String entityName,
		@Nonnull String referenceName,
		@Nonnull Class<?> itemType,
		@Nonnull BiFunction<EntityContract, String, Collection<ReferenceContract>> referenceExtractor,
		@Nonnull Function<ReferenceDecorator, Optional<SealedEntity>> entityExtractor,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		return (entityClassifier, theMethod, args, theState, invokeSuper) -> {
			final Collection<ReferenceContract> references = referenceExtractor.apply(theState.getEntity(), referenceName);
			if (references == null || references.isEmpty()) {
				return resultWrapper.apply(null);
			} else {
				return resultWrapper.apply(
					createProxy(entityName, referenceName, itemType, theState, references.iterator().next(), entityExtractor)
				);
			}
		};
	}

	/**
	 * Creates an implementation of the method returning a single referenced entity wrapped into a custom proxy instance.
	 */
	@Nonnull
	private static ExceptionRethrowingFunction<EntityContract, Object> singleEntityResult(
		@Nonnull String entityName,
		@Nonnull String referenceName,
		@Nonnull Class<?> itemType,
		@Nonnull Function<ReferenceDecorator, Optional<SealedEntity>> entityExtractor,
		@Nonnull ProxyFactory proxyFactory
	) {
		return sealedEntity -> {
			if (sealedEntity.referencesAvailable(referenceName)) {
				final Collection<ReferenceContract> references = sealedEntity.getReferences(referenceName);
				if (references.isEmpty()) {
					return null;
				} else {
					return createProxy(entityName, referenceName, itemType, proxyFactory, references.iterator().next(), entityExtractor);
				}
			} else {
				return null;
			}
		};
	}

	/**
	 * Creates an implementation of the method returning a list of referenced entities wrapped into a custom proxy instances.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> listOfEntityResult(
		@Nonnull String entityName,
		@Nonnull String referenceName,
		@Nonnull Class<?> itemType,
		@Nonnull BiFunction<EntityContract, String, Collection<ReferenceContract>> referenceExtractor,
		@Nonnull Function<ReferenceDecorator, Optional<SealedEntity>> entityExtractor,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		return (entityClassifier, theMethod, args, theState, invokeSuper) ->
			resultWrapper.apply(
				ofNullable(referenceExtractor.apply(theState.getEntity(), referenceName))
					.map(
						refs -> refs.stream()
							.map(it -> createProxy(entityName, referenceName, itemType, theState, it, entityExtractor))
							.filter(Objects::nonNull)
							.toList()
					).orElse(Collections.emptyList())
			);
	}

	/**
	 * Creates an implementation of the method returning a list of referenced entities wrapped into a custom proxy instances.
	 */
	@Nonnull
	private static ExceptionRethrowingFunction<EntityContract, Object> listOfEntityResult(
		@Nonnull String entityName,
		@Nonnull String referenceName,
		@Nonnull Class<?> itemType,
		@Nonnull Function<ReferenceDecorator, Optional<SealedEntity>> entityExtractor,
		@Nonnull ProxyFactory proxyFactory
	) {
		return sealedEntity -> {
			if (sealedEntity.referencesAvailable(referenceName)) {
				final Collection<ReferenceContract> references = sealedEntity.getReferences(referenceName);
				if (references.isEmpty()) {
					return Collections.emptyList();
				} else {
					return references.stream()
						.map(it -> createProxy(entityName, referenceName, itemType, proxyFactory, it, entityExtractor))
						.filter(Objects::nonNull)
						.toList();
				}
			} else {
				return Collections.emptyList();
			}
		};
	}

	/**
	 * Creates an implementation of the method returning a set of referenced entities wrapped into a custom proxy instances.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> setOfEntityResult(
		@Nonnull String entityName,
		@Nonnull String referenceName,
		@Nonnull Class<?> itemType,
		@Nonnull BiFunction<EntityContract, String, Collection<ReferenceContract>> referenceExtractor,
		@Nonnull Function<ReferenceDecorator, Optional<SealedEntity>> entityExtractor,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		return (entityClassifier, theMethod, args, theState, invokeSuper) ->
			resultWrapper.apply(
				ofNullable(referenceExtractor.apply(theState.getEntity(), referenceName))
					.map(
						refs -> refs.stream()
							.map(it -> createProxy(entityName, referenceName, itemType, theState, it, entityExtractor))
							.filter(Objects::nonNull)
							.collect(CollectorUtils.toUnmodifiableLinkedHashSet())
					).orElse(Collections.emptySet())
			);
	}

	/**
	 * Creates an implementation of the method returning a set of referenced entities wrapped into a custom proxy instances.
	 */
	@Nonnull
	private static ExceptionRethrowingFunction<EntityContract, Object> setOfEntityResult(
		@Nonnull String entityName,
		@Nonnull String referenceName,
		@Nonnull Class<?> itemType,
		@Nonnull Function<ReferenceDecorator, Optional<SealedEntity>> entityExtractor,
		@Nonnull ProxyFactory proxyFactory
	) {
		return sealedEntity -> {
			if (sealedEntity.referencesAvailable(referenceName)) {
				final Collection<ReferenceContract> references = sealedEntity.getReferences(referenceName);
				if (references.isEmpty()) {
					return Collections.emptySet();
				} else {
					return references.stream()
						.map(it -> createProxy(entityName, referenceName, itemType, proxyFactory, it, entityExtractor))
						.filter(Objects::nonNull)
						.collect(CollectorUtils.toUnmodifiableLinkedHashSet());
				}
			} else {
				return Collections.emptySet();
			}
		};
	}

	/**
	 * Creates an implementation of the method returning an array of referenced entities wrapped into a custom proxy instances.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> arrayOfEntityResult(
		@Nonnull String entityName,
		@Nonnull String referenceName,
		@Nonnull Class<?> itemType,
		@Nonnull BiFunction<EntityContract, String, Collection<ReferenceContract>> referenceExtractor,
		@Nonnull Function<ReferenceDecorator, Optional<SealedEntity>> entityExtractor,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		return (entityClassifier, theMethod, args, theState, invokeSuper) ->
			resultWrapper.apply(
				ofNullable(referenceExtractor.apply(theState.getEntity(), referenceName))
					.map(
						refs -> refs.stream()
							.map(it -> createProxy(entityName, referenceName, itemType, theState, it, entityExtractor))
							.filter(Objects::nonNull)
							.toArray(count -> (Object[]) Array.newInstance(itemType, count))
					).orElse(null)
			);
	}

	/**
	 * Creates an implementation of the method returning an array of referenced entities wrapped into a custom proxy instances.
	 */
	@Nonnull
	private static ExceptionRethrowingFunction<EntityContract, Object> arrayOfEntityResult(
		@Nonnull String entityName,
		@Nonnull String referenceName,
		@Nonnull Class<?> itemType,
		@Nonnull Function<ReferenceDecorator, Optional<SealedEntity>> entityExtractor,
		@Nonnull ProxyFactory proxyFactory
	) {
		return sealedEntity -> {
			if (sealedEntity.referencesAvailable(referenceName)) {
				final Collection<ReferenceContract> references = sealedEntity.getReferences(referenceName);
				if (references.isEmpty()) {
					return null;
				} else {
					return references.stream()
						.map(it -> createProxy(entityName, referenceName, itemType, proxyFactory, it, entityExtractor))
						.filter(Objects::nonNull)
						.toArray(count -> (Object[]) Array.newInstance(itemType, count));
				}
			} else {
				return null;
			}
		};
	}

	/**
	 * Creates an implementation of the method returning a single reference wrapped into a custom proxy instance.
	 */
	@Nonnull
	private static ExceptionRethrowingFunction<EntityContract, Object> singleReferenceResult(
		@Nonnull String referenceName,
		@Nonnull Class<?> itemType,
		@Nonnull ProxyReferenceFactory proxyReferenceFactory
	) {
		return sealedEntity -> {
			if (sealedEntity.referencesAvailable(referenceName)) {
				final Collection<ReferenceContract> references = sealedEntity.getReferences(referenceName);
				if (references.isEmpty()) {
					return null;
				} else {
					return proxyReferenceFactory.createEntityReferenceProxy(itemType, sealedEntity, references.iterator().next());
				}
			} else {
				return null;
			}
		};
	}

	/**
	 * Creates an implementation of the method returning a single reference wrapped into a custom proxy instance.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> singleReferenceResult(
		@Nonnull String referenceName,
		@Nonnull Class<?> itemType,
		@Nonnull BiFunction<EntityContract, String, Collection<ReferenceContract>> referenceExtractor,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		return (entityClassifier, theMethod, args, theState, invokeSuper) -> {
			final EntityContract entity = theState.getEntity();
			final Collection<ReferenceContract> references = referenceExtractor.apply(entity, referenceName);
			if (references == null || references.isEmpty()) {
				return resultWrapper.apply(null);
			} else {
				return resultWrapper.apply(
					theState.createEntityReferenceProxy(itemType, entity, references.iterator().next())
				);
			}
		};
	}

	/**
	 * Creates an implementation of the method returning a list of references wrapped into a custom proxy instances.
	 */
	@Nonnull
	private static ExceptionRethrowingFunction<EntityContract, Object> listOfReferenceResult(
		@Nonnull String referenceName,
		@Nonnull Class<?> itemType,
		@Nonnull ProxyReferenceFactory proxyReferenceFactory
	) {
		return sealedEntity -> {
			if (sealedEntity.referencesAvailable(referenceName)) {
				return sealedEntity.getReferences(referenceName)
					.stream()
					.map(it -> proxyReferenceFactory.createEntityReferenceProxy(itemType, sealedEntity, it))
					.toList();
			} else {
				return Collections.emptyList();
			}
		};
	}

	/**
	 * Creates an implementation of the method returning a list of references wrapped into a custom proxy instances.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> listOfReferenceResult(
		@Nonnull String cleanReferenceName,
		@Nonnull Class<?> itemType,
		@Nonnull BiFunction<EntityContract, String, Collection<ReferenceContract>> referenceExtractor,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		return (entityClassifier, theMethod, args, theState, invokeSuper) -> {
			final EntityContract entity = theState.getEntity();
			return resultWrapper.apply(
				ofNullable(referenceExtractor.apply(entity, cleanReferenceName))
					.map(
						refs -> refs.stream()
							.map(it -> theState.createEntityReferenceProxy(itemType, entity, it))
							.toList()
					).orElse(Collections.emptyList())
			);
		};
	}

	/**
	 * Creates an implementation of the method returning a set of references wrapped into a custom proxy instances.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> setOfReferenceResult(
		@Nonnull String cleanReferenceName,
		@Nonnull Class<?> itemType,
		@Nonnull BiFunction<EntityContract, String, Collection<ReferenceContract>> referenceExtractor,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		return (entityClassifier, theMethod, args, theState, invokeSuper) -> {
			final EntityContract entity = theState.getEntity();
			return resultWrapper.apply(
				ofNullable(referenceExtractor.apply(entity, cleanReferenceName))
					.map(
						refs -> refs.stream()
							.map(it -> theState.createEntityReferenceProxy(itemType, entity, it))
							.collect(CollectorUtils.toUnmodifiableLinkedHashSet())
					).orElse(Collections.emptySet())
			);
		};
	}

	/**
	 * Creates an implementation of the method returning a set of references wrapped into a custom proxy instances.
	 */
	@Nonnull
	private static ExceptionRethrowingFunction<EntityContract, Object> setOfReferenceResult(
		@Nonnull String referenceName,
		@Nonnull Class<?> itemType,
		@Nonnull ProxyReferenceFactory proxyReferenceFactory
	) {
		return sealedEntity -> {
			if (sealedEntity.referencesAvailable(referenceName)) {
				return sealedEntity.getReferences(referenceName)
					.stream()
					.map(it -> proxyReferenceFactory.createEntityReferenceProxy(itemType, sealedEntity, it))
					.collect(CollectorUtils.toUnmodifiableLinkedHashSet());
			} else {
				return Collections.emptySet();
			}
		};
	}

	/**
	 * Creates an implementation of the method returning an array of references wrapped into a custom proxy instances.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> arrayOfReferenceResult(
		@Nonnull String referenceName,
		@Nonnull Class<?> itemType,
		@Nonnull BiFunction<EntityContract, String, Collection<ReferenceContract>> referenceExtractor,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		return (entityClassifier, theMethod, args, theState, invokeSuper) -> {
			final EntityContract entity = theState.getEntity();
			return resultWrapper.apply(
				ofNullable(referenceExtractor.apply(entity, referenceName))
					.map(
						refs -> refs.stream()
							.map(it -> theState.createEntityReferenceProxy(itemType, entity, it))
							.toArray(count -> (Object[]) Array.newInstance(itemType, count))
					).orElse(null)
			);
		};
	}

	/**
	 * Creates an implementation of the method returning an array of references wrapped into a custom proxy instances.
	 */
	@Nonnull
	private static ExceptionRethrowingFunction<EntityContract, Object> arrayOfReferenceResult(
		@Nonnull String referenceName,
		@Nonnull Class<?> itemType,
		@Nonnull ProxyReferenceFactory proxyReferenceFactory
	) {
		return sealedEntity -> {
			if (sealedEntity.referencesAvailable(referenceName)) {
				return sealedEntity.getReferences(referenceName)
					.stream()
					.map(it -> proxyReferenceFactory.createEntityReferenceProxy(itemType, sealedEntity, it))
					.toArray(count -> (Object[]) Array.newInstance(itemType, count));
			} else {
				return null;
			}
		};
	}

	/**
	 * Method returns implementation of the method returning referenced entities in the simple or complex (custom type)
	 * form.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> getReferencedEntity(
		@Nonnull EntitySchemaContract entitySchema,
		@Nonnull ReferenceSchemaContract referenceSchema,
		@Nonnull String targetEntityType,
		@Nullable Class<?> collectionType,
		@Nonnull Class<?> itemType,
		@Nonnull BiFunction<EntityContract, String, Collection<ReferenceContract>> referenceExtractor,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		// we return directly the referenced entity - either it or its group by matching the entity referenced name
		final String entityName = entitySchema.getName();
		final String referenceName = referenceSchema.getName();
		final String referencedEntityType = referenceSchema.getReferencedEntityType();
		final String referencedGroupType = referenceSchema.getReferencedGroupType();
		if (targetEntityType.equals(referencedEntityType)) {
			Assert.isTrue(
				referenceSchema.isReferencedEntityTypeManaged(),
				() -> new EntityClassInvalidException(
					itemType,
					"Entity class type `" + itemType + "` reference `" +
						referenceSchema.getName() + "` relates to the entity type `" + referencedEntityType +
						"` which is not managed by evitaDB and cannot be wrapped into a proxy class!"
				)
			);
			if (collectionType == null) {
				return singleEntityResult(entityName, referenceName, itemType, referenceExtractor, ReferenceDecorator::getReferencedEntity, resultWrapper);
			} else if (collectionType.isArray()) {
				return arrayOfEntityResult(entityName, referenceName, itemType, referenceExtractor, ReferenceDecorator::getReferencedEntity, resultWrapper);
			} else if (Set.class.isAssignableFrom(collectionType)) {
				return setOfEntityResult(entityName, referenceName, itemType, referenceExtractor, ReferenceDecorator::getReferencedEntity, resultWrapper);
			} else {
				return listOfEntityResult(entityName, referenceName, itemType, referenceExtractor, ReferenceDecorator::getReferencedEntity, resultWrapper);
			}
		} else if (targetEntityType.equals(referencedGroupType)) {
			Assert.isTrue(
				referenceSchema.isReferencedGroupTypeManaged(),
				() -> new EntityClassInvalidException(
					itemType,
					"Entity class type `" + itemType + "` reference `" +
						referenceSchema.getName() + "` relates to the group type `" + referencedGroupType +
						"` which is not managed by evitaDB and cannot be wrapped into a proxy class!"
				)
			);
			if (collectionType == null) {
				return singleEntityResult(entityName, referenceName, itemType, referenceExtractor, ReferenceDecorator::getGroupEntity, resultWrapper);
			} else if (collectionType.isArray()) {
				return arrayOfEntityResult(entityName, referenceName, itemType, referenceExtractor, ReferenceDecorator::getGroupEntity, resultWrapper);
			} else if (Set.class.isAssignableFrom(collectionType)) {
				return setOfEntityResult(entityName, referenceName, itemType, referenceExtractor, ReferenceDecorator::getGroupEntity, resultWrapper);
			} else {
				return listOfEntityResult(entityName, referenceName, itemType, referenceExtractor, ReferenceDecorator::getGroupEntity, resultWrapper);
			}
		} else {
			throw new EntityClassInvalidException(
				itemType,
				"Entity class type `" + itemType + "` is not compatible with reference `" +
					referenceSchema.getName() + "` of entity type `" + referencedEntityType +
					"` or group type `" + referencedGroupType + "`!"
			);
		}
	}

	/**
	 * Method returns implementation of the method returning referenced entities in the simple or complex (custom type)
	 * form.
	 */
	@Nonnull
	private static ExceptionRethrowingFunction<EntityContract, Object> getReferencedEntity(
		@Nonnull EntitySchemaContract entitySchema,
		@Nonnull ReferenceSchemaContract referenceSchema,
		@Nonnull String targetEntityName,
		@Nullable Class<?> collectionType,
		@Nonnull Class<?> itemType,
		@Nonnull ProxyFactory proxyFactory
	) {
		// we return directly the referenced entity - either it or its group by matching the entity referenced name
		final String entityName = entitySchema.getName();
		final String referenceName = referenceSchema.getName();

		final String referencedEntityType = referenceSchema.getReferencedEntityType();
		final String referencedGroupType = referenceSchema.getReferencedGroupType();
		if (targetEntityName.equals(referencedEntityType)) {
			Assert.isTrue(
				referenceSchema.isReferencedEntityTypeManaged(),
				() -> new EntityClassInvalidException(
					itemType,
					"Entity class type `" + itemType + "` reference `" +
						referenceSchema.getName() + "` relates to the entity type `" + referencedEntityType +
						"` which is not managed by evitaDB and cannot be wrapped into a proxy class!"
				)
			);

			if (collectionType == null) {
				return singleEntityResult(entityName, referenceName, itemType, ReferenceDecorator::getReferencedEntity, proxyFactory);
			} else if (collectionType.isArray()) {
				return arrayOfEntityResult(entityName, referenceName, itemType, ReferenceDecorator::getReferencedEntity, proxyFactory);
			} else if (Set.class.isAssignableFrom(collectionType)) {
				return setOfEntityResult(entityName, referenceName, itemType, ReferenceDecorator::getReferencedEntity, proxyFactory);
			} else {
				return listOfEntityResult(entityName, referenceName, itemType, ReferenceDecorator::getReferencedEntity, proxyFactory);
			}
		} else if (targetEntityName.equals(referencedGroupType)) {
			Assert.isTrue(
				referenceSchema.isReferencedGroupTypeManaged(),
				() -> new EntityClassInvalidException(
					itemType,
					"Entity class type `" + itemType + "` reference `" +
						referenceSchema.getName() + "` relates to the group type `" + referencedGroupType +
						"` which is not managed by evitaDB and cannot be wrapped into a proxy class!"
				)
			);
			if (collectionType == null) {
				return singleEntityResult(entityName, referenceName, itemType, ReferenceDecorator::getGroupEntity, proxyFactory);
			} else if (collectionType.isArray()) {
				return arrayOfEntityResult(entityName, referenceName, itemType, ReferenceDecorator::getGroupEntity, proxyFactory);
			} else if (Set.class.isAssignableFrom(collectionType)) {
				return setOfEntityResult(entityName, referenceName, itemType, ReferenceDecorator::getGroupEntity, proxyFactory);
			} else {
				return listOfEntityResult(entityName, referenceName, itemType, ReferenceDecorator::getGroupEntity, proxyFactory);
			}
		} else {
			throw new EntityClassInvalidException(
				itemType,
				"Entity class type `" + itemType + "` is not compatible with reference `" +
					referenceSchema.getName() + "` of entity type `" + referencedEntityType +
					"` or group type `" + referencedGroupType + "`!"
			);
		}
	}

	/**
	 * Method returns implementation of the method returning referenced entities in the form of {@link EntityReference}.
	 */
	@Nonnull
	private static ExceptionRethrowingFunction<EntityContract, Object> getEntityReference(
		@Nonnull String cleanReferenceName,
		@Nullable Class<?> collectionType
	) {
		if (collectionType == null) {
			return singleEntityReferenceResult(cleanReferenceName);
		} else if (collectionType.isArray()) {
			return arrayOfEntityReferencesResult(cleanReferenceName);
		} else if (Set.class.isAssignableFrom(collectionType)) {
			return setOfEntityReferencesResult(cleanReferenceName);
		} else {
			return listOfEntityReferencesResult(cleanReferenceName);
		}
	}

	/**
	 * Method returns implementation of the method returning referenced entities in the form of {@link EntityReference}.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> getEntityReference(
		@Nonnull String cleanReferenceName,
		@Nullable Class<?> collectionType,
		@Nonnull BiFunction<EntityContract, String, Collection<ReferenceContract>> referenceExtractor,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		if (collectionType == null) {
			return singleEntityReferenceResult(cleanReferenceName, referenceExtractor, resultWrapper);
		} else if (collectionType.isArray()) {
			return arrayOfEntityReferencesResult(cleanReferenceName, referenceExtractor, resultWrapper);
		} else if (Set.class.isAssignableFrom(collectionType)) {
			return setOfEntityReferencesResult(cleanReferenceName, referenceExtractor, resultWrapper);
		} else {
			return listOfEntityReferencesResult(cleanReferenceName, referenceExtractor, resultWrapper);
		}
	}

	/**
	 * Method returns implementation of the method returning referenced entities in the form of integer primary key.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> getEntityId(
		@Nonnull String cleanReferenceName,
		@Nullable Class<?> collectionType,
		@Nullable Class<? extends Serializable> itemType,
		@Nonnull BiFunction<EntityContract, String, Collection<ReferenceContract>> referenceExtractor,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		if (collectionType == null) {
			return singleEntityIdResult(cleanReferenceName, referenceExtractor, resultWrapper);
		} else if (collectionType.isArray()) {
			return arrayOfEntityIdsResult(cleanReferenceName, itemType, referenceExtractor, resultWrapper);
		} else if (Set.class.isAssignableFrom(collectionType)) {
			return setOfEntityIdsResult(cleanReferenceName, referenceExtractor, resultWrapper);
		} else {
			return listOfEntityIdsResult(cleanReferenceName, referenceExtractor, resultWrapper);
		}
	}

	/**
	 * Method returns implementation of the method returning referenced entities in the form of integer primary key.
	 */
	@Nonnull
	private static ExceptionRethrowingFunction<EntityContract, Object> getEntityId(
		@Nonnull String cleanReferenceName,
		@Nullable Class<?> collectionType,
		@Nullable Class<? extends Serializable> itemType
	) {
		if (collectionType == null) {
			return singleEntityIdResult(cleanReferenceName);
		} else if (collectionType.isArray()) {
			return arrayOfEntityIdsResult(cleanReferenceName, itemType);
		} else if (Set.class.isAssignableFrom(collectionType)) {
			return setOfEntityIdsResult(cleanReferenceName);
		} else {
			return listOfEntityIdsResult(cleanReferenceName);
		}
	}

	/**
	 * Method returns implementation of the method returning references entities in the form of custom proxied types.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> getReference(
		@Nonnull String cleanReferenceName,
		@Nullable Class<?> collectionType,
		@Nonnull BiFunction<EntityContract, String, Collection<ReferenceContract>> referenceExtractor,
		@Nonnull Class<?> itemType,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		if (collectionType == null) {
			return singleReferenceResult(cleanReferenceName, itemType, referenceExtractor, resultWrapper);
		} else if (collectionType.isArray()) {
			return arrayOfReferenceResult(cleanReferenceName, itemType, referenceExtractor, resultWrapper);
		} else if (Set.class.isAssignableFrom(collectionType)) {
			return setOfReferenceResult(cleanReferenceName, itemType, referenceExtractor, resultWrapper);
		} else {
			return listOfReferenceResult(cleanReferenceName, itemType, referenceExtractor, resultWrapper);
		}
	}

	/**
	 * Method returns implementation of the method returning references entities in the form of custom proxied types.
	 */
	@Nonnull
	private static ExceptionRethrowingFunction<EntityContract, Object> getReference(
		@Nonnull String referenceName,
		@Nullable Class<?> collectionType,
		@Nonnull Class<?> itemType,
		@Nonnull ProxyReferenceFactory proxyReferenceFactory
	) {
		if (collectionType == null) {
			return singleReferenceResult(referenceName, itemType, proxyReferenceFactory);
		} else if (collectionType.isArray()) {
			return arrayOfReferenceResult(referenceName, itemType, proxyReferenceFactory);
		} else if (Set.class.isAssignableFrom(collectionType)) {
			return setOfReferenceResult(referenceName, itemType, proxyReferenceFactory);
		} else {
			return listOfReferenceResult(referenceName, itemType, proxyReferenceFactory);
		}
	}

	public GetReferenceMethodClassifier() {
		super(
			"getReference",
			(method, proxyState) -> {
				// we are interested only in abstract methods without parameters
				if (method.getParameterCount() > 0) {
					return null;
				}
				// now we need to identify reference schema that is being requested
				final ReflectionLookup reflectionLookup = proxyState.getReflectionLookup();
				final EntitySchemaContract entitySchema = proxyState.getEntitySchema();
				final ReferenceSchemaContract referenceSchema = getReferenceSchema(
					method, reflectionLookup, entitySchema
				);
				// if not found, this method is not classified by this implementation
				if (referenceSchema == null) {
					return null;
				} else {
					// finally provide implementation that will retrieve the reference or reference entity from the entity
					final String referenceName = referenceSchema.getName();
					// now we need to identify the return type
					@SuppressWarnings("rawtypes") final Class returnType = method.getReturnType();
					final Class<?>[] resolvedTypes = getResolvedTypes(method, proxyState.getProxyClass());
					final UnaryOperator<Object> resultWrapper = ProxyUtils.createOptionalWrapper(Optional.class.isAssignableFrom(resolvedTypes[0]) ? Optional.class : null);
					final int index = Optional.class.isAssignableFrom(resolvedTypes[0]) ? 1 : 0;

					@SuppressWarnings("rawtypes") final Class collectionType;
					@SuppressWarnings("rawtypes") final Class itemType;
					if (Collection.class.equals(resolvedTypes[index]) || List.class.isAssignableFrom(resolvedTypes[index]) || Set.class.isAssignableFrom(resolvedTypes[index])) {
						collectionType = resolvedTypes[index];
						itemType = resolvedTypes.length > index + 1 ? resolvedTypes[index + 1] : EntityReference.class;
					} else if (resolvedTypes[index].isArray()) {
						collectionType = resolvedTypes[index];
						itemType = returnType.getComponentType();
					} else {
						collectionType = null;
						itemType = resolvedTypes[index];
					}

					@Nonnull BiFunction<EntityContract, String, Collection<ReferenceContract>> referenceExtractor =
						resultWrapper instanceof OptionalProducingOperator ?
							(entity, theReferenceName) -> entity.referencesAvailable(theReferenceName) ?
								entity.getReferences(theReferenceName) : null :
							EntityContract::getReferences;

					// return the appropriate result
					final Entity entityInstance = reflectionLookup.getClassAnnotation(itemType, Entity.class);
					final EntityRef entityRefInstance = reflectionLookup.getClassAnnotation(itemType, EntityRef.class);
					if (itemType.equals(EntityReference.class)) {
						return getEntityReference(referenceName, collectionType, referenceExtractor, resultWrapper);
					} else if (Number.class.isAssignableFrom(EvitaDataTypes.toWrappedForm(itemType))) {
						//noinspection unchecked
						return getEntityId(referenceName, collectionType, itemType, referenceExtractor, resultWrapper);
					} else if (entityInstance != null) {
						return getReferencedEntity(
							entitySchema, referenceSchema, entityInstance.name(), collectionType, itemType, referenceExtractor, resultWrapper
						);
					} else if (entityRefInstance != null) {
						return getReferencedEntity(
							entitySchema, referenceSchema, entityRefInstance.value(), collectionType, itemType, referenceExtractor, resultWrapper
						);
					} else {
						return getReference(referenceName, collectionType, referenceExtractor, itemType, resultWrapper);
					}
				}
			}
		);
	}

}
