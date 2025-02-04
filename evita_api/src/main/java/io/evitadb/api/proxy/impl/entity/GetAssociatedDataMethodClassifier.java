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
import io.evitadb.api.proxy.impl.ProxyUtils;
import io.evitadb.api.proxy.impl.ProxyUtils.OptionalProducingOperator;
import io.evitadb.api.proxy.impl.SealedEntityProxyState;
import io.evitadb.api.requestResponse.data.AssociatedDataContract;
import io.evitadb.api.requestResponse.data.AssociatedDataContract.AssociatedDataValue;
import io.evitadb.api.requestResponse.data.EntityContract;
import io.evitadb.api.requestResponse.data.annotation.AssociatedData;
import io.evitadb.api.requestResponse.data.annotation.AssociatedDataRef;
import io.evitadb.api.requestResponse.data.structure.EntityDecorator;
import io.evitadb.api.requestResponse.data.structure.predicate.LocaleSerializablePredicate;
import io.evitadb.api.requestResponse.schema.AssociatedDataSchemaContract;
import io.evitadb.api.requestResponse.schema.EntitySchemaContract;
import io.evitadb.dataType.data.ComplexDataObjectConverter;
import io.evitadb.exception.EvitaInvalidUsageException;
import io.evitadb.function.ExceptionRethrowingFunction;
import io.evitadb.function.TriFunction;
import io.evitadb.utils.Assert;
import io.evitadb.utils.ClassUtils;
import io.evitadb.utils.CollectionUtils;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static io.evitadb.api.proxy.impl.ProxyUtils.getResolvedTypes;

/**
 * Identifies methods that are used to get associated data from an entity and provides their implementation.
 *
 * @author Jan Novotný (novotny@fg.cz), FG Forrest a.s. (c) 2023
 */
public class GetAssociatedDataMethodClassifier extends DirectMethodClassification<Object, SealedEntityProxyState> {
	/**
	 * We may reuse singleton instance since advice is stateless.
	 */
	public static final GetAssociatedDataMethodClassifier INSTANCE = new GetAssociatedDataMethodClassifier();

	/**
	 * Provides default value (according to a schema or implicit rules) instead of null.
	 */
	@Nonnull
	public static UnaryOperator<Serializable> createDefaultValueProvider(@Nonnull Class<? extends Serializable> returnType) {
		final UnaryOperator<Serializable> defaultValueProvider;
		if (boolean.class.equals(returnType)) {
			defaultValueProvider = o -> o == null ? false : o;
		} else {
			defaultValueProvider = UnaryOperator.identity();
		}
		return defaultValueProvider;
	}

	/**
	 * Tries to identify associated data name from the class field related to the constructor parameter.
	 *
	 * @param expectedType     class the constructor belongs to
	 * @param parameter        constructor parameter
	 * @param reflectionLookup reflection lookup
	 * @return attribute name derived from the annotation if found
	 */
	@Nullable
	public static <T> ExceptionRethrowingFunction<EntityContract, Object> getExtractorIfPossible(
		@Nonnull Class<T> expectedType,
		@Nonnull Parameter parameter,
		@Nonnull ReflectionLookup reflectionLookup,
		@Nonnull EntitySchemaContract schema
	) {
		final String parameterName = parameter.getName();
		final Class<?> parameterType = parameter.getType();

		final AssociatedDataSchemaContract associatedDataSchema = getAssociatedDataSchema(expectedType, parameter, reflectionLookup, schema);
		if (associatedDataSchema != null) {
			final String associatedDataName = associatedDataSchema.getName();
			if (associatedDataSchema.isLocalized()) {
				if (associatedDataSchema.getType().isArray()) {
					if (parameterType.isArray()) {
						return entity -> getLocalizedAssociatedDataAsSingleValue(entity, associatedDataName, parameterType, reflectionLookup);
					} else if (Set.class.equals(parameterType)) {
						return entity -> getLocalizedAssociatedDataAsSet(entity, associatedDataName, parameterType, reflectionLookup);
					} else if (List.class.equals(parameterType) || Collection.class.equals(parameterType)) {
						return entity -> getLocalizedAssociatedDataAsList(entity, associatedDataName, parameterType, reflectionLookup);
					} else {
						throw new EntityClassInvalidException(
							expectedType,
							"Unsupported data type `" + parameterType + "` for associated data `" + associatedDataName + "` in entity `" + schema.getName() +
								"` related to constructor parameter `" + parameterName + "`!"
						);
					}
				} else {
					if (parameterType.isEnum()) {
						return entity -> getLocalizedAssociateDataAsEnum(entity, associatedDataName, parameterType, reflectionLookup);
					} else {
						return entity -> getLocalizedAssociatedDataAsSingleValue(entity, associatedDataName, parameterType, reflectionLookup);
					}
				}
			} else {
				if (associatedDataSchema.getType().isArray()) {
					if (parameterType.isArray()) {
						return entity -> getAssociatedDataAsSingleValue(entity, associatedDataName, parameterType, reflectionLookup);
					} else if (Set.class.equals(parameterType)) {
						return entity -> getAssociatedDataAsSet(entity, associatedDataName, parameterType, reflectionLookup);
					} else if (List.class.equals(parameterType) || Collection.class.equals(parameterType)) {
						return entity -> getAssociatedDataAsList(entity, associatedDataName, parameterType, reflectionLookup);
					} else {
						throw new EntityClassInvalidException(
							expectedType,
							"Unsupported data type `" + parameterType + "` for associated data `" + associatedDataName + "` in entity `" + schema.getName() +
								"` related to constructor parameter `" + parameterName + "`!"
						);
					}
				} else {
					if (parameterType.isEnum()) {
						return entity -> getAssociateDataAsEnum(entity, associatedDataName, parameterType, reflectionLookup);
					} else {
						return entity -> getAssociatedDataAsSingleValue(entity, associatedDataName, parameterType, reflectionLookup);
					}
				}
			}
		}

		return null;
	}

	/**
	 * Retrieves appropriate associated data schema from the annotations on the method. If no Evita annotation is found
	 * it tries to match the associated data name by the name of the method.
	 */
	@Nullable
	private static AssociatedDataSchemaContract getAssociatedDataSchema(
		@Nonnull Method method,
		@Nonnull ReflectionLookup reflectionLookup,
		@Nonnull EntitySchemaContract entitySchema
	) {
		final AssociatedData associatedDataInstance = reflectionLookup.getAnnotationInstanceForProperty(method, AssociatedData.class);
		final AssociatedDataRef associatedDataRefInstance = reflectionLookup.getAnnotationInstanceForProperty(method, AssociatedDataRef.class);
		if (associatedDataInstance != null) {
			return entitySchema.getAssociatedDataOrThrowException(associatedDataInstance.name());
		} else if (associatedDataRefInstance != null) {
			return entitySchema.getAssociatedDataOrThrowException(associatedDataRefInstance.value());
		} else if (!reflectionLookup.hasAnnotationInSamePackage(method, AssociatedData.class) && ClassUtils.isAbstract(method)) {
			return ReflectionLookup.getPropertyNameFromMethodNameIfPossible(method.getName())
				.flatMap(
					associatedDataName -> entitySchema.getAssociatedDataByName(
						associatedDataName,
						NamingConvention.CAMEL_CASE
					)
				)
				.orElse(null);
		} else {
			return null;
		}
	}

	/**
	 * Retrieves appropriate associated data schema from the annotations on the method. If no Evita annotation is found
	 * it tries to match the associated data name by the name of the method.
	 */
	@Nullable
	private static AssociatedDataSchemaContract getAssociatedDataSchema(
		@Nonnull Class<?> expectedType,
		@Nonnull Parameter parameter,
		@Nonnull ReflectionLookup reflectionLookup,
		@Nonnull EntitySchemaContract entitySchema
	) {
		final String parameterName = parameter.getName();
		final AssociatedData associatedDataInstance = reflectionLookup.getAnnotationInstanceForProperty(expectedType, parameterName, AssociatedData.class);
		final AssociatedDataRef associatedDataRefInstance = reflectionLookup.getAnnotationInstanceForProperty(expectedType, parameterName, AssociatedDataRef.class);
		if (associatedDataInstance != null) {
			return entitySchema.getAssociatedDataOrThrowException(associatedDataInstance.name());
		} else if (associatedDataRefInstance != null) {
			return entitySchema.getAssociatedDataOrThrowException(associatedDataRefInstance.value());
		} else {
			return ReflectionLookup.getPropertyNameFromMethodNameIfPossible(parameterName)
				.flatMap(
					associatedDataName -> entitySchema.getAssociatedDataByName(
						associatedDataName,
						NamingConvention.CAMEL_CASE
					)
				)
				.orElse(null);
		}
	}

	/**
	 * Creates an implementation of the method returning an associated data as a requested type.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> singleLocalizedResult(
		@Nonnull String cleanAssociatedDataName,
		@Nonnull Method method,
		@Nonnull Class<Serializable> itemType,
		@Nonnull BiFunction<EntityContract, String, Optional<AssociatedDataValue>> associatedDataExtractor,
		@Nonnull TriFunction<EntityContract, String, Locale, Optional<AssociatedDataValue>> localizedAssociatedDataExtractor,
		@Nonnull UnaryOperator<Serializable> defaultValueProvider,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		return method.getParameterCount() == 0 ?
			(entityClassifier, theMethod, args, theState, invokeSuper) -> resultWrapper.apply(
				associatedDataExtractor.apply(theState.getEntity(), cleanAssociatedDataName)
					.map(AssociatedDataValue::value)
					.map(defaultValueProvider)
					.map(it -> ComplexDataObjectConverter.getOriginalForm(it, itemType, theState.getReflectionLookup()))
					.orElse(null)
			) :
			(entityClassifier, theMethod, args, theState, invokeSuper) -> resultWrapper.apply(
				localizedAssociatedDataExtractor.apply(theState.getEntity(), cleanAssociatedDataName, (Locale) args[0])
					.map(AssociatedDataValue::value)
					.map(defaultValueProvider)
					.map(it -> ComplexDataObjectConverter.getOriginalForm(it, itemType, theState.getReflectionLookup()))
					.orElse(null)
			);
	}

	/**
	 * Creates an implementation of the method returning an associated data of an array type wrapped into a set.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> setOfLocalizedResults(
		@Nonnull String cleanAssociatedDataName,
		@Nonnull Method method,
		@Nonnull Class<Serializable> itemType,
		@Nonnull BiFunction<EntityContract, String, Optional<AssociatedDataValue>> associatedDataExtractor,
		@Nonnull TriFunction<EntityContract, String, Locale, Optional<AssociatedDataValue>> localizedAssociatedDataExtractor,
		@Nonnull UnaryOperator<Serializable> defaultValueProvider,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		Assert.isTrue(
			itemType.isArray(),
			() -> new EntityClassInvalidException(
				method.getDeclaringClass(),
				"Method " + method + " is annotated with @AssociatedData related to `" + cleanAssociatedDataName + "` " +
					"of non-array associated data, but returns a collection!"
			)
		);
		return method.getParameterCount() == 0 ?
			(entityClassifier, theMethod, args, theState, invokeSuper) -> resultWrapper.apply(
				associatedDataExtractor.apply(theState.getEntity(), cleanAssociatedDataName)
					.map(AssociatedDataValue::value)
					.map(defaultValueProvider)
					.map(it -> ComplexDataObjectConverter.getOriginalForm(it, itemType, theState.getReflectionLookup()))
					.map(Object[].class::cast)
					.map(it -> {
						final Set<Object> result = CollectionUtils.createHashSet(it.length);
						result.addAll(Arrays.asList(it));
						return result;
					})
					.orElse(Collections.emptySet())
			) :
			(entityClassifier, theMethod, args, theState, invokeSuper) -> resultWrapper.apply(
				localizedAssociatedDataExtractor.apply(theState.getEntity(), cleanAssociatedDataName, (Locale) args[0])
					.map(AssociatedDataValue::value)
					.map(defaultValueProvider)
					.map(it -> ComplexDataObjectConverter.getOriginalForm(it, itemType, theState.getReflectionLookup()))
					.map(Object[].class::cast)
					.map(it -> {
						final Set<Object> result = CollectionUtils.createHashSet(it.length);
						result.addAll(Arrays.asList(it));
						return result;
					})
					.orElse(Collections.emptySet())
			);
	}

	/**
	 * Creates an implementation of the method returning an associated data of an array type wrapped into a list.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> listOfLocalizedResults(
		@Nonnull String cleanAssociatedDataName,
		@Nonnull Method method,
		@Nonnull Class<Serializable> itemType,
		@Nonnull BiFunction<EntityContract, String, Optional<AssociatedDataValue>> associatedDataExtractor,
		@Nonnull TriFunction<EntityContract, String, Locale, Optional<AssociatedDataValue>> localizedAssociatedDataExtractor,
		@Nonnull UnaryOperator<Serializable> defaultValueProvider,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		Assert.isTrue(
			itemType.isArray(),
			() -> new EntityClassInvalidException(
				method.getDeclaringClass(),
				"Method " + method + " is annotated with @AssociatedData related to `" + cleanAssociatedDataName + "` " +
					"of non-array associated data, but returns a collection!"
			)
		);
		return method.getParameterCount() == 0 ?
			(entityClassifier, theMethod, args, theState, invokeSuper) -> resultWrapper.apply(
				associatedDataExtractor.apply(theState.getEntity(), cleanAssociatedDataName)
					.map(AssociatedDataValue::value)
					.map(defaultValueProvider)
					.map(it -> ComplexDataObjectConverter.getOriginalForm(it, itemType, theState.getReflectionLookup()))
					.map(Object[].class::cast)
					.map(List::of)
					.orElse(Collections.emptyList())
			) :
			(entityClassifier, theMethod, args, theState, invokeSuper) -> resultWrapper.apply(
				localizedAssociatedDataExtractor.apply(theState.getEntity(), cleanAssociatedDataName, (Locale) args[0])
					.map(AssociatedDataValue::value)
					.map(defaultValueProvider)
					.map(it -> ComplexDataObjectConverter.getOriginalForm(it, itemType, theState.getReflectionLookup()))
					.map(Object[].class::cast)
					.map(List::of)
					.orElse(Collections.emptyList())
			);
	}

	/**
	 * Creates an implementation of the method returning an associated data as a requested type.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> singleResult(
		@Nonnull String cleanAssociatedDataName,
		@Nonnull Class<Serializable> itemType,
		@Nonnull BiFunction<EntityContract, String, Optional<AssociatedDataValue>> associatedDataExtractor,
		@Nonnull UnaryOperator<Serializable> defaultValueProvider,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		return (entityClassifier, theMethod, args, theState, invokeSuper) -> resultWrapper.apply(
			associatedDataExtractor.apply(theState.getEntity(), cleanAssociatedDataName)
				.map(AssociatedDataValue::value)
				.map(defaultValueProvider)
				.map(it -> ComplexDataObjectConverter.getOriginalForm(it, itemType, theState.getReflectionLookup()))
				.orElse(null)
		);
	}

	/**
	 * Creates an implementation of the method returning an associated data of an array type wrapped into a set.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> setOfResults(
		@Nonnull String cleanAssociatedDataName,
		@Nonnull Method method,
		@Nonnull Class<Serializable> itemType,
		@Nonnull BiFunction<EntityContract, String, Optional<AssociatedDataValue>> associatedDataExtractor,
		@Nonnull UnaryOperator<Serializable> defaultValueProvider,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		Assert.isTrue(
			itemType.isArray(),
			() -> new EntityClassInvalidException(
				method.getDeclaringClass(),
				"Method " + method + " is annotated with @AssociatedData related to `" + cleanAssociatedDataName + "` " +
					"of non-array associated data, but returns a collection!"
			)
		);
		return (entityClassifier, theMethod, args, theState, invokeSuper) -> resultWrapper.apply(
			associatedDataExtractor.apply(theState.getEntity(), cleanAssociatedDataName)
				.map(AssociatedDataValue::value)
				.map(defaultValueProvider)
				.map(it -> ComplexDataObjectConverter.getOriginalForm(it, itemType, theState.getReflectionLookup()))
				.map(Object[].class::cast)
				.map(it -> {
					final Set<Object> result = CollectionUtils.createHashSet(it.length);
					result.addAll(Arrays.asList(it));
					return result;
				})
				.orElse(Collections.emptySet())
		);
	}

	/**
	 * Creates an implementation of the method returning an associated data of an array type wrapped into a list.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<Object, SealedEntityProxyState> listOfResults(
		@Nonnull String cleanAssociatedDataName,
		@Nonnull Method method,
		@Nonnull Class<Serializable> itemType,
		@Nonnull BiFunction<EntityContract, String, Optional<AssociatedDataValue>> associatedDataExtractor,
		@Nonnull UnaryOperator<Serializable> defaultValueProvider,
		@Nonnull UnaryOperator<Object> resultWrapper
	) {
		Assert.isTrue(
			itemType.isArray(),
			() -> new EntityClassInvalidException(
				method.getDeclaringClass(),
				"Method " + method + " is annotated with @AssociatedData related to `" + cleanAssociatedDataName + "` " +
					"of non-array associated data, but returns a collection!"
			)
		);
		return (entityClassifier, theMethod, args, theState, invokeSuper) -> resultWrapper.apply(
			associatedDataExtractor.apply(theState.getEntity(), cleanAssociatedDataName)
				.map(AssociatedDataValue::value)
				.map(defaultValueProvider)
				.map(it -> ComplexDataObjectConverter.getOriginalForm(it, itemType, theState.getReflectionLookup()))
				.map(Object[].class::cast)
				.map(List::of)
				.orElse(Collections.emptyList())
		);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	@Nullable
	private static Enum<?> getAssociateDataAsEnum(
		@Nonnull EntityContract entity,
		@Nonnull String associatedDataName,
		@Nonnull Class parameterType,
		@Nonnull ReflectionLookup reflectionLookup
	) {
		if (entity.associatedDataAvailable(associatedDataName)) {
			return Enum.valueOf(
				parameterType,
				(String) entity.getAssociatedData(
					associatedDataName,
					String.class,
					reflectionLookup
				)
			);
		} else {
			return null;
		}
	}

	@SuppressWarnings({"rawtypes"})
	@Nullable
	private static Enum<?> getLocalizedAssociateDataAsEnum(
		@Nonnull EntityContract entity,
		@Nonnull String associatedDataName,
		@Nonnull Class parameterType,
		@Nonnull ReflectionLookup reflectionLookup
	) {
		if (entity.attributeAvailable(associatedDataName)) {
			final LocaleSerializablePredicate localePredicate = ((EntityDecorator) entity).getLocalePredicate();
			final Set<Locale> locales = localePredicate.getLocales();
			final Locale locale = locales != null && locales.size() == 1 ? locales.iterator().next() : localePredicate.getImplicitLocale();
			if (locale == null && locales != null && locales.isEmpty()) {
				throw new EvitaInvalidUsageException(
					"Cannot initialize associated data `" + associatedDataName + "` in a constructor as a single enum value since " +
						"it could localized to multiple locales and no locale was requested when fetching the entity!"
				);
			} else if (locale != null) {
				//noinspection unchecked
				return Enum.valueOf(
					parameterType,
					(String) entity.getAssociatedData(
						associatedDataName,
						locale,
						String.class,
						reflectionLookup
					)
				);
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	@SuppressWarnings({"rawtypes"})
	@Nullable
	private static Serializable getAssociatedDataAsSingleValue(
		@Nonnull EntityContract entity,
		@Nonnull String associatedDataName,
		@Nonnull Class parameterType,
		@Nonnull ReflectionLookup reflectionLookup
	) {
		if (entity.associatedDataAvailable(associatedDataName)) {
			return entity.getAssociatedData(
				associatedDataName,
				parameterType,
				reflectionLookup
			);
		} else {
			return null;
		}
	}

	@SuppressWarnings({"rawtypes"})
	@Nullable
	private static Serializable getLocalizedAssociatedDataAsSingleValue(
		@Nonnull EntityContract entity,
		@Nonnull String associatedDataName,
		@Nonnull Class parameterType,
		@Nonnull ReflectionLookup reflectionLookup
	) {
		if (entity.attributeAvailable(associatedDataName)) {
			final LocaleSerializablePredicate localePredicate = ((EntityDecorator) entity).getLocalePredicate();
			final Set<Locale> locales = localePredicate.getLocales();
			final Locale locale = locales != null && locales.size() == 1 ? locales.iterator().next() : localePredicate.getImplicitLocale();
			if (locale == null && locales != null && locales.isEmpty()) {
				if (parameterType.isArray()) {
					return entity.getAttributeLocales()
						.stream()
						.map(it -> {
							//noinspection DataFlowIssue,unchecked
							return entity.getAssociatedData(
								associatedDataName,
								locale,
								parameterType.getComponentType(),
								reflectionLookup
							);
						})
						.toArray(count -> (Serializable[]) Array.newInstance(parameterType.getComponentType(), count));
				} else {
					throw new EvitaInvalidUsageException(
						"Cannot initialize associated data `" + associatedDataName + "` in a constructor as a single value since " +
							"it could localized to multiple locales and no locale was requested when fetching the entity!"
					);
				}
			} else if (locale != null) {
				//noinspection unchecked
				return entity.getAssociatedData(
					associatedDataName,
					locale,
					parameterType,
					reflectionLookup
				);
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	@Nullable
	private static Set<?> getAssociatedDataAsSet(
		@Nonnull EntityContract entity,
		@Nonnull String associatedDataName,
		@Nonnull Class parameterType,
		@Nonnull ReflectionLookup reflectionLookup
	) {
		if (entity.associatedDataAvailable(associatedDataName)) {
			final Serializable[] associatedData = (Serializable[]) entity.getAssociatedData(
				associatedDataName,
				parameterType,
				reflectionLookup
			);
			return Arrays.stream(associatedData).collect(Collectors.toSet());
		} else {
			return null;
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	@Nullable
	private static Set<?> getLocalizedAssociatedDataAsSet(
		@Nonnull EntityContract entity,
		@Nonnull String associatedDataName,
		@Nonnull Class parameterType,
		@Nonnull ReflectionLookup reflectionLookup
	) {
		if (entity.associatedDataAvailable(associatedDataName)) {
			final LocaleSerializablePredicate localePredicate = ((EntityDecorator) entity).getLocalePredicate();
			final Set<Locale> locales = localePredicate.getLocales();
			final Locale locale = locales != null && locales.size() == 1 ? locales.iterator().next() : localePredicate.getImplicitLocale();

			final Serializable[] associatedData;
			if (locale == null && locales != null && locales.isEmpty()) {
				throw new EvitaInvalidUsageException(
					"Cannot initialize associated data `" + associatedDataName + "` in a constructor as a set since " +
						"it could localized to multiple locales and it's expected to be an array data type. " +
						"When no locale was requested when fetching the entity, it would require concatenating " +
						"multiple arrays and losing the information about the associated locale!"
				);
			} else if (locale != null) {
				associatedData = (Serializable[]) entity.getAssociatedData(
					associatedDataName,
					locale,
					parameterType,
					reflectionLookup
				);
			} else {
				return Collections.emptySet();
			}

			return Arrays.stream(associatedData)
				.collect(Collectors.toSet());
		} else {
			return null;
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	@Nullable
	private static List<?> getAssociatedDataAsList(
		@Nonnull EntityContract entity,
		@Nonnull String associatedDataName,
		@Nonnull Class parameterType,
		@Nonnull ReflectionLookup reflectionLookup
	) {
		if (entity.associatedDataAvailable(associatedDataName)) {
			final Serializable[] associatedData = (Serializable[]) entity.getAssociatedData(
				associatedDataName,
				parameterType,
				reflectionLookup
			);
			return Arrays.stream(associatedData).toList();
		} else {
			return null;
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	@Nullable
	private static List<?> getLocalizedAssociatedDataAsList(
		@Nonnull EntityContract entity,
		@Nonnull String associatedDataName,
		@Nonnull Class parameterType,
		@Nonnull ReflectionLookup reflectionLookup
	) {
		if (entity.associatedDataAvailable(associatedDataName)) {
			final LocaleSerializablePredicate localePredicate = ((EntityDecorator) entity).getLocalePredicate();
			final Set<Locale> locales = localePredicate.getLocales();
			final Locale locale = locales != null && locales.size() == 1 ? locales.iterator().next() : localePredicate.getImplicitLocale();

			final Serializable[] associatedData;
			if (locale == null && locales != null && locales.isEmpty()) {
				throw new EvitaInvalidUsageException(
					"Cannot initialize associated data `" + associatedDataName + "` in a constructor as a set since " +
						"it could localized to multiple locales and it's expected to be an array data type. " +
						"When no locale was requested when fetching the entity, it would require concatenating " +
						"multiple arrays and losing the information about the associated locale!"
				);
			} else if (locale != null) {
				associatedData = (Serializable[]) entity.getAssociatedData(
					associatedDataName,
					locale,
					parameterType,
					reflectionLookup
				);
			} else {
				return Collections.emptyList();
			}

			return Arrays.stream(associatedData)
				.toList();
		} else {
			return null;
		}
	}

	public GetAssociatedDataMethodClassifier() {
		super(
			"getAssociatedData",
			(method, proxyState) -> {
				// we only want to handle methods that are not abstract and have at most one parameter of type Locale.
				if (method.getParameterCount() > 1 ||
					(method.getParameterCount() == 1 && !method.getParameterTypes()[0].equals(Locale.class))
				) {
					return null;
				}
				// now we need to identify associated data schema that is being requested
				final AssociatedDataSchemaContract associatedDataSchema = getAssociatedDataSchema(
					method, proxyState.getReflectionLookup(), proxyState.getEntitySchema()
				);
				// if not found, this method is not classified by this implementation
				if (associatedDataSchema == null) {
					return null;
				} else {
					// finally provide implementation that will retrieve the associated data from the entity
					final String cleanAssociatedDataName = associatedDataSchema.getName();

					// now we need to identify the return type
					@SuppressWarnings("rawtypes") final Class returnType = method.getReturnType();
					final Class<?>[] resolvedTypes = getResolvedTypes(method, proxyState.getProxyClass());
					@SuppressWarnings("unchecked") final UnaryOperator<Serializable> defaultValueProvider = createDefaultValueProvider(returnType);
					final UnaryOperator<Object> resultWrapper;
					final int index = Optional.class.isAssignableFrom(resolvedTypes[0]) ? 1 : 0;

					@SuppressWarnings("rawtypes") final Class collectionType;
					@SuppressWarnings("rawtypes") final Class itemType;
					if (Collection.class.equals(resolvedTypes[index]) || List.class.isAssignableFrom(resolvedTypes[index]) || Set.class.isAssignableFrom(resolvedTypes[index])) {
						collectionType = resolvedTypes[index];
						itemType = Array.newInstance(resolvedTypes.length > index + 1 ? resolvedTypes[index + 1] : Object.class, 0).getClass();
						resultWrapper = ProxyUtils.createOptionalWrapper(Optional.class.isAssignableFrom(resolvedTypes[0]) ? Optional.class : null);
					} else if (resolvedTypes[index].isArray()) {
						collectionType = null;
						itemType = resolvedTypes[index];
						resultWrapper = ProxyUtils.createOptionalWrapper(Optional.class.isAssignableFrom(resolvedTypes[0]) ? Optional.class : null);
					} else {
						collectionType = null;
						if (OptionalInt.class.isAssignableFrom(resolvedTypes[0])) {
							itemType = int.class;
							resultWrapper = ProxyUtils.createOptionalWrapper(itemType);
						} else if (OptionalLong.class.isAssignableFrom(resolvedTypes[0])) {
							itemType = long.class;
							resultWrapper = ProxyUtils.createOptionalWrapper(itemType);
						} else if (Optional.class.isAssignableFrom(resolvedTypes[0])) {
							itemType = resolvedTypes[index];
							resultWrapper = ProxyUtils.createOptionalWrapper(itemType);
						} else {
							itemType = resolvedTypes[index];
							resultWrapper = ProxyUtils.createOptionalWrapper(null);
						}
					}

					final BiFunction<EntityContract, String, Optional<AssociatedDataValue>> associatedDataExtractor =
						resultWrapper instanceof OptionalProducingOperator ?
							(entity, associatedDataName) -> entity.associatedDataAvailable(associatedDataName) ? entity.getAssociatedDataValue(associatedDataName) : Optional.empty() :
							AssociatedDataContract::getAssociatedDataValue;

					if (associatedDataSchema.isLocalized()) {

						final TriFunction<EntityContract, String, Locale, Optional<AssociatedDataValue>> localizedAssociatedDataExtractor =
							resultWrapper instanceof OptionalProducingOperator ?
								(entity, associatedDataName, locale) -> entity.associatedDataAvailable(associatedDataName, locale) ? entity.getAssociatedDataValue(associatedDataName, locale) : Optional.empty() :
								AssociatedDataContract::getAssociatedDataValue;

						if (collectionType != null && Set.class.isAssignableFrom(collectionType)) {
							//noinspection unchecked
							return setOfLocalizedResults(
								cleanAssociatedDataName, method, itemType,
								associatedDataExtractor, localizedAssociatedDataExtractor,
								defaultValueProvider, resultWrapper
							);
						} else if (collectionType != null) {
							//noinspection unchecked
							return listOfLocalizedResults(
								cleanAssociatedDataName, method, itemType,
								associatedDataExtractor, localizedAssociatedDataExtractor,
								defaultValueProvider, resultWrapper
							);
						} else {
							//noinspection unchecked
							return singleLocalizedResult(
								cleanAssociatedDataName, method, itemType,
								associatedDataExtractor, localizedAssociatedDataExtractor,
								defaultValueProvider, resultWrapper
							);
						}
					} else {
						Assert.isTrue(
							method.getParameterCount() == 0,
							"Non-localized associated data `" + associatedDataSchema.getName() + "` must not have a locale parameter!"
						);
						if (collectionType != null && Set.class.isAssignableFrom(collectionType)) {
							//noinspection unchecked
							return setOfResults(
								cleanAssociatedDataName, method, itemType,
								associatedDataExtractor, defaultValueProvider, resultWrapper
							);
						} else if (collectionType != null) {
							//noinspection unchecked
							return listOfResults(
								cleanAssociatedDataName, method, itemType,
								associatedDataExtractor, defaultValueProvider, resultWrapper
							);
						} else {
							//noinspection unchecked
							return singleResult(
								cleanAssociatedDataName, itemType,
								associatedDataExtractor, defaultValueProvider, resultWrapper
							);
						}
					}
				}
			}
		);
	}

}
