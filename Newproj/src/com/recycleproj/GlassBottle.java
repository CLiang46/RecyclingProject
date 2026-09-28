package com.recycleproj;

public class GlassBottle extends Bottle {
	private final String color;

	public GlassBottle(int ounces, boolean refundable, String color) {
		super(ounces, refundable);
		this.color = color;
	}

	public String getColor() {
		return this.color;
	}

	@Override
	public Material getMaterial() {
		return Material.GLASS;
	}
}
