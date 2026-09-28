package com.recycleproj;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Totals up a load of containers. Following California buyback rules, each material is paid
 * by count (CRV per container) when there are {@value #COUNT_LIMIT} or fewer refundable containers
 * of that material, and by weight (per-pound rate) above that.
 */
public class BuybackCalculator {
	public static final int COUNT_LIMIT = 50;

	private final Map<Material, Tally> tallies = new EnumMap<>(Material.class);

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
		Tally t = tally(container.getMaterial());
		if (!container.isRefundable()) {
			t.nonRefundable += quantity;
			return;
		}
		if (container.isLarge()) {
			t.large += quantity;
		} else {
			t.regular += quantity;
		}
		t.estimatedWeightOz += container.getEstimatedEmptyWeightOz() * quantity;
	}

	/** Overrides the estimated weight with a measured weight for the refundable containers of a material. */
	public void setActualWeightPounds(Material material, double pounds) {
		if (!(pounds >= 0) || Double.isInfinite(pounds)) {
			throw new IllegalArgumentException("Weight must be a non-negative number");
		}
		tally(material).actualWeightOz = pounds * 16;
	}

	public List<MaterialResult> calculate() {
		List<MaterialResult> results = new ArrayList<>();
		for (Map.Entry<Material, Tally> e : tallies.entrySet()) {
			Material m = e.getKey();
			Tally t = e.getValue();
			int refundable = t.regular + t.large;
			if (refundable == 0 && t.nonRefundable == 0) {
				continue;
			}
			boolean estimated = t.actualWeightOz == null;
			double weightOz = estimated ? t.estimatedWeightOz : t.actualWeightOz;
			double countValue = t.regular * Recycle.REGULAR_CRV + t.large * Recycle.LARGE_CRV;
			double weightValue = weightOz * m.getPerOzRate();
			boolean byWeight = refundable > COUNT_LIMIT;
			results.add(new MaterialResult(m, t.regular, t.large, t.nonRefundable, weightOz, estimated,
					countValue, weightValue, byWeight, byWeight ? weightValue : countValue));
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

	private Tally tally(Material m) {
		return tallies.computeIfAbsent(m, k -> new Tally());
	}

	private static final class Tally {
		int regular;
		int large;
		int nonRefundable;
		double estimatedWeightOz;
		Double actualWeightOz;
	}

	public static final class MaterialResult {
		public final Material material;
		public final int regularCount;
		public final int largeCount;
		public final int nonRefundableCount;
		public final double weightOz;
		public final boolean weightEstimated;
		public final double countValue;
		public final double weightValue;
		public final boolean paidByWeight;
		public final double payout;

		MaterialResult(Material material, int regularCount, int largeCount, int nonRefundableCount, double weightOz,
				boolean weightEstimated, double countValue, double weightValue, boolean paidByWeight, double payout) {
			this.material = material;
			this.regularCount = regularCount;
			this.largeCount = largeCount;
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
