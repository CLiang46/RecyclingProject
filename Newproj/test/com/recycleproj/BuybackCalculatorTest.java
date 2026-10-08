package com.recycleproj;

import java.util.List;
import java.util.Map;

/** Dependency-free tests. Run with ./run.sh test or .\run.ps1 test. Exits non-zero on failure. */
public class BuybackCalculatorTest {
	private static int failures = 0;
	private static int passed = 0;

	public static void main(String[] args) {
		containerSizesDetermineCrv();
		nonRefundableContainersEarnNothing();
		glassBottleUsesItsOwnRate();
		glassColorsAreCountedSeparately();
		materialsAreGroupedIntoCategories();
		smallLoadsArePaidByCount();
		countLimitAppliesPerSizeGroup();
		loadsOverFiftyArePaidByWeight();
		actualWeightOverridesEstimate();
		weightWithoutCountIsPaidByWeight();
		countLimitAppliesPerMaterial();
		webParamsAreParsedAndValidated();
		jsonOutputIsWellFormed();

		System.out.println(passed + " passed, " + failures + " failed");
		if (failures > 0) {
			System.exit(1);
		}
	}

	static void containerSizesDetermineCrv() {
		assertClose("12 oz can CRV", 0.05, new AluminumCan(12, true).getCRV());
		assertClose("23 oz can CRV", 0.05, new AluminumCan(23, true).getCRV());
		assertClose("24 oz can CRV", 0.10, new AluminumCan(24, true).getCRV());
		assertClose("2 L bottle CRV", 0.10, new PETBottle(68, true).getCRV());
	}

	static void nonRefundableContainersEarnNothing() {
		BuybackCalculator calc = new BuybackCalculator();
		calc.add(new PETBottle(16, false), 10);
		assertClose("non-CRV total", 0, calc.total());
		assertEquals("non-CRV counted", 10, calc.calculate().get(0).nonRefundableCount);
	}

	static void glassBottleUsesItsOwnRate() {
		assertClose("glass per-oz rate", 0.0063, new GlassBottle(12, true, "green").getPerOzRate());
		assertEquals("glass color", "green", new GlassBottle(12, true, "green").getColor());
		assertEquals("clear material", Material.GLASS_CLEAR, new GlassBottle(12, true, "Clear").getMaterial());
		assertEquals("green material", Material.GLASS_GREEN, new GlassBottle(12, true, "green").getMaterial());
		assertEquals("dark is brown", Material.GLASS_BROWN, new GlassBottle(12, true, "dark").getMaterial());
		expectThrows("unknown glass color", () -> new GlassBottle(12, true, "purple"));
	}

	static void materialsAreGroupedIntoCategories() {
		assertEquals("aluminum is a can", Material.Category.CANS, Material.ALUMINUM.getCategory());
		assertEquals("bimetal is a can", Material.Category.CANS, Material.BIMETAL.getCategory());
		for (Material m : Material.values()) {
			Recycle sample = m.newContainer(12, true);
			Material.Category expected = sample instanceof Can ? Material.Category.CANS
					: sample instanceof GlassBottle ? Material.Category.GLASS : Material.Category.PLASTIC;
			assertEquals(m + " category matches its container class", expected, m.getCategory());
		}
	}

	static void glassColorsAreCountedSeparately() {
		BuybackCalculator calc = new BuybackCalculator();
		calc.add(new GlassBottle(12, true, "clear"), 40);
		calc.add(new GlassBottle(12, true, "green"), 40);
		calc.add(new GlassBottle(12, true, "brown"), 60);
		List<BuybackCalculator.MaterialResult> results = calc.calculate();
		assertEquals("three glass groups", 3, results.size());
		assertEquals("clear by count", false, results.get(0).paidByWeight);
		assertEquals("green by count", false, results.get(1).paidByWeight);
		assertEquals("brown by weight", true, results.get(2).paidByWeight);
		assertClose("glass total", 80 * 0.05 + 60 * 7.0 * 0.0063, calc.total());
	}

	static void smallLoadsArePaidByCount() {
		BuybackCalculator calc = new BuybackCalculator();
		calc.add(new AluminumCan(12, true), 30);
		calc.add(new AluminumCan(24, true), 20);
		List<BuybackCalculator.MaterialResult> results = calc.calculate();
		assertEquals("regular and large are separate groups", 2, results.size());
		assertEquals("regular group first", false, results.get(0).large);
		assertEquals("regular count", 30, results.get(0).count);
		assertEquals("large count", 20, results.get(1).count);
		assertEquals("regular paid by count", false, results.get(0).paidByWeight);
		assertEquals("large paid by count", false, results.get(1).paidByWeight);
		assertClose("50 cans payout", 30 * 0.05 + 20 * 0.10, calc.total());
	}

	static void countLimitAppliesPerSizeGroup() {
		BuybackCalculator calc = new BuybackCalculator();
		calc.add(new PETBottle(16, true), 40);
		calc.add(new PETBottle(68, true), 40);
		List<BuybackCalculator.MaterialResult> results = calc.calculate();
		assertEquals("40 regular PET by count", false, results.get(0).paidByWeight);
		assertEquals("40 large PET by count", false, results.get(1).paidByWeight);
		assertClose("80 PET split by size", 40 * 0.05 + 40 * 0.10, calc.total());

		calc.add(new PETBottle(68, true), 20);
		results = calc.calculate();
		assertEquals("regular PET still by count", false, results.get(0).paidByWeight);
		assertEquals("60 large PET by weight", true, results.get(1).paidByWeight);
		assertClose("large PET estimated weight oz", 60 * 1.6, results.get(1).weightOz);
		assertClose("mixed payout", 40 * 0.05 + 60 * 1.6 * 0.09, calc.total());
	}

	static void loadsOverFiftyArePaidByWeight() {
		BuybackCalculator calc = new BuybackCalculator();
		calc.add(new AluminumCan(12, true), 100);
		BuybackCalculator.MaterialResult r = calc.calculate().get(0);
		assertEquals("100 cans paid by weight", true, r.paidByWeight);
		assertEquals("weight estimated", true, r.weightEstimated);
		assertClose("estimated weight oz", 50.0, r.weightOz);
		assertClose("by-weight payout", 50.0 * 0.104, r.payout);
		assertClose("by-count value still reported", 5.00, r.countValue);
	}

	static void actualWeightOverridesEstimate() {
		BuybackCalculator calc = new BuybackCalculator();
		calc.add(new AluminumCan(12, true), 100);
		calc.add(new AluminumCan(24, true), 60);
		calc.setActualWeightPounds(Material.ALUMINUM, false, 3.5);
		calc.setActualWeightPounds(Material.ALUMINUM, true, 4);
		List<BuybackCalculator.MaterialResult> results = calc.calculate();
		assertEquals("regular weight measured", false, results.get(0).weightEstimated);
		assertClose("regular measured payout", 3.5 * 16 * 0.104, results.get(0).payout);
		assertClose("large measured payout", 4 * 16 * 0.104, results.get(1).payout);
		expectThrows("negative weight", () -> calc.setActualWeightPounds(Material.ALUMINUM, false, -1));
		expectThrows("NaN weight", () -> calc.setActualWeightPounds(Material.ALUMINUM, true, Double.NaN));
	}

	static void weightWithoutCountIsPaidByWeight() {
		BuybackCalculator calc = new BuybackCalculator();
		calc.setActualWeightPounds(Material.GLASS_GREEN, true, 10);
		BuybackCalculator.MaterialResult r = calc.calculate().get(0);
		assertEquals("weight-only group paid by weight", true, r.paidByWeight);
		assertClose("weight-only payout", 10 * 16 * 0.0063, r.payout);
	}

	static void countLimitAppliesPerMaterial() {
		BuybackCalculator calc = new BuybackCalculator();
		calc.add(new AluminumCan(12, true), 40);
		calc.add(new GlassBottle(12, true, "clear"), 40);
		List<BuybackCalculator.MaterialResult> results = calc.calculate();
		assertEquals("two materials", 2, results.size());
		for (BuybackCalculator.MaterialResult r : results) {
			assertEquals(r.material + " paid by count", false, r.paidByWeight);
		}
		assertClose("mixed total", 80 * 0.05, calc.total());
	}

	static void webParamsAreParsedAndValidated() {
		Map<String, String> p = WebServer.parseParams("aluminum_regular=10&pet_large=3&glass_clear_regular_weight=&x%20y=a+b");
		assertEquals("decoded key", "a b", p.get("x y"));
		BuybackCalculator calc = WebServer.calculate(p);
		assertClose("web total", 10 * 0.05 + 3 * 0.10, calc.total());
		BuybackCalculator weighed = WebServer.calculate(WebServer.parseParams(
				"pet_regular=80&pet_regular_weight=2&pet_large=80&pet_large_weight=5"));
		assertClose("web weights per size", (2 + 5) * 16 * 0.09, weighed.total());
		expectThrows("negative count", () -> WebServer.calculate(WebServer.parseParams("pet_regular=-1")));
		expectThrows("decimal count", () -> WebServer.calculate(WebServer.parseParams("pet_regular=1.5")));
		expectThrows("text weight", () -> WebServer.calculate(WebServer.parseParams("pet_large_weight=abc")));
		expectThrows("huge count", () -> WebServer.calculate(WebServer.parseParams("pet_regular=99999999")));
	}

	static void jsonOutputIsWellFormed() {
		String json = WebServer.toJson(WebServer.calculate(WebServer.parseParams("aluminum_regular=2")));
		assertEquals("json", "{\"results\":[{\"key\":\"aluminum\",\"name\":\"Aluminum cans\",\"size\":\"regular\",\"count\":2,"
				+ "\"nonRefundableCount\":0,\"weightPounds\":0.063,\"weightEstimated\":true,"
				+ "\"countValue\":0.10,\"weightValue\":0.10,\"method\":\"count\",\"payout\":0.10}],\"total\":0.10}", json);
	}

	private static void assertClose(String name, double expected, double actual) {
		check(name, Math.abs(expected - actual) < 1e-9, expected, actual);
	}

	private static void assertEquals(String name, Object expected, Object actual) {
		check(name, expected.equals(actual), expected, actual);
	}

	private static void expectThrows(String name, Runnable r) {
		try {
			r.run();
			check(name, false, "IllegalArgumentException", "no exception");
		} catch (IllegalArgumentException e) {
			check(name, true, null, null);
		}
	}

	private static void check(String name, boolean ok, Object expected, Object actual) {
		if (ok) {
			passed++;
		} else {
			failures++;
			System.out.println("FAIL " + name + ": expected " + expected + " but was " + actual);
		}
	}
}
