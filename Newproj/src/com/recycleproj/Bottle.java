package com.recycleproj;

public class Bottle {
	private int ounces;
	private boolean refundable;
	private String color;
	private double perozcostdry = 0;
	public Bottle(int ounces, boolean refundable, String color) {
		this.refundable = refundable;
		this.color = color;
		this.ounces = ounces;
	}
	
	public boolean isRefundable() {
		return this.refundable;
	}
	public int getOunces() {
		return this.ounces;
	}
	public String getColor() {
		return this.color;
	}
	public double getPerOzCostDry() {
		return this.perozcostdry;
	}
}
