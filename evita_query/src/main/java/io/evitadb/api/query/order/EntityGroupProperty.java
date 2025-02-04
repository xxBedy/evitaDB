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

package io.evitadb.api.query.order;

import io.evitadb.api.query.Constraint;
import io.evitadb.api.query.EntityConstraint;
import io.evitadb.api.query.OrderConstraint;
import io.evitadb.api.query.descriptor.ConstraintDomain;
import io.evitadb.api.query.descriptor.annotation.Child;
import io.evitadb.api.query.descriptor.annotation.ConstraintDefinition;
import io.evitadb.api.query.descriptor.annotation.Creator;
import io.evitadb.api.query.require.ReferenceContent;

import javax.annotation.Nonnull;
import java.io.Serial;
import java.io.Serializable;

/**
 * The `entityGroupProperty` ordering constraint can only be used within the {@link ReferenceContent} requirement. It
 * allows to change the context of the reference ordering from attributes of the reference itself to attributes of
 * the entity group the reference is aggregated within.
 *
 * In other words, if the `Product` entity has multiple references to `Parameter` entities (blue/red/yellow) grouped
 * within `ParameterType` (color) entity, you can sort those references by, for example, the `priority` or `name`
 * attribute of the `ParameterType` entity.
 *
 * Example:
 *
 * <pre>
 * query(
 *     collection('Product'),
 *     filterBy(
 *         attributeEquals('code', 'garmin-vivoactive-4')
 *     ),
 *     require(
 *         entityFetch(
 *             attributeContent('code'),
 *             referenceContent(
 *                 'parameterValues',
 *                 orderBy(
 *                     entityGroupProperty(
 *                         attributeNatural('code', DESC)
 *                     )
 *                 ),
 *                 entityFetch(
 *                     attributeContent('code')
 *                 )
 *             )
 *         )
 *     )
 * )
 * </pre>
 *
 * Most of the time, you will want to group primarily by a group property and secondarily by a referenced entity
 * property, which can be achieved in the following way:
 *
 * Example:
 *
 * <pre>
 * query(
 *     collection('Product'),
 *     filterBy(
 *         attributeEquals('code', 'garmin-vivoactive-4')
 *     ),
 *     require(
 *         entityFetch(
 *             attributeContent('code'),
 *             referenceContent(
 *                 'parameterValues',
 *                 orderBy(
 *                     entityGroupProperty(
 *                         attributeNatural('code', DESC)
 *                     ),
 *                     entityProperty(
 *                         attributeNatural('code', DESC)
 *                     )
 *                 ),
 *                 entityFetch(
 *                     attributeContent('code')
 *                 )
 *             )
 *         )
 *     )
 * )
 * </pre>
 *
 * @author Jan Novotný (novotny@fg.cz), FG Forrest a.s. (c) 2021
 */
@ConstraintDefinition(
	name = "groupProperty",
	shortDescription = "The constraint sorts returned references by applying ordering constraint on referenced entity group.",
	supportedIn = ConstraintDomain.REFERENCE
)
public class EntityGroupProperty extends AbstractOrderConstraintContainer implements EntityConstraint<OrderConstraint> {

	@Serial private static final long serialVersionUID = 3103623569235263761L;

	private EntityGroupProperty(Serializable[] arguments, OrderConstraint... children) {
		super(arguments, children);
	}

	@Creator
	public EntityGroupProperty(@Nonnull @Child OrderConstraint... children) {
		super(children);
	}

	@Nonnull
	@Override
	public OrderConstraint cloneWithArguments(@Nonnull Serializable[] newArguments) {
		return new EntityGroupProperty(newArguments, getChildren());
	}

	@Override
	public boolean isNecessary() {
		return getChildren().length >= 1;
	}

	@Nonnull
	@Override
	public OrderConstraint getCopyWithNewChildren(@Nonnull OrderConstraint[] children, @Nonnull Constraint<?>[] additionalChildren) {
		return new EntityGroupProperty(children);
	}
}
