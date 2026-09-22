package com.recycleproj;

public class PETBottle extends PlasticBottle {
	private final double costdryoz = 0.09;
	public PETBottle(int ounces, boolean refundable, String type) {
		super(ounces, refundable, type);
		// TODO Auto-generated constructor stub
	}
	public double getCostDryOZ() {
		return this.costdryoz;
	}
}
