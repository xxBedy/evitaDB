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

// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: GrpcEvitaSessionAPI.proto

package io.evitadb.externalApi.grpc.generated;

public interface GrpcDeleteEntitiesRequestOrBuilder extends
    // @@protoc_insertion_point(interface_extends:io.evitadb.externalApi.grpc.generated.GrpcDeleteEntitiesRequest)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>string query = 1;</code>
   * @return The query.
   */
  java.lang.String getQuery();
  /**
   * <code>string query = 1;</code>
   * @return The bytes for query.
   */
  com.google.protobuf.ByteString
      getQueryBytes();

  /**
   * <code>repeated .io.evitadb.externalApi.grpc.generated.QueryParam positionalQueryParams = 2;</code>
   */
  java.util.List<io.evitadb.externalApi.grpc.generated.QueryParam>
      getPositionalQueryParamsList();
  /**
   * <code>repeated .io.evitadb.externalApi.grpc.generated.QueryParam positionalQueryParams = 2;</code>
   */
  io.evitadb.externalApi.grpc.generated.QueryParam getPositionalQueryParams(int index);
  /**
   * <code>repeated .io.evitadb.externalApi.grpc.generated.QueryParam positionalQueryParams = 2;</code>
   */
  int getPositionalQueryParamsCount();
  /**
   * <code>repeated .io.evitadb.externalApi.grpc.generated.QueryParam positionalQueryParams = 2;</code>
   */
  java.util.List<? extends io.evitadb.externalApi.grpc.generated.QueryParamOrBuilder>
      getPositionalQueryParamsOrBuilderList();
  /**
   * <code>repeated .io.evitadb.externalApi.grpc.generated.QueryParam positionalQueryParams = 2;</code>
   */
  io.evitadb.externalApi.grpc.generated.QueryParamOrBuilder getPositionalQueryParamsOrBuilder(
      int index);

  /**
   * <code>map&lt;string, .io.evitadb.externalApi.grpc.generated.QueryParam&gt; namedQueryParams = 3;</code>
   */
  int getNamedQueryParamsCount();
  /**
   * <code>map&lt;string, .io.evitadb.externalApi.grpc.generated.QueryParam&gt; namedQueryParams = 3;</code>
   */
  boolean containsNamedQueryParams(
      java.lang.String key);
  /**
   * Use {@link #getNamedQueryParamsMap()} instead.
   */
  @java.lang.Deprecated
  java.util.Map<java.lang.String, io.evitadb.externalApi.grpc.generated.QueryParam>
  getNamedQueryParams();
  /**
   * <code>map&lt;string, .io.evitadb.externalApi.grpc.generated.QueryParam&gt; namedQueryParams = 3;</code>
   */
  java.util.Map<java.lang.String, io.evitadb.externalApi.grpc.generated.QueryParam>
  getNamedQueryParamsMap();
  /**
   * <code>map&lt;string, .io.evitadb.externalApi.grpc.generated.QueryParam&gt; namedQueryParams = 3;</code>
   */

  io.evitadb.externalApi.grpc.generated.QueryParam getNamedQueryParamsOrDefault(
      java.lang.String key,
      io.evitadb.externalApi.grpc.generated.QueryParam defaultValue);
  /**
   * <code>map&lt;string, .io.evitadb.externalApi.grpc.generated.QueryParam&gt; namedQueryParams = 3;</code>
   */

  io.evitadb.externalApi.grpc.generated.QueryParam getNamedQueryParamsOrThrow(
      java.lang.String key);
}
