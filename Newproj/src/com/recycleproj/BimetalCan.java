package com.recycleproj;

public class BimetalCan extends Can {
	private final double dryozcost = 0.027;
	protected final double coefficient = .54;
	protected final double exponent = .43;
	public BimetalCan(int ounces, boolean refundable) {
		super(ounces, refundable);
		// TODO Auto-generated constructor stub
	}
	public double getDryPerOZCost() {
		return this.dryozcost;
	}

}
