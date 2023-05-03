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
// source: GrpcCatalogSchemaMutation.proto

package io.evitadb.externalApi.grpc.generated;

public final class GrpcCatalogSchemaMutation {
  private GrpcCatalogSchemaMutation() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_io_evitadb_externalApi_grpc_generated_GrpcLocalCatalogSchemaMutation_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_io_evitadb_externalApi_grpc_generated_GrpcLocalCatalogSchemaMutation_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_io_evitadb_externalApi_grpc_generated_GrpcTopLevelCatalogSchemaMutation_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_io_evitadb_externalApi_grpc_generated_GrpcTopLevelCatalogSchemaMutation_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\037GrpcCatalogSchemaMutation.proto\022%io.ev" +
      "itadb.externalApi.grpc.generated\032\"GrpcAt" +
      "tributeSchemaMutations.proto\032 GrpcCatalo" +
      "gSchemaMutations.proto\"\235\024\n\036GrpcLocalCata" +
      "logSchemaMutation\022\203\001\n&modifyCatalogSchem" +
      "aDescriptionMutation\030\001 \001(\0132Q.io.evitadb." +
      "externalApi.grpc.generated.GrpcModifyCat" +
      "alogSchemaDescriptionMutationH\000\022\211\001\n)allo" +
      "wEvolutionModeInCatalogSchemaMutation\030\002 " +
      "\001(\0132T.io.evitadb.externalApi.grpc.genera" +
      "ted.GrpcAllowEvolutionModeInCatalogSchem" +
      "aMutationH\000\022\217\001\n,disallowEvolutionModeInC" +
      "atalogSchemaMutation\030\003 \001(\0132W.io.evitadb." +
      "externalApi.grpc.generated.GrpcDisallowE" +
      "volutionModeInCatalogSchemaMutationH\000\022}\n" +
      "#createGlobalAttributeSchemaMutation\030\004 \001" +
      "(\0132N.io.evitadb.externalApi.grpc.generat" +
      "ed.GrpcCreateGlobalAttributeSchemaMutati" +
      "onH\000\022\211\001\n)modifyAttributeSchemaDefaultVal" +
      "ueMutation\030\005 \001(\0132T.io.evitadb.externalAp" +
      "i.grpc.generated.GrpcModifyAttributeSche" +
      "maDefaultValueMutationH\000\022\223\001\n.modifyAttri" +
      "buteSchemaDeprecationNoticeMutation\030\006 \001(" +
      "\0132Y.io.evitadb.externalApi.grpc.generate" +
      "d.GrpcModifyAttributeSchemaDeprecationNo" +
      "ticeMutationH\000\022\207\001\n(modifyAttributeSchema" +
      "DescriptionMutation\030\007 \001(\0132S.io.evitadb.e" +
      "xternalApi.grpc.generated.GrpcModifyAttr" +
      "ibuteSchemaDescriptionMutationH\000\022y\n!modi" +
      "fyAttributeSchemaNameMutation\030\010 \001(\0132L.io" +
      ".evitadb.externalApi.grpc.generated.Grpc" +
      "ModifyAttributeSchemaNameMutationH\000\022y\n!m" +
      "odifyAttributeSchemaTypeMutation\030\t \001(\0132L" +
      ".io.evitadb.externalApi.grpc.generated.G" +
      "rpcModifyAttributeSchemaTypeMutationH\000\022q" +
      "\n\035removeAttributeSchemaMutation\030\n \001(\0132H." +
      "io.evitadb.externalApi.grpc.generated.Gr" +
      "pcRemoveAttributeSchemaMutationH\000\022\177\n$set" +
      "AttributeSchemaFilterableMutation\030\013 \001(\0132" +
      "O.io.evitadb.externalApi.grpc.generated." +
      "GrpcSetAttributeSchemaFilterableMutation" +
      "H\000\022\207\001\n(setAttributeSchemaGloballyUniqueM" +
      "utation\030\014 \001(\0132S.io.evitadb.externalApi.g" +
      "rpc.generated.GrpcSetAttributeSchemaGlob" +
      "allyUniqueMutationH\000\022}\n#setAttributeSche" +
      "maLocalizedMutation\030\r \001(\0132N.io.evitadb.e" +
      "xternalApi.grpc.generated.GrpcSetAttribu" +
      "teSchemaLocalizedMutationH\000\022{\n\"setAttrib" +
      "uteSchemaNullableMutation\030\016 \001(\0132M.io.evi" +
      "tadb.externalApi.grpc.generated.GrpcSetA" +
      "ttributeSchemaNullableMutationH\000\022{\n\"setA" +
      "ttributeSchemaSortableMutation\030\017 \001(\0132M.i" +
      "o.evitadb.externalApi.grpc.generated.Grp" +
      "cSetAttributeSchemaSortableMutationH\000\022w\n" +
      " setAttributeSchemaUniqueMutation\030\020 \001(\0132" +
      "K.io.evitadb.externalApi.grpc.generated." +
      "GrpcSetAttributeSchemaUniqueMutationH\000\022k" +
      "\n\032createEntitySchemaMutation\030\021 \001(\0132E.io." +
      "evitadb.externalApi.grpc.generated.GrpcC" +
      "reateEntitySchemaMutationH\000\022k\n\032modifyEnt" +
      "itySchemaMutation\030\022 \001(\0132E.io.evitadb.ext" +
      "ernalApi.grpc.generated.GrpcModifyEntity" +
      "SchemaMutationH\000\022s\n\036modifyEntitySchemaNa" +
      "meMutation\030\023 \001(\0132I.io.evitadb.externalAp" +
      "i.grpc.generated.GrpcModifyEntitySchemaN" +
      "ameMutationH\000\022k\n\032removeEntitySchemaMutat" +
      "ion\030\024 \001(\0132E.io.evitadb.externalApi.grpc." +
      "generated.GrpcRemoveEntitySchemaMutation" +
      "H\000B\n\n\010mutation\"\204\003\n!GrpcTopLevelCatalogSc" +
      "hemaMutation\022m\n\033createCatalogSchemaMutat" +
      "ion\030\001 \001(\0132F.io.evitadb.externalApi.grpc." +
      "generated.GrpcCreateCatalogSchemaMutatio" +
      "nH\000\022u\n\037modifyCatalogSchemaNameMutation\030\002" +
      " \001(\0132J.io.evitadb.externalApi.grpc.gener" +
      "ated.GrpcModifyCatalogSchemaNameMutation" +
      "H\000\022m\n\033removeCatalogSchemaMutation\030\003 \001(\0132" +
      "F.io.evitadb.externalApi.grpc.generated." +
      "GrpcRemoveCatalogSchemaMutationH\000B\n\n\010mut" +
      "ationB\002P\001b\006proto3"
    };
    descriptor = com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
          io.evitadb.externalApi.grpc.generated.GrpcAttributeSchemaMutations.getDescriptor(),
          io.evitadb.externalApi.grpc.generated.GrpcCatalogSchemaMutations.getDescriptor(),
        });
    internal_static_io_evitadb_externalApi_grpc_generated_GrpcLocalCatalogSchemaMutation_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_io_evitadb_externalApi_grpc_generated_GrpcLocalCatalogSchemaMutation_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_io_evitadb_externalApi_grpc_generated_GrpcLocalCatalogSchemaMutation_descriptor,
        new java.lang.String[] { "ModifyCatalogSchemaDescriptionMutation", "AllowEvolutionModeInCatalogSchemaMutation", "DisallowEvolutionModeInCatalogSchemaMutation", "CreateGlobalAttributeSchemaMutation", "ModifyAttributeSchemaDefaultValueMutation", "ModifyAttributeSchemaDeprecationNoticeMutation", "ModifyAttributeSchemaDescriptionMutation", "ModifyAttributeSchemaNameMutation", "ModifyAttributeSchemaTypeMutation", "RemoveAttributeSchemaMutation", "SetAttributeSchemaFilterableMutation", "SetAttributeSchemaGloballyUniqueMutation", "SetAttributeSchemaLocalizedMutation", "SetAttributeSchemaNullableMutation", "SetAttributeSchemaSortableMutation", "SetAttributeSchemaUniqueMutation", "CreateEntitySchemaMutation", "ModifyEntitySchemaMutation", "ModifyEntitySchemaNameMutation", "RemoveEntitySchemaMutation", "Mutation", });
    internal_static_io_evitadb_externalApi_grpc_generated_GrpcTopLevelCatalogSchemaMutation_descriptor =
      getDescriptor().getMessageTypes().get(1);
    internal_static_io_evitadb_externalApi_grpc_generated_GrpcTopLevelCatalogSchemaMutation_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_io_evitadb_externalApi_grpc_generated_GrpcTopLevelCatalogSchemaMutation_descriptor,
        new java.lang.String[] { "CreateCatalogSchemaMutation", "ModifyCatalogSchemaNameMutation", "RemoveCatalogSchemaMutation", "Mutation", });
    io.evitadb.externalApi.grpc.generated.GrpcAttributeSchemaMutations.getDescriptor();
    io.evitadb.externalApi.grpc.generated.GrpcCatalogSchemaMutations.getDescriptor();
  }

  // @@protoc_insertion_point(outer_class_scope)
}
