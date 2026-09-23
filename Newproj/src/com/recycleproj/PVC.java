package com.recycleproj;

public class PVC extends PlasticBottle {
	protected final double coefficient = .205;
	protected final double exponent = .63;
	private final double plasticcostperoz = 0.48/16;
	public PVC(int ounces, boolean refundable, String type, String drinktype) {
		super(ounces, refundable, type, drinktype);
		// TODO Auto-generated constructor stub
	}
	public double getCostDryOZ() {
		return this.plasticcostperoz;
	}
}
