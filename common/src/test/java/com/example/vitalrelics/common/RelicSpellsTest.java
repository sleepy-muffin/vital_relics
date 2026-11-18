package com.example.vitalrelics.common;

import com.example.vitalrelics.common.relics.Relic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for spell parameter parsing, scoring and selection.
 */
class RelicSpellsTest {

	private static Relic relic(final String id, final String rarity, final Object... spells) {
		final Relic relic = new Relic();
		relic.id = id;
		relic.rarity = rarity;

		for (int i = 0; i < spells.length; i += 2)
			relic.available_spells.put((String) spells[i], (Relic.Spells.Info) spells[i + 1]);

		return relic;
	}

	private static Relic.Spells.Info spell(final Object... pairs) {
		final Relic.Spells.Info info = new Relic.Spells.Info();

		for (int i = 0; i < pairs.length; i += 2)
			info.parameters.put((String) pairs[i], pairs[i + 1]);

		return info;
	}

	@Test
	void numberParameterReadsNumbersAndFallsBack() {
		assertEquals(3.0, RelicSpells.numberParameter(
				spell("intensity", 3), "intensity", -1.0), 1e-9);
		assertEquals(-1.0, RelicSpells.numberParameter(
				spell(), "intensity", -1.0), 1e-9);
		assertEquals(-1.0, RelicSpells.numberParameter(
				spell("intensity", "high"), "intensity", -1.0), 1e-9);
	}

	@Test
	void cooldownTicksInvertsRecovery() {
		assertEquals(20, RelicSpells.cooldownTicks(spell("recovery", 1.0)));
		assertEquals(5, RelicSpells.cooldownTicks(spell("recovery", 4.0)));
		assertEquals(1, RelicSpells.cooldownTicks(spell("recovery", 100.0)));
	}

	@Test
	void cooldownTicksIsClampedToTheMaximum() {
		assertEquals(1200, RelicSpells.cooldownTicks(spell("recovery", 0.001)));
	}

	@Test
	void cooldownTicksRejectsInvalidRecovery() {
		assertThrows(IllegalArgumentException.class,
				() -> RelicSpells.cooldownTicks(spell()));
		assertThrows(IllegalArgumentException.class,
				() -> RelicSpells.cooldownTicks(spell("recovery", Double.NaN)));
		assertThrows(IllegalArgumentException.class,
				() -> RelicSpells.cooldownTicks(spell("recovery", 0.0)));
		assertThrows(IllegalArgumentException.class,
				() -> RelicSpells.cooldownTicks(spell("recovery", -1.0)));
		assertThrows(IllegalArgumentException.class,
				() -> RelicSpells.cooldownTicks(spell("recovery", Double.POSITIVE_INFINITY)));
		assertThrows(IllegalArgumentException.class,
				() -> RelicSpells.cooldownTicks(spell("recovery", Double.NEGATIVE_INFINITY)));
		assertThrows(IllegalArgumentException.class,
				() -> RelicSpells.cooldownTicks(spell("recovery", "fast")));
	}

	@Test
	void explicitPriorityWinsOverIntensityTimesRecovery() {
		final Relic.Spells.Info base =
				spell("intensity", 100.0, "recovery", 1.0, "priority", 10);
		final Relic.Spells.Info candidate =
				spell("intensity", 1.0, "recovery", 1.0, "priority", 20);

		assertTrue(RelicSpells.isTheSpellBetter(base, candidate));
		assertFalse(RelicSpells.isTheSpellBetter(candidate, base));
	}

	@Test
	void equalScoresAreNotBetter() {
		final Relic.Spells.Info base = spell("intensity", 2.0, "recovery", 3.0);
		final Relic.Spells.Info candidate = spell("intensity", 3.0, "recovery", 2.0);

		assertFalse(RelicSpells.isTheSpellBetter(base, candidate));
	}

	@Test
	void nonNumericPriorityFallsBackToTheDefaultScore() {
		final Relic.Spells.Info base =
				spell("intensity", 2.0, "recovery", 3.0, "priority", "high");
		final Relic.Spells.Info candidate =
				spell("intensity", 3.0, "recovery", 3.0, "priority", "low");

		assertTrue(RelicSpells.isTheSpellBetter(base, candidate));
		assertFalse(RelicSpells.isTheSpellBetter(candidate, base));
	}

	@Test
	void gatherSpellsKeepsTheBestEntryAndInsertionOrder() {
		final Relic first = relic("first", "uncommon",
				"a", spell("priority", 1),
				"b", spell("priority", 5));

		final Relic second = relic("second", "rare",
				"b", spell("priority", 9),
				"c", spell("priority", 2));

		final Map<String, Relic.Spells.Info> result =
				RelicSpells.gatherSpells(List.of(first, second));

		assertEquals(List.of("a", "b", "c"), List.copyOf(result.keySet()));
		assertSame(second.available_spells.get("b"), result.get("b"));
	}

	@Test
	void gatherSpellsKeepsAnEarlierStrongerEntry() {
		final Relic first = relic("first", "rare", "a", spell("priority", 1));
		final Relic second = relic("second", "common", "a", spell("priority", 0));

		final Map<String, Relic.Spells.Info> result =
				RelicSpells.gatherSpells(List.of(first, second));

		assertSame(first.available_spells.get("a"), result.get("a"));
	}

	@Test
	void aHigherTierSpellWinsRegardlessOfPriority() {
		final Relic low = relic("low", "rare",
				"teleport", spell("range", 30, "recovery", 0.25, "priority", 100));
		final Relic high = relic("high", "epic",
				"teleport", spell("range", 50, "recovery", 0.25));

		assertSame(high.available_spells.get("teleport"),
				RelicSpells.gatherSpells(List.of(low, high)).get("teleport"));
		assertSame(high.available_spells.get("teleport"),
				RelicSpells.gatherSpells(List.of(high, low)).get("teleport"));
	}

	@Test
	void tierOrderRunsFromCommonToEpic() {
		assertEquals(0, RelicSpells.tierRank("common"));
		assertEquals(1, RelicSpells.tierRank("uncommon"));
		assertEquals(2, RelicSpells.tierRank("rare"));
		assertEquals(3, RelicSpells.tierRank("epic"));
	}

	@Test
	void priorityBreaksTiesWithinOneTier() {
		final Relic base = relic("base", "rare", "a", spell("priority", 1));
		final Relic candidate = relic("candidate", "rare", "a", spell("priority", 2));

		assertTrue(RelicSpells.isTheSpellBetter(
				base, base.available_spells.get("a"),
				candidate, candidate.available_spells.get("a")));
	}

	@Test
	void theSameSpellOnTwoRelicsOfOneTierIsRejected() {
		final Relic first = relic("first", "rare", "teleport", spell("range", 30));
		final Relic second = relic("second", "rare", "teleport", spell("range", 50));

		final IllegalArgumentException error = assertThrows(
				IllegalArgumentException.class,
				() -> RelicSpells.checkNoSameTierSpellDuplicates(List.of(first, second)));

		assertTrue(error.getMessage().contains("teleport"));
		assertTrue(error.getMessage().contains("rare"));
	}

	@Test
	void theSameSpellOnDifferentTiersIsAllowed() {
		final Relic low = relic("low", "rare", "teleport", spell("range", 30));
		final Relic high = relic("high", "epic", "teleport", spell("range", 50));

		RelicSpells.checkNoSameTierSpellDuplicates(List.of(low, high));
	}

	@Test
	void theSameSpellTwiceOnOneRelicIsNotADuplicate() {
		final Relic one = relic("one", "rare", "teleport", spell("range", 30));

		RelicSpells.checkNoSameTierSpellDuplicates(List.of(one));
	}
}
