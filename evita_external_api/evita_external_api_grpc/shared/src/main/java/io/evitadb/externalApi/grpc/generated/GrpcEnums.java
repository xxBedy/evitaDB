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
// source: GrpcEnums.proto

package io.evitadb.externalApi.grpc.generated;

public final class GrpcEnums {
  private GrpcEnums() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_io_evitadb_externalApi_grpc_generated_GrpcEvitaAssociatedDataDataType_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_io_evitadb_externalApi_grpc_generated_GrpcEvitaAssociatedDataDataType_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\017GrpcEnums.proto\022%io.evitadb.externalAp" +
      "i.grpc.generated\"\374\005\n\037GrpcEvitaAssociated" +
      "DataDataType\"\330\005\n\021GrpcEvitaDataType\022\n\n\006ST" +
      "RING\020\000\022\010\n\004BYTE\020\001\022\t\n\005SHORT\020\002\022\013\n\007INTEGER\020\003" +
      "\022\010\n\004LONG\020\004\022\013\n\007BOOLEAN\020\005\022\r\n\tCHARACTER\020\006\022\017" +
      "\n\013BIG_DECIMAL\020\007\022\024\n\020OFFSET_DATE_TIME\020\010\022\023\n" +
      "\017LOCAL_DATE_TIME\020\t\022\016\n\nLOCAL_DATE\020\n\022\016\n\nLO" +
      "CAL_TIME\020\013\022\023\n\017DATE_TIME_RANGE\020\014\022\025\n\021BIG_D" +
      "ECIMAL_RANGE\020\r\022\016\n\nLONG_RANGE\020\016\022\021\n\rINTEGE" +
      "R_RANGE\020\017\022\017\n\013SHORT_RANGE\020\020\022\016\n\nBYTE_RANGE" +
      "\020\021\022\027\n\023COMPLEX_DATA_OBJECT\020\022\022\020\n\014STRING_AR" +
      "RAY\020\023\022\016\n\nBYTE_ARRAY\020\024\022\017\n\013SHORT_ARRAY\020\025\022\021" +
      "\n\rINTEGER_ARRAY\020\026\022\016\n\nLONG_ARRAY\020\027\022\021\n\rBOO" +
      "LEAN_ARRAY\020\030\022\023\n\017CHARACTER_ARRAY\020\031\022\025\n\021BIG" +
      "_DECIMAL_ARRAY\020\032\022\032\n\026OFFSET_DATE_TIME_ARR" +
      "AY\020\033\022\031\n\025LOCAL_DATE_TIME_ARRAY\020\034\022\024\n\020LOCAL" +
      "_DATE_ARRAY\020\035\022\024\n\020LOCAL_TIME_ARRAY\020\036\022\031\n\025D" +
      "ATE_TIME_RANGE_ARRAY\020\037\022\033\n\027BIG_DECIMAL_RA" +
      "NGE_ARRAY\020 \022\024\n\020LONG_RANGE_ARRAY\020!\022\027\n\023INT" +
      "EGER_RANGE_ARRAY\020\"\022\025\n\021SHORT_RANGE_ARRAY\020" +
      "#\022\024\n\020BYTE_RANGE_ARRAY\020$*-\n\020GrpcCatalogSt" +
      "ate\022\016\n\nWARMING_UP\020\000\022\t\n\005ALIVE\020\001*3\n\022GrpcQu" +
      "eryPriceMode\022\014\n\010WITH_TAX\020\000\022\017\n\013WITHOUT_TA" +
      "X\020\001*F\n\024GrpcPriceContentMode\022\016\n\nFETCH_NON" +
      "E\020\000\022\025\n\021RESPECTING_FILTER\020\001\022\007\n\003ALL\020\002*\'\n\022G" +
      "rpcOrderDirection\022\007\n\003ASC\020\000\022\010\n\004DESC\020\001*3\n\031" +
      "GrpcAttributeSpecialValue\022\010\n\004NULL\020\000\022\014\n\010N" +
      "OT_NULL\020\001*2\n\030GrpcFacetStatisticsDepth\022\n\n" +
      "\006COUNTS\020\000\022\n\n\006IMPACT\020\001*T\n\034GrpcPriceInnerR" +
      "ecordHandling\022\010\n\004NONE\020\000\022\024\n\020FIRST_OCCURRE" +
      "NCE\020\001\022\007\n\003SUM\020\002\022\013\n\007UNKNOWN\020\003*]\n\017GrpcSessi" +
      "onType\022\r\n\tREAD_ONLY\020\000\022\016\n\nREAD_WRITE\020\001\022\024\n" +
      "\020BINARY_READ_ONLY\020\002\022\025\n\021BINARY_READ_WRITE" +
      "\020\003*i\n\017GrpcCardinality\022\021\n\rNOT_SPECIFIED\020\000" +
      "\022\017\n\013ZERO_OR_ONE\020\001\022\017\n\013EXACTLY_ONE\020\002\022\020\n\014ZE" +
      "RO_OR_MORE\020\003\022\017\n\013ONE_OR_MORE\020\004*\323\001\n\021GrpcEv" +
      "olutionMode\022 \n\034ADAPT_PRIMARY_KEY_GENERAT" +
      "ION\020\000\022\025\n\021ADDING_ATTRIBUTES\020\001\022\032\n\026ADDING_A" +
      "SSOCIATED_DATA\020\002\022\025\n\021ADDING_REFERENCES\020\003\022" +
      "\021\n\rADDING_PRICES\020\004\022\022\n\016ADDING_LOCALES\020\005\022\025" +
      "\n\021ADDING_CURRENCIES\020\006\022\024\n\020ADDING_HIERARCH" +
      "Y\020\007*3\n\030GrpcCatalogEvolutionMode\022\027\n\023ADDIN" +
      "G_ENTITY_TYPES\020\000*\305\006\n\021GrpcEvitaDataType\022\n" +
      "\n\006STRING\020\000\022\010\n\004BYTE\020\001\022\t\n\005SHORT\020\002\022\013\n\007INTEG" +
      "ER\020\003\022\010\n\004LONG\020\004\022\013\n\007BOOLEAN\020\005\022\r\n\tCHARACTER" +
      "\020\006\022\017\n\013BIG_DECIMAL\020\007\022\024\n\020OFFSET_DATE_TIME\020" +
      "\010\022\023\n\017LOCAL_DATE_TIME\020\t\022\016\n\nLOCAL_DATE\020\n\022\016" +
      "\n\nLOCAL_TIME\020\013\022\023\n\017DATE_TIME_RANGE\020\014\022\034\n\030B" +
      "IG_DECIMAL_NUMBER_RANGE\020\r\022\025\n\021LONG_NUMBER" +
      "_RANGE\020\016\022\030\n\024INTEGER_NUMBER_RANGE\020\017\022\026\n\022SH" +
      "ORT_NUMBER_RANGE\020\020\022\025\n\021BYTE_NUMBER_RANGE\020" +
      "\021\022\n\n\006LOCALE\020\022\022\014\n\010CURRENCY\020\023\022\020\n\014STRING_AR" +
      "RAY\020\024\022\016\n\nBYTE_ARRAY\020\025\022\017\n\013SHORT_ARRAY\020\026\022\021" +
      "\n\rINTEGER_ARRAY\020\027\022\016\n\nLONG_ARRAY\020\030\022\021\n\rBOO" +
      "LEAN_ARRAY\020\031\022\023\n\017CHARACTER_ARRAY\020\032\022\025\n\021BIG" +
      "_DECIMAL_ARRAY\020\033\022\032\n\026OFFSET_DATE_TIME_ARR" +
      "AY\020\034\022\031\n\025LOCAL_DATE_TIME_ARRAY\020\035\022\024\n\020LOCAL" +
      "_DATE_ARRAY\020\036\022\024\n\020LOCAL_TIME_ARRAY\020\037\022\031\n\025D" +
      "ATE_TIME_RANGE_ARRAY\020 \022\"\n\036BIG_DECIMAL_NU" +
      "MBER_RANGE_ARRAY\020!\022\033\n\027LONG_NUMBER_RANGE_" +
      "ARRAY\020\"\022\036\n\032INTEGER_NUMBER_RANGE_ARRAY\020#\022" +
      "\034\n\030SHORT_NUMBER_RANGE_ARRAY\020$\022\033\n\027BYTE_NU" +
      "MBER_RANGE_ARRAY\020%\022\020\n\014LOCALE_ARRAY\020&\022\022\n\016" +
      "CURRENCY_ARRAY\020\'*\253\004\n\016GrpcQueryPhase\022\013\n\007O" +
      "VERALL\020\000\022\014\n\010PLANNING\020\001\022\031\n\025PLANNING_NESTE" +
      "D_QUERY\020\002\022\030\n\024PLANNING_INDEX_USAGE\020\003\022\023\n\017P" +
      "LANNING_FILTER\020\004\022 \n\034PLANNING_FILTER_NEST" +
      "ED_QUERY\020\005\022\037\n\033PLANNING_FILTER_ALTERNATIV" +
      "E\020\006\022\021\n\rPLANNING_SORT\020\007\022\035\n\031PLANNING_SORT_" +
      "ALTERNATIVE\020\010\022%\n!PLANNING_EXTRA_RESULT_F" +
      "ABRICATION\020\t\0221\n-PLANNING_EXTRA_RESULT_FA" +
      "BRICATION_ALTERNATIVE\020\n\022\r\n\tEXECUTION\020\013\022\026" +
      "\n\022EXECUTION_PREFETCH\020\014\022\024\n\020EXECUTION_FILT" +
      "ER\020\r\022!\n\035EXECUTION_FILTER_NESTED_QUERY\020\016\022" +
      "\034\n\030EXECUTION_SORT_AND_SLICE\020\017\022\035\n\031EXTRA_R" +
      "ESULTS_FABRICATION\020\020\022!\n\035EXTRA_RESULT_ITE" +
      "M_FABRICATION\020\021\022\014\n\010FETCHING\020\022\022\027\n\023FETCHIN" +
      "G_REFERENCES\020\023*H\n\023GrpcEntityExistence\022\r\n" +
      "\tMAY_EXIST\020\000\022\022\n\016MUST_NOT_EXIST\020\001\022\016\n\nMUST" +
      "_EXIST\020\002B\002P\001b\006proto3"
    };
    descriptor = com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        });
    internal_static_io_evitadb_externalApi_grpc_generated_GrpcEvitaAssociatedDataDataType_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_io_evitadb_externalApi_grpc_generated_GrpcEvitaAssociatedDataDataType_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_io_evitadb_externalApi_grpc_generated_GrpcEvitaAssociatedDataDataType_descriptor,
        new java.lang.String[] { });
  }

  // @@protoc_insertion_point(outer_class_scope)
}
