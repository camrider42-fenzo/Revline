package com.revline.idle.model

/**
 * Long-block architecture. One filter rule decides which shop parts fit:
 * [allows] hides rotary-only hardware on pistons and poppet/reciprocating
 * hardware on rotaries. Shared bays stay on both.
 */
enum class EngineFamily {
    PISTON,
    ROTARY,
    ;

    fun allows(partId: String): Boolean = when (this) {
        PISTON -> partId !in ROTARY_ONLY
        ROTARY -> partId !in PISTON_ONLY
    }

    companion object {
        /** Poppet valves, cams, pistons, rods, rings, bore — nonsense on a Wankel. */
        val PISTON_ONLY: Set<String> = setOf(
            "lap", "springs", "cam", "vvt", "vvl", "cyl_off", "limiter", "rev_kit",
            "pistons", "rods", "crank", "rings", "bore", "inconel",
        )

        /** Apex seals, porting, oil metering, housings, light rotors, eccentric, rotor kits. */
        val ROTARY_ONLY: Set<String> = setOf(
            "apex_seals", "port_street", "port_bridge", "port_peri",
            "oil_meter", "housing_coat", "light_rotor", "rotor3", "rotor4", "eccentric",
        )
    }
}
