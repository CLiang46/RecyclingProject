package com.recycleproj;

public class OtherPlastic extends PlasticBottle {
	private final double costdryoz = 0.31/16;
	public OtherPlastic(int ounces, boolean refundable, String type, String drinktype) {
		super(ounces, refundable, type, drinktype);
		// TODO Auto-generated constructor stub
	}
	public double getCostDryOZ() {
		return this.costdryoz;
	}

}
