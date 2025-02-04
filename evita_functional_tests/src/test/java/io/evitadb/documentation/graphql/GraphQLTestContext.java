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

package io.evitadb.documentation.graphql;

import io.evitadb.documentation.TestContext;
import io.evitadb.test.client.GraphQLClient;
import lombok.Getter;

/**
 * Context creates new {@link GraphQLClient} instance that is connected to the demo server.
 * The {@link GraphQLClient} instance is reused between tests to speed them up.
 *
 * @author Lukáš Hornych, FG Forrest a.s. (c) 2023
 */
public class GraphQLTestContext implements TestContext {
	/**
	 * Initialized client instance.
	 */
	@Getter
	private final GraphQLClient graphQLClient;

	public GraphQLTestContext() {
		this.graphQLClient = new GraphQLClient("https://demo.evitadb.io:5555/gql/evita");
		// for local documentation testing
//		this.graphQLClient = new GraphQLClient("https://localhost:5555/gql/evita", false);
	}
}
