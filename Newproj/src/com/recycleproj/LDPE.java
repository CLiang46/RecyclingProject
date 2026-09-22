package com.recycleproj;

public class LDPE extends PlasticBottle {
	private final double plasticcostperoz = 1.98/16;
	public LDPE(int ounces, boolean refundable, String type, String drinktype) {
		super(ounces, refundable, type, drinktype);
		// TODO Auto-generated constructor stub
	}
	public double getCostDryOZ() {
		return this.plasticcostperoz;
	}
}
