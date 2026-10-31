package com.revline.idle.economy

import com.revline.idle.data.GameState
import com.revline.idle.game.RaceDrive
import com.revline.idle.game.Racing
import com.revline.idle.game.ResearchTree
import com.revline.idle.game.Upgrades
import com.revline.idle.model.Block

/** Representative race builds, one set per engine block. Shared by the driver balance harness and the driver tests. */
object DriverBuilds {
    data class Build(val name: String, val state: GameState) {
        val setup: RaceDrive.Setup by lazy { RaceDrive.setup(state) }
    }

    private val research = ResearchTree.all.map { it.id }.toSet()
    private val studies = Racing.studies.map { it.id }.toSet()

    private fun upgrades(share: Double, extra: Map<String, Int> = emptyMap()): Map<String, Int> {
        val all = Upgrades.all
        val count = (all.size * share).toInt()
        return all.take(count).associate { it.id to it.maxLevel } + extra
    }

    /** The circuit tire that matches a drag tire's tier: the same money spent, on the other kind of rubber. */
    fun circuitTireOf(dragTire: String): String = when (dragTire) {
        "race" -> "sport"
        "slick" -> "racing"
        else -> "street"
    }

    /** [tire] is the drag tire. [ctire] is the circuit tire, and follows the tier of [tire] unless given. */
    fun make(
        p: Int, build: Racing.Build, tire: String, box: String, line: String, share: Double, rev: Boolean = false,
        ctire: String = circuitTireOf(tire), kit: Map<String, Int> = emptyMap(),
    ): GameState {
        val body = Racing.groupChassis(p, build)
        return GameState(
            prestiges = p,
            knowledge = p,
            money = 1.0e12,
            racing = true,
            researched = research,
            upgrades = upgrades(share, if (rev) mapOf("rev_kit" to 4, "springs" to 4, "cam" to 4) else emptyMap()),
            chassis = body.id,
            chassisLevel = mapOf(body.id to 3),
            coolingSlots = Racing.slotMax(body, 3),
            transmission = box,
            driveline = line,
            tire = tire,
            circuitTire = ctire,
            kit = kit,
            raceStudies = studies,
        )
    }

    /** Five setups that stress different hands. */
    fun forBlock(p: Int): List<Build> = listOf(
        Build("grip hog", make(p, Racing.Build.LIGHT, "bias", "four", "open", 0.9)),
        Build("hooked sleeper", make(p, Racing.Build.HEAVY, "slick", "crash", "cv", 0.45)),
        Build("rev monster", make(p, Racing.Build.MEDIUM, "radial", "four", "lsd", 1.0, rev = true)),
        Build("dog club", make(p, Racing.Build.MEDIUM, "slick", "dog", "irs", 0.8)),
        Build("close street", make(p, Racing.Build.MEDIUM, "radial", "close", "lsd", 0.6)),
    )

    /** Five builds that differ in what they are good at, for circuit work: same era, different bias. */
    fun orientations(p: Int): List<Build> = listOf(
        Build("power", make(p, Racing.Build.HEAVY, "slick", "dog", "irs", 1.0, rev = true).copy(finalDrive = "tall")),
        Build("grip", make(p, Racing.Build.HEAVY, "slick", "crash", "cv", 0.45)),
        Build("light", make(p, Racing.Build.LIGHT, "slick", "close", "irs", 0.5).copy(finalDrive = "short")),
        Build("balanced", make(p, Racing.Build.MEDIUM, "slick", "dog", "irs", 0.8)),
        Build("street", make(p, Racing.Build.MEDIUM, "radial", "close", "lsd", 0.6)),
    )
}
