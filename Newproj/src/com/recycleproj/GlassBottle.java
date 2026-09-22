package com.recycleproj;

public class GlassBottle extends Bottle {
	private final double costdryoz = 0.0063;
	public GlassBottle(int ounces, boolean refundable, String color) {
		super(ounces, refundable, color);
		// TODO Auto-generated constructor stub
	}
	public double getPerOZCostDry() {
		return this.costdryoz;
	}
}
