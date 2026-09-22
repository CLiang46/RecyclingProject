package com.recycleproj;

public class LDPE extends PlasticBottle {
	private final double plasticcostperoz = 1.98/16;
	public LDPE(int ounces, boolean refundable, String type) {
		super(ounces, refundable, type);
		// TODO Auto-generated constructor stub
	}
	public double getCostDryOZ() {
		return this.plasticcostperoz;
	}
}
