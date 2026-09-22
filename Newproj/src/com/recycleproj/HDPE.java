package com.recycleproj;

public class HDPE extends PlasticBottle {
	private final double plasticcostperoz = 0.67/16;
	public HDPE(int ounces, boolean refundable, String type, String drinktype) {
		super(ounces, refundable, type, drinktype);
		// TODO Auto-generated constructor stub
	}
	public double getCostDryOZ() {
		return this.plasticcostperoz;
	}
}
