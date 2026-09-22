package com.recycleproj;

public class PVC extends PlasticBottle {
	private final double plasticcostperoz = 0.48/16;
	public PVC(int ounces, boolean refundable, String type) {
		super(ounces, refundable, type);
		// TODO Auto-generated constructor stub
	}
	public double getCostDryOZ() {
		return this.plasticcostperoz;
	}
}
