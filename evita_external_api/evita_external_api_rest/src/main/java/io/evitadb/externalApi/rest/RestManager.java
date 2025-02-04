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

package io.evitadb.externalApi.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.evitadb.api.CatalogContract;
import io.evitadb.core.CorruptedCatalog;
import io.evitadb.core.Evita;
import io.evitadb.externalApi.http.CorsFilter;
import io.evitadb.externalApi.http.PathNormalizingHandler;
import io.evitadb.externalApi.rest.api.Rest;
import io.evitadb.externalApi.rest.api.Rest.Endpoint;
import io.evitadb.externalApi.rest.api.catalog.CatalogRestBuilder;
import io.evitadb.externalApi.rest.api.openApi.OpenApiSystemEndpoint;
import io.evitadb.externalApi.rest.api.system.SystemRestBuilder;
import io.evitadb.externalApi.rest.configuration.RestConfig;
import io.evitadb.externalApi.rest.exception.OpenApiInternalError;
import io.evitadb.externalApi.rest.io.CorsEndpoint;
import io.evitadb.externalApi.rest.io.RestEndpointHandler;
import io.evitadb.externalApi.rest.io.RestExceptionHandler;
import io.evitadb.externalApi.utils.UriPath;
import io.evitadb.utils.Assert;
import io.evitadb.utils.StringUtils;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

import static io.evitadb.utils.CollectionUtils.createConcurrentHashMap;
import static io.evitadb.utils.CollectionUtils.createHashSet;

/**
 * Creates REST endpoints for particular Evita catalogs according generated OpenAPI schema.
 *
 * @author Martin Veska (veska@fg.cz), FG Forrest a.s. (c) 2022
 */
@Slf4j
public class RestManager {

	/**
	 * Common object mapper for endpoints
	 */
	@Nonnull private final ObjectMapper objectMapper = new ObjectMapper();

	@Nonnull private final Evita evita;
	@Nullable private final String exposedOn;
	@Nonnull private final RestConfig restConfig;

	/**
	 * All registered endpoint paths for each catalog
	 */
	@Nonnull private final Map<String, Set<Endpoint>> registeredCatalogEndpoints = createConcurrentHashMap(20);
	/**
	 * REST specific endpoint router.
	 */
	private final RoutingHandler restRouter = Handlers.routing();
	@Nonnull private final Map<UriPath, CorsEndpoint> corsEndpoints = createConcurrentHashMap(20);

	public RestManager(@Nonnull Evita evita, @Nullable String exposedOn, @Nonnull RestConfig restConfig) {
		this.evita = evita;
		this.exposedOn = exposedOn;
		this.restConfig = restConfig;

		final long buildingStartTime = System.currentTimeMillis();

		// register initial endpoints
		registerSystemApi();
		this.evita.getCatalogs().forEach(catalog -> registerCatalog(catalog.getName()));
		corsEndpoints.forEach((path, endpoint) -> restRouter.add(Methods.OPTIONS, path.toString(), endpoint.toHandler()));

		log.info("Built REST API in " + StringUtils.formatPreciseNano(System.currentTimeMillis() - buildingStartTime));
	}

	@Nonnull
	public HttpHandler getRestRouter() {
		return new PathNormalizingHandler(restRouter);
	}

	/**
	 * Register REST endpoints for new catalog.
	 */
	public void registerCatalog(@Nonnull String catalogName) {
		final CatalogContract catalog = evita.getCatalogInstanceOrThrowException(catalogName);
		if (catalog instanceof CorruptedCatalog) {
			log.warn("Catalog `" + catalogName + "` is corrupted. Skipping...");
			return;
		}
		Assert.isPremiseValid(
			!registeredCatalogEndpoints.containsKey(catalogName),
			() -> new OpenApiInternalError("Catalog `" + catalogName + "` has been already registered.")
		);

		final CatalogRestBuilder catalogRestBuilder = new CatalogRestBuilder(exposedOn, restConfig, evita, catalog);
		final Rest builtRest = catalogRestBuilder.build();

		builtRest.endpoints().forEach(endpoint -> registerCatalogRestEndpoint(catalog, endpoint));
	}

	/**
	 * Unregister all REST endpoints of catalog.
	 */
	public void unregisterCatalog(@Nonnull String catalogName) {
		final CatalogContract catalog = evita.getCatalogInstanceOrThrowException(catalogName);

		final Set<Endpoint> endpointsForCatalog = registeredCatalogEndpoints.remove(catalogName);
		if (endpointsForCatalog != null) {
			endpointsForCatalog.forEach(endpoint -> {
				final String catalogPath = constructCatalogPath(catalog, endpoint.path()).toString();

				restRouter.remove(endpoint.method(), catalogPath);

				corsEndpoints.remove(catalogPath);
				restRouter.remove(Methods.OPTIONS, catalogPath);
			});
		}
	}

	/**
	 * Update REST endpoints and OpenAPI schema of catalog.
	 */
	public void refreshCatalog(@Nonnull String catalogName) {
		if (!registeredCatalogEndpoints.containsKey(catalogName)) {
			// there may be case where initial registration failed and catalog is not registered at all
			// for example, when catalog was corrupted and is replaced with new fresh one
			log.info("Could not refresh existing catalog `{}`. Registering new one instead...", catalogName);
			registerCatalog(catalogName);
			return;
		}

		final CatalogContract catalog = evita.getCatalogInstanceOrThrowException(catalogName);
		final CatalogRestBuilder catalogRestBuilder = new CatalogRestBuilder(exposedOn, restConfig, evita, catalog);
		final Rest builtRest = catalogRestBuilder.build();

		unregisterCatalog(catalogName);
		builtRest.endpoints().forEach(endpoint -> registerCatalogRestEndpoint(catalog, endpoint));
	}

	/**
	 * Builds and registers system API to manage evitaDB
	 */
	private void registerSystemApi() {
		final SystemRestBuilder systemRestBuilder = new SystemRestBuilder(exposedOn, restConfig, evita);
		final Rest builtRest = systemRestBuilder.build();
		builtRest.endpoints().forEach(this::registerSystemRestEndpoint);
	}

	/**
	 * Creates new REST endpoint on specified path with specified {@link Rest} instance.
	 */
	private void registerCatalogRestEndpoint(@Nonnull CatalogContract catalog, @Nonnull Rest.Endpoint endpoint) {
		final Set<Endpoint> endpointsForCatalog = registeredCatalogEndpoints.computeIfAbsent(catalog.getName(), key -> createHashSet(100));
		endpointsForCatalog.add(endpoint);

		registerRestEndpoint(
			endpoint.method(),
			constructCatalogPath(catalog, endpoint.path()),
			endpoint.handler()
		);
	}

	/**
	 * Creates new REST endpoint on specified path with specified {@link Rest} instance.
	 */
	private void registerSystemRestEndpoint(@Nonnull Rest.Endpoint endpoint) {
		registerRestEndpoint(
			endpoint.method(),
			UriPath.of("/", OpenApiSystemEndpoint.URL_PREFIX, endpoint.path()),
			endpoint.handler()
		);
	}

	/**
	 * Registers endpoints into router. Also CORS endpoint is created automatically for this endpoint.
	 */
	private void registerRestEndpoint(@Nonnull HttpString method, @Nonnull UriPath path, @Nonnull RestEndpointHandler<?, ?> handler) {
		final CorsEndpoint corsEndpoint = corsEndpoints.computeIfAbsent(path, p -> new CorsEndpoint(restConfig));
		corsEndpoint.addMetadataFromHandler(handler);

		restRouter.add(
			method,
			path.toString(),
			new BlockingHandler(
				new CorsFilter(
					new RestExceptionHandler(
						objectMapper,
						handler
					),
					restConfig.getAllowedOrigins()
				)
			)
		);
	}

	@Nonnull
	private UriPath constructCatalogPath(@Nonnull CatalogContract catalog, @Nonnull UriPath endpointPath) {
		return UriPath.of(catalog.getSchema().getName(), endpointPath);
	}
}
