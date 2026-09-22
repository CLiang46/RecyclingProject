package com.recycleproj;

public class PP extends PlasticBottle {
	private final double plasticcostperoz = .56/16;
	public PP(int ounces, boolean refundable, String type) {
		super(ounces, refundable, type);
		// TODO Auto-generated constructor stub
	}
	public double getCostDryOZ() {
		return this.plasticcostperoz;
	}
}
