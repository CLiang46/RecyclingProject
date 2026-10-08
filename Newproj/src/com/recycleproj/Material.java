package com.recycleproj;

/**
 * Recyclable material types, with the per-ounce refund rate used when a load is paid by weight
 * and a typical empty-container weight used when the actual weight is not known.
 */
public enum Material {
	ALUMINUM("aluminum", "Aluminum cans", Category.CANS, 0.104, 0.5, 1.0),
	BIMETAL("bimetal", "Bimetal cans", Category.CANS, 0.027, 1.5, 3.0),
	GLASS_CLEAR("glass_clear", "Glass bottles, clear", Category.GLASS, 0.0063, 7.0, 17.0),
	GLASS_GREEN("glass_green", "Glass bottles, green", Category.GLASS, 0.0063, 7.0, 17.0),
	GLASS_BROWN("glass_brown", "Glass bottles, brown/dark", Category.GLASS, 0.0063, 7.0, 17.0),
	PET("pet", "#1 PET plastic", Category.PLASTIC, 0.09, 0.35, 1.6),
	HDPE("hdpe", "#2 HDPE plastic", Category.PLASTIC, 0.67 / 16, 1.0, 2.2),
	PVC("pvc", "#3 PVC plastic", Category.PLASTIC, 0.48 / 16, 0.8, 1.8),
	LDPE("ldpe", "#4 LDPE plastic", Category.PLASTIC, 1.98 / 16, 0.8, 1.8),
	PP("pp", "#5 PP plastic", Category.PLASTIC, 0.56 / 16, 0.8, 1.8),
	PS("ps", "#6 PS plastic", Category.PLASTIC, 5.45 / 16, 0.5, 1.2),
	OTHER_PLASTIC("other", "#7 Other plastic", Category.PLASTIC, 0.31 / 16, 0.8, 1.8);

	/** Broad container families the user picks from before entering counts. */
	public enum Category {
		CANS("cans", "Cans"),
		GLASS("glass", "Glass"),
		PLASTIC("plastic", "Plastic");

		private final String key;
		private final String displayName;

		Category(String key, String displayName) {
			this.key = key;
			this.displayName = displayName;
		}

		public String getKey() {
			return this.key;
		}

		public String getDisplayName() {
			return this.displayName;
		}
	}

	private final String key;
	private final String displayName;
	private final Category category;
	private final double perOzRate;
	private final double smallEmptyWeightOz;
	private final double largeEmptyWeightOz;

	Material(String key, String displayName, Category category, double perOzRate, double smallEmptyWeightOz,
			double largeEmptyWeightOz) {
		this.key = key;
		this.displayName = displayName;
		this.category = category;
		this.perOzRate = perOzRate;
		this.smallEmptyWeightOz = smallEmptyWeightOz;
		this.largeEmptyWeightOz = largeEmptyWeightOz;
	}

	public String getKey() {
		return this.key;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public Category getCategory() {
		return this.category;
	}

	public double getPerOzRate() {
		return this.perOzRate;
	}

	public double getPerPoundRate() {
		return this.perOzRate * 16;
	}

	public double getEmptyWeightOz(boolean large) {
		return large ? this.largeEmptyWeightOz : this.smallEmptyWeightOz;
	}

	public static Material fromKey(String key) {
		for (Material m : values()) {
			if (m.key.equalsIgnoreCase(key)) {
				return m;
			}
		}
		throw new IllegalArgumentException("Unknown material: " + key);
	}

	/** Creates a container of this material with the given fluid-ounce size. */
	public Recycle newContainer(int ounces, boolean refundable) {
		switch (this) {
		case ALUMINUM: return new AluminumCan(ounces, refundable);
		case BIMETAL: return new BimetalCan(ounces, refundable);
		case GLASS_CLEAR: return new GlassBottle(ounces, refundable, GlassBottle.CLEAR);
		case GLASS_GREEN: return new GlassBottle(ounces, refundable, GlassBottle.GREEN);
		case GLASS_BROWN: return new GlassBottle(ounces, refundable, GlassBottle.BROWN);
		case PET: return new PETBottle(ounces, refundable);
		case HDPE: return new HDPE(ounces, refundable);
		case PVC: return new PVC(ounces, refundable);
		case LDPE: return new LDPE(ounces, refundable);
		case PP: return new PP(ounces, refundable);
		case PS: return new PS(ounces, refundable);
		case OTHER_PLASTIC: return new OtherPlastic(ounces, refundable);
		default: throw new IllegalStateException("Unhandled material " + this);
		}
	}
}
