package com.recycleproj;

public class LDPE extends PlasticBottle {
	protected final double coefficient=.17;
	protected final double exponenet=.65;
	private final double plasticcostperoz = 1.98/16;
	public LDPE(int ounces, boolean refundable, String type, String drinktype) {
		super(ounces, refundable, type, drinktype);
		// TODO Auto-generated constructor stub
	}
	public double getCostDryOZ() {
		return this.plasticcostperoz;
	}
}
