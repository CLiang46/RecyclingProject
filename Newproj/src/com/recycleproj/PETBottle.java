package com.recycleproj;

public class PETBottle extends PlasticBottle {
	protected final double carbcoefficient = 0.088;
	protected final double othercoefficient = 0.053;
	protected final double carbexponent = 0.75;
	protected final double otherexponent = .71;
	protected PETBottle(int ounces, boolean refundable, String type, String drinktype) {
		super(ounces, refundable, type, drinktype);
		// TODO Auto-generated constructor stub
	}
	/*public double getCostDryOZ() {
		return this.costdryoz;
	}*/
}
