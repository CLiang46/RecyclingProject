package com.recycleproj;

public class BimetalCan extends Can {
	public BimetalCan(int ounces, boolean refundable) {
		super(ounces, refundable);
	}

	@Override
	public Material getMaterial() {
		return Material.BIMETAL;
	}
}
