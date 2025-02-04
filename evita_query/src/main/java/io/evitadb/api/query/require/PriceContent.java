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

package io.evitadb.api.query.require;

import io.evitadb.api.query.ConstraintWithSuffix;
import io.evitadb.api.query.PriceConstraint;
import io.evitadb.api.query.RequireConstraint;
import io.evitadb.api.query.descriptor.ConstraintDomain;
import io.evitadb.api.query.descriptor.annotation.AliasForParameter;
import io.evitadb.api.query.descriptor.annotation.ConstraintDefinition;
import io.evitadb.api.query.descriptor.annotation.Creator;
import io.evitadb.utils.ArrayUtils;
import io.evitadb.utils.Assert;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serial;
import java.io.Serializable;
import java.util.Optional;

import static java.util.Optional.empty;
import static java.util.Optional.of;

/**
 * This `prices` requirement changes default behaviour of the query engine returning only entity primary keys in the result. When
 * this requirement is used result contains [entity prices](entity_model.md).
 *
 * This requirement implicitly triggers {@link EntityFetch} requirement because prices cannot be returned without entity.
 * When price constraints are used returned prices are filtered according to them by default. This behaviour might be
 * changed, however.
 *
 * Accepts single {@link PriceContentMode} parameter. When {@link PriceContentMode#ALL} all prices of the entity are returned
 * regardless of the input query constraints otherwise prices are filtered by those constraints. Default is {@link PriceContentMode#RESPECTING_FILTER}.
 *
 * Example:
 *
 * ```
 * prices() // defaults to respecting filter
 * prices(RESPECTING_FILTER)
 * prices(ALL)
 * prices(NONE)
 * ```
 *
 * @author Jan Novotný (novotny@fg.cz), FG Forrest a.s. (c) 2021
 */
@ConstraintDefinition(
	name = "content",
	shortDescription = "The constraint triggers fetching the entity prices into the returned entities.",
	supportedIn = ConstraintDomain.ENTITY
)
public class PriceContent extends AbstractRequireConstraintLeaf
	implements PriceConstraint<RequireConstraint>, EntityContentRequire, ConstraintWithSuffix {
	public static final String[] EMPTY_PRICE_LISTS = new String[0];
	@Serial private static final long serialVersionUID = -8521118631539528009L;
	private static final String SUFFIX_ALL = "all";
	private static final String SUFFIX_FILTERED = "respectingFilter";

	private PriceContent(Serializable... arguments) {
		super(arguments);
	}

	@Creator
	public PriceContent(@Nonnull PriceContentMode contentMode,
	                    @Nonnull String... priceLists) {
		super(ArrayUtils.mergeArrays(new Serializable[] {contentMode}, priceLists));
	}

	@Creator(suffix = SUFFIX_ALL)
	public static PriceContent all() {
		return new PriceContent(PriceContentMode.ALL);
	}

	@Creator(suffix = SUFFIX_FILTERED)
	public static PriceContent respectingFilter(@Nullable String... priceLists) {
		return new PriceContent(PriceContentMode.RESPECTING_FILTER, priceLists);
	}

	/**
	 * Returns fetch mode for prices. Controls whether only those that comply with the filter query
	 * should be returned along with entity or all prices of the entity.
	 */
	public PriceContentMode getFetchMode() {
		final Serializable argument = getArguments()[0];
		return argument instanceof PriceContentMode ? (PriceContentMode) argument : PriceContentMode.valueOf(argument.toString());
	}

	/**
	 * Returns set of price list names that should be fetched along with entities on top of prices that are fetched
	 * due to {@link #getFetchMode()} or empty array.
	 */
	@AliasForParameter("priceLists")
	@Nonnull
	public String[] getAdditionalPriceListsToFetch() {
		final Serializable[] arguments = getArguments();
		if (arguments.length > 1) {
			return ArrayUtils.copyOf(arguments, String.class, 1, arguments.length);
		} else {
			return EMPTY_PRICE_LISTS;
		}
	}

	@Nonnull
	@Override
	public Optional<String> getSuffixIfApplied() {
		return switch (getFetchMode()) {
			case NONE -> empty();
			case RESPECTING_FILTER -> of(SUFFIX_FILTERED);
			case ALL -> of(SUFFIX_ALL);
		};
	}

	@Override
	public boolean isArgumentImplicitForSuffix(@Nonnull Serializable argument) {
		return argument instanceof PriceContentMode &&
			getFetchMode() != PriceContentMode.NONE;
	}

	@Nonnull
	@SuppressWarnings("unchecked")
	@Override
	public <T extends EntityContentRequire> T combineWith(@Nonnull T anotherRequirement) {
		Assert.isTrue(anotherRequirement instanceof PriceContent, "Only Prices requirement can be combined with this one!");
		final PriceContent anotherPriceContent = (PriceContent) anotherRequirement;
		if (anotherPriceContent.getFetchMode().ordinal() >= getFetchMode().ordinal()) {
			final String[] additionalPriceListsToFetch = getAdditionalPriceListsToFetch();
			if (ArrayUtils.isEmpty(additionalPriceListsToFetch)) {
				return anotherRequirement;
			} else {
				return (T) new PriceContent(
					anotherPriceContent.getFetchMode(),
					ArrayUtils.mergeArrays(additionalPriceListsToFetch, anotherPriceContent.getAdditionalPriceListsToFetch())
				);
			}
		} else {
			final String[] additionalPriceListsToFetch = anotherPriceContent.getAdditionalPriceListsToFetch();
			if (ArrayUtils.isEmpty(additionalPriceListsToFetch)) {
				return (T) this;
			} else {
				return (T) new PriceContent(
					getFetchMode(),
					ArrayUtils.mergeArrays(additionalPriceListsToFetch, anotherPriceContent.getAdditionalPriceListsToFetch())
				);
			}
		}
	}

	@Nonnull
	@Override
	public RequireConstraint cloneWithArguments(@Nonnull Serializable[] newArguments) {
		return new PriceContent(newArguments);
	}

}
