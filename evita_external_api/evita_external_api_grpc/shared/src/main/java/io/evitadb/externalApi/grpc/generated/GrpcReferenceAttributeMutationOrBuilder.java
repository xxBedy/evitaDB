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
// source: GrpcReferenceMutations.proto

package io.evitadb.externalApi.grpc.generated;

public interface GrpcReferenceAttributeMutationOrBuilder extends
    // @@protoc_insertion_point(interface_extends:io.evitadb.externalApi.grpc.generated.GrpcReferenceAttributeMutation)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * Unique identifier of the reference.
   * </pre>
   *
   * <code>string referenceName = 1;</code>
   * @return The referenceName.
   */
  java.lang.String getReferenceName();
  /**
   * <pre>
   * Unique identifier of the reference.
   * </pre>
   *
   * <code>string referenceName = 1;</code>
   * @return The bytes for referenceName.
   */
  com.google.protobuf.ByteString
      getReferenceNameBytes();

  /**
   * <pre>
   * Primary key of the referenced entity. Might be also any integer that uniquely identifies some external
   * resource not maintained by Evita.
   * </pre>
   *
   * <code>int32 referencePrimaryKey = 2;</code>
   * @return The referencePrimaryKey.
   */
  int getReferencePrimaryKey();

  /**
   * <pre>
   * One attribute mutation to update / insert / delete single attribute of the reference.
   * </pre>
   *
   * <code>.io.evitadb.externalApi.grpc.generated.GrpcAttributeMutation attributeMutation = 3;</code>
   * @return Whether the attributeMutation field is set.
   */
  boolean hasAttributeMutation();
  /**
   * <pre>
   * One attribute mutation to update / insert / delete single attribute of the reference.
   * </pre>
   *
   * <code>.io.evitadb.externalApi.grpc.generated.GrpcAttributeMutation attributeMutation = 3;</code>
   * @return The attributeMutation.
   */
  io.evitadb.externalApi.grpc.generated.GrpcAttributeMutation getAttributeMutation();
  /**
   * <pre>
   * One attribute mutation to update / insert / delete single attribute of the reference.
   * </pre>
   *
   * <code>.io.evitadb.externalApi.grpc.generated.GrpcAttributeMutation attributeMutation = 3;</code>
   */
  io.evitadb.externalApi.grpc.generated.GrpcAttributeMutationOrBuilder getAttributeMutationOrBuilder();
}
