package com.recycleproj;

public class AluminumCan extends Can {
	public AluminumCan(int ounces, boolean refundable) {
		super(ounces, refundable);
	}

	@Override
	public Material getMaterial() {
		return Material.ALUMINUM;
	}
}
