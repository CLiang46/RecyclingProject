package com.recycleproj;

/** Base class for every recyclable beverage container. */
public abstract class Recycle {
	/** Containers of this fluid size or larger earn the large CRV. */
	public static final int LARGE_CONTAINER_OZ = 24;
	public static final double REGULAR_CRV = 0.05;
	public static final double LARGE_CRV = 0.10;

	private final int ounces;
	private final boolean refundable;

	protected Recycle(int ounces, boolean refundable) {
		if (ounces <= 0) {
			throw new IllegalArgumentException("Container size must be positive, got " + ounces);
		}
		this.ounces = ounces;
		this.refundable = refundable;
	}

	public abstract Material getMaterial();

	public int getOunces() {
		return this.ounces;
	}

	public boolean isRefundable() {
		return this.refundable;
	}

	public boolean isLarge() {
		return this.ounces >= LARGE_CONTAINER_OZ;
	}

	/** Deposit refunded when paid by count; non-refundable containers earn nothing. */
	public double getCRV() {
		if (!this.refundable) {
			return 0;
		}
		return isLarge() ? LARGE_CRV : REGULAR_CRV;
	}

	public double getPerOzRate() {
		return getMaterial().getPerOzRate();
	}

	public double getEstimatedEmptyWeightOz() {
		return getMaterial().getEmptyWeightOz(isLarge());
	}
}
