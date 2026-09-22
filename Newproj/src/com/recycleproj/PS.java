package com.recycleproj;

public class PS extends PlasticBottle {
	private final double plasticcostperoz = 5.45/16;
	public PS(int ounces, boolean refundable, String type, String drinktype) {
		super(ounces, refundable, type, drinktype);
		// TODO Auto-generated constructor stub
	}
	public double getCostDryOZ() {
		return this.plasticcostperoz;
	}
}
