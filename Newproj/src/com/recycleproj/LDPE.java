package com.recycleproj;

public class LDPE extends PlasticBottle {
	public LDPE(int ounces, boolean refundable) {
		super(ounces, refundable);
	}

	@Override
	public Material getMaterial() {
		return Material.LDPE;
	}
}
