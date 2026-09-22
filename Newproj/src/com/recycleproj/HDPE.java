package com.recycleproj;

public class HDPE extends PlasticBottle {
	private final double plasticcostperoz = 0.67/16;
	public HDPE(int ounces, boolean refundable, String type) {
		super(ounces, refundable, type);
		// TODO Auto-generated constructor stub
	}
	public double getCostDryOZ() {
		return this.plasticcostperoz;
	}
}
