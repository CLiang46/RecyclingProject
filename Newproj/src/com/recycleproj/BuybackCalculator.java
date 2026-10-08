package com.recycleproj;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Totals up a load of containers. Each material is split into two groups, regular (under
 * {@value Recycle#LARGE_CONTAINER_OZ} oz) and large, and each group is paid separately:
 * by count (CRV per container) when it has {@value #COUNT_LIMIT} or fewer refundable containers,
 * and by weight (per-pound rate) above that.
 */
public class BuybackCalculator {
	public static final int COUNT_LIMIT = 50;

	private final Map<Material, Tally[]> tallies = new EnumMap<>(Material.class);

	public void add(Recycle container) {
		add(container, 1);
	}

	public void add(Recycle container, int quantity) {
		if (quantity < 0) {
			throw new IllegalArgumentException("Quantity cannot be negative");
		}
		if (quantity == 0) {
			return;
		}
		Tally t = tally(container.getMaterial(), container.isLarge());
		if (!container.isRefundable()) {
			t.nonRefundable += quantity;
			return;
		}
		t.count += quantity;
		t.estimatedWeightOz += container.getEstimatedEmptyWeightOz() * quantity;
	}

	/** Overrides the estimated weight with a measured weight for one size group of a material. */
	public void setActualWeightPounds(Material material, boolean large, double pounds) {
		if (!(pounds >= 0) || Double.isInfinite(pounds)) {
			throw new IllegalArgumentException("Weight must be a non-negative number");
		}
		tally(material, large).actualWeightOz = pounds * 16;
	}

	/** One result per material and size group that has containers or a measured weight, regular before large. */
	public List<MaterialResult> calculate() {
		List<MaterialResult> results = new ArrayList<>();
		for (Map.Entry<Material, Tally[]> e : tallies.entrySet()) {
			for (int i = 0; i < 2; i++) {
				MaterialResult r = result(e.getKey(), i == 1, e.getValue()[i]);
				if (r != null) {
					results.add(r);
				}
			}
		}
		return results;
	}

	public double total() {
		double sum = 0;
		for (MaterialResult r : calculate()) {
			sum += r.payout;
		}
		return sum;
	}

	private static MaterialResult result(Material m, boolean large, Tally t) {
		if (t == null || (t.count == 0 && t.nonRefundable == 0 && t.actualWeightOz == null)) {
			return null;
		}
		boolean estimated = t.actualWeightOz == null;
		double weightOz = estimated ? t.estimatedWeightOz : t.actualWeightOz;
		double countValue = t.count * (large ? Recycle.LARGE_CRV : Recycle.REGULAR_CRV);
		double weightValue = weightOz * m.getPerOzRate();
		// A measured weight with no count can only be paid by weight.
		boolean byWeight = t.count > COUNT_LIMIT || (t.count == 0 && !estimated);
		return new MaterialResult(m, large, t.count, t.nonRefundable, weightOz, estimated,
				countValue, weightValue, byWeight, byWeight ? weightValue : countValue);
	}

	private Tally tally(Material m, boolean large) {
		return tallies.computeIfAbsent(m, k -> new Tally[] { new Tally(), new Tally() })[large ? 1 : 0];
	}

	private static final class Tally {
		int count;
		int nonRefundable;
		double estimatedWeightOz;
		Double actualWeightOz;
	}

	public static final class MaterialResult {
		public final Material material;
		public final boolean large;
		public final int count;
		public final int nonRefundableCount;
		public final double weightOz;
		public final boolean weightEstimated;
		public final double countValue;
		public final double weightValue;
		public final boolean paidByWeight;
		public final double payout;

		MaterialResult(Material material, boolean large, int count, int nonRefundableCount, double weightOz,
				boolean weightEstimated, double countValue, double weightValue, boolean paidByWeight, double payout) {
			this.material = material;
			this.large = large;
			this.count = count;
			this.nonRefundableCount = nonRefundableCount;
			this.weightOz = weightOz;
			this.weightEstimated = weightEstimated;
			this.countValue = countValue;
			this.weightValue = weightValue;
			this.paidByWeight = paidByWeight;
			this.payout = payout;
		}
	}
}
