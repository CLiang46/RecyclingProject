package com.recycleproj;

public class Can {
	private boolean refundable;
	private int ounces;
	private double dryozcost = 0;
	public Can(int ounces, boolean refundable) {
		this.ounces = ounces;
		this.refundable = refundable;
	}
	
	public int getOunces() {
		return this.ounces;
	}
	
	public boolean isRefundable() {
		return this.refundable;
	}
	public double getDryPerOZCost() {
		return this.dryozcost;
	}
}
