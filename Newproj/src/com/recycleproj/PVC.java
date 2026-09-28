package com.recycleproj;

public class PVC extends PlasticBottle {
	public PVC(int ounces, boolean refundable) {
		super(ounces, refundable);
	}

	@Override
	public Material getMaterial() {
		return Material.PVC;
	}
}
