package com.recycleproj;

public class PVC extends PlasticBottle {
	private final double plasticcostperoz = 0.48/16;
	public PVC(int ounces, boolean refundable, String type, String drinktype) {
		super(ounces, refundable, type, drinktype);
		// TODO Auto-generated constructor stub
	}
	public double getCostDryOZ() {
		return this.plasticcostperoz;
	}
}
