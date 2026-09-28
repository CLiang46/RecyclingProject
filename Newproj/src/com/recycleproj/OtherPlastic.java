package com.recycleproj;

public class OtherPlastic extends PlasticBottle {
	public OtherPlastic(int ounces, boolean refundable) {
		super(ounces, refundable);
	}

	@Override
	public Material getMaterial() {
		return Material.OTHER_PLASTIC;
	}
}
