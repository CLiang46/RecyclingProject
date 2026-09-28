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
		smallLoadsArePaidByCount();
		loadsOverFiftyArePaidByWeight();
		actualWeightOverridesEstimate();
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
	}

	static void smallLoadsArePaidByCount() {
		BuybackCalculator calc = new BuybackCalculator();
		calc.add(new AluminumCan(12, true), 30);
		calc.add(new AluminumCan(24, true), 20);
		BuybackCalculator.MaterialResult r = calc.calculate().get(0);
		assertEquals("50 cans paid by count", false, r.paidByWeight);
		assertClose("50 cans payout", 30 * 0.05 + 20 * 0.10, r.payout);
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
		calc.setActualWeightPounds(Material.ALUMINUM, 3.5);
		BuybackCalculator.MaterialResult r = calc.calculate().get(0);
		assertEquals("weight measured", false, r.weightEstimated);
		assertClose("measured payout", 3.5 * 16 * 0.104, r.payout);
		expectThrows("negative weight", () -> calc.setActualWeightPounds(Material.ALUMINUM, -1));
		expectThrows("NaN weight", () -> calc.setActualWeightPounds(Material.ALUMINUM, Double.NaN));
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
		Map<String, String> p = WebServer.parseParams("aluminum_regular=10&pet_large=3&glass_weight=&x%20y=a+b");
		assertEquals("decoded key", "a b", p.get("x y"));
		BuybackCalculator calc = WebServer.calculate(p);
		assertClose("web total", 10 * 0.05 + 3 * 0.10, calc.total());
		expectThrows("negative count", () -> WebServer.calculate(WebServer.parseParams("pet_regular=-1")));
		expectThrows("decimal count", () -> WebServer.calculate(WebServer.parseParams("pet_regular=1.5")));
		expectThrows("text weight", () -> WebServer.calculate(WebServer.parseParams("pet_weight=abc")));
		expectThrows("huge count", () -> WebServer.calculate(WebServer.parseParams("pet_regular=99999999")));
	}

	static void jsonOutputIsWellFormed() {
		String json = WebServer.toJson(WebServer.calculate(WebServer.parseParams("aluminum_regular=2")));
		assertEquals("json", "{\"results\":[{\"key\":\"aluminum\",\"name\":\"Aluminum cans\",\"regularCount\":2,"
				+ "\"largeCount\":0,\"nonRefundableCount\":0,\"weightPounds\":0.063,\"weightEstimated\":true,"
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
