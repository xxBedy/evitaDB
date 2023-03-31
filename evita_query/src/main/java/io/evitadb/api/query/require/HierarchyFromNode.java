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

import io.evitadb.api.query.Constraint;
import io.evitadb.api.query.RequireConstraint;
import io.evitadb.api.query.descriptor.ConstraintDomain;
import io.evitadb.api.query.descriptor.annotation.ConstraintDef;
import io.evitadb.api.query.filter.FilterBy;
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
 * TOBEDONE JNO: docs
 *
 * @author Jan Novotný (novotny@fg.cz), FG Forrest a.s. (c) 2023
 */
@ConstraintDef(
	name = "fromNode",
	shortDescription = "The constraint triggers computing the hierarchy subtree starting at pivot node.",
	supportedIn = ConstraintDomain.HIERARCHY
)
public class HierarchyFromNode extends AbstractRequireConstraintContainer implements HierarchyRequireConstraint {
	@Serial private static final long serialVersionUID = 283525753371686479L;
	private static final String CONSTRAINT_NAME = "fromNode";

	private HierarchyFromNode(@Nonnull String outputName, @Nonnull RequireConstraint[] children, @Nonnull Constraint<?>... additionalChildren) {
		super(CONSTRAINT_NAME, new Serializable[]{outputName}, children, additionalChildren);
		for (RequireConstraint requireConstraint : children) {
			Assert.isTrue(
				requireConstraint instanceof HierarchyOutputRequireConstraint ||
					requireConstraint instanceof EntityFetch,
				"Constraint HierarchyFromRoot accepts only HierarchyStopAt, HierarchyStopAt and EntityFetch as inner constraints!"
			);
		}
	}

	public HierarchyFromNode(@Nonnull String outputName, @Nonnull HierarchyNode node, @Nullable FilterBy filterBy, @Nullable EntityFetch entityFetch, @Nonnull HierarchyOutputRequireConstraint... requirements) {
		super(
			CONSTRAINT_NAME,
			new Serializable[]{outputName},
			ArrayUtils.mergeArrays(
				new RequireConstraint[] {node, entityFetch},
				requirements
			),
			filterBy
		);
	}

	public HierarchyFromNode(@Nonnull String outputName, @Nonnull HierarchyNode node, @Nullable EntityFetch entityFetch, @Nonnull HierarchyOutputRequireConstraint... requirements) {
		super(
			CONSTRAINT_NAME,
			new Serializable[]{outputName},
			ArrayUtils.mergeArrays(
				new RequireConstraint[] {node, entityFetch},
				requirements
			)
		);
	}

	public HierarchyFromNode(@Nonnull String outputName, @Nonnull HierarchyNode fromNode) {
		super(CONSTRAINT_NAME, new Serializable[]{outputName}, fromNode);
	}

	public HierarchyFromNode(@Nonnull String outputName, @Nonnull HierarchyNode fromNode, @Nullable FilterBy filterBy, @Nonnull HierarchyOutputRequireConstraint... requirements) {
		super(
			CONSTRAINT_NAME,
			new Serializable[]{outputName},
			ArrayUtils.mergeArrays(
				new RequireConstraint[] {fromNode},
				requirements
			),
			filterBy
		);
	}

	public HierarchyFromNode(@Nonnull String outputName, @Nonnull HierarchyNode fromNode, @Nonnull HierarchyOutputRequireConstraint... requirements) {
		super(
			CONSTRAINT_NAME,
			new Serializable[]{outputName},
			ArrayUtils.mergeArrays(
				new RequireConstraint[] {fromNode},
				requirements
			)
		);
	}

	/**
	 * Returns the key the computed extra result should be registered to.
	 */
	@Nonnull
	public String getOutputName() {
		return (String) getArguments()[0];
	}

	/**
	 * Contains filtering condition that allows to find a pivot node that should be used as a root for enclosing
	 * hierarchy constraint container.
	 */
	@Nonnull
	public HierarchyNode getFromNode() {
		for (RequireConstraint constraint : getChildren()) {
			if (constraint instanceof HierarchyNode hierarchyNode) {
				return hierarchyNode;
			}
		}
		throw new IllegalStateException("The HierarchyNode inner constraint unexpectedly not found!");
	}

	/**
	 * Returns the condition that limits the top-down hierarchy traversal.
	 */
	@Nonnull
	public Optional<HierarchyStopAt> getStopAt() {
		for (RequireConstraint constraint : getChildren()) {
			if (constraint instanceof HierarchyStopAt hierarchyStopAt) {
				return of(hierarchyStopAt);
			}
		}
		return empty();
	}

	/**
	 * Returns content requirements for hierarchy entities.
	 */
	@Nonnull
	public Optional<EntityFetch> getEntityFetch() {
		for (RequireConstraint constraint : getChildren()) {
			if (constraint instanceof EntityFetch entityFetch) {
				return of(entityFetch);
			}
		}
		return empty();
	}

	/**
	 * Contains filtering condition filters out hierarchy nodes that should not be part of the result.
	 */
	@Nonnull
	public Optional<FilterBy> getFilterBy() {
		return Optional.ofNullable(getAdditionalChild(FilterBy.class));
	}

	/**
	 * Returns {@link HierarchyStatistics} settings.
	 */
	@Nonnull
	public Optional<HierarchyStatistics> getStatistics() {
		for (RequireConstraint constraint : getChildren()) {
			if (constraint instanceof HierarchyStatistics statistics) {
				return of(statistics);
			}
		}
		return empty();
	}

	@Override
	public boolean isApplicable() {
		return isArgumentsNonNull() && getArguments().length == 1 && getChildren().length >= 1;
	}

	@Nonnull
	@Override
	public RequireConstraint getCopyWithNewChildren(@Nonnull RequireConstraint[] children, @Nonnull Constraint<?>[] additionalChildren) {
		for (RequireConstraint requireConstraint : children) {
			Assert.isTrue(
				requireConstraint instanceof HierarchyOutputRequireConstraint ||
					requireConstraint instanceof EntityFetch,
				"Constraint HierarchyFromNode accepts only HierarchyNode, HierarchyStopAt, HierarchyStopAt and EntityFetch as inner constraints!"
			);
		}

		Assert.isTrue(ArrayUtils.isEmpty(additionalChildren), "Inner constraints of different type than `require` are not expected.");
		return new HierarchyFromNode(getOutputName(), children);
	}

	@Nonnull
	@Override
	public RequireConstraint cloneWithArguments(@Nonnull Serializable[] newArguments) {
		Assert.isTrue(
			newArguments.length == 1 && newArguments[0] instanceof String,
			"HierarchyFromNode container accepts only single String argument!"
		);
		return new HierarchyFromNode((String) newArguments[0], getChildren());
	}

}
