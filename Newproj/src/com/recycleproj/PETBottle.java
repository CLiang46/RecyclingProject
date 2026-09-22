package com.recycleproj;

public class PETBottle extends PlasticBottle {
	private final double costdryoz = 0.09;
	public PETBottle(int ounces, boolean refundable, String type, String drinktype) {
		super(ounces, refundable, type, drinktype);
		// TODO Auto-generated constructor stub
	}
	public double getCostDryOZ() {
		return this.costdryoz;
	}
}
