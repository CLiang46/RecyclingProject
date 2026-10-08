package com.recycleproj;

public class HDPE extends PlasticBottle {
	public HDPE(int ounces, boolean refundable) {
		super(ounces, refundable);
	}

	@Override
	public Material getMaterial() {
		return Material.HDPE;
	}
}
