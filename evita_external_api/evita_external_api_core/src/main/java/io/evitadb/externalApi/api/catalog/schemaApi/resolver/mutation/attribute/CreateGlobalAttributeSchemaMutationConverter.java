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

package io.evitadb.externalApi.api.catalog.schemaApi.resolver.mutation.attribute;

import io.evitadb.api.requestResponse.schema.mutation.attribute.CreateGlobalAttributeSchemaMutation;
import io.evitadb.externalApi.api.catalog.dataApi.resolver.mutation.ValueTypeMapper;
import io.evitadb.externalApi.api.catalog.resolver.mutation.Input;
import io.evitadb.externalApi.api.catalog.resolver.mutation.MutationObjectParser;
import io.evitadb.externalApi.api.catalog.resolver.mutation.MutationResolvingExceptionFactory;
import io.evitadb.externalApi.api.catalog.schemaApi.model.mutation.attribute.AttributeSchemaMutationDescriptor;
import io.evitadb.externalApi.api.catalog.schemaApi.model.mutation.attribute.CreateGlobalAttributeSchemaMutationDescriptor;
import io.evitadb.externalApi.api.catalog.schemaApi.resolver.mutation.SchemaMutationConverter;

import javax.annotation.Nonnull;
import java.io.Serializable;

/**
 * Implementation of {@link SchemaMutationConverter} for resolving {@link CreateGlobalAttributeSchemaMutation}.
 *
 * @author Lukáš Hornych, FG Forrest a.s. (c) 2023
 */
public class CreateGlobalAttributeSchemaMutationConverter extends AttributeSchemaMutationConverter<CreateGlobalAttributeSchemaMutation> {

	public CreateGlobalAttributeSchemaMutationConverter(@Nonnull MutationObjectParser objectParser,
	                                                    @Nonnull MutationResolvingExceptionFactory exceptionFactory) {
		super(objectParser, exceptionFactory);
	}

	@Nonnull
	@Override
	protected String getMutationName() {
		return CreateGlobalAttributeSchemaMutationDescriptor.THIS.name();
	}

	@Nonnull
	@Override
	protected CreateGlobalAttributeSchemaMutation convert(@Nonnull Input input) {
		final Class<? extends Serializable> valueType = input.getRequiredField(
			CreateGlobalAttributeSchemaMutationDescriptor.TYPE.name(),
			new ValueTypeMapper(getExceptionFactory(), CreateGlobalAttributeSchemaMutationDescriptor.TYPE)
		);

		return new CreateGlobalAttributeSchemaMutation(
			input.getRequiredField(AttributeSchemaMutationDescriptor.NAME),
			input.getOptionalField(CreateGlobalAttributeSchemaMutationDescriptor.DESCRIPTION),
			input.getOptionalField(CreateGlobalAttributeSchemaMutationDescriptor.DEPRECATION_NOTICE),
			input.getOptionalField(CreateGlobalAttributeSchemaMutationDescriptor.UNIQUE, false),
			input.getOptionalField(CreateGlobalAttributeSchemaMutationDescriptor.UNIQUE_GLOBALLY, false),
			input.getOptionalField(CreateGlobalAttributeSchemaMutationDescriptor.FILTERABLE, false),
			input.getOptionalField(CreateGlobalAttributeSchemaMutationDescriptor.SORTABLE, false),
			input.getOptionalField(CreateGlobalAttributeSchemaMutationDescriptor.LOCALIZED, false),
			input.getOptionalField(CreateGlobalAttributeSchemaMutationDescriptor.NULLABLE, false),
			input.getOptionalField(CreateGlobalAttributeSchemaMutationDescriptor.REPRESENTATIVE, false),
			valueType,
			input.getOptionalField(CreateGlobalAttributeSchemaMutationDescriptor.DEFAULT_VALUE.name(), valueType),
			input.getOptionalField(CreateGlobalAttributeSchemaMutationDescriptor.INDEXED_DECIMAL_PLACES, 0)
		);
	}
}
