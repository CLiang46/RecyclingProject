package com.recycleproj;

public class PlasticBottle {
	private double plasticcostperoz = 0;
	private int ounces;
	private boolean refundable;
	private String type;
	private String drinktype;
	public PlasticBottle(int ounces, boolean refundable, String type, String drinktype) {
		this.refundable = refundable;
		this.type = type;
		this.ounces = ounces;
		this.drinktype = drinktype;
	}
	
	public boolean isRefundable() {
		return this.refundable;
	}
	public int getOunces() {
		return this.ounces;
	}
	public String getType() {
		return this.type;
	}
	public double getCostDryOZ() {
		return this.plasticcostperoz;
	}
}
