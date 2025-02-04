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

package io.evitadb.core.query.extraResult.translator.facet;

import io.evitadb.api.query.require.FacetSummary;
import io.evitadb.core.query.algebra.Formula;
import io.evitadb.core.query.algebra.facet.FacetGroupFormula;
import io.evitadb.core.query.algebra.utils.visitor.FormulaFinder;
import io.evitadb.core.query.algebra.utils.visitor.FormulaFinder.LookUp;
import io.evitadb.core.query.common.translator.SelfTraversingTranslator;
import io.evitadb.core.query.extraResult.ExtraResultPlanningVisitor;
import io.evitadb.core.query.extraResult.ExtraResultProducer;
import io.evitadb.core.query.extraResult.translator.RequireConstraintTranslator;
import io.evitadb.core.query.extraResult.translator.facet.producer.FacetSummaryProducer;
import io.evitadb.core.query.indexSelection.TargetIndexes;
import io.evitadb.index.EntityIndex;
import io.evitadb.index.bitmap.Bitmap;
import io.evitadb.index.bitmap.collection.BitmapIntoBitmapCollector;
import io.evitadb.index.facet.FacetReferenceIndex;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.evitadb.core.query.extraResult.translator.facet.FacetSummaryOfReferenceTranslator.*;

/**
 * This implementation of {@link RequireConstraintTranslator} converts {@link FacetSummary} to {@link FacetSummaryProducer}.
 * The producer instance has all pointer necessary to compute result. All operations in this translator are relatively
 * cheap comparing to final result computation, that is deferred to {@link ExtraResultProducer#fabricate(List)} method.
 *
 * @author Jan Novotný (novotny@fg.cz), FG Forrest a.s. (c) 2021
 */
public class FacetSummaryTranslator implements RequireConstraintTranslator<FacetSummary>, SelfTraversingTranslator {

	@Override
	public ExtraResultProducer apply(FacetSummary facetSummary, ExtraResultPlanningVisitor extraResultPlanner) {
		// find user filters that enclose variable user defined part
		final Set<Formula> formulaScope = extraResultPlanner.getUserFilteringFormula().isEmpty() ?
			Set.of(extraResultPlanner.getFilteringFormula()) :
			extraResultPlanner.getUserFilteringFormula();
		// find all requested facets
		final Map<String, Bitmap> requestedFacets = formulaScope
			.stream()
			.flatMap(it -> FormulaFinder.find(it, FacetGroupFormula.class, LookUp.SHALLOW).stream())
			.collect(
				Collectors.groupingBy(
					FacetGroupFormula::getReferenceName,
					Collectors.mapping(
						FacetGroupFormula::getFacetIds,
						BitmapIntoBitmapCollector.INSTANCE
					)
				)
			);
		// collect all facet statistics
		final TargetIndexes<EntityIndex<?>> indexSetToUse = extraResultPlanner.getIndexSetToUse();
		final List<Map<String, FacetReferenceIndex>> facetIndexes = indexSetToUse.getIndexes()
			.stream()
			.map(EntityIndex::getFacetingEntities)
			.collect(Collectors.toList());

		// find existing FacetSummaryProducer for potential reuse
		FacetSummaryProducer facetSummaryProducer = extraResultPlanner.findExistingProducer(FacetSummaryProducer.class);
		if (facetSummaryProducer == null) {
			// now create the producer instance that has all pointer necessary to compute result
			// all operations above should be relatively cheap comparing to final result computation, that is deferred
			// to FacetSummaryProducer#fabricate method
			facetSummaryProducer = new FacetSummaryProducer(
				extraResultPlanner.getQueryContext(),
				extraResultPlanner.getFilteringFormula(),
				extraResultPlanner.getFilteringFormulaWithoutUserFilter(),
				facetIndexes,
				requestedFacets
			);
		}

		facetSummaryProducer.requireDefaultFacetSummary(
			facetSummary.getStatisticsDepth(),
			referenceSchema -> facetSummary.getFilterBy().map(it -> createFacetPredicate(it, extraResultPlanner, referenceSchema, false)).orElse(null),
			referenceSchema -> facetSummary.getFilterGroupBy().map(it -> createFacetGroupPredicate(it, extraResultPlanner, referenceSchema, false)).orElse(null),
			referenceSchema -> facetSummary.getOrderBy().map(it -> createFacetSorter(it, findLocale(facetSummary.getFilterBy().orElse(null)), extraResultPlanner, referenceSchema, false)).orElse(null),
			referenceSchema -> facetSummary.getOrderGroupBy().map(it -> createFacetGroupSorter(it, findLocale(facetSummary.getFilterGroupBy().orElse(null)), extraResultPlanner, referenceSchema, false)).orElse(null),
			facetSummary.getFacetEntityRequirement().orElse(null),
			facetSummary.getGroupEntityRequirement().orElse(null)
		);
		return facetSummaryProducer;
	}

}
