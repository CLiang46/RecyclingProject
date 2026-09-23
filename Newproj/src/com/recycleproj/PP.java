package com.recycleproj;

public class PP extends PlasticBottle {
	protected final double coefficient = .145;
	protected final double exponent = .67;
	private final double plasticcostperoz = .56/16;
	public PP(int ounces, boolean refundable, String type, String drinktype) {
		super(ounces, refundable, type, drinktype);
		// TODO Auto-generated constructor stub
	}
	public double getCostDryOZ() {
		return this.plasticcostperoz;
	}
}
