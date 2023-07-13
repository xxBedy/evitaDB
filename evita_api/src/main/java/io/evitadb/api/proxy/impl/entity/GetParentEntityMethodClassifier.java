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
import io.evitadb.api.proxy.impl.SealedEntityProxyState;
import io.evitadb.api.requestResponse.data.EntityClassifier;
import io.evitadb.api.requestResponse.data.EntityClassifierWithParent;
import io.evitadb.api.requestResponse.data.SealedEntity;
import io.evitadb.api.requestResponse.data.annotation.Entity;
import io.evitadb.api.requestResponse.data.annotation.EntityRef;
import io.evitadb.api.requestResponse.data.annotation.ParentEntity;
import io.evitadb.api.requestResponse.data.structure.EntityReference;
import io.evitadb.dataType.EvitaDataTypes;
import io.evitadb.utils.Assert;
import io.evitadb.utils.ClassUtils;
import io.evitadb.utils.ReflectionLookup;
import one.edee.oss.proxycian.CurriedMethodContextInvocationHandler;
import one.edee.oss.proxycian.DirectMethodClassification;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;

/**
 * Identifies methods that are used to get parent entity from an sealed entity and provides their implementation.
 *
 * @author Jan Novotný (novotny@fg.cz), FG Forrest a.s. (c) 2023
 */
public class GetParentEntityMethodClassifier extends DirectMethodClassification<EntityClassifier, SealedEntityProxyState> {
	/**
	 * We may reuse singleton instance since advice is stateless.
	 */
	public static final GetParentEntityMethodClassifier INSTANCE = new GetParentEntityMethodClassifier();

	/**
	 * Implementation that returns an integer value of parent entity id.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<EntityClassifier, SealedEntityProxyState> singleParentIdResult() {
		return (entityClassifier, theMethod, args, theState, invokeSuper) ->
			theState.getSealedEntity().getParent().stream().boxed().findFirst().orElse(null);
	}

	/**
	 * Implementation that returns an {@link EntityReference} of parent entity.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<EntityClassifier, SealedEntityProxyState> singleParentReferenceResult() {
		return (entityClassifier, theMethod, args, theState, invokeSuper) -> {
			final SealedEntity sealedEntity = theState.getSealedEntity();
			return sealedEntity
				.getParent()
				.stream()
				.mapToObj(it -> new EntityReference(sealedEntity.getType(), it))
				.findFirst()
				.orElse(null);
		};
	}

	/**
	 * Implementation that returns a custom proxy class wrapping a {@link SealedEntity} object of parent entity.
	 */
	@Nonnull
	private static CurriedMethodContextInvocationHandler<EntityClassifier, SealedEntityProxyState> singleParentEntityResult(
		@Nonnull Class<? extends EntityClassifier> itemType
	) {
		return (entityClassifier, theMethod, args, theState, invokeSuper) -> {
			final Optional<EntityClassifierWithParent> parentEntity = theState.getSealedEntity().getParentEntity();
			Assert.isTrue(
				parentEntity.map(it -> it instanceof SealedEntity).orElse(true),
				() -> "Entity `" + theState.getSealedEntity().getType() + "` hierarchy content was not fetched with " +
					"`entityFetch` requirement. Parent entity body is not available."
			);
			return parentEntity
				.map(it -> theState.createEntityProxy(itemType, (SealedEntity) it))
				.orElse(null);
		};
	}

	public GetParentEntityMethodClassifier() {
		super(
			"getParentEntity",
			(method, proxyState) -> {
				// Method must be abstract and have no parameters
				if (!ClassUtils.isAbstractOrDefault(method) || method.getParameterCount() > 0) {
					return null;
				}

				// first we need to identify whether the method returns a parent entity
				final ReflectionLookup reflectionLookup = proxyState.getReflectionLookup();
				final ParentEntity parentEntity = reflectionLookup.getAnnotationInstance(method, ParentEntity.class);
				if (parentEntity == null) {
					return null;
				}

				// it may also return an class that is annotated with entity annotation and its entity name must exactly
				// match the parent entity name derived from actual schema
				@SuppressWarnings("rawtypes") final Class returnType = method.getReturnType();
				final Entity entityInstance = reflectionLookup.getClassAnnotation(returnType, Entity.class);
				final EntityRef entityRefInstance = reflectionLookup.getClassAnnotation(returnType, EntityRef.class);
				final Optional<String> entityType = Optional.ofNullable(entityInstance)
					.map(Entity::name)
					.or(() -> Optional.ofNullable(entityRefInstance).map(EntityRef::value));

				Assert.isTrue(
					entityType.map(it -> Objects.equals(it, proxyState.getEntitySchema().getName())).orElse(true),
					() -> new EntityClassInvalidException(
						returnType,
						"Entity class type `" + proxyState.getProxyClass() + "` parent must represent same entity type, " +
							" but the return class `" + returnType + "` is annotated with @Entity referencing `" +
							entityType.orElse("N/A") + "` entity type!"
					)
				);

				// now we need to identify the return type and return appropriate implementation
				if (Number.class.isAssignableFrom(EvitaDataTypes.toWrappedForm(returnType))) {
					return singleParentIdResult();
				} else if (returnType.equals(EntityReference.class)) {
					return singleParentReferenceResult();
				} else {
					//noinspection unchecked
					return singleParentEntityResult(returnType);
				}
			}
		);
	}

}
