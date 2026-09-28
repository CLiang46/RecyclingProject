package com.recycleproj;

/**
 * Recyclable material types, with the per-ounce refund rate used when a load is paid by weight
 * and a typical empty-container weight used when the actual weight is not known.
 */
public enum Material {
	ALUMINUM("aluminum", "Aluminum cans", 0.104, 0.5, 1.0),
	BIMETAL("bimetal", "Bimetal cans", 0.027, 1.5, 3.0),
	GLASS("glass", "Glass bottles", 0.0063, 7.0, 17.0),
	PET("pet", "#1 PET plastic", 0.09, 0.35, 1.6),
	HDPE("hdpe", "#2 HDPE plastic", 0.67 / 16, 1.0, 2.2),
	PVC("pvc", "#3 PVC plastic", 0.48 / 16, 0.8, 1.8),
	LDPE("ldpe", "#4 LDPE plastic", 1.98 / 16, 0.8, 1.8),
	PP("pp", "#5 PP plastic", 0.56 / 16, 0.8, 1.8),
	PS("ps", "#6 PS plastic", 5.45 / 16, 0.5, 1.2),
	OTHER_PLASTIC("other", "#7 Other plastic", 0.31 / 16, 0.8, 1.8);

	private final String key;
	private final String displayName;
	private final double perOzRate;
	private final double smallEmptyWeightOz;
	private final double largeEmptyWeightOz;

	Material(String key, String displayName, double perOzRate, double smallEmptyWeightOz, double largeEmptyWeightOz) {
		this.key = key;
		this.displayName = displayName;
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
		case GLASS: return new GlassBottle(ounces, refundable, "clear");
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
