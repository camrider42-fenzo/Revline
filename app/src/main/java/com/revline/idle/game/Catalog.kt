package com.revline.idle.game

enum class Category(val title: String) {
    INTAKE("Intake"),
    EXHAUST("Exhaust"),
    FUEL("Fuel"),
    IGNITION("Ignition"),
    VALVETRAIN("Valvetrain"),
    INDUCTION("Forced induction"),
    INTERNALS("Internals"),
    COOLING("Cooling"),
    ECU("ECU"),
}

/** What the part changes on the sheet. The parts map is grouped by this, not by bay. */
enum class Effect(val title: String) {
    POWER("Power"),
    ECONOMY("Economy"),
    BOOST("Boost"),
    COOLING("Cooling"),
    DURABILITY("Durability"),
}

data class Upgrade(
    val id: String,
    val category: Category,
    val name: String,
    val blurb: String,
    val maxLevel: Int,
    val baseCost: Double,
    val growth: Double,
    val requires: String? = null,
    val levelRequires: Map<Int, String> = emptyMap(),
)

data class ResearchNode(
    val id: String,
    val branch: String,
    val name: String,
    val detail: String,
    val cost: Double,
    val requires: List<String> = emptyList(),
)

data class Perk(
    val id: String,
    val name: String,
    val blurb: String,
    val costs: List<Int>,
)

object Upgrades {
    val all: List<Upgrade> = listOf(
        Upgrade("filter", Category.INTAKE, "Cotton panel filter", "Less pressure drop across the element. Peak volumetric efficiency rises a little.", 5, 800.0, 1.72),
        Upgrade("cold_air", Category.INTAKE, "Cold-air inlet", "The snorkel leaves the hot bay. Inlet air drops toward ambient, so density and knock margin both improve.", 4, 2200.0, 1.75),
        Upgrade("manifold", Category.INTAKE, "Ported intake manifold", "Cleaner runners move the breathing peak up the tach and add VE.", 4, 4500.0, 1.78, requires = "tbi"),
        Upgrade("itb", Category.INTAKE, "Individual throttle bodies", "One plate per cylinder. Strong top end, a narrower island, weaker low-rpm torque.", 3, 14000.0, 1.80, requires = "port"),
        Upgrade("catback", Category.EXHAUST, "Cat-back exhaust", "A straighter tailpipe cuts backpressure and frees a bit of VE.", 4, 1600.0, 1.70),
        Upgrade("headers", Category.EXHAUST, "Tubular headers", "Primary tubes scavenge the port. VE and the power peak both climb.", 4, 3800.0, 1.74, requires = "exhaust_fab"),
        Upgrade("long_tube", Category.EXHAUST, "Long-tube headers", "Longer primaries favor the top of the pull and give up a little low-end fill.", 3, 9000.0, 1.78, requires = "exhaust_fab"),
        Upgrade("ceramic", Category.EXHAUST, "Ceramic-coated manifold", "Coating keeps enthalpy in the stream: less heat into the bay, slightly earlier turbo spool, higher EGT.", 1, 3500.0, 1.0, requires = "exhaust_fab"),
        Upgrade("jets", Category.FUEL, "Carburetor jets", "Closer to the power AFR on a carb, plus more fuel mass when the bowls can keep up.", 4, 600.0, 1.65),
        Upgrade("fuel_pump", Category.FUEL, "High-flow fuel pump", "Raises the kilograms per second the rail can deliver. Boost without this goes lean.", 6, 1800.0, 1.70),
        Upgrade("tbi_unit", Category.FUEL, "Throttle-body injection", "One injector and a computer. Mixture control tightens versus a float bowl.", 1, 2500.0, 1.0, requires = "tbi"),
        Upgrade("injectors", Category.FUEL, "Port injectors", "An injector at each runner. Evaporation cools the port charge and the AFR tracks the map.", 5, 4200.0, 1.75, requires = "port"),
        Upgrade("hpfp", Category.FUEL, "High-pressure direct injection", "Fuel in the chamber. In-cylinder evaporation is a knock credit. The pump must move a lot of mass.", 3, 11000.0, 1.80, requires = "di"),
        Upgrade("plugs", Category.IGNITION, "Colder spark plugs", "The gap still fires under cylinder pressure. Combustion efficiency inches up.", 4, 450.0, 1.60),
        Upgrade("coils", Category.IGNITION, "Ignition coils", "More secondary energy. Levels 3 and 4 are coil-on-plug.", 4, 2000.0, 1.72, requires = "wasted_spark", levelRequires = mapOf(3 to "cop", 4 to "cop")),
        Upgrade("base_timing", Category.IGNITION, "Distributor advance", "A few degrees toward MBT. Past the fuel's knock limit the ECU, or the piston, pays for it.", 4, 900.0, 1.55),
        Upgrade("mapped_timing", Category.IGNITION, "Mapped spark table", "The piggyback moves advance with rpm instead of a vacuum can.", 5, 3200.0, 1.68, requires = "piggyback"),
        Upgrade("knock_box", Category.IGNITION, "Knock listening", "The ECU hears detonation and pulls spark. Unresolved knock falls; a hard pull still costs a little torque.", 1, 8500.0, 1.0, requires = "knock_node"),
        Upgrade("springs", Category.VALVETRAIN, "Valve springs", "Controls the valve at higher rpm. Redline and a little bottom-end strength come with it.", 5, 1500.0, 1.74),
        Upgrade("cam", Category.VALVETRAIN, "Performance cam", "More duration. The VE peak climbs and moves up-rpm. Torque at 1,800 rpm falls until VVT widens the island.", 4, 5000.0, 1.76, requires = "perf_cam"),
        Upgrade("vvt", Category.VALVETRAIN, "Variable valve timing", "Cam phasing gives back low-rpm fill without giving up the top-end lobe.", 2, 16000.0, 1.70, requires = "vvt_node"),
        Upgrade("vvl", Category.VALVETRAIN, "Variable lift", "A second lobe. Low lift for cruise manners, high lift for the pull.", 1, 36000.0, 1.0, requires = "vvl_node"),
        Upgrade("supercharger", Category.INDUCTION, "Roots-style supercharger", "Boost follows rpm, with a belt-drive friction hit. Replaced if a turbo goes on.", 4, 28000.0, 1.85, requires = "sc_node"),
        Upgrade("turbo", Category.INDUCTION, "Turbocharger", "Exhaust energy makes boost after spool. It adds backpressure, heat, and a fuel-system demand.", 5, 36000.0, 1.82, requires = "turbo_node"),
        Upgrade("twin", Category.INDUCTION, "Twin turbos", "Less plumbing volume. Spool comes earlier and the turbine side flows a little better.", 2, 70000.0, 1.80, requires = "twin_node"),
        Upgrade("intercooler", Category.INDUCTION, "Air-air intercooler", "Effectiveness drops compressor-outlet temperature toward ambient. Density and octane demand both move the right way.", 4, 6000.0, 1.78, requires = "ic_node"),
        Upgrade("boost_controller", Category.INDUCTION, "Electronic boost controller", "Wastegate duty adds a controlled amount of boost on top of the hardware.", 3, 7500.0, 1.70, requires = "standalone"),
        Upgrade("studs", Category.INTERNALS, "Head studs", "Clamping load. A small bump in how much cylinder pressure the gasket will live with.", 2, 1600.0, 1.60),
        Upgrade("pistons", Category.INTERNALS, "Forged pistons", "They tolerate detonation and boost better than a cast slug. Strength and a little octane forgiveness.", 3, 12000.0, 1.80, requires = "forged"),
        Upgrade("rods", Category.INTERNALS, "Forged connecting rods", "The beam handles the compressive spike from boost.", 3, 9000.0, 1.78, requires = "hyper"),
        Upgrade("crank", Category.INTERNALS, "Balanced crank", "Less friction loss from an unbalanced rotating assembly, plus strength.", 2, 14000.0, 1.75, requires = "forged"),
        Upgrade("bore", Category.INTERNALS, "Overbore", "Each level adds 2 mm of bore. Displacement, airflow, and heat all go up.", 3, 10000.0, 1.85, requires = "hyper"),
        Upgrade("compression", Category.INTERNALS, "Compression ratio", "Each step is about +0.38:1. Otto efficiency rises. So does octane demand.", 6, 2000.0, 1.70, levelRequires = mapOf(5 to "forged", 6 to "forged")),
        Upgrade("billet", Category.INTERNALS, "Billet mains", "The paranoid bottom end. Strength for boost the cast block was never sized for.", 1, 48000.0, 1.0, requires = "billet_node"),
        Upgrade("synthetic", Category.COOLING, "Synthetic oil", "The film holds past 145°C, up toward 160°C, and the crank turns with a little less friction.", 1, 350.0, 1.0),
        Upgrade("cap", Category.COOLING, "Radiator cap", "13 psi stock, then 16, then 20. Boiling point climbs about 1.4°C per psi, plus the glycol.", 2, 280.0, 1.80),
        Upgrade("thermostat", Category.COOLING, "Low-temperature thermostat", "82°C, then 71°C. Cooler metal, slightly more wall heat loss, a bit less heat soak.", 2, 220.0, 1.50),
        Upgrade("radiator", Category.COOLING, "Aluminum radiator", "More tube rows raise coolant-side conductance (W/K). Levels 3 and 4 need a real core design.", 4, 1400.0, 1.72, levelRequires = mapOf(3 to "heat_basics", 4 to "heat_basics")),
        Upgrade("duct", Category.COOLING, "Radiator ducting", "More of the air actually goes through the core. Effective UA rises about 10% per level.", 2, 900.0, 1.60),
        Upgrade("water_pump", Category.COOLING, "High-flow water pump", "Coolant velocity lifts the radiator's realized conductance. A belt pump still costs a little friction.", 3, 1100.0, 1.65),
        Upgrade("electric_pump", Category.COOLING, "Electric water pump", "No belt drag. Flow stays up at idle and on a dyno pull.", 1, 4500.0, 1.0, requires = "e_pump"),
        Upgrade("oil_cooler", Category.COOLING, "Thermostatic oil cooler", "Air-side UA for the oil. Heat that used to soak into the galleries can leave through the core.", 3, 1800.0, 1.70, requires = "oil_science"),
        Upgrade("meth", Category.COOLING, "Water-meth injection", "Latent heat of the spray pulls charge temperature down on the pull. Knock demand and EGT follow.", 2, 6500.0, 1.75, requires = "meth_science"),
        Upgrade("inconel", Category.COOLING, "Inconel exhaust valves", "The valve face survives EGT that would burn a stainless tulip. Limit moves from about 900°C to 980°C.", 1, 8000.0, 1.0, requires = "inconel_science"),
        Upgrade("waterless", Category.COOLING, "Waterless coolant", "Boiling point near 191°C. Heat transfer is worse, so UA drops about 16%.", 1, 1200.0, 1.0, requires = "waterless_science"),
        Upgrade("afr_map", Category.ECU, "AFR map", "WOT mixture walks toward the power AFR. Cruise is allowed to lean out. Deeper levels need a real ECU.", 6, 1500.0, 1.62, levelRequires = mapOf(3 to "piggyback", 4 to "piggyback", 5 to "standalone", 6 to "standalone")),
        Upgrade("torque_model", Category.ECU, "Torque-model ECU", "Spark, fuel, and knock control close around a torque target instead of open-loop guesses.", 1, 22000.0, 1.0, requires = "torque_node"),
    )

    fun byId(id: String): Upgrade = all.first { it.id == id }
}

object ResearchTree {
    val all: List<ResearchNode> = listOf(
        ResearchNode("exhaust_fab", "Exhaust", "Header fabrication", "Primary length, merge collectors, and why a log manifold leaves torque on the table.", 30.0),
        ResearchNode("heat_basics", "Thermal", "Heat rejection", "Radiator conductance, the thermostat, and why coolant temp is Q over UA once the stat is open.", 25.0),
        ResearchNode("oil_science", "Thermal", "Oil cooling", "The sump and the coolant galleries share the oil's heat. An air-oil cooler gives that heat another exit.", 70.0, listOf("heat_basics")),
        ResearchNode("e_pump", "Thermal", "Electric water pump", "Belt-driven pumps steal power and fade when the engine is not spinning hard.", 120.0, listOf("heat_basics")),
        ResearchNode("meth_science", "Thermal", "Water-methanol", "A 50/50 spray's latent heat comes out of the charge, not the radiator.", 160.0, listOf("ic_node")),
        ResearchNode("inconel_science", "Thermal", "Exhaust valve alloys", "Stainless gives up near 900°C EGT. Inconel keeps a face.", 140.0),
        ResearchNode("waterless_science", "Thermal", "Waterless coolant", "The boiling point jumps. The convection coefficient does not.", 80.0, listOf("heat_basics")),
        ResearchNode("premium", "Fuel", "Premium pump gas", "91–93 AKI. The knock limit moves before any hardware does.", 35.0),
        ResearchNode("tbi", "Fuel", "Throttle-body injection", "One injector, a computer, no float bowl.", 50.0),
        ResearchNode("port", "Fuel", "Port injection", "Fuel at the valve. The port charge cools on the way in.", 130.0, listOf("tbi")),
        ResearchNode("di", "Fuel", "Direct injection", "Injection after the valve. The cooling happens in the cylinder, which is where knock cares.", 400.0, listOf("port")),
        ResearchNode("e85", "Fuel", "E85 calibration", "Stoich near 9.8:1, LHV near 26.9 MJ/kg, latent heat more than double gasoline.", 200.0, listOf("premium", "port")),
        ResearchNode("race", "Fuel", "Race fuel", "About 100 octane, gasoline energy content. Octane without E85's fuel-mass penalty.", 340.0, listOf("premium")),
        ResearchNode("piggyback", "Management", "Piggyback ECU", "Intercept the spark and fuel tables.", 60.0),
        ResearchNode("standalone", "Management", "Standalone ECU", "You own the whole map, including boost duty.", 240.0, listOf("piggyback")),
        ResearchNode("torque_node", "Management", "Torque-model control", "The target is wheel torque. Spark and boost are the actuators.", 460.0, listOf("standalone")),
        ResearchNode("wasted_spark", "Ignition", "Wasted spark", "A coil for every pair of cylinders.", 45.0),
        ResearchNode("cop", "Ignition", "Coil-on-plug", "A coil on every plug. Dwell is no longer shared.", 150.0, listOf("wasted_spark")),
        ResearchNode("knock_node", "Ignition", "Knock listening", "Ion sense or a microphone on the block. Timing yields before the piston does.", 320.0, listOf("cop", "piggyback")),
        ResearchNode("perf_cam", "Valvetrain", "Performance cams", "Duration and lobe center. The VE curve is a hill, and the cam moves its peak.", 70.0),
        ResearchNode("vvt_node", "Valvetrain", "Variable valve timing", "Phasing widens the rpm range where VE stays high.", 260.0, listOf("perf_cam")),
        ResearchNode("vvl_node", "Valvetrain", "Variable lift", "Two lobes. One for the street, one for the pull.", 480.0, listOf("vvt_node")),
        ResearchNode("sc_node", "Induction", "Supercharging", "Boost that tracks rpm, paid for at the crank.", 190.0),
        ResearchNode("ic_node", "Induction", "Charge-air cooling", "Intercooler effectiveness. Compressor outlet temperature is not optional reading.", 85.0),
        ResearchNode("turbo_node", "Induction", "Turbocharging", "The turbine takes exhaust enthalpy. Spool, backpressure, and heat come with the boost.", 230.0, listOf("exhaust_fab")),
        ResearchNode("twin_node", "Induction", "Twin turbos", "Smaller turbines in parallel. Less volume to fill, earlier boost.", 520.0, listOf("turbo_node")),
        ResearchNode("hyper", "Internals", "Hypereutectic pistons", "Tighter clearance than a plain cast piston. A step toward rpm and bore.", 80.0),
        ResearchNode("forged", "Internals", "Forged internals", "Pistons and cranks that survive the pressure spike.", 260.0, listOf("hyper")),
        ResearchNode("billet_node", "Internals", "Billet bottom end", "When the forging still isn't the limit you were looking for.", 560.0, listOf("forged")),
    )

    fun byId(id: String): ResearchNode = all.first { it.id == id }
}

object Perks {
    val all: List<Perk> = listOf(
        Perk("flow", "Flow bench notes", "Each rank adds a little peak VE to every long-block you build after this.", listOf(1, 2, 4)),
        Perk("notebook", "Dyno habit", "Each rank adds 22% to passive research. The shop learns faster between pulls.", listOf(1, 2, 3)),
        Perk("octane", "Octane schooling", "Each rank is worth about 1.2 octane of knock resistance, on top of the fuel.", listOf(2, 3)),
        Perk("tooling", "Fixture credit", "Each rank takes 5% off part prices, to a floor of 18% off.", listOf(1, 2, 4)),
        Perk("fixtures", "Pattern strength", "Each rank leaves a little more bottom-end strength in the next stock block.", listOf(2, 4)),
    )

    fun byId(id: String): Perk = all.first { it.id == id }
}
