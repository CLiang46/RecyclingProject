package com.recycleproj;

/** A glass bottle. Clear, green and brown/dark glass are sorted, counted and weighed separately. */
public class GlassBottle extends Bottle {
	public static final String CLEAR = "clear";
	public static final String GREEN = "green";
	public static final String BROWN = "brown";

	private final String color;
	private final Material material;

	/** @param color "clear", "green", or "brown" (also accepts "dark" or "amber" for brown) */
	public GlassBottle(int ounces, boolean refundable, String color) {
		super(ounces, refundable);
		String c = color == null ? "" : color.trim().toLowerCase(java.util.Locale.ROOT);
		switch (c) {
		case CLEAR:
			this.material = Material.GLASS_CLEAR;
			this.color = CLEAR;
			break;
		case GREEN:
			this.material = Material.GLASS_GREEN;
			this.color = GREEN;
			break;
		case BROWN:
		case "dark":
		case "amber":
			this.material = Material.GLASS_BROWN;
			this.color = BROWN;
			break;
		default:
			throw new IllegalArgumentException("Glass color must be clear, green or brown, got " + color);
		}
	}

	public String getColor() {
		return this.color;
	}

	@Override
	public Material getMaterial() {
		return this.material;
	}
}
