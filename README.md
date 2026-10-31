# Revline

An offline idle shop where the contract pays from a running engine model. You buy parts, study a tech tree, and occasionally scrap the long-block for Engineering Knowledge. Nothing here phones home. There are no ads and no API keys.

The screen is pure black (`#000000`). Instrument lines are drawn as a bright core with a soft halo: white structure, lime for money and horsepower, cyan for coolant and boost, amber for torque and oil, red for redline, heat, and a floored pedal. The parts bay is a black bubble map you drag and pinch. Two strips sit at the top. The first names the bottleneck: a head that is still soaking, a dyno note, the short number on the rebuild sheet, or the bay with the most left once the sheet is clear. Tapping it moves the map to that bay. The second names the next level for the goal you picked: horsepower, torque, cruise mpg, or dollars per second. (The cruise goal there is the road-load number. The rebuild sheet reads mpg at the profit pedal.) Tapping a goal on that strip switches it. Tapping it opens that part. A fitted part can be pulled one level at a time, and the price of that level comes back. Bubbles are grouped by what the part changes: power, economy, boost, cooling, or durability. The groups sit close together. Studying turbocharging, supercharging, or twins opens that part for purchase. Nothing bolts on by itself: a lone small turbo on a big block can make less than the same block breathing on its own, so boost is a choice you can preview first.

## Build

Java 17, Android SDK 35, minSdk 26.

```bash
export ANDROID_HOME="$HOME/Android/Sdk"   # or wherever the SDK lives
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug testDebugUnitTest
```

The sideload APK is `app/build/outputs/apk/release/app-release.apk`. Debug and release use the same key in `app/sideload.keystore`, so a later build can install over this one.

## Install on a phone

The phone package is `dist/revline.zip` in this repository (version 1.72.0). It is a zip 2.0 archive with one file, `revline.apk`. Download that zip from the repo, extract it, then tap `revline.apk` and choose Install. Each release replaces `dist/revline.zip` in the same place.

If Revline is already on the phone from a Grok Bot build, uninstall that copy first. Those builds were signed with a different key, and Android will not replace them. Uninstalling clears the save in app storage. Native libraries are stored inside the APK and signed with the JAR signature as well as the newer APK signatures, which is what phone installers expect when you tap a downloaded file.

`adb sideload` is a recovery command. It will not install this app. With USB debugging on:

```bash
adb devices
adb install -r app/build/outputs/apk/release/app-release.apk
```

`./scripts/pack-release.sh` writes `dist/revline.zip`. That zip is the file kept in the repository. The loose APK is not, because `*.apk` is gitignored.

## What the contract pays

The pedal is a large part of the contract. It is scored on how much of the engine's maximum output that stop is actually making, and on the mpg at that same stop. The maximum horsepower and torque set how big the check can be. A closed pedal leaves most of that output unused and pays like it. Rpm is not its own term: an engine that makes its peak at 4,000 rpm is paid in full there, and one that makes it at 7,000 is paid in full there. Current mpg pulls the other way. The last of the pedal dumps fuel through a rich mixture, on a 50 cc single the same as on the V12, so a wide-open stop pays less than the one that is actually using the engine.

Heat soak is not a settled temperature. At the rpm you are holding it climbs at a rate in degrees per 10 seconds, and it keeps climbing while you stay there. The head can take 80°C of soak. The soaked head is hotter everywhere else too: coolant, oil, and the exhaust all rise with it, and the charge is hotter, so power and mpg fall while you stay in it. Past about 7°C the contract starts to feel it, and past 30°C the heat term is on the floor. Drop the pedal under about 30% and the rate goes negative, so a short easy stretch pulls the temperature back down. A bigger radiator, ducting, an oil cooler, a cold-air inlet, an intercooler, or water-meth cuts the rate, and a serious cooling system can hold a working stop without the head running away. Heat soaks a wide-open pull and takes more off. A cold-air inlet, radiator, oil cooler, intercooler, or water-meth spray cuts that soak, so those parts let you hold more pedal.

```
use             = (hpAtStop / peakHp)^0.85 * (torqueAtStop / peakTq)^0.15
powerScore      = peakHp^1.62 * (1 + peakTq / 950) * use^1.85
economyScore    = (mpgAtStop / 28) * (3.40 / dollarsPerGallon)^0.42
durabilityScore = (reliability/100)^1.15
heat            = condition^0.75
dollars/second  = 0.0397 * powerScore * economyScore * durabilityScore * heat * multiplier
multiplier      = 1 + 0.12 * EngineeringKnowledge
```

`mpgAtStop` is this pedal's fuel flow scored at a steady 60 mph. `condition` falls when the pull is hot, knocking, fuel-limited, or heat-soaked past about 7°C.

Pump 87 is $3.40/gal, 93 is $4.50, E85 is $4.10, and race fuel is $15. The price exponent is mild. The grade earns its price as burn speed and timing: each 6 octane over 87 is about 9% more of the fuel's energy and about 3.7° of timing, until knock. On a mild engine, 93 lands near 87. On a hot or boosted engine, 87 gives the timing back and 93 or E85 pulls ahead on output. Race fuel's price can cost more than the power it adds. Moving the pedal across the tach changes the check by a small lean. Cooling and a bigger peak move it by more.

A locked part on the bubble map names the upgrade you still have to buy, or the study if that is the only gate, instead of showing a price. Lines stay inside one bay.

The throttle you set is saved. Offline pay uses that same stop, at 80% of the contract and 20% of passive research, capped at 8 hours. A rebuild puts the pedal back to a light opening so the next long-block gets its own pull.

Research points tick from peak horsepower (`0.04 + 0.022 × HP^0.8` per second, times the knowledge multiplier, more with the Dyno habit perk). A dyno pull pays 50 seconds of that same research rate, then the dyno-slip factor, and waits 35 seconds. Sitting on the passive trickle is the smaller share, so most of the research comes from pulling. The pull grows when horsepower, Engineering Knowledge, or Dyno habit grows. The pedal is locked while the rollers are moving. Pulls do not happen while the app is closed.

## The engine model

A 4-stroke inline-four. Stock is an 86 mm × 86 mm, 9.5:1, carbureted long-block (about 2.0 L).

- **Otto efficiency** uses γ = 1.30 for hot combustion gas: `1 - CR^(1-γ)`. A real-cycle factor of 0.84 stands in for finite burn, crevices, and incomplete expansion.
- **Volumetric efficiency** is a hill centered on the cam's power peak. Duration moves and narrows it. VVT and variable lift widen it again, which is why a big cam costs low-rpm torque until the phaser shows up.
- **Boost** is a supercharger that follows rpm, a turbo with a logistic spool, or twins that spool earlier. Compressor outlet temperature is the isentropic relation at γ = 1.40 and a compressor efficiency of 0.72. An intercooler of effectiveness ε pulls that temperature toward ambient.
- **Fuel.** Gasoline LHV is 43.4 MJ/kg, stoich 14.7. E85 is 26.9 MJ/kg, stoich 9.76, with much higher latent heat. Port evaporation cools the charge (`m_fuel * h_fg / (m_air * cp)`). Direct injection spends that cooling in the cylinder, which is a knock credit rather than an intake-density gain. If the pump cannot supply the commanded AFR, the mixture leans out and power falls.
- **Spark.** Torque falls off as a quadratic in degrees away from MBT. If octane demand (compression, boost, charge temperature, advance) exceeds the fuel, knock control retards timing. What it cannot cover becomes detonation and a reliability hit. Retarded spark dumps enthalpy into the exhaust, so EGT climbs.
- **Friction** is a mean effective pressure that rises with rpm, plus blower drive on a supercharger. An electric water pump takes a little of that back.
- **Heat.** Fuel energy splits into brake work, wall heat, and exhaust. Wall heat and friction go to coolant, oil, and a little bay radiation. Ceramic coating moves a slice of wall heat back into the exhaust stream.
- **Cooling.** The thermostat holds a floor. Above that, coolant temperature is ambient plus radiator load divided by conductance (W/K). Oil settles between the sump/cooler (air) and the galleries (coolant). A bigger radiator, ducting, or pump raises UA. A low-temp stat lowers the floor and slightly increases wall loss. The radiator cap raises boiling point (about 100°C at 0 psi, about 121°C at 15 psi for water, plus about 7°C for 50/50 glycol). Waterless coolant boils near 191°C and conducts worse. Oil that the air cooler does not take still loads the radiator through the galleries. If coolant climbs past the stat, intake heat-soak warms the charge and the point is recomputed once.
- **Reliability** drops for unresolved knock, coolant near the boil, oil past 145°C (160°C on synthetic), EGT past 900°C (980°C on Inconel valves), mean piston speed the bottom end cannot take, boost the rods cannot take, and a lean mixture under boost.

Cruise MPG is a 60 mph, 15 kW road load at 2,200 rpm with the wastegate or bypass closed, plus throttling loss when the engine is oversized for that load.

The rebuild sheet does not use cruise mpg. It reads **MPG@$**, the live mpg at the profit pedal: the throttle opening, in 5% steps, where the contract pays the most on the current build (the same `Logic.bestThrottle` search the pace tests use), looking no higher than a 50% pedal (`Logic.GATE_CAP`). The tune and parts screens show it as `mpg@$` with the pedal opening, and the parts forecast shows its change per level. That mpg follows the fuel burned to make power, so a stronger build reads lower at the same pedal, and the cap keeps nitrous and boost, which only open up late on the pedal, out of the reading. The economy parts (boot, tip, needle, cold-air inlet, cat-back, jets, AFR map) lift this number by up to +160% when all seven are at their top level, scaled per part and bounded by the class realism cap. They do not touch the paycheck. Every engine's goal sits above what a stock engine shows and below what a build with the parts and the power shows, so stock misses it and the economy parts are the way to clear it.

## The rest of the shop

Parts change the inputs above. The economy parts — intake boot, turn-down, needle, cold-air inlet, cat-back, jets, and the AFR map — raise cruise mpg by up to +10% and the profit-pedal mpg on the sheet by up to +160%. Four more open along the ladder, each behind its own study: low-tension rings (no study), a lean-burn spark table (thumper on), a cooled EGR loop (inline-three on) and cylinder deactivation (V6 on). Together they add up to another +80% at the profit pedal, and the sheet's mpg targets from the twin up were raised to keep the goal worth chasing. Each level is enough to move the number. A turbo does not add a horsepower sticker. It adds boost, backpressure, heat, and a fuel-flow demand, and the sheet moves accordingly. The contract pays for the pedal you are holding. A full-throttle pull can still knock or overheat, and that shows on the dyno sheet, but a cruise is not billed for a pull you are not making. Boost pressure itself asks for less octane than the heat off the compressor, so an intercooler is what makes the same charger livable. The price of a part is about an hour and a half of the contract that first level adds on the inline-four, at the pedal that pays. Opening tools are a few minutes. A level that adds less as it stacks does not get much more expensive. A part that does not move the contract stays a cheap tool. Chargers are a better deal on the bigger blocks, where the same boost adds more to the check than the price scale adds to the sticker.

Research spends research points along a tree (carb to TBI to port to direct injection, distributor to coil-on-plug, natural aspiration to supercharger or turbo to twins, cams to VVT to variable lift, and the cooling studies). Studies come in five price tiers, from about 25–60 points for the basics to 10,000–11,000 for torque-model control, variable lift, and a billet bottom end. A shop that studies whenever it can finishes the basics on the moped, works through the middle tiers from the thumper to the inline-three, and closes that notebook around the V6. The research pace test checks that spread.

The first pass of an opening tool is the one you feel. On the moped it more than doubles the paycheck, and later passes of the same tool add less, so a valve lap and a plug gap do not finish the engine. Filter, cold air, headers, springs, cams, and the rest of that middle band cost about a quarter less than they did. A shop that buys the part the sheet is asking for takes about fifty minutes to an hour on each engine. The opening tools, the big chargers, and the past-the-limit parts are unchanged.

Once a V8 is on the stand, a second branch opens: Past the limit. It is nitrous, a wastegate shim, a deleted rev limiter, and a dyno fuel cut, behind one study that names the risk. The parts add a lot of horsepower and multiply the dyno slip. They also pile heat into the coolant, the oil, and the exhaust, and the reliability line drops on purpose. Cruise economy is left alone, so the rebuild sheet still clears. The branch is priced so the V8 starts it and the V12 finishes it.

Any engine can let go when it is pushed past what it can live with. Temperature does most of that: coolant, oil, exhaust gas, and heat soak. A low reliability number and a floored pedal make the same heat much more likely to break something. A cool stop on a sound engine does not. The first repair on a long-block is 30% of ten minutes of ideal income at the stop that broke — the check that stop would pay with full reliability and no heat penalty. Each later failure on that block costs 1.5 times the previous one. The contract pays nothing for 40 seconds. A rebuild clears the repair ladder, so the next long-block starts at 30% again. Failures do not roll while the app is closed.

Each rebuild throws the parts away and bolts on the next engine, from a 50 cc moped single up through a thumper, a parallel twin, an inline-three, the 2.0 L inline-four, a V6, a V8, and a V12. The same step can be a race rebuild instead. A race long-block leaves the crate with more compression and a higher tach. Its sheet asks for about 42% more horsepower and about 18% more torque, and the mpg target drops to about 64% of the street number. The choice is per rebuild, so a race four can be followed by a street V6.

The race tab is a separate car. Each long-block has a light, a medium, and a heavy chassis, at published weights: a Peel P50 at 59 kg up through a Lotus Seven at 387 kg, a Mini Cooper S at 634 kg, an Alfa Giulia Sprint GTA at 745 kg, a lightweight E-Type at about 1,016 kg, a Stratos at 960 kg, a GT40 at 910 kg, a 300 SEL 6.3 at 1,780 kg, and a 917K at the 800 kg class minimum. A light car is the quick one on the strip and the weak one on the dyno. Its bay and its radiator will not take a bigger engine. A medium car is the middle, and the engine only sits in a place that car used. The Renault 5 is front-wheel drive as a road car and mid-engined, driving the rear wheels, as the Turbo. A full bay runs hot. A heavy car's bay is mostly empty around a smaller engine, so the dyno reads higher and the radiator fades less. The pace number follows the distance: an eighth listens to the launch, a half mile listens to the top end. The car list marks the quickest chassis your engine fits, and the final-drive line times a clean run in short, road, and tall. On the strip, each chassis is its own rough wireframe between a start and a finish, large enough to read the cabin. Exhaust leaves the tailpipe on the bench and trails off the tail of the car on the strip, and a spinning tire throws a cloud of smoke. The pedal can be set while the tree is still lit. The wheels turn with the road, a lime mark is the shift, and the gap to the other car is in meters. The tree is four lamps. At redline the fuel cuts: the needle hunts, the exhaust chops, and on the strip a gear held there stops pulling until you shift. The top of a dyno pull does the same. A heavy car takes a much larger engine, reads higher on the dyno, and is slower on the track. Each chassis is its own study, then three steps of period lightening that stop at a race trim. Gearbox, driveline, tires, drive type, and cooling ducts swap on top of that.

Quick race is three fields in your engine's class. The ladder is a fixed field that gets harder no matter what you brought, sixteen rungs from a moped club to a V12 national. The stake is $200 on the first long-block and $2,000 on the next, then it climbs with the shop's price scale each rebuild after (about $94,000 on the last block). A circuit entry is half the stake. Only the top five circuit finishers are paid, 3.5, 2.4, 1.8, 1.4 and 1.0 times the entry; sixth and below, and a retirement, get nothing. Circuit parts and circuit tires cost a fifth of their old list price; drag parts and tires are unchanged. It draws a random car. Win pays double the stake. Lose pays nothing. All three put you on the strip you picked: an eighth mile, a quarter, or a half. An eighth is the launch. A quarter is the gears. A half mile is the top end. The longer one pays more. The throttle is the same pedal as the bench, and it stays where you leave it. The clutch is that same plate and springs back to engaged when you let go. Feather the launch or slip the clutch, or the tires spin, and you shift the box yourself with the clutch in. The ratios and the shift time are the gearbox you fitted. Short, road, and tall final drives change how the run leaves and how it carries the top. A gearbox, a final drive, and a tire each take a study, then money. The mule starts on the 4-speed, the road gear, and bias-ply, and those stay free. A part you have already bought goes back on for nothing. The pace number is still the class of the car. The clock is the run you drove. A new game, and every rebuild, starts with enough cash for the four cheapest tools on that engine. The drawing follows the cylinder count and, from the V6 up, the vee. Studies and Engineering Knowledge stay. Knowledge still multiplies the contract and the research (`1 + 0.12 × knowledge`).

A rebuild opens when peak horsepower, peak torque, and mpg at the profit pedal all clear that engine's sheet. The targets sit where about 60% of the parts have been bought. Meeting the sheet pays one Engineering Knowledge. Horsepower and torque each add four more per time they beat their own target (mpg only has to clear), up to sixteen from one rebuild, and that knowledge raises the multiplier you keep (`1 + 0.12 × knowledge`). The rebuild tab spends it on perks that stay with the shop: breathing, research, octane, part prices, bottom-end strength, friction, bay heat, cooling headroom, spark, charger spool, dyno research, and fuel flow. Stock misses the power, torque, and mpg numbers.

The first tools on a fresh engine are cheap (a torque wrench, an oil service, a plug gap, a valve lap) so the opening minutes have something to buy. That wider shop still lands in about an hour and a half per engine.

The bench drawing follows the parts on the engine. A filter grows pleats, a cold-air snorkel reaches farther, headers replace the log manifold and get longer, the turbo and supercharger get larger, the intercooler and oil cooler gain rows, fins stick out on an air-cooled jug, and the pistons widen and thicken as the bore and compression go up. Higher levels of the same part change the picture again.

The drawing runs a real four-stroke cycle slowed down so the pistons stay visible. The speaker does not. It fires at the tach: 6,000 rpm is 50 exhaust pulses a second on a single and 200 on a four. Battery saver, on the bar under the tabs, freezes that drawing and stops the speaker. The contract keeps paying. The shop itself ticks a little slower, and the parts map only pulses when a bubble can be bought. Each cylinder fires once every two turns in its own order (1-3-4-2 on the four). The plug flashes just before top dead center, and the flame spreads and fades through the power stroke. Intake and exhaust valves open on their own strokes, driven by cam lobes you can see once a cam is fitted. Intake charge drops toward the valve, and each exhaust pulse runs out through the headers to the tip. A flywheel with a timing mark turns at the crank end, the rods blur past about 2,500 rpm, and the block rocks a little at idle. From the V6 up the drawing is a front-on vee at the block's bank angle (90° for the V8, 60° otherwise). Each pair of cylinders shares a crank pin, each piston slides along its own bank, and the rear rows recede behind the front pair. The plenum (or the blower) sits in the valley. A nitrous bottle, a red arc where the rev limiter used to be, and a red ring on an overboosted turbo show up once those parts are on the engine.

DEV MODE at the bottom of the REBUILD tab makes every part level and every study free for testing. Study gates still apply. A red DEV tag sits next to the title while it is on, and the switch is saved and survives rebuilds.

The engine is audible, and each block has its own voice. A single chugs high and long. A twin rasps. A four screams. A V8 is a low rumble with a bank lope. A V12 is a smoother, higher sheen. Bore still drops the pitch as the jug gets wider, and the note thins out as the tach nears redline. Stock exhaust is a muffled log manifold: a long, woolly boom. A turn-down adds bark, a cat-back opens the pipe, and headers bring a short metallic rasp. Long-tube headers and a cam make that rasp lope. Raising compression takes the wool out of the hit and makes the crack harder, and it is loudest once the pipe is open. The intake stays under the blast. Closing the pedal all the way stalls the crank and the sound dies with it.

The first engines come with a lower tach: 6,800 on the moped, 7,600 on the thumper, 8,200 on the twin. The longer-stroke blocks sit where the piston can live, and the V12 stock is 8,000. A rev kit, the valve springs, and a deleted limiter raise that tach. Each block has a hard stop that climbs the whole way, from 8,000 on the moped to 10,000 on the V12. Nothing goes past that stop. Past it, mean piston speed is what the bottom end has to live with.

The Tune tab is the screws: spark advance, mixture, cam center, wastegate, rev limiter, and thermostat. There is no idle-speed screw. The bottom of the pedal is a stall: the crank stops and the contract pays nothing until you crack it open. A screw stays locked until its part is on the engine. More of that part, and the parts that refine it (a mapped spark table, VVT, a boost controller), make the range wider and the clicks smaller. The position goes into the same engine model as the hardware, so the sheet and the contract move when you turn it. A rebuild throws the screws away with the parts.

Part prices on later engines include the smallest knowledge multiplier you can have when you arrive, and most of that engine's larger contract versus the moped. The paycheck's multiplier is in the price too, so it does not make the next engine a sprint. Further rebuilds after the V12 stay on that block and only add knowledge.

Offline earnings pay 80% of the contract and 20% of passive research, capped at 8 hours. The save is a JSON file in app storage and writes every few seconds.

## Tests

```bash
./gradlew testDebugUnitTest
```

The unit tests cover the Otto and boiling-point anchors, the stock band, energy balance, cam tradeoffs, radiator and intercooler temperature, spark versus EGT, fuel-flow limiting, knock on high compression, the income formula, offline pay, the save file, a scripted first-rebuild timing check, the per-engine pace of the horsepower / torque / mpg sheet, and a full shop run that buys every study and every part level. That full run is the balance and bug gate before an APK is published.
