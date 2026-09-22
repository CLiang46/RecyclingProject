package com.recycleproj;

public class PS extends PlasticBottle {
	private final double plasticcostperoz = 5.45/16;
	public PS(int ounces, boolean refundable, String type) {
		super(ounces, refundable, type);
		// TODO Auto-generated constructor stub
	}
	public double getCostDryOZ() {
		return this.plasticcostperoz;
	}
}
