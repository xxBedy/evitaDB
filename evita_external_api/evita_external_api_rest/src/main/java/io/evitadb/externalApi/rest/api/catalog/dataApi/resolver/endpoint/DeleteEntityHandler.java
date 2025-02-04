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

package io.evitadb.externalApi.rest.api.catalog.dataApi.resolver.endpoint;

import io.evitadb.api.query.require.EntityContentRequire;
import io.evitadb.api.requestResponse.data.SealedEntity;
import io.evitadb.externalApi.http.EndpointResponse;
import io.evitadb.externalApi.http.NotFoundEndpointResponse;
import io.evitadb.externalApi.http.SuccessEndpointResponse;
import io.evitadb.externalApi.rest.api.catalog.dataApi.model.header.DeleteEntityEndpointHeaderDescriptor;
import io.evitadb.externalApi.rest.api.catalog.dataApi.resolver.constraint.RequireConstraintFromRequestQueryBuilder;
import io.evitadb.externalApi.rest.exception.RestInvalidArgumentException;
import io.evitadb.externalApi.rest.io.RestEndpointExchange;
import io.evitadb.utils.Assert;
import io.undertow.util.Methods;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;

/**
 * Handles single entity delete request.
 *
 * @author Martin Veska (veska@fg.cz), FG Forrest a.s. (c) 2022
 */
@Slf4j
public class DeleteEntityHandler extends EntityHandler<SealedEntity, CollectionRestHandlingContext> {

	public DeleteEntityHandler(@Nonnull CollectionRestHandlingContext restApiHandlingContext) {
		super(restApiHandlingContext);
	}

	@Override
	protected boolean modifiesData() {
		return true;
	}

	@Override
	@Nonnull
	protected EndpointResponse<SealedEntity> doHandleRequest(@Nonnull RestEndpointExchange exchange) {
		final Map<String, Object> parametersFromRequest = getParametersFromRequest(exchange);

		Assert.isTrue(
			parametersFromRequest.containsKey(DeleteEntityEndpointHeaderDescriptor.PRIMARY_KEY.name()),
			() -> new RestInvalidArgumentException("Primary key wasn't found in URL.")
		);

		final EntityContentRequire[] entityContentRequires = RequireConstraintFromRequestQueryBuilder.getEntityContentRequires(parametersFromRequest);

		return exchange.session()
			.deleteEntity(
				restApiHandlingContext.getEntityType(),
				(Integer) parametersFromRequest.get(DeleteEntityEndpointHeaderDescriptor.PRIMARY_KEY.name()),
				entityContentRequires
			)
			.map(it -> (EndpointResponse<SealedEntity>) new SuccessEndpointResponse<>(it))
			.orElse(new NotFoundEndpointResponse<>());
	}

	@Nonnull
	@Override
	public Set<String> getSupportedHttpMethods() {
		return Set.of(Methods.DELETE_STRING);
	}
}
