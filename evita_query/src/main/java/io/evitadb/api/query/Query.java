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

package io.evitadb.api.query;

import io.evitadb.api.query.filter.FilterBy;
import io.evitadb.api.query.head.Collection;
import io.evitadb.api.query.order.OrderBy;
import io.evitadb.api.query.require.Require;
import io.evitadb.api.query.visitor.PrettyPrintingVisitor;
import io.evitadb.api.query.visitor.PrettyPrintingVisitor.StringWithParameters;
import io.evitadb.api.query.visitor.QueryPurifierVisitor;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.CacheStrategy;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Main transfer object for Evita Query Language. Contains all data and conditions that query what entities will
 * be queried, in what order and how rich the returned results will be.
 *
 * evitaDB query language is composed of nested set of functions. Each function has its name and set of arguments inside
 * round brackets. Arguments and functions are delimited by a comma. Strings are enveloped inside apostrophes. This language
 * is expected to be used by human operators, on the code level query is represented by a query object tree, that can
 * be constructed directly without intermediate string language form. For the sake of documentation human readable form
 * is used here.
 *
 * Query has these four parts:
 *
 * - {@link #getCollection()}: contains collection (mandatory) specification
 * - {@link #getFilterBy()}: contains constraints limiting entities being returned (optional, if missing all are returned)
 * - {@link #getOrderBy()}: defines in what order will the entities return (optional, if missing entities are ordered by primary integer
 * key in ascending order)
 * - {@link #getRequire()}: contains additional information for the query engine, may hold pagination settings, richness of the entities
 * and so on (optional, if missing only primary keys of all the entities are returned)
 *
 * @author Jan Novotný (novotny@fg.cz), FG Forrest a.s. (c) 2021
 */
@EqualsAndHashCode(of = {"collection", "filterBy", "orderBy", "require"}, cacheStrategy = CacheStrategy.LAZY)
public class Query implements Serializable {
	@Serial private static final long serialVersionUID = -1797234436133920949L;

	@Nullable private final Collection collection;
	@Nullable private final FilterBy filterBy;
	@Nullable private final OrderBy orderBy;
	@Nullable private final Require require;
	private boolean normalized;

	private Query(@Nullable Collection collection, @Nullable FilterBy filterBy, @Nullable OrderBy orderBy, @Nullable Require require) {
		this.collection = collection;
		this.filterBy = filterBy;
		this.orderBy = orderBy;
		this.require = require;
		this.normalized = false;
	}

	/*
		SHORTHAND FACTORY METHODS
	 */

	public static Query query(@Nullable FilterBy filter) {
		return new Query(null, filter, null, null);
	}

	public static Query query(@Nullable FilterBy filter, @Nullable OrderBy order) {
		return new Query(null, filter, order, null);
	}

	public static Query query(@Nullable FilterBy filter, @Nullable OrderBy order, @Nullable Require require) {
		return new Query(null, filter, order, require);
	}

	public static Query query(@Nullable FilterBy filter, @Nullable Require require) {
		return new Query(null, filter, null, require);
	}

	public static Query query(@Nullable Collection entityType) {
		return new Query(entityType, null, null, null);
	}

	public static Query query(@Nullable Collection entityType, @Nullable FilterBy filter) {
		return new Query(entityType, filter, null, null);
	}

	public static Query query(@Nullable Collection entityType, @Nullable FilterBy filter, @Nullable OrderBy order) {
		return new Query(entityType, filter, order, null);
	}

	public static Query query(@Nullable Collection entityType, @Nullable FilterBy filter, @Nullable OrderBy order, @Nullable Require require) {
		return new Query(entityType, filter, order, require);
	}

	public static Query query(@Nullable Collection entityType, @Nullable FilterBy filter, @Nullable Require require, @Nullable OrderBy order) {
		return new Query(entityType, filter, order, require);
	}

	public static Query query(@Nullable Collection entityType, @Nullable OrderBy order) {
		return new Query(entityType, null, order, null);
	}

	public static Query query(@Nullable Collection entityType, @Nullable OrderBy order, @Nullable Require require) {
		return new Query(entityType, null, order, require);
	}

	public static Query query(@Nullable Collection entityType, @Nullable FilterBy filter, @Nullable Require require) {
		return new Query(entityType, filter, null, require);
	}

	public static Query query(@Nullable Collection entityType, @Nullable Require require) {
		return new Query(entityType, null, null, require);
	}

	/**
	 * Returns type of entity that is expected to be returned. Determines the primary entity collection the query
	 * will be processed against.
	 */
	@Nullable
	public Collection getCollection() {
		return collection;
	}

	/**
	 * Returns filter that will be used for narrowing entities in the result.
	 */
	@Nullable
	public FilterBy getFilterBy() {
		return filterBy;
	}

	/**
	 * Returns query that will be used to control order or the entities in the result.
	 */
	@Nullable
	public OrderBy getOrderBy() {
		return orderBy;
	}

	/**
	 * Returns query that drives entity depth or computation extra results along with the query.
	 */
	@Nullable
	public Require getRequire() {
		return require;
	}

	/**
	 * Returns this query or copy of this query without constraints that make no sense or are unnecessary. In other
	 * words - all constraints that has not all required arguments (not {@link Constraint#isApplicable()}) are removed
	 * from the query, all query containers that are {@link ConstraintContainer#isNecessary()} are removed
	 * and their contents are propagated to their parent.
	 */
	@Nonnull
	public Query normalizeQuery() {
		return normalizeQuery(null, null, null);
	}

	/**
	 * Returns this query or copy of this query without constraints that make no sense or are unnecessary. In other
	 * words - all constraints that has not all required arguments (not {@link Constraint#isApplicable()}) are removed
	 * from the query, all query containers that are {@link ConstraintContainer#isNecessary()} are removed
	 * and their contents are propagated to their parent.
	 */
	@Nonnull
	public Query normalizeQuery(
		@Nullable UnaryOperator<Constraint<?>> filterConstraintTranslator,
		@Nullable UnaryOperator<Constraint<?>> orderConstraintTranslator,
		@Nullable UnaryOperator<Constraint<?>> requireConstraintTranslator
	) {
		// avoid costly normalization on already normalized query
		if (normalized) {
			return this;
		}

		final FilterBy normalizedFilter = filterBy == null ? null : (FilterBy) purify(filterBy, filterConstraintTranslator);
		final OrderBy normalizedOrder = orderBy == null ? null : (OrderBy) purify(orderBy, orderConstraintTranslator);
		final Require normalizedRequire = require == null ? null : (Require) purify(require, requireConstraintTranslator);

		// if normalized constraint are same as originals, this query is in normalized form
		if (Objects.equals(filterBy, normalizedFilter) && Objects.equals(orderBy, normalizedOrder) && Objects.equals(require, normalizedRequire)) {
			this.normalized = true;
			return this;
		} else {
			// otherwise create leaner query in normalized form
			final Query normalizedQuery = new Query(collection, normalizedFilter, normalizedOrder, normalizedRequire);
			normalizedQuery.normalized = true;
			return normalizedQuery;
		}
	}

	@Nonnull
	public String prettyPrint() {
		return PrettyPrintingVisitor.toString(this, "\t");
	}

	@Nonnull
	@Override
	public String toString() {
		return PrettyPrintingVisitor.toString(this);
	}

	@Nonnull
	public StringWithParameters toStringWithParameterExtraction() {
		return PrettyPrintingVisitor.toStringWithParameterExtraction(this);
	}

	@SuppressWarnings("unchecked")
	private static <T extends Constraint<T>> T purify(@Nonnull Constraint<?> constraint, @Nullable UnaryOperator<Constraint<?>> translator) {
		return (T) QueryPurifierVisitor.purify(constraint, translator);
	}

}
