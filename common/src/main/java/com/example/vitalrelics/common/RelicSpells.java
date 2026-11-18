package com.example.vitalrelics.common;

import com.example.vitalrelics.common.relics.Relic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RelicSpells {
	private RelicSpells() {}

	/*
	 * Relic tiers, ordered from weakest to strongest. A spell's level is its
	 * relic's tier, so a rare teleport always beats an uncommon one, and an
	 * epic teleport always beats a rare one.
	 */
	public static final List<String> TIERS =
			List.of("common", "uncommon", "rare", "epic");

	public static int tierRank(final String rarity) {
		final int index = TIERS.indexOf(rarity);

		return index < 0 ? 0 : index;
	}

	/*
	 * A same-named spell is chosen in two steps:
	 *
	 *   1. the relic with the higher tier wins, that tier being the spell level;
	 *   2. only between equal tiers does the "priority" parameter decide.
	 *
	 * Step 2 can only ever happen for hand-written configurations, because the
	 * loader rejects the same spell on two relics of one tier.
	 */
	public static boolean isTheSpellBetter(
			final Relic baseRelic,
			final Relic.Spells.Info base,
			final Relic candidateRelic,
			final Relic.Spells.Info candidate) {

		final int baseTier = tierRank(baseRelic.rarity);
		final int candidateTier = tierRank(candidateRelic.rarity);

		if (candidateTier != baseTier)
			return candidateTier > baseTier;

		return isTheSpellBetter(base, candidate);
	}

	/*
	 * The default score keeps the old teleport behavior: intensity times
	 * recovery. Other spell types may supply a numeric "priority" parameter
	 * without requiring a new Java field.
	 */
	public static boolean isTheSpellBetter(
			final Relic.Spells.Info base,
			final Relic.Spells.Info candidate) {

		return score(candidate) > score(base);
	}

	private static double score(final Relic.Spells.Info spell) {
		final Object priority = spell.parameters.get("priority");

		if (priority instanceof Number value)
			return value.doubleValue();

		return numberParameter(spell, "intensity", 0.0) *
				numberParameter(spell, "recovery", 1.0);
	}

	public static double numberParameter(
			final Relic.Spells.Info spell,
			final String key,
			final double fallback) {

		final Object value = spell.parameters.get(key);

		return value instanceof Number number
				? number.doubleValue()
				: fallback;
	}

	private static final int MAX_COOLDOWN_TICKS = 20 * 60;

	/*
	 * There are potential issues if ticks are unstable on large-scale modpacks.
	 * Therefore, the ticks should be stablized.
	 */
	public static int cooldownTicks(final Relic.Spells.Info spell) {
		final double recovery =
				numberParameter(spell, "recovery", Double.NaN);

		if (!Double.isFinite(recovery) || recovery <= 0.0) {
			throw new IllegalArgumentException(
					"Spell recovery must be finite and positive: " + recovery
			);
		}

		final double calculatedTicks = 20.0 / recovery;

		return (int) Math.max(1L, Math.min(Math.round(calculatedTicks), MAX_COOLDOWN_TICKS));
	}

	public static Map<String, Relic.Spells.Info> gatherSpells(
			final List<Relic> relics) {

		final Map<String, Relic.Spells.Info> result =
				new LinkedHashMap<>();
		final Map<String, Relic> owners = new LinkedHashMap<>();

		for (final Relic relic : relics) {
			for (final var entry : relic.available_spells.entrySet()) {
				final Relic.Spells.Info current = result.get(entry.getKey());

				if (current == null || isTheSpellBetter(
						owners.get(entry.getKey()),
						current,
						relic,
						entry.getValue()
				)) {
					result.put(entry.getKey(), entry.getValue());
					owners.put(entry.getKey(), relic);
				}
			}
		}

		return result;
	}

	/*
	 * The same spell name on two relics of the same tier would make the winner
	 * meaningless, so it is rejected. Different tiers are fine: that is how a
	 * stronger relic upgrades a spell.
	 */
	public static void checkNoSameTierSpellDuplicates(
			final List<Relic> relics) {

		final Map<String, String> seen = new LinkedHashMap<>();

		for (final Relic relic : relics) {
			for (final String spellId : relic.available_spells.keySet()) {
				final String key = tierRank(relic.rarity) + "/" + spellId;
				final String previous = seen.put(key, relic.id);

				if (previous != null && !previous.equals(relic.id)) {
					throw new IllegalArgumentException(
							"Spell '" + spellId + "' is registered to both '" +
									previous + "' and '" + relic.id +
									"'; they share the '" + relic.rarity +
									"' tier, so neither could win. " +
									"Move one of them to another tier."
					);
				}
			}
		}
	}

}
