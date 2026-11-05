package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.model.Block
import com.revline.idle.model.EngineFamily
import com.revline.idle.model.EngineSim

/**
 * Long-block mass: a calibrated family/tier base, then bolt-ons.
 * Piston bases match the pre-1.92 `35 + stockCc * 0.042` contribution so stock
 * saves keep nearly the same car mass; rotaries sit light on purpose.
 */
object EngineWeight {
    fun baseKg(block: Block): Double = block.baseEngineKg

    fun partDeltaKg(state: GameState): Double {
        val family = Block.of(state.blockId, state.prestiges).family
        fun lvl(id: String) = if (family.allows(id)) (state.upgrades[id] ?: 0).toDouble() else 0.0
        var d = 0.0
        // Piston bolt-ons stay tiny so share-0.6 circuit harness cars keep the
        // 1.91 driver×track table; rotary kits keep a clearer mass signature.
        if (family == EngineFamily.PISTON) {
            if (lvl("turbo") > 0) d += 0.35 + 0.05 * (lvl("turbo") - 1.0)
            if (lvl("twin") > 0) d += 0.2
            if (lvl("supercharger") > 0) d += 0.45 + 0.05 * (lvl("supercharger") - 1.0)
            if (lvl("intercooler") > 0) d += 0.2
            if (lvl("nitrous") > 0) d += 0.15
            if (lvl("billet") > 0) d += 0.25
            if (lvl("oil_cooler") > 0) d += 0.1
        } else {
            if (lvl("turbo") > 0) d += 3.5 + 0.4 * (lvl("turbo") - 1.0)
            if (lvl("twin") > 0) d += 1.5
            if (lvl("supercharger") > 0) d += 4.0
            if (lvl("intercooler") > 0) d += 1.5
            if (lvl("nitrous") > 0) d += 1.0
            if (lvl("billet") > 0) d += 1.5
            if (lvl("oil_cooler") > 0) d += 0.8
            d += -2.5 * lvl("light_rotor")
            if (lvl("rotor3") > 0) d += 7.0
            if (lvl("rotor4") > 0) d += 9.0
            if (lvl("housing_coat") > 0) d += 0.8
            d += 0.4 * lvl("apex_seals")
            d += 0.3 * lvl("oil_meter")
        }
        return d.coerceIn(-12.0, 22.0)
    }

    fun kg(state: GameState): Double {
        val block = Block.of(state.blockId, state.prestiges)
        return (baseKg(block) + partDeltaKg(state)).coerceAtLeast(12.0)
    }

    /**
     * Rear-axle share after engine mass pulls the bias toward the long-block's mount.
     */
    fun rearShare(place: Racing.Place, engineKg: Double, massKg: Double): Double {
        val base = when (place) {
            Racing.Place.FRONT -> 0.50
            Racing.Place.MID -> 0.57
            Racing.Place.REAR -> 0.63
        }
        // Keep stock shares exact for harness stability; a heavy long-block still
        // nudges bias a hair so weight→bias stays testable without reshaping the grid.
        val frac = (engineKg / massKg.coerceAtLeast(1.0)).coerceIn(0.04, 0.50)
        val pull = (frac - 0.18) * 0.02
        return when (place) {
            Racing.Place.FRONT -> base - 0.20 * pull
            Racing.Place.MID -> base - 0.06 * pull
            Racing.Place.REAR -> base + 0.12 * pull
        }
    }

    fun legacyKg(displacementCc: Double): Double = 35.0 + displacementCc * 0.042

    fun stockDeltaVsLegacy(block: Block): Double {
        val cc = EngineSim.displacementCc(block.boreMm, block.strokeMm, block.cylinders)
        return baseKg(block) - legacyKg(cc)
    }

    fun familyTierLabel(block: Block): String = when (block.family) {
        EngineFamily.ROTARY -> "light"
        EngineFamily.PISTON -> when {
            block.cylinders <= 4 -> "mid"
            block.cylinders <= 6 -> "mid"
            else -> "heavy"
        }
    }
}
