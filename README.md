# Revline

An offline idle shop where the contract pays from a running engine model. You buy parts, study a tech tree, and occasionally scrap the long-block for Engineering Knowledge. Nothing here phones home. There are no ads and no API keys.

The screen is pure black (`#000000`). Instrument lines are drawn as a bright core with a soft halo: white structure, lime for money and horsepower, cyan for coolant and boost, amber for torque and oil, red for redline, heat, and a floored pedal. The parts bay is a black bubble map you drag and pinch. Two strips sit at the top. The first names the bottleneck: a head that is still soaking, a dyno note, the short number on the rebuild sheet, or the bay with the most left once the sheet is clear. Tapping it moves the map to that bay. The second names the next level for the goal you picked: horsepower, torque, cruise mpg, dollars per second, or cooling. (The cruise goal there is the road-load number. The rebuild sheet reads mpg at the profit pedal. Cooling is degrees under the hottest of the coolant, oil, and exhaust limits on a wide-open pull.) Tapping a goal on that strip switches it. Under the goals, Cheapest, Affordable, Almost affordable, and Best change which part that line names. Cheapest is the lowest price that clears the sheet number, or the cheapest step when nothing clears it alone. Affordable is the best step the cash can buy, and it says so when that cash cannot clear the number. Almost affordable is a part priced at most 20% over the cash, and it leaves out parts the cash already covers. Best is the biggest step, whatever it costs, which is what the line did before. The choice is saved with the game. An old save opens on Best. Tapping the line opens that part. A fitted part can be pulled one level at a time, and the price of that level comes back. Bubbles are grouped by what the part changes: power, economy, boost, cooling, or durability. The groups sit close together. Studying turbocharging, supercharging, or twins opens that part for purchase. Nothing bolts on by itself: a lone small turbo on a big block can make less than the same block breathing on its own, so boost is a choice you can preview first.

## Build

Java 17, Android SDK 35, minSdk 26.

```bash
export ANDROID_HOME="$HOME/Android/Sdk"   # or wherever the SDK lives
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug testDebugUnitTest
```

The sideload APK is `app/build/outputs/apk/release/app-release.apk`. Debug and release use the same key in `app/sideload.keystore`, so a later build can install over this one.

## Install on a phone

The phone package is `dist/revline.zip` in this repository (version 1.94.1). It is a zip 2.0 archive with one file, `revline.apk`. Download that zip from the repo, extract it, then tap `revline.apk` and choose Install. Each release replaces `dist/revline.zip` in the same place.

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

The race tab is a separate car. Drag and Circuit are separate pages under that tab. Drag has the distance, the strip driver, Quick, Ladder, Stake, drag tires and drag parts. Circuit has the venue, the championship, the pit wall, Run race, the result, circuit tires and circuit parts. Chassis, gearbox, final drive, driveline, layout, ducts and the hire roster sit at the bottom of both.  Each long-block has a light, a medium, and a heavy chassis, at published weights: a Peel P50 at 59 kg up through a Lotus Seven at 387 kg, a Mini Cooper S at 634 kg, an Alfa Giulia Sprint GTA at 745 kg, a lightweight E-Type at about 1,016 kg, a Stratos at 960 kg, a GT40 at 910 kg, a 300 SEL 6.3 at 1,780 kg, and a 917K at the 800 kg class minimum. A light car is the quick one on the strip and the weak one on the dyno. Its bay and its radiator will not take a bigger engine. A medium car is the middle, and the engine only sits in a place that car used. The Renault 5 is front-wheel drive as a road car and mid-engined, driving the rear wheels, as the Turbo. A full bay runs hot. A heavy car's bay is mostly empty around a smaller engine, so the dyno reads higher and the radiator fades less. The pace number follows the distance: an eighth listens to the launch, a half mile listens to the top end. The car list marks the quickest chassis your engine fits, and the final-drive line times a clean run in short, road, and tall. On the strip, each chassis is its own rough wireframe between a start and a finish, large enough to read the cabin. Exhaust leaves the tailpipe on the bench and trails off the tail of the car on the strip, and a spinning tire throws a cloud of smoke. The pedal can be set while the tree is still lit. The wheels turn with the road, a lime mark is the shift, and the gap to the other car is in meters. The tree is four lamps. At redline the fuel cuts: the needle hunts, the exhaust chops, and on the strip a gear held there stops pulling until you shift. The top of a dyno pull does the same. A heavy car takes a much larger engine, reads higher on the dyno, and is slower on the track. Each chassis is its own study, then three steps of period lightening that stop at a race trim. Gearbox, driveline, tires, drive type, and cooling ducts swap on top of that.

Quick race is three fields in your engine's class. The ladder is a fixed field that gets harder no matter what you brought, sixteen rungs from a moped club to a V12 national. The stake is $200 on the first long-block and $2,000 on the next, then it climbs with the shop's price scale each rebuild after (about $94,000 on the last block). A circuit entry is half the stake. Only the top five circuit finishers are paid, 3.5, 2.4, 1.8, 1.4 and 1.0 times the entry; sixth and below, and a retirement, get nothing. Circuit racing is now a full race weekend. Each entry runs a qualifying lap and sets the grid, then the race with a lap-by-lap log (positions, gaps, lap times, events). Eight named rival teams drive it, each with its own pace, aggression, wet skill and a tire plan. Tires are soft, medium, hard and wet. Every dry race needs one change of compound (two different dry compounds); in the wet the change is waived. Races run about 38% of an F1 distance, scaled by lap length: Monza 20 laps, Monaco 30, Silverstone 20, Spa 17, Hungaroring 27, Suzuka 20, Montreal 27 and Singapore 24 (roughly 100 to 119 km). A race is the old short race stretched, so engine hazard, mistakes, wide runs and lap noise are rescaled by the stretch and payouts, repair costs and retirement rates are the same per race as before. A stop costs a small share of a lap, scaled the same way. Before the start you pick the starting compound, the lap of the first stop, the finishing compound and a pace (push, steady or save). A two-stop option adds a second stop lap and a middle compound; the change-of-compound rule still has to be met across the stints. The pit wall shows grip at each stop and at the flag, the planned race time, and the best plan on paper with its stop laps (the best one-stop and two-stop are both listed, and which wins depends on the track's tire wear). Rivals can run two stops by style. The lap log shows the key laps (first, last, every fifth, pit stops and your events) with a Show all laps toggle. After a race is run, free or championship, the race screen moves to the championship's next round (round 1 of the new season after round 8); the circuit chips still pick any venue. Tires wear on a curve with a cliff, faster on tracks with more corners and heat, on softer compounds, at a harder pace and with a rougher driver, and not by race length: a soft tire hits the cliff in about 5 laps on the roughest circuit (Singapore) to about 10 on the kindest (Monza), mediums last 1.7 times as long and hards 2.5 times (Monza cliff laps S/M/H: 10/16/24; Monaco 6/9/13), so a soft run the whole race is under 35% by the flag and most races want two stops; the circuit tire you own sets the base grip and wear and the compound adjusts it. The rivals obey the same rule. Race days are dry, damp or wet (the forecast is shown for each circuit): slicks lose grip in the wet, and a driver's care, cornering and mistakes decide how much. A championship runs across the eight circuits in order, scoring 25, 18, 15, 12, 10, 8, 6 and 4 points for the whole field (nothing for a retirement). The race you enter on the next round counts; any other circuit is a free race. After round eight the champion gets 3.0 entries, then 2.0, 1.2, 0.8 and 0.4 for second to fifth, and a new season starts. Saves from 1.72.0 and earlier load into season one. Circuit parts and circuit tires cost a fifth of their old list price; drag parts and tires are unchanged. It draws a random car. Win pays double the stake. Lose pays nothing. All three put you on the strip you picked: an eighth mile, a quarter, or a half. An eighth is the launch. A quarter is the gears. A half mile is the top end. The longer one pays more. The throttle is the same pedal as the bench, and it stays where you leave it. The clutch is that same plate and springs back to engaged when you let go. Feather the launch or slip the clutch, or the tires spin, and you shift the box yourself with the clutch in. The ratios and the shift time are the gearbox you fitted. Short, road, and tall final drives change how the run leaves and how it carries the top. A gearbox, a final drive, and a tire each take a study, then money. The mule starts on the 4-speed, the road gear, and bias-ply, and those stay free. A part you have already bought goes back on for nothing. The pace number is still the class of the car. The clock is the run you drove. A new game, and every rebuild, starts with enough cash for the four cheapest tools on that engine. The drawing follows the cylinder count and, from the V6 up, the vee. Studies and Engineering Knowledge stay. Knowledge still multiplies the contract and the research (`1 + 0.12 × knowledge`).

A rebuild opens when peak horsepower, peak torque, and mpg at the profit pedal all clear that engine's sheet. The targets sit where about 60% of the parts have been bought. Meeting the sheet pays one Engineering Knowledge. Horsepower and torque each add four more per time they beat their own target (mpg only has to clear), up to sixteen from one rebuild, and that knowledge raises the multiplier you keep (`1 + 0.12 × knowledge`). The rebuild tab spends it on perks that stay with the shop: breathing, research, octane, part prices, bottom-end strength, friction, bay heat, cooling headroom, spark, charger spool, dyno research, and fuel flow. Stock misses the power, torque, and mpg numbers.

The first tools on a fresh engine are cheap (a torque wrench, an oil service, a plug gap, a valve lap) so the opening minutes have something to buy. That wider shop still lands in about an hour and a half per engine.

The bench drawing follows the parts on the engine. A filter grows pleats, a cold-air snorkel reaches farther, headers replace the log manifold and get longer, the turbo and supercharger get larger, the intercooler and oil cooler gain rows, fins stick out on an air-cooled jug, and the pistons widen and thicken as the bore and compression go up. Higher levels of the same part change the picture again.

The drawing runs a real four-stroke cycle slowed down so the pistons stay visible. The speaker does not. It fires at the tach: 6,000 rpm is 50 exhaust pulses a second on a single and 200 on a four. Battery saver, on the bar under the tabs, freezes that drawing and stops the speaker. The contract keeps paying. The shop itself ticks a little slower, and the parts map only pulses when a bubble can be bought. Each cylinder fires once every two turns in its own order (1-3-4-2 on the four). The plug flashes just before top dead center, and the flame spreads and fades through the power stroke. Intake and exhaust valves open on their own strokes, driven by cam lobes you can see once a cam is fitted. Intake charge drops toward the valve, and each exhaust pulse runs out through the headers to the tip. A flywheel with a timing mark turns at the crank end, the rods blur past about 2,500 rpm, and the block rocks a little at idle. From the V6 up the drawing is a front-on vee at the block's bank angle (90° for the V8, 60° otherwise). Each pair of cylinders shares a crank pin, each piston slides along its own bank, and the rear rows recede behind the front pair. The plenum (or the blower) sits in the valley. A nitrous bottle, a red arc where the rev limiter used to be, and a red ring on an overboosted turbo show up once those parts are on the engine.

DEV MODE at the bottom of the REBUILD tab makes every part level and every study free for testing. Study gates still apply. A red DEV tag sits next to the title while it is on, and the switch is saved and survives rebuilds. The switch stays on that tab, next to the rebuilds it is for.

A gear on the bar opens Settings. Restart game is there, not under the dev switch. It still asks first. Cancel does nothing. The red confirm has to be held down; a tap does not wipe the save. Reset writes a brand-new game over the save and shows it, with no relaunch. The previous file stays until that write is in place, so a restart cannot leave a half-written save as the only copy. A save that will not open is kept, and the game falls back to the last good copy or to whatever prefix of the file still reads.

DRAG PARTS (1.84.0) sit under a DRAG PARTS heading on the Drag page, and each is a study then a purchase like the rest. They fit the strip only. The circuit and the rivals never see them. Launch control (3 levels) holds the tires near the edge of grip and trims a spin, so a flat-footed launch does not light them up. A transbrake holds the car on the button and lets it go with a hit sized to the tires. Wheelie bars raise the point where the nose comes up (a wheelie wastes drive), at the cost of weight and a little drag. A light flywheel revs faster for a lower ET and a higher trap speed, but bogs the launch. A short shifter (2 levels) saves shift time, though a clumsy hand misses more gates. A line lock heats sticky tires for more grip. A drag nitrous kit (3 levels) adds a shot for a limited bottle, and when it opens decides what it is worth. Drag radials and drag slicks already existed as tires, so they are not repeated. The engine's own nitrous upgrade is separate from the strip kit.

The TUNE tab has an ENGINE page and a new DRAG page with a clean-pass forecast. Each knob has a slider, - and + buttons and a note on what it gives and costs: tire pressure (launch grip against rolling drag), final drive (pull against trap speed), front and rear stiffness (rear grip against nose lift and squat), ride height (grip against drag), and, once their part is fitted, launch rpm (transbrake), shift rpm (short shifter) and nitrous point. The settings are saved with the game (old saves open on the defaults) and are not reset by a rebuild.

SHIFT POINT NEVER REACHED (1.86.0): with a Short shifter and the Shift rpm knob turned up, the driver could aim at a shift rpm above the highest the needle reads on the limiter (redline minus 20) and never change up. The driver now never aims above that, so every setting of the knob shifts through the gears. Drivers who used to stick on the limiter now shift, which lifts every driver and every human a few points of win rate on the revvy builds. Ines (Rev hound) lost the mid-ladder sleeper builds she used to win only because the others stuck, so her dyno knowledge goes from 76 to 90 to keep her the right hire somewhere.

RACING LINE (1.89.0): circuit cars drive a smoothed racing line (inside at the apex) and only step a lane aside while alongside another car, then ease back; sides lock until clear, so nothing wiggles. Follow mode centers on your car only. 16x and 32x replay chips. Kerbs cover only the apex and exit and pull in on tight hairpins.

PAR FIT (1.88.2): the field par clamp now measures the same lap the circuit screen and the race use (your driver and orders), so the shown fit is always within ±10.0%.

## 1.94.1

A car only races when the engine on the bench fits its bay. The bay is the published displacement on the car, and the shop mule takes every engine. Quick, ladder, stake, and a circuit all use the same car. When the engine does not fit the one you picked, they line the shop mule up instead and the race page names it: Shop mule (engine doesn't fit the car you picked). The car you picked stays selected. It comes back on its own once an engine that fits is on the stand. An old save that had a car the engine will not fit keeps that car and races the mule. Nothing is refunded.

## 1.94.0

A race uses the engine on the dyno. Drag and circuit both take that engine's torque curve, redline, displacement, heat, and reliability, with the parts and the fuel that are fitted. The chassis, the gearbox, the tires, the drag and circuit parts, and the driver stay what they were. The strip already read a peak under 8 lb-ft and 8 hp as 8, and it still does: the curve keeps the dyno shape and only comes up to that floor, so a 2 hp single can finish the quarter. Above the floor the curve is the dyno curve. On a stock inline-four the quarter moved from 17.08 s to 16.25 s on the old 640 kg mule, and the mule is now 620 kg, so that same pass is 16.12 s. The first ladder rung stays 18.63 s and the club field stays 17.10 s. A stock V8 moved from 12.52 s to 12.60 s on the 640 kg mule, and reads 12.58 s at 620 kg. The other cars stay on the old curve, so a ladder rung is still that rung and a quick field is still that class. The speaker on the strip uses that same engine's voice.

The shop mule is 620 kg, down from 640. It is still the bedstead you start on, not the car to keep. On a stock inline-four the clean quarter goes from 16.25 s to 16.12 s and a Monza lap from 174.4 s to 173.8 s. The Lotus Seven in that class is still quicker, at 14.32 s and 162.2 s. A stock V8 goes from 12.60 s to 12.58 s and from 157.4 s to 156.9 s, and the GT40 still matches the quarter (12.57 s) and is quicker at Monza (155.0 s). Every car you can buy is 35% off its old sticker, rounded to the nearest $10. The mule stays free. A car you already own stays owned, and nothing is refunded. Studies are the same.

| Car | Was | Now |
| --- | ---: | ---: |
| Peel P50 | 140 | 90 |
| BMW Isetta 250 | 380 | 250 |
| Citroen 2CV | 720 | 470 |
| Messerschmitt KR200 | 460 | 300 |
| Fiat 500 | 820 | 530 |
| Saab 96 | 1,500 | 980 |
| Austin-Healey Sprite | 980 | 640 |
| Mini Cooper S | 900 | 580 |
| Volvo Amazon | 2,100 | 1,360 |
| Lotus Elan | 1,800 | 1,170 |
| Alfa Giulia Sprint GTA | 2,400 | 1,560 |
| BMW 2002 | 3,200 | 2,080 |
| Lotus Seven | 480 | 310 |
| Renault 5 | 2,600 | 1,690 |
| Jaguar E-Type lightweight | 4,200 | 2,730 |
| Lancia Stratos | 6,400 | 4,160 |
| Porsche 911 | 8,200 | 5,330 |
| Chevrolet Corvette | 9,800 | 6,370 |
| Ford GT40 | 15,000 | 9,750 |
| Shelby Cobra 289 | 6,400 | 4,160 |
| Mercedes 300 SEL 6.3 | 18,000 | 11,700 |
| Porsche 917K | 24,000 | 15,600 |
| Ferrari Daytona competizione | 20,000 | 13,000 |
| Jensen Interceptor | 22,000 | 14,300 |
| Mazda RX-7 FD | 24,000 | 15,600 |
| Mazda Cosmo Sport | 22,000 | 14,300 |
| Rotary luxury coupe | 26,000 | 16,900 |

The bay drawing heats up. The head, the manifold, and the exhaust shift from their instrument colors toward amber and then red as coolant, oil, exhaust gas, or soak climb, and they cool back when those fall. The wires stay single neon lines on black. Battery saver still freezes the drawing.

The same drawing sits beside the strip on a wide screen and above it on a phone. Before the green it turns at idle. During the pass it follows the car's rpm, and the glow follows that pull.

## 1.93.17

The next-part line can chase cooling. The number is how many degrees the hottest of the three limits still has in hand on a wide-open pull: coolant against boiling point minus 12°C, oil against 145°C (160°C on synthetic), exhaust against 900°C (980°C with inconel valves). At zero the engine is on the limit. Under it, the line says Clear. Cheapest, Affordable, Almost affordable, and Best rank that headroom the same way they rank horsepower. An old save keeps the goal it already had. Each part card also shows the wide-open change in coolant, oil, and exhaust temperature. A step under half a degree reads as 0. Cooler is lime, hotter is red. The parts themselves are unchanged.

## 1.93.16

Opening the app on an unfolded fold no longer closes it. The wide bay asked the engine drawing for a depth lip whose floor sat above its cap, and that throw happened on the first frame, after the splash and a moment of audio. The lip now stops at the cap. A crash that still gets out is written to `revline_crash.txt` and shown the next time the app opens. Pay, the sheet, and the speaker are unchanged.

## 1.93.15

Restart writes a complete new game before it forgets the old one, and opening the app no longer dies on a save that was cut off mid-write. A damaged file is kept. The last good copy is tried next, then whatever prefix of the file still reads. Pay, the sheet, and the speaker are unchanged.

## 1.93.14

The next-part line can spend four ways. Cheapest names the lowest price that clears the sheet number. Affordable names the best step the cash on hand can buy, and says when that still falls short. Almost affordable names a part within 20% over the cash, and skips parts that are already affordable. Best names the biggest step and shows the price, which is what the line did before. The choice is saved. An old save opens on Best. Pay, the sheet, and the speaker are unchanged.

## 1.93.13

A turbo is one housing ring. The offset back rim and the scroll that sat beside it are gone, on a piston engine and on a rotary. The face of that ring is lit toward the top-left and darker toward the bottom-right, and the pool under the engine is unchanged. Pulleys and the flywheel use the same single disk, so they no longer carry a second circle. The rotor stack is unchanged. Saves, pay, and the speaker are unchanged.

## 1.93.12

A multi-rotor engine draws its housings in a stack, each one up and to the left of the one in front. The near housing stays fully lit. The ones behind fade. A 13B and a Renesis are two deep, a 20B is three, a 26B is four. The turbo hangs on the near housing and the blower sits on the far one's intake side. One rotor is drawn where it was. The empty corners stay empty. Saves, pay, and the speaker are unchanged.

## 1.93.11

The engine bay keeps the neon wire look and gains a little depth. Blocks, heads, the radiator, plenums and the sump grow a lit top and a darker side. Pulleys and turbos pick up a back rim, and a turbo grows a scroll. Rotary housings get a far wall, and each rotor face fills with the chamber color it already used. A soft pool sits under the casting. The pulley and the turbo drift a fraction of a stroke against the block. The empty corners of a rotary stay empty. Nothing about the pass, the lap, the goals or a save changes.

## 1.93.10

Drag and circuit races play the car's rpm through the same speaker as the dyno. The note eases onto the engine so a shift, a launch, and a lift are audible without a click. At 8x, 16x, and 32x the film is too fast for that, so the speaker stays on the dyno note. Pause, the flag, and leaving the race stop the race note. Battery saver still mutes the speaker. The pass and the lap are unchanged.

## 1.93.9

The drag strip shows a nitrous bottle. With no drag nitrous kit it stays grey. Fitted, it reads ARMED and full at the line. A new pass fills it. While the shot is open the lamp reads ACTIVE and the fill drops with the seconds the strip already spends. EMPTY is a dry bottle. The circuit page does not show it. The pass itself is unchanged.

## 1.93.8

Profit-pedal mpg goals are set from one rule in `MpgGoals`. The reference build is half of each power and boost part, never the top level, and a quarter of each economy part, on premium, with the studies that ladder step has open. Nitrous, overboost, the limiter and a second charger stay off. A goal's difficulty is `goal / what that build already shows`. Horsepower and torque set the fraction, and the mpg goal is that same fraction of the reference mpg, floored to a tenth and never above the mpg the build shows. The single rotor is also capped at 57. Where a turbo drops the profit-pedal reading under stock, stock already clears mpg and horsepower or torque still gates the rebuild.

| Engine | Was | Now |
| --- | --- | --- |
| 50cc moped | 104.7 | 104.8 |
| 250cc thumper | 74.0 | 54.4 |
| 500cc twin | 55.0 | 32.1 |
| 1.0L triple | 36.0 | 22.3 |
| 2.0L inline-four | 22.5 | 8.6 |
| 3.5L V6 | 12.0 | 6.9 |
| 5.0L V8 | 8.7 | 6.4 |
| 6.5L V12 | 6.4 | 7.6 |
| 1.3L 13B | 21.0 | 14.0 |
| 0.6L single-rotor | 57.0 | 27.2 |
| Renesis | 22.0 | 16.6 |
| 20B | 16.0 | 12.8 |
| 26B | 14.0 | 8.0 |

## 1.93.7

The single-rotor profit-pedal mpg goal is 57. It was 64.5. A turbo, intake, exhaust, intercooler and the other power parts, left on with the economy parts, read 57.5. Stock is 63.3, because that boosted pedal drinks more than the stock engine, so the mpg line is already met before any parts go on. Horsepower is not. The other engines' goals already sit under the same kind of build.

## 1.93.6

The single-rotor profit-pedal mpg goal is 64.5. It was 70, which sat above what the economy parts can show once a turbo is fitted. Stock still misses it.

## 1.93.5

The rotary bay no longer draws the loose corner brackets or the short dash in the corner. A turbo or supercharger is drawn on the housing when one is fitted.

## 1.93.4

A turbo fitted to the single-rotor is drawn on the exhaust side of the housing. The other rotaries, the contract, and saves are unchanged.

## 1.93.3

The single-rotor's contract uses the thumper pay scale, so a stock best pedal pays about $1.64/s instead of $0.10/s. The 26B rebuild sheet is 460 hp and 300 lb-ft, so its club Quick field matches the power it makes and a stock 26B can win it.

## 1.93.2

The Race tab is two pages, Drag and Circuit. The strip, the replay, payouts, and saves are unchanged.

## 1.93.1

Circuit maps are drawn wider, and stretches that would paint on top of each other are eased apart on the picture only. The measured lap, Suzuka's bridge, the race result and saves are unchanged.

## 1.93.0

Engine voices are data-driven from layout, displacement, induction, and family: rotaries brap at rotor×shaft firing with lumpy idle and decel spit; I4/V6/V8/thumper stay distinct; turbo blow-off and supercharger whine read clearer.

## 1.92.4

Single-rotor weakened and moved to the thumper step so its stock power sits with the other step-1 engines; rotary order still follows relative power.

## 1.92.3

Rotary alternatives re-slotted by stock power next to the closest piston: single-rotor at the twin, Renesis and classic 13B at the inline-four, 20B and 26B at the V6. Ladder top is the V12 again.

## 1.92.2

Rotary alternatives at ladder steps (single-rotor at the start, Renesis at the inline-four, 20B at the V8, 26B at the V12; classic 13B stays on top). Pick them on Rebuild. Wheelspin dash lamp now follows per-step tire slip for the whole pass.

## 1.92.1

Rotary bay redrawn from real Wankel geometry: true epitrochoid housing (R/e ≈ 7), Reuleaux-like rotor with recesses, eccentric 1/3 gear train, side ports, leading/trailing plugs, apex seals that track the bore. Frame strip with Wikipedia diagram reference at `circuit-gfx/rotary-anim-v2.png`.

## 1.92.0

Rotary (Wankel) long-block after the V12: its own family filter, apex/port/rotor parts, and a triangular-rotor bench animation. Engine weight is first-class — family/tier bases with part deltas — feeding car mass, front bias, drag ET and circuit lap/wear, with stock piston bases calibrated so existing saves keep nearly the same mass.

## 1.91.0

Drag grip and tire mu lowered so wheelspin and launch management matter more; driver skill ET spread on the strip is about doubled. Race result board shows player and opponent reaction times after the tree (player lane even with a hired driver). Ladder race 2 no longer uses the Peel P50 toy-light national opponent that spiked pace off the curve; ladder paces are monotonic and smooth.

## 1.90.0

Playthrough rebalance for racing and dyno: cold-air and FI spend share pulled down, low-tension rings actually cut friction, timing/ignition and intercooler do more per level, economy parts and early tools retuned, club ghosts and chassis parts nudged. Engine bay animation gets piston rings, a wrist pin, richer burn glow, and a serpentine belt — same neon wire style.

FORCED INDUCTION (1.88.1): a turbo or supercharger no longer loses power on low-octane fuel without an intercooler. The ECU now backs the boost off until knock it cannot pull timing for is gone, so unsupported boost is limited toward the naturally aspirated figure instead of becoming detonation, and supported boost gains clearly.

BALANCE (1.88.0): the circuit and drag parts are repriced so each costs about the same per point of lap (or quarter-mile) gain within its level: the seat swap and the weight line cost more, the wing, the alignment-free steering rack and the 50 shot cost less. Race rebuild targets are about half as far above street: x1.21 power and x1.09 torque, down from x1.42 and x1.18. A race rebuild costs research, three hours of that block's income, 1.5x more for each race rebuild in a row, and is blocked until you have it. The circuit field's par car is held within 10% of your car's speed on the planned lap, so a weak car gets an easier field and a strong one a harder field.

GRIP CLIFF (1.87.0): below 35% wear a tire now falls off a cliff. The grip the car feels keeps dropping about three times as steeply as before and bottoms at half grip on a bare tire, never zero. About 7% a lap slower at 20% wear and about 22% at 0%, so the strategy sheet and the rival AI pit before it.

REACTION (1.87.0): drivers have a Reaction stat. It sets the launch delay on the strip (with per-run noise) and the standing-start delay on the grid. It shows on the driver card and the hire screen. Old saves read it from the roster, and a hand-built driver defaults to 50.

TIRE WEAR PAST 35% (1.85.1): the circuit strategy sheet no longer stops a tire at 35%. The wear it shows keeps falling toward 0% on a long stint. Only the grip the car feels keeps its floor, so lap times and results are unchanged.

GRIP THE WHOLE WAY (1.85.0): on the strip the tires are checked at every step, not only at the launch. The most push the driven tires can hold is the tire's grip times the load on them: the car's weight on the driven wheels, plus the weight the push itself moves onto them (centre-of-gravity height over wheelbase, set by the chassis and the front stiffness and ride height knobs), plus aero downforce at speed, and a little more grip as the tire warms and as the car goes faster. When the engine asks for more than that, in any gear, the tires spin: the drive is cut to the sliding grip, the rpm flares and the slip shows. That is also how a high-power car loses time in second, after a shift, or when nitrous opens at a low speed. Tire pressure, front and rear stiffness, ride height, line lock, wheelie bars, shift rpm, final drive and the nitrous point all act through this one model, so their effect shows up the whole run. A clean pass and the rival drivers feather to what the tires hold; a poor hand stays flat on it and spins. Typical ETs and trap speeds are close to 1.84.0 (most move by under 0.02 s); the circuit does not use the model.

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

The drag strip is drawn in code, with no art assets: `DragLooks.kt` holds one silhouette per chassis, `DragCars.kt` paints it (gradients, glass, wing, driver), `DragWheels.kt` draws slick and skinny tires with spoked rims that turn with the road covered, and `DragTrack.kt` bakes the night scenery (sky, grandstand, wall, asphalt) into bitmaps once per size and draws the moving parts over it. The screenshot tests (`DragCarsShotTest`, `DragSceneShotTest`) render the same drawing code through Robolectric and write PNGs to `/workspace/drag-gfx/`, or to the folder in `DRAG_GFX_DIR`.

Circuit races are drawn the same way, from above. `CircuitData.kt` holds the real centerline of each of the eight circuits (Monza, Monaco, Silverstone, Spa-Francorchamps, Hungaroring, Suzuka, Montreal, Marina Bay) from the open [bacinger/f1-circuits](https://github.com/bacinger/f1-circuits) data (MIT licence, see `NOTICE.md`), projected to local metres, resampled every 8 m, lightly smoothed and packed as one string per circuit. `CircuitLayouts.kt` turns that into a closed loop at true scale and north-up, so the shape is the real one and the computed lap length is within 2% of the official length (a test asserts it). Direction of travel, the start/finish line, the side of the pit lane and each corner's side and place come from the real track and the Gridpedia turn maps; the corner counts are the official ones and match the circuit data, so tire wear is unchanged. Suzuka's crossover is a bridge. Silverstone's start line is slid 150 m further along the Hamilton Straight than the data has it, and the grid (five rows of two, room for the whole field) follows the road back through Club with each box turned to the local tangent. The road is drawn wider than the real one (about 23 to 75 m instead of 10 to 15 m) so two cars fit side by side and the map reads on a phone. Where two parts of the lap would otherwise touch, the drawing eases them apart; the measured centreline, Suzuka's bridge and the race result stay put, and a test caps how much of the lap may still run close. `CircuitCars.kt` turns each `DragLooks` side silhouette into a top-down plan (body width from a per-chassis ratio, nose and tail shaped by how blunt the silhouette is, glass, stripes, wing, wheels with rims). `CircuitTrack.kt` bakes the night scenery once per size: grass and floodlights, runoff, a pit lane, a stand, asphalt with grain and two worn racing lines, white edge lines, red and white kerbs on the inside of each corner, grid boxes and the checkered line. The road is wide enough for two cars side by side. `CircuitReplay.kt` plays a finished race back from the result with the sim untouched: each car's lap-end times are rebuilt from the log's gaps to the leader, progress is interpolated inside each lap, and the whole field runs along the centerline: you and every rival, each its own car from its own grid slot and in its own livery, taking alternate lanes when they bunch. Lanes are sticky: a car keeps its lane while it is alongside another, only the car behind moves over when two would share a lane, and a car that has been clear for a while drifts back toward the middle slowly; every sideways move is a glide, so cars running level do not swap sides. The screenshot tests (`CircuitTrackShotTest`) write PNGs to `/workspace/circuit-gfx/`, or to the folder in `CIRCUIT_GFX_DIR`.

Run race opens the race on its own full screen: the track map, the tachometer and speedometer, a Pause/Play button next to Replay and Skip, a Follow camera that zooms in on your car, and a speed control (0.5x, 1x, 2x, 4x and 8x). 1x is real car pace, so a lap takes its sim lap time in real seconds. Under the dials a live speed trace (mph against distance) draws as the lap is run and wipes at the line, with the followed rival's lap faint behind yours. The Follow camera stays on your car. The status line shows your place in the field. At the flag the screen shows every car's best-lap speed trace (mph against distance) in the car's livery, with yours thick and the rest thin and faded, and a legend of lap times, quickest first. The race screen underneath never draws the map; it keeps a Watch the race button and the best-lap chart. The speed and the camera choice survive a screen rotation. The screen never scrolls: on a wide or near-square display, or the unfolded Fold held upright (width at least three quarters of the height), the map sits on the left with the dials, the live trace and the controls on the right, and on a tall narrow one they stack, each part taking a weighted share of the height, with the best-lap chart taking the live trace's slot at the flag.

The circuit replay now runs on a speed trace. Each car's lap is solved over the real centerline of the track (corner radii in true metres) with the sim's own forward/backward solver: the tires set the corner speed (v = sqrt(mu g R), with compound, wear on that lap, weather, tire parts and driver skill), the engine sets the pull out of corners and the top speed (power, gearing, drag, shift points), and the brakes set where the car lifts (better brakes, a later braking point). The trace is stretched in time so its lap lasts exactly as long as the sim's lap, so results and the economy are unchanged; only the shape of the lap is the car's own. Cars are placed by integrating the trace, so they brake later, carry more speed through a corner or pull away on a straight according to their stats, and overtakes happen where the traces differ. Every rival is synthesised from the par car on his own tires and driving style, running to the lap times the race log gives for him. The first lap is a standing start. A car that retires stops where it stopped.

Under the chips the race dialog shows the conditions on the lap in progress, updated as the replay plays, and only what the sim models: the track state (dry, damp or wet) with the forecast for the laps to come, the tire compound with its grip percent (worn lap by lap, read between the start and end of the lap), the laps on the set, the engine power and its failure risk per lap. The sim has no fuel and no track temperature, so none is shown, and the weather is fixed at the start, so the forecast is exact and says no change. The Pit strategy button pauses the film and opens the pit wall: move a stop lap, change the compound fitted at a stop, add or drop a stop, or call Box this lap. A call runs your car again from the lap in progress with the same seed (`Circuits.revise`), so the laps before it, the rivals, the grid and the qualifying stay as shown; the result, prize, season points and repair bill are repriced from the new run (`Season.resettle`). Stops already made, the start tire and the pace cannot change, a plan has to pass the same compound rule as at the start, and nothing can be called once the flag is down.

Under the track is a dash for your car: a tachometer (rpm from speed, the gear ratios and the car's shift logic, with the redline marked), a speedometer in mph (the drag screen's unit), the gear and a brake lamp. The speed shown is the trace's speed over the time stretch, so it is a physical speed to within a few per cent (`CircuitRadii.LINE_GAIN` and `HALF_MIN` tune how tight the centerline is read).

## Credits

Circuit centrelines: bacinger/f1-circuits by Tomislav Bacinger, MIT licence (full text in `NOTICE.md`). Turn numbers, turn sides and the start/finish line were checked against Gridpedia's circuit maps. The race sim, the economy and the numbers are unchanged by the map.

