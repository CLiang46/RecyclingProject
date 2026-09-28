package com.recycleproj;

public class PS extends PlasticBottle {
	public PS(int ounces, boolean refundable) {
		super(ounces, refundable);
	}

	@Override
	public Material getMaterial() {
		return Material.PS;
	}
}
