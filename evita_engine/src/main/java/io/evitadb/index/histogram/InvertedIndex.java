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

package io.evitadb.index.histogram;

import io.evitadb.core.Transaction;
import io.evitadb.core.query.algebra.Formula;
import io.evitadb.core.query.algebra.base.ConstantFormula;
import io.evitadb.core.query.algebra.base.EmptyFormula;
import io.evitadb.core.query.algebra.base.OrFormula;
import io.evitadb.core.query.algebra.deferred.DeferredFormula;
import io.evitadb.index.array.TransactionalComplexObjArray;
import io.evitadb.index.bitmap.BaseBitmap;
import io.evitadb.index.bitmap.Bitmap;
import io.evitadb.index.bitmap.EmptyBitmap;
import io.evitadb.index.histogram.suppliers.HistogramBitmapSupplier;
import io.evitadb.index.transactionalMemory.TransactionalLayerMaintainer;
import io.evitadb.index.transactionalMemory.VoidTransactionMemoryProducer;
import io.evitadb.utils.ArrayUtils;
import io.evitadb.utils.Assert;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.BiFunction;

/**
 * Histogram index is based on <a href="https://en.wikipedia.org/wiki/Histogram">Histogram data structure</a>. It's
 * organized as a set of "buckets" ordered from minimal to maximal {@link Comparable} value. Each bucket has assigned
 * bitmap (ordered distinct set of primitive integer values) that are assigned to bucket {@link ValueToRecordBitmap#getValue()}.
 * 
 * Search in histogram is possible via. binary search with O(log n) complexity due its sorted nature. Set of records
 * are easily available as the set assigned to that value. Range look-ups are also available as boolean OR of all bitmaps
 * from / to looked up value threshold.
 * 
 * Histogram MUST NOT contain same record id in multiple buckets. This prerequisite is not checked internally by this
 * data structure and client code must this ensure by its internal logic! If this prerequisite is not met, histogram
 * may return confusing results.
 * 
 * Thread safety:
 * 
 * Histogram supports transaction memory. This means, that the histogram can be updated by multiple writers and also
 * multiple readers can read from it's original array without spotting the changes made in transactional access. Each
 * transaction is bound to the same thread and different threads doesn't see changes in another threads.
 * 
 * If no transaction is opened, changes are applied directly to the delegate array. In such case the class is not thread
 * safe for multiple writers!
 *
 * @author Jan Novotný (novotny@fg.cz), FG Forrest a.s. (c) 2019
 */
@ThreadSafe
@EqualsAndHashCode
public class InvertedIndex<T extends Comparable<T>> implements VoidTransactionMemoryProducer<InvertedIndex<T>>, Serializable {
	@Serial private static final long serialVersionUID = 3019703951858227807L;
	/**
	 * The buckets contain ordered comparable values with bitmaps of all records with such value.
	 */
	private final TransactionalComplexObjArray<ValueToRecordBitmap<T>> valueToRecordBitmap;

	/**
	 * This lambda lay out records by {@link ValueToRecordBitmap#getValue()} one after another.
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static final BiFunction<Long, ValueToRecordBitmap[], Formula> UNSORTED_AGGREGATION_LAMBDA = (indexTransactionId, histogramBuckets) -> new DeferredFormula(
		new HistogramBitmapSupplier<>(histogramBuckets)
	);

	/**
	 * This lambda lay out records in natural ascending order.
	 */
	@SuppressWarnings("rawtypes")
	private static final BiFunction<Long, ValueToRecordBitmap[], Formula> SORTED_AGGREGATION_LAMBDA = (indexTransactionId, histogramBuckets) -> {
		final Bitmap[] bitmaps = new Bitmap[histogramBuckets.length];
		for (int i = 0; i < histogramBuckets.length; i++) {
			bitmaps[i] = histogramBuckets[i].getRecordIds();
		}
		if (bitmaps.length == 0) {
			return EmptyFormula.INSTANCE;
		} else if (bitmaps.length == 1) {
			return new ConstantFormula(bitmaps[0]);
		} else {
			return new OrFormula(new long[] {indexTransactionId}, bitmaps);
		}
	};

	/**
	 * Method verifies that {@link ValueToRecordBitmap#getValue()}s in passed set are monotonically increasing and contain
	 * no duplicities.
	 */
	private static <T extends Comparable<T>> void assertValueIsMonotonic(@Nonnull ValueToRecordBitmap<T>[] points) {
		T previous = null;
		for (ValueToRecordBitmap<T> bucket : points) {
			Assert.isTrue(
				previous == null || previous.compareTo(bucket.getValue()) < 0,
				"Histogram values are not monotonic - conflicting values: " + previous + ", " + bucket.getValue()
			);
			previous = bucket.getValue();
		}
	}

	public InvertedIndex() {
		//noinspection unchecked, rawtypes
		valueToRecordBitmap = new TransactionalComplexObjArray<>(
			new ValueToRecordBitmap[0],
			ValueToRecordBitmap::add,
			ValueToRecordBitmap::remove,
			ValueToRecordBitmap::isEmpty,
			ValueToRecordBitmap::deepEquals
		);
	}

	public InvertedIndex(@Nonnull ValueToRecordBitmap<T>[] buckets) {
		// contract check
		assertValueIsMonotonic(buckets);
		this.valueToRecordBitmap = new TransactionalComplexObjArray<>(
			buckets,
			ValueToRecordBitmap::add,
			ValueToRecordBitmap::remove,
			ValueToRecordBitmap::isEmpty,
			ValueToRecordBitmap::deepEquals
		);
	}

	/**
	 * Adds single record id into the bucket with specified `value`. If no bucket with this value exists, it is automatically
	 * created and first record id is assigned to it.
	 *
	 * @return position where insertion happened
	 */
	public int addRecord(@Nonnull T value, int recordId) {
		final ValueToRecordBitmap<T> bucket = new ValueToRecordBitmap<>(value, EmptyBitmap.INSTANCE);
		bucket.addRecord(recordId);
		return valueToRecordBitmap.addReturningIndex(bucket);
	}

	/**
	 * Adds multiple records id into the bucket with specified `value`. If no bucket with this value exists, it is automatically
	 * created and first record ida are assigned to it.
	 */
	public void addRecord(@Nonnull T value, int... recordId) {
		Assert.isTrue(!ArrayUtils.isEmpty(recordId), "Record ids must be not null and non empty!");
		final ValueToRecordBitmap<T> bucket = new ValueToRecordBitmap<>(value, EmptyBitmap.INSTANCE);
		bucket.addRecord(recordId);
		valueToRecordBitmap.add(bucket);
	}

	/**
	 * Removes one or multiple record ids from the bucket with specified `value`. If no bucket with this value exists,
	 * nothing happens. If the bucket contains no record id that match passed record id, nothing happens. If removal
	 * of the record ids leaves the bucket empty, it's entirely removed.
	 *
	 * @return position where removal occurred or -1 if no removal occurred
	 */
	public int removeRecord(@Nonnull T value, int... recordId) {
		Assert.isTrue(!ArrayUtils.isEmpty(recordId), "Record ids must be not null and non-empty!");
		return valueToRecordBitmap.remove(new ValueToRecordBitmap<>(value, new BaseBitmap(recordId)));
	}

	/**
	 * Method returns ture if histogram contains no records (i.e. no, or empty buckets).
	 */
	public boolean isEmpty() {
		final Iterator<ValueToRecordBitmap<T>> it = valueToRecordBitmap.iterator();
		while (it.hasNext()) {
			final ValueToRecordBitmap<T> bucket = it.next();
			if (!bucket.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Returns true if there is a bucket related to passed `value`.
	 */
	public boolean contains(@Nullable T value) {
		if (value == null) {
			return false;
		}
		final ValueToRecordBitmap<T>[] pointsArray = valueToRecordBitmap.getArray();
		final int index = Arrays.binarySearch(pointsArray, new ValueToRecordBitmap<>(value));
		return index >= 0;
	}

	/**
	 * Returns set of record ids that are present at bucket at the specific index.
	 */
	@Nonnull
	public Bitmap getRecordsAtIndex(int index) {
		final ValueToRecordBitmap<T>[] pointsArray = valueToRecordBitmap.getArray();
		if (index >= 0) {
			return pointsArray[index].getRecordIds();
		} else {
			return EmptyBitmap.INSTANCE;
		}
	}

	/**
	 * Returns array of "buckets" ordered by {@link ValueToRecordBitmap#getValue()} that contain record ids assigned in them.
	 */
	@Nonnull
	public ValueToRecordBitmap<T>[] getValueToRecordBitmap() {
		return this.valueToRecordBitmap.getArray();
	}

	/**
	 * Returns entire content of this histogram as "subset" that allows easy access to the record ids inside.
	 * Records returned by this {@link HistogramSubSet} are sorted by the order of the bucket
	 * {@link ValueToRecordBitmap#getValue()}.
	 * 
	 * This histogram:
	 * A: [1, 4]
	 * B: [2, 9]
	 * C: [3]
	 * 
	 * Will return subset providing record ids bitmap in form of: [3, 2, 9, 1, 4]
	 */
	@Nonnull
	public HistogramSubSet<T> getRecords() {
		return getRecords(null, null);
	}

	/**
	 * Returns subset of this histogram with buckets between `moreThanEq` and `lessThanEq` (i.e. inclusive subset).
	 * Records returned by this {@link HistogramSubSet} are sorted by the order of the bucket
	 * {@link ValueToRecordBitmap#getValue()}.
	 *
	 * @see #getRecords()
	 */
	public HistogramSubSet<T> getRecords(@Nullable T moreThanEq, @Nullable T lessThanEq) {
		final ValueToRecordBitmap<T>[] records = getRecordsInternal(moreThanEq, lessThanEq, BoundsHandling.INCLUSIVE);
		return convertToUnSortedResult(records);
	}

	/**
	 * Returns entire content of this histogram as "subset" that allows easy access to the record ids inside.
	 * Records returned by this {@link HistogramSubSet} are sorted by record id value.
	 * 
	 * This histogram:
	 * A: [1, 4]
	 * B: [2, 9]
	 * C: [3]
	 * 
	 * Will return subset providing record ids bitmap in form of: [1, 2, 3, 4, 9]
	 */
	@Nonnull
	public HistogramSubSet<T> getSortedRecords() {
		return getSortedRecords(null, null);
	}

	/**
	 * Returns subset of this histogram with buckets between `moreThanEq` and `lessThanEq` (i.e. inclusive subset).
	 * Records returned by this {@link HistogramSubSet} are sorted by record id value.
	 *
	 * @see #getSortedRecords()
	 */
	@Nonnull
	public HistogramSubSet<T> getSortedRecords(@Nullable T moreThanEq, @Nullable T lessThanEq) {
		final ValueToRecordBitmap<T>[] records = getRecordsInternal(moreThanEq, lessThanEq, BoundsHandling.INCLUSIVE);
		return convertToSortedResult(records);
	}

	/**
	 * Returns subset of this histogram with buckets between `moreThan` and `lessThan` (i.e. exclusive subset).
	 * Records returned by this {@link HistogramSubSet} are sorted by record id value.
	 *
	 * @see #getSortedRecords()
	 */
	@Nonnull
	public HistogramSubSet<T> getSortedRecordsExclusive(@Nullable T moreThan, @Nullable T lessThan) {
		final ValueToRecordBitmap<T>[] records = getRecordsInternal(moreThan, lessThan, BoundsHandling.EXCLUSIVE);
		return convertToSortedResult(records);
	}

	/**
	 * Returns count of the buckets in the histogram.
	 */
	public int getBucketCount() {
		return valueToRecordBitmap.getLength();
	}

	/**
	 * Returns count of all record ids in the histogram.
	 */
	public int getLength() {
		int count = 0;
		for (ValueToRecordBitmap<T> bucket : this.valueToRecordBitmap.getArray()) {
			count += bucket.getRecordIds().size();
		}
		return count;
	}

	@Override
	public String toString() {
		return "InvertedIndex{" +
			"points=" + valueToRecordBitmap +
			'}';
	}

	/*
		Implementation of TransactionalLayerProducer
	 */

	@Nonnull
	@Override
	public InvertedIndex<T> createCopyWithMergedTransactionalMemory(Void layer, @Nonnull TransactionalLayerMaintainer transactionalLayer, @Nullable Transaction transaction) {
		return new InvertedIndex<>(transactionalLayer.getStateCopyWithCommittedChanges(this.valueToRecordBitmap, transaction));
	}

	@Override
	public void removeLayer(@Nonnull TransactionalLayerMaintainer transactionalLayer) {
		transactionalLayer.removeTransactionalMemoryLayerIfExists(this);
		this.valueToRecordBitmap.removeLayer(transactionalLayer);
	}

	/*
		PRIVATE METHODS
	 */

	/**
	 * Returns subset that aggregates inner record ids by {@link ValueToRecordBitmap#getValue()} and thus the result may
	 * look unsorted on first look.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	@Nonnull
	private HistogramSubSet<T> convertToUnSortedResult(@Nonnull ValueToRecordBitmap<T>[] records) {
		return new HistogramSubSet(
			getId(),
			records,
			UNSORTED_AGGREGATION_LAMBDA
		);
	}

	/**
	 * Returns subset that aggregates inner record ids by natural ascending ordering.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	@Nonnull
	private HistogramSubSet<T> convertToSortedResult(@Nonnull ValueToRecordBitmap<T>[] records) {
		return new HistogramSubSet(
			getId(),
			records,
			SORTED_AGGREGATION_LAMBDA
		);
	}

	/**
	 * Searches histogram and select all buckets that fulfill the between `moreThanEq` and `lessThanEq` constraints.
	 * Returns array of all {@link ValueToRecordBitmap} in the range.
	 */
	@Nonnull
	private ValueToRecordBitmap<T>[] getRecordsInternal(@Nullable T moreThanEq, @Nullable T lessThanEq, @Nonnull BoundsHandling boundsHandling) {
		final HistogramBounds<T> histogramBounds = new HistogramBounds<>(valueToRecordBitmap.getArray(), moreThanEq, lessThanEq, boundsHandling);
		@SuppressWarnings("unchecked") final ValueToRecordBitmap<T>[] result = new ValueToRecordBitmap[histogramBounds.getNormalizedEndIndex() - histogramBounds.getNormalizedStartIndex()];
		int index = -1;
		final Iterator<ValueToRecordBitmap<T>> it = valueToRecordBitmap.iterator();
		while (it.hasNext()) {
			final ValueToRecordBitmap<T> bucket = it.next();
			index++;
			if (index >= histogramBounds.getNormalizedStartIndex() && index < histogramBounds.getNormalizedEndIndex()) {
				result[index - histogramBounds.getNormalizedStartIndex()] = bucket;
			}
			if (index >= histogramBounds.getNormalizedEndIndex()) {
				break;
			}
		}
		return result;
	}

	/**
	 * Represents search mode - i.e. whether records at the very bounds should be included in result or not.
	 */
	private enum BoundsHandling {

		EXCLUSIVE, INCLUSIVE

	}

	/**
	 * Class is used to search for the bucked bounds defined by `moreThanEq` and `lessThanEq` constraints.
	 */
	private static class HistogramBounds<T extends Comparable<T>> {
		/**
		 * Index of the first bucket to be included in search result.
		 */
		@Getter private final int normalizedStartIndex;
		/**
		 * Index of the last bucket to be included in search result.
		 */
		@Getter private final int normalizedEndIndex;

		HistogramBounds(@Nonnull ValueToRecordBitmap<T>[] points, @Nullable T moreThanEq, @Nullable T lessThanEq, @Nonnull BoundsHandling boundsHandling) {
			Assert.isTrue(
				moreThanEq == null || lessThanEq == null || moreThanEq.compareTo(lessThanEq) <= 0,
				"From must be lower than to: " + moreThanEq + " vs. " + lessThanEq
			);

			if (moreThanEq != null) {
				final int startIndex = Arrays.binarySearch(points, new ValueToRecordBitmap<>(moreThanEq));
				if (boundsHandling == BoundsHandling.EXCLUSIVE) {
					normalizedStartIndex = startIndex >= 0 ? startIndex + 1 : -1 * (startIndex) - 1;
				} else {
					normalizedStartIndex = startIndex >= 0 ? startIndex : -1 * (startIndex) - 1;
				}
			} else {
				normalizedStartIndex = 0;
			}

			if (lessThanEq != null) {
				final int endIndex = Arrays.binarySearch(points, new ValueToRecordBitmap<>(lessThanEq));
				if (boundsHandling == BoundsHandling.EXCLUSIVE) {
					normalizedEndIndex = endIndex >= 0 ? endIndex : (-1 * (endIndex) - 1);
				} else {
					normalizedEndIndex = endIndex >= 0 ? endIndex + 1 : (-1 * (endIndex) - 1);
				}
			} else {
				normalizedEndIndex = points.length;
			}
		}

	}
}