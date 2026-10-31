package com.revline.idle.game

/**
 * The seven other cars on a circuit grid: a driver, a team, how fast he runs against the par car and how he races.
 * A style is a shape, not a speed: the numbers below move the lap (the one-lap pace, whether he fades or comes on, how
 * ragged he is, how often he binds it) around the same average pace, so a style changes where he is on a lap and not
 * how good he is. The exception is [Style.AGGRESSIVE], which is quicker on average and pays for it in the wall.
 */
object Rivals {
    enum class Style(
        val title: String,
        val blurb: String,
        /** Qualifying lap against his race pace: negative is a better one-lap car. */
        val quali: Double,
        /** Race pace change per lap, as a share of the lap, centred on the middle of the race. Positive fades. */
        val fade: Double,
        /** Lap-to-lap scatter against the field's usual. */
        val scatter: Double,
        /** Against the field's usual chance of a retirement. */
        val dnf: Double,
        /** Race pace on average, a small bonus (negative is faster) that pays for the risk. */
        val pace: Double,
        /** How often he makes a mistake of his own, 0..1. */
        val aggression: Double,
        /** Skill in the wet, -1..1. Applied only in weather. */
        val wet: Double,
        /** Whether he takes a pit stop on a race with wear. */
        val stops: Boolean,
    ) {
        QUALIFIER("One-lap specialist", "Quick on a fresh set. Takes pole, fades as the tires go.", -0.0060, 0.0016, 1.0, 1.0, 0.0, 0.25, 0.0, false),
        CLOSER("Race pace", "Starts mid-grid and comes on. The tires last for him.", 0.0050, -0.0016, 1.0, 1.0, 0.0, 0.25, 0.0, false),
        STEADY("Metronome", "Same lap every lap, and he finishes. Never wins on a hard day.", 0.0, 0.0, 0.4, 0.4, 0.0, 0.1, 0.0, false),
        AGGRESSIVE("Hard charger", "Quicker on average. Ragged, runs wide, and puts it in the wall.", 0.0010, 0.0, 2.0, 3.0, -0.0030, 0.9, -0.2, false),
        STRATEGIST("Strategist", "Pits for fresh tires and runs hard after it.", 0.0, 0.0, 1.0, 1.0, 0.0, 0.3, 0.1, true),
        WET_MASTER("Rain specialist", "Ordinary in the dry. In the wet he is a different driver.", 0.0, 0.0, 1.0, 1.0, 0.0, 0.4, 0.9, false),
        SURVIVOR("Banker", "Nursed through every race. Slow qualifier, rarely out.", 0.0040, 0.0, 0.7, 0.3, 0.0010, 0.1, 0.1, false),
    }

    data class Rival(
        val name: String,
        val team: String,
        val style: Style,
        /** How far off the par car he runs (share of lap time). Negative is quicker. */
        val offset: Double,
    )

    val all = listOf(
        Rival("Jim Clark", "Ardent Works", Style.QUALIFIER, -0.130),
        Rival("John Surtees", "Ironside Racing", Style.STEADY, -0.075),
        Rival("Jochen Rindt", "Harlow & Vane", Style.AGGRESSIVE, -0.015),
        Rival("Emerson Fittipaldi", "Orchard Lane GP", Style.STRATEGIST, 0.005),
        Rival("Mario Andretti", "Crown Hill Motorsport", Style.CLOSER, 0.025),
        Rival("Nelson Piquet", "Redfern Speed", Style.WET_MASTER, 0.045),
        Rival("Nigel Mansell", "Blackwater Racing", Style.SURVIVOR, 0.075),
    )

    fun byName(name: String): Rival? = all.firstOrNull { it.name == name }

    /** The team a name races for: a rival's own, or the player's. */
    const val YOUR_TEAM = "Your team"

    /** A line each for the circuit screen. */
    fun lines(): List<String> = all.map { "${it.name}  ${it.team}  ${it.style.title}  aggression ${(it.style.aggression * 100).toInt()}" }
}
