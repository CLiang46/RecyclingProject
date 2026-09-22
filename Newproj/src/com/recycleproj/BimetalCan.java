package com.recycleproj;

public class BimetalCan extends Can {
	private final double dryozcost = 0.027;
	public BimetalCan(int ounces, boolean refundable) {
		super(ounces, refundable);
		// TODO Auto-generated constructor stub
	}
	public double getDryPerOZCost() {
		return this.dryozcost;
	}

}
