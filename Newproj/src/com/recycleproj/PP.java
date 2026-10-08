package com.recycleproj;

public class PP extends PlasticBottle {
	public PP(int ounces, boolean refundable) {
		super(ounces, refundable);
	}

	@Override
	public Material getMaterial() {
		return Material.PP;
	}
}
