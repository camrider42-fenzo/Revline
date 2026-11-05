package com.revline.idle.game

import com.revline.idle.model.Block

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
    /** Prestige index this study waits for. 0 is the moped. The V8 is the past-the-limit gate. */
    val minPrestige: Int = 0,
)

data class Perk(
    val id: String,
    val name: String,
    val blurb: String,
    val costs: List<Int>,
)

object Upgrades {
    val all: List<Upgrade> = listOf(
        // First levels are about an hour and a half of the inline-four contract they add.
        // Opening tools are a few minutes on purpose. A level that stops paying does not get much dearer.
        Upgrade("lap", Category.VALVETRAIN, "Valve lap", "A light cut on the seat. The valve seals, and a little more mixture stays in the cylinder.", 5, 12.0, 1.18),
        Upgrade("gap", Category.IGNITION, "Plug gap", "The gap is opened a hair. The spark kernel gets larger.", 4, 18.0, 1.22),
        Upgrade("boot", Category.INTAKE, "Intake boot", "The coupler stops sucking hot bay air. Density picks up before any real intake work.", 4, 16.0, 1.22),
        Upgrade("tip", Category.EXHAUST, "Turn-down tip", "A short outlet. A little less backpressure, nothing like a header.", 3, 16.0, 1.20),
        Upgrade("needle", Category.FUEL, "Needle clip", "The clip moves one groove. The carb runs closer to the mixture you asked for.", 3, 20.0, 1.22),
        Upgrade("oil_service", Category.COOLING, "Oil and filter service", "Fresh oil and a new filter. The crank turns easier until the next interval.", 3, 14.0, 1.15),
        Upgrade("fins", Category.COOLING, "Fin cleanup", "Straightened fins and a clear path for air. The jug sheds a little more heat.", 4, 14.0, 1.20),
        Upgrade("wrench", Category.INTERNALS, "Torque wrench pass", "Clamps and fasteners brought to spec. A small bump in how together the engine is.", 4, 12.0, 1.15),
        Upgrade("filter", Category.INTAKE, "Cotton panel filter", "Less pressure drop across the element. Peak volumetric efficiency rises a little.", 5, 180.0, 1.05),
        Upgrade("cold_air", Category.INTAKE, "Cold-air inlet", "The snorkel leaves the hot bay. Inlet air drops toward ambient, so density and knock margin both improve.", 4, 980.0, 1.42),
        Upgrade("manifold", Category.INTAKE, "Ported intake manifold", "Cleaner runners move the breathing peak up the tach and add VE.", 4, 220.0, 1.15, requires = "tbi"),
        Upgrade("itb", Category.INTAKE, "Individual throttle bodies", "One plate per cylinder. Strong top end, a narrower island, weaker low-rpm torque.", 3, 220.0, 1.15, requires = "port"),
        Upgrade("catback", Category.EXHAUST, "Cat-back exhaust", "A straighter tailpipe cuts backpressure and frees a bit of VE.", 4, 130.0, 1.08),
        Upgrade("headers", Category.EXHAUST, "Tubular headers", "Primary tubes scavenge the port. VE and the power peak both climb.", 4, 220.0, 1.15, requires = "exhaust_fab"),
        Upgrade("long_tube", Category.EXHAUST, "Long-tube headers", "Longer primaries favor the top of the pull and give up a little low-end fill.", 3, 220.0, 1.15, requires = "exhaust_fab"),
        Upgrade("ceramic", Category.EXHAUST, "Ceramic-coated manifold", "Coating keeps enthalpy in the stream: less heat into the bay, slightly earlier turbo spool, higher EGT.", 1, 360.0, 1.0, requires = "exhaust_fab"),
        Upgrade("jets", Category.FUEL, "Carburetor jets", "Closer to the power AFR on a carb, plus more fuel mass when the bowls can keep up.", 4, 120.0, 1.08),
        Upgrade("fuel_pump", Category.FUEL, "High-flow fuel pump", "Raises the kilograms per second the rail can deliver. Boost without this goes lean.", 6, 120.0, 1.08),
        Upgrade("tbi_unit", Category.FUEL, "Throttle-body injection", "One injector and a computer. Mixture control tightens versus a float bowl.", 1, 120.0, 1.0, requires = "tbi"),
        Upgrade("injectors", Category.FUEL, "Port injectors", "An injector at each runner. Evaporation cools the port charge and the AFR tracks the map.", 5, 180.0, 0.70, requires = "port"),
        Upgrade("hpfp", Category.FUEL, "High-pressure direct injection", "Fuel in the chamber. In-cylinder evaporation is a knock credit. The pump must move a lot of mass.", 3, 360.0, 0.65, requires = "di"),
        Upgrade("plugs", Category.IGNITION, "Colder spark plugs", "The gap still fires under cylinder pressure. Combustion efficiency inches up.", 4, 65.0, 1.08),
        Upgrade("coils", Category.IGNITION, "Ignition coils", "More secondary energy. Levels 3 and 4 are coil-on-plug.", 4, 80.0, 1.08, requires = "wasted_spark", levelRequires = mapOf(3 to "cop", 4 to "cop")),
        Upgrade("base_timing", Category.IGNITION, "Distributor advance", "A few degrees toward MBT. Past the fuel's knock limit the ECU, or the piston, pays for it.", 4, 70.0, 1.08),
        Upgrade("mapped_timing", Category.IGNITION, "Mapped spark table", "The piggyback moves advance with rpm instead of a vacuum can.", 5, 70.0, 1.08, requires = "piggyback"),
        Upgrade("knock_box", Category.IGNITION, "Knock listening", "The ECU hears detonation and pulls spark. Unresolved knock falls; a hard pull still costs a little torque.", 1, 220.0, 1.0, requires = "knock_node"),
        Upgrade("springs", Category.VALVETRAIN, "Valve springs", "Controls the valve at higher rpm. Redline and a little bottom-end strength come with it.", 5, 400.0, 1.08),
        Upgrade("rev_kit", Category.VALVETRAIN, "Rev kit", "A raised limiter and retainers that keep up. Each level is another 380 rpm, until this block's tach stops.", 4, 240.0, 1.22),
        Upgrade("cam", Category.VALVETRAIN, "Performance cam", "More duration. The VE peak climbs and moves up-rpm. Torque at 1,800 rpm falls until VVT widens the island.", 4, 360.0, 1.15, requires = "perf_cam"),
        Upgrade("vvt", Category.VALVETRAIN, "Variable valve timing", "Cam phasing gives back low-rpm fill without giving up the top-end lobe.", 2, 150.0, 1.15, requires = "vvt_node"),
        Upgrade("vvl", Category.VALVETRAIN, "Variable lift", "A second lobe. Low lift for cruise manners, high lift for the pull.", 1, 500.0, 1.0, requires = "vvl_node"),
        Upgrade("supercharger", Category.INDUCTION, "Roots-style supercharger", "Boost follows rpm, with a belt-drive friction hit. Replaced if a turbo goes on.", 4, 3400.0, 0.68, requires = "sc_node"),
        Upgrade("turbo", Category.INDUCTION, "Turbocharger", "Exhaust energy makes boost after spool. It adds backpressure, heat, and a fuel-system demand.", 5, 2800.0, 0.70, requires = "turbo_node"),
        Upgrade("twin", Category.INDUCTION, "Twin turbos", "Less plumbing volume. Spool comes earlier and the turbine side flows a little better.", 2, 720.0, 1.15, requires = "twin_node"),
        Upgrade("intercooler", Category.INDUCTION, "Air-air intercooler", "Effectiveness drops compressor-outlet temperature toward ambient. Density and octane demand both move the right way.", 4, 180.0, 1.55, requires = "ic_node"),
        Upgrade("boost_controller", Category.INDUCTION, "Electronic boost controller", "Wastegate duty adds a controlled amount of boost on top of the hardware.", 3, 500.0, 1.15, requires = "standalone"),
        Upgrade("studs", Category.INTERNALS, "Head studs", "Clamping load. A small bump in how much cylinder pressure the gasket will live with.", 2, 220.0, 1.15),
        Upgrade("pistons", Category.INTERNALS, "Forged pistons", "They tolerate detonation and boost better than a cast slug. Strength and a little octane forgiveness.", 3, 220.0, 1.50, requires = "forged"),
        Upgrade("rods", Category.INTERNALS, "Forged connecting rods", "The beam handles the compressive spike from boost.", 3, 220.0, 1.15, requires = "hyper"),
        Upgrade("crank", Category.INTERNALS, "Balanced crank", "Less friction loss from an unbalanced rotating assembly, plus strength.", 2, 220.0, 1.15, requires = "forged"),
        Upgrade("bore", Category.INTERNALS, "Overbore", "Each level adds 2 mm of bore. Displacement, airflow, and heat all go up.", 3, 360.0, 1.15, requires = "hyper"),
        Upgrade("compression", Category.INTERNALS, "Compression ratio", "Each step is about +0.38:1. Otto efficiency rises. So does octane demand.", 6, 300.0, 1.20, levelRequires = mapOf(5 to "forged", 6 to "forged")),
        Upgrade("billet", Category.INTERNALS, "Billet mains", "The paranoid bottom end. Strength for boost the cast block was never sized for.", 1, 220.0, 1.0, requires = "billet_node"),
        Upgrade("synthetic", Category.COOLING, "Synthetic oil", "The film holds past 145°C, up toward 160°C, and the crank turns with a little less friction.", 1, 150.0, 1.0),
        Upgrade("cap", Category.COOLING, "Radiator cap", "13 psi stock, then 16, then 20. Boiling point climbs about 1.4°C per psi, plus the glycol.", 2, 75.0, 1.08),
        Upgrade("thermostat", Category.COOLING, "Low-temperature thermostat", "82°C, then 71°C. Cooler metal, slightly more wall heat loss, a bit less heat soak.", 2, 150.0, 1.80),
        Upgrade("radiator", Category.COOLING, "Aluminum radiator", "More tube rows raise coolant-side conductance (W/K). Levels 3 and 4 need a real core design.", 4, 500.0, 1.15, levelRequires = mapOf(3 to "heat_basics", 4 to "heat_basics")),
        Upgrade("duct", Category.COOLING, "Radiator ducting", "More of the air actually goes through the core. Effective UA rises about 10% per level.", 2, 150.0, 1.15),
        Upgrade("water_pump", Category.COOLING, "High-flow water pump", "Coolant velocity lifts the radiator's realized conductance. A belt pump still costs a little friction.", 3, 80.0, 1.08),
        Upgrade("electric_pump", Category.COOLING, "Electric water pump", "No belt drag. Flow stays up at idle and on a dyno pull.", 1, 220.0, 1.0, requires = "e_pump"),
        Upgrade("oil_cooler", Category.COOLING, "Thermostatic oil cooler", "Air-side UA for the oil. Heat that used to soak into the galleries can leave through the core.", 3, 1100.0, 0.85, requires = "oil_science"),
        Upgrade("meth", Category.COOLING, "Water-meth injection", "Latent heat of the spray pulls charge temperature down on the pull. Knock demand and EGT follow.", 2, 220.0, 0.65, requires = "meth_science"),
        Upgrade("inconel", Category.COOLING, "Inconel exhaust valves", "The valve face survives EGT that would burn a stainless tulip. Limit moves from about 900°C to 980°C.", 1, 75.0, 1.0, requires = "inconel_science"),
        Upgrade("waterless", Category.COOLING, "Waterless coolant", "Boiling point near 191°C. Heat transfer is worse, so UA drops about 16%.", 1, 150.0, 1.0, requires = "waterless_science"),
        Upgrade("afr_map", Category.ECU, "AFR map", "WOT mixture walks toward the power AFR. Cruise is allowed to lean out. Deeper levels need a real ECU.", 6, 90.0, 1.05, levelRequires = mapOf(3 to "piggyback", 4 to "piggyback", 5 to "standalone", 6 to "standalone")),
        Upgrade("torque_model", Category.ECU, "Torque-model ECU", "Spark, fuel, and knock control close around a torque target instead of open-loop guesses.", 1, 220.0, 1.0, requires = "torque_node"),
        // Economy parts added in 1.72.0. They only move the mpg read under load, and the cheapest one is a rebuild-0 part. Later ones wait on studies.
        Upgrade("rings", Category.INTERNALS, "Low-tension rings", "Less ring drag on the bore. The same fuel turns more of the crank, and it shows most at a light pedal.", 3, 75.0, 1.22),
        Upgrade("lean_spark", Category.IGNITION, "Lean-burn spark table", "Timing and dwell for a lean part-throttle mixture. The engine sips at the pedal where it is paid.", 3, 160.0, 1.22, requires = "lean_burn"),
        Upgrade("egr", Category.EXHAUST, "Cooled EGR loop", "Cooled exhaust fed back to the intake. The throttle opens further for the same power, so the engine pumps less air.", 3, 380.0, 1.25, requires = "egr_node"),
        Upgrade("cyl_off", Category.VALVETRAIN, "Cylinder deactivation", "Half the cylinders go quiet at a light pedal. The ones still firing run at a better load.", 2, 1400.0, 1.50, requires = "cda_node"),
        Upgrade("nitrous", Category.INDUCTION, "Nitrous shot", "A plate on the intake. Each level is a bigger hit of oxygen and fuel, and a bigger slug of heat the radiator did not agree to. Failures get likely.", 3, 8000.0, 1.35, requires = "nitrous_node"),
        Upgrade("overboost", Category.INDUCTION, "Wastegate shim", "Holds the gate shut at the top of the pull. Boost climbs past what the rods were sized for, the charge leaves the cooler hot, and the dyno slip gets louder.", 3, 9000.0, 1.40, requires = "overboost_node"),
        Upgrade("limiter", Category.VALVETRAIN, "Limiter delete", "The rev limiter moves up, then goes away. Power follows the tach. Piston speed and the bearings follow it.", 3, 7000.0, 1.35, requires = "limiter_node"),
        Upgrade("dyno_fuel", Category.FUEL, "Dyno fuel cut", "The wide-open map leans toward stoich for the rollers. Horsepower climbs. EGT climbs faster, and so does the chance the valve gives up.", 3, 6000.0, 1.30, requires = "lean_node"),
        // Rotary-only. Hidden on piston blocks by EngineFamily.allows.
        Upgrade("apex_seals", Category.INTERNALS, "Ceramic apex seals", "Harder tips that hold compression at nine grand. Strength and a little tip-speed headroom.", 4, 280.0, 1.22, requires = "apex_science"),
        Upgrade("port_street", Category.INTAKE, "Street port", "Milder window. Peak VE climbs without throwing away the midrange.", 3, 320.0, 1.18, requires = "port_science"),
        Upgrade("port_bridge", Category.INTAKE, "Bridge port", "A bridged intake window. Top-end fill jumps; idle and low torque get ragged.", 2, 480.0, 1.25, requires = "port_science"),
        Upgrade("port_peri", Category.INTAKE, "Peripheral port", "Side housing ports for race fill. Peakiest curve on the map, thirsty, loud.", 1, 1400.0, 1.0, requires = "port_science"),
        Upgrade("oil_meter", Category.COOLING, "Oil metering / premix", "Meters oil to the seals. Less friction, cooler tips, and the premix smell of a healthy Wankel.", 3, 180.0, 1.20, requires = "apex_science"),
        Upgrade("housing_coat", Category.COOLING, "Housing coating", "A ceramic coat on the trochoid. Heat into the coolant drops; EGT climbs a little.", 2, 420.0, 1.22, requires = "housing_science"),
        Upgrade("light_rotor", Category.INTERNALS, "Lightened rotors", "Less rotating mass. The eccentric spins freer and the tach climbs cleaner.", 2, 560.0, 1.30, requires = "apex_science"),
        Upgrade("eccentric", Category.VALVETRAIN, "Eccentric shaft kit", "Bearings and a raised limiter for the eccentric. Each level is another few hundred rpm, until this block's tach stops.", 4, 300.0, 1.22, requires = "apex_science"),
        Upgrade("rotor3", Category.INTERNALS, "Third rotor", "20B conversion. Another chamber, more displacement, more mass.", 1, 4200.0, 1.0, requires = "rotor_kit"),
        Upgrade("rotor4", Category.INTERNALS, "Fourth rotor", "26B conversion. Needs the third rotor already in. Peak power, peak thirst.", 1, 6800.0, 1.0, requires = "rotor_kit"),
    )

    fun byId(id: String): Upgrade = all.first { it.id == id }

    fun forFamily(family: com.revline.idle.model.EngineFamily): List<Upgrade> =
        all.filter { family.allows(it.id) }
}

object ResearchTree {
    val all: List<ResearchNode> = listOf(
        ResearchNode("exhaust_fab", "Exhaust", "Header fabrication", "Primary length, merge collectors, and why a log manifold leaves torque on the table.", 30.0),
        ResearchNode("heat_basics", "Thermal", "Heat rejection", "Radiator conductance, the thermostat, and why coolant temp is Q over UA once the stat is open.", 25.0),
        ResearchNode("oil_science", "Thermal", "Oil cooling", "The sump and the coolant galleries share the oil's heat. An air-oil cooler gives that heat another exit.", 300.0, listOf("heat_basics")),
        ResearchNode("e_pump", "Thermal", "Electric water pump", "Belt-driven pumps steal power and fade when the engine is not spinning hard.", 360.0, listOf("heat_basics")),
        ResearchNode("meth_science", "Thermal", "Water-methanol", "A 50/50 spray's latent heat comes out of the charge, not the radiator.", 1200.0, listOf("ic_node")),
        ResearchNode("inconel_science", "Thermal", "Exhaust valve alloys", "Stainless gives up near 900°C EGT. Inconel keeps a face.", 340.0),
        ResearchNode("waterless_science", "Thermal", "Waterless coolant", "The boiling point jumps. The convection coefficient does not.", 320.0, listOf("heat_basics")),
        ResearchNode("premium", "Fuel", "Premium pump gas", "91–93 AKI. The knock limit moves before any hardware does.", 35.0),
        ResearchNode("tbi", "Fuel", "Throttle-body injection", "One injector, a computer, no float bowl.", 40.0),
        ResearchNode("port", "Fuel", "Port injection", "Fuel at the valve. The port charge cools on the way in.", 400.0, listOf("tbi")),
        ResearchNode("di", "Fuel", "Direct injection", "Injection after the valve. The cooling happens in the cylinder, which is where knock cares.", 5200.0, listOf("port")),
        ResearchNode("e85", "Fuel", "E85 calibration", "Stoich near 9.8:1, LHV near 26.9 MJ/kg, latent heat more than double gasoline.", 4500.0, listOf("premium", "port")),
        ResearchNode("race", "Fuel", "Race fuel", "About 100 octane, gasoline energy content. Octane without E85's fuel-mass penalty.", 1400.0, listOf("premium")),
        ResearchNode("piggyback", "Management", "Piggyback ECU", "Intercept the spark and fuel tables.", 55.0),
        ResearchNode("standalone", "Management", "Standalone ECU", "You own the whole map, including boost duty.", 1300.0, listOf("piggyback")),
        ResearchNode("torque_node", "Management", "Torque-model control", "The target is wheel torque. Spark and boost are the actuators.", 10000.0, listOf("standalone")),
        ResearchNode("wasted_spark", "Ignition", "Wasted spark", "A coil for every pair of cylinders.", 40.0),
        ResearchNode("cop", "Ignition", "Coil-on-plug", "A coil on every plug. Dwell is no longer shared.", 420.0, listOf("wasted_spark")),
        ResearchNode("knock_node", "Ignition", "Knock listening", "Ion sense or a microphone on the block. Timing yields before the piston does.", 5800.0, listOf("cop", "piggyback")),
        ResearchNode("perf_cam", "Valvetrain", "Performance cams", "Duration and lobe center. The VE curve is a hill, and the cam moves its peak.", 50.0),
        ResearchNode("vvt_node", "Valvetrain", "Variable valve timing", "Phasing widens the rpm range where VE stays high.", 1600.0, listOf("perf_cam")),
        ResearchNode("vvl_node", "Valvetrain", "Variable lift", "Two lobes. One for the street, one for the pull.", 11000.0, listOf("vvt_node")),
        ResearchNode("sc_node", "Induction", "Supercharging", "Boost that tracks rpm, paid for at the crank.", 450.0),
        ResearchNode("ic_node", "Induction", "Charge-air cooling", "Intercooler effectiveness. Compressor outlet temperature is not optional reading.", 60.0),
        ResearchNode("turbo_node", "Induction", "Turbocharging", "The turbine takes exhaust enthalpy. Spool, backpressure, and heat come with the boost.", 1500.0, listOf("exhaust_fab")),
        ResearchNode("twin_node", "Induction", "Twin turbos", "Smaller turbines in parallel. Less volume to fill, earlier boost.", 6500.0, listOf("turbo_node")),
        ResearchNode("hyper", "Internals", "Hypereutectic pistons", "Tighter clearance than a plain cast piston. A step toward rpm and bore.", 60.0),
        ResearchNode("forged", "Internals", "Forged internals", "Pistons and cranks that survive the pressure spike.", 1700.0, listOf("hyper")),
        ResearchNode("billet_node", "Internals", "Billet bottom end", "When the forging still isn't the limit you were looking for.", 11000.0, listOf("forged")),
        ResearchNode("lean_burn", "Management", "Lean-burn calibration", "A lean part-throttle map with enough spark energy to light it.", 150.0, listOf("piggyback"), minPrestige = Block.THUMPER.ordinal),
        ResearchNode("egr_node", "Management", "Exhaust gas recirculation", "Cooled exhaust back into the intake charge. Less pumping loss at a light pedal, and cooler combustion.", 1100.0, listOf("lean_burn"), minPrestige = Block.TRIPLE.ordinal),
        ResearchNode("cda_node", "Valvetrain", "Cylinder deactivation", "Shut the valves on half the bank. The rest of the engine runs at a heavier load, where it is more efficient.", 5200.0, listOf("egr_node", "vvt_node"), minPrestige = Block.V6.ordinal),
        ResearchNode("overlimit", "Past the limit", "Past the limit", "The safe map is a suggestion. Cylinder pressure, rpm, and mixture can all go further. The block is the one that says when they have. Opens once a V8 is on the stand.", 16000.0, listOf("billet_node"), minPrestige = Block.V8.ordinal),
        ResearchNode("lean_node", "Past the limit", "Dyno fuel cut", "The pull is leaned toward stoich so more of the burn reaches the rollers. The exhaust valve gets what the dyno does not.", 10000.0, listOf("overlimit"), minPrestige = Block.V8.ordinal),
        ResearchNode("nitrous_node", "Past the limit", "Nitrous oxide", "A shot adds oxygen the intake never measured, and fuel to match. The hit shows up on the dyno slip immediately. So does the heat in the chamber.", 12000.0, listOf("overlimit"), minPrestige = Block.V8.ordinal),
        ResearchNode("limiter_node", "Past the limit", "Rev limiter delete", "The tach keeps climbing. Mean piston speed does not care what number used to be the limiter.", 14000.0, listOf("overlimit"), minPrestige = Block.V8.ordinal),
        ResearchNode("overboost_node", "Past the limit", "Wastegate delete", "The wastegate spring is a limit, not a law. Past it the compressor, the charge temperature, and the rods are all guessing.", 18000.0, listOf("overlimit", "turbo_node"), minPrestige = Block.V8.ordinal),
        ResearchNode("apex_science", "Rotary", "Apex seals", "Tip seals, corner seals, and why a Wankel dies when they go soft. Opens on the rotary rebuild.", 800.0, minPrestige = Block.ROTARY_1R.ladderStep),
        ResearchNode("port_science", "Rotary", "Porting", "Street, bridge, and peripheral windows. The VE hill gets taller and narrower.", 2200.0, listOf("apex_science"), minPrestige = Block.ROTARY_1R.ladderStep),
        ResearchNode("housing_science", "Rotary", "Housing coats", "Trochoid coatings that keep heat out of the coolant and in the exhaust.", 1600.0, listOf("apex_science"), minPrestige = Block.ROTARY_1R.ladderStep),
        ResearchNode("rotor_kit", "Rotary", "Multi-rotor kit", "Adding a third and fourth rotor. More chambers, more mass, more fuel.", 9000.0, listOf("apex_science"), minPrestige = Block.ROTARY_1R.ladderStep),
    )

    fun byId(id: String): ResearchNode = all.first { it.id == id }
}

object Perks {
    val all: List<Perk> = listOf(
        Perk("flow", "Flow bench notes", "Each rank adds a little peak VE to every long-block you build after this.", listOf(1, 2, 4)),
        Perk("notebook", "Dyno habit", "Each rank adds 22% to research. The passive trickle and the dyno pull both grow.", listOf(1, 2, 3)),
        Perk("octane", "Octane schooling", "Each rank is worth about 1.2 octane of knock resistance, on top of the fuel.", listOf(2, 3)),
        Perk("tooling", "Fixture credit", "Each rank takes 5% off part prices, to a floor of 18% off.", listOf(1, 2, 4)),
        Perk("fixtures", "Pattern strength", "Each rank leaves a little more bottom-end strength in the next stock block.", listOf(2, 4)),
        Perk("breakin", "Break-in sheet", "Each rank takes 1.2% off the friction of a fresh long-block.", listOf(1, 2)),
        Perk("bay_air", "Bay thermometer", "Each rank drops the air in the engine bay by 1.5°C.", listOf(1, 2)),
        Perk("cores", "Radiator notes", "Each rank adds a tenth of cooling headroom, so the head soaks more slowly.", listOf(2, 3)),
        Perk("readers", "Plug reading", "Each rank is a slightly cleaner spark, before any coil is on the engine.", listOf(1, 2)),
        Perk("spool", "Spool notes", "Each rank brings a charger's boost in 160 rpm sooner. An unboosted engine does not use it.", listOf(2, 4)),
        Perk("slips", "Dyno slips", "Each rank adds 10% to the research a dyno pull pays. The contract does not change.", listOf(1, 3)),
        Perk("pump", "Pump margin", "Each rank adds a little fuel flow the block was not sold with, so a hard pull leans out later.", listOf(2, 3)),
    )

    fun byId(id: String): Perk = all.first { it.id == id }
}
