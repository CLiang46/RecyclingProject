package com.recycleproj;

public class PETBottle extends PlasticBottle {
	public PETBottle(int ounces, boolean refundable) {
		super(ounces, refundable);
	}

	@Override
	public Material getMaterial() {
		return Material.PET;
	}
}
