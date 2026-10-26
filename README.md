# Revline

An offline idle shop where the contract pays from a running engine model. You buy parts, study a tech tree, and occasionally scrap the long-block for Engineering Knowledge. Nothing here phones home. There are no ads and no API keys.

The screen is pure black (`#000000`). Instrument lines are drawn as a bright core with a soft halo: white structure, lime for money and horsepower, cyan for coolant and boost, amber for torque and oil, red for redline, heat, and a floored pedal. The parts bay is a black bubble map you drag and pinch. Bubbles are grouped by what the part changes: power, economy, boost, cooling, or durability. The groups sit close together. Studying turbocharging, supercharging, or twins bolts the first unit on, so boost shows up on the gauge once the pedal is in the spool.

## Build

Java 17, Android SDK 35, minSdk 26.

```bash
export ANDROID_HOME="$HOME/Android/Sdk"   # or wherever the SDK lives
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug testDebugUnitTest
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Install on a phone

`adb sideload` is a recovery command. It will not install this app. With USB debugging on (the Fold included):

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A copy is also written to `dist/revline-debug.apk` after a local release build from this repo's scripts, and the same file is the CI artifact `revline-debug-apk`.

## What the contract pays

Income is the pedal you are holding, after the intake has heat-soaked. The stop sets rpm and throttle-plate load together. Part throttle is vacuum, a leaner mixture, less boost, and less heat in the runners. Wide open is a rich pull, and the head soaks the charge: coolant and oil warm the manifold, a small radiator lets the head run away from the coolant, and that soak costs density and octane. A cold-air inlet, radiator, oil cooler, intercooler, or water-meth spray cuts the soak, so those parts move the pedal opening that pays.

```
powerScore      = HP^1.62 * (1 + torqueLbFt / 950)
economyScore    = (mpg / 28)^0.72 * (3.40 / dollarsPerGallon)^0.42
durabilityScore = (reliability/100 * condition)^1.15
dollars/second  = 0.035 * powerScore * economyScore * durabilityScore * multiplier
multiplier      = 1 + 0.12 * EngineeringKnowledge
```

Horsepower, torque, MPG, and soak are the operating point at this pedal, not the peak dyno stickers. MPG is that fuel flow scored at a steady 60 mph. `condition` falls when the pull is hot, knocking, fuel-limited, or heat-soaked past about 7°C.

Pump 87 is $3.40/gal, 93 is $4.50, E85 is $4.10, and race fuel is $15. The price exponent is mild. The grade earns its price as burn speed and timing: each 6 octane over 87 is about 9% more of the fuel's energy and about 3.7° of timing, until knock. On a mild engine, 93 lands near 87. On a hot or boosted engine, 87 gives the timing back and 93 or race pulls ahead. E85 still drinks more gallons. Flooring a stock long-block is not the contract. The best stop moves up with cooling, cam, boost, and octane.

A locked part on the bubble map names the upgrade you still have to buy, or the study if that is the only gate, instead of showing a price. Lines stay inside one bay.

The throttle you set is saved. Offline pay uses that same stop, at 80% of the contract and 50% of passive research, capped at 8 hours. A rebuild puts the pedal back to a light opening so the next long-block gets its own pull.

Research points still tick from peak horsepower (`0.22 + HP/850` per second, more with the Dyno habit perk). A dyno pull sweeps the tach on its own, adds `8 + 0.055 * HP`, and then waits 35 seconds. The pedal is locked while the rollers are moving. Pulls do not happen while the app is closed.

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

## The rest of the shop

Parts change the inputs above. A turbo does not add a horsepower sticker. It adds boost, backpressure, heat, and a fuel-flow demand, and the sheet moves accordingly. The price of a part is about twelve minutes of the contract that first level adds on a cooled street car, at the pedal that pays. A level that stops adding revenue gets cheaper. A level that adds more gets dearer. A part that does not move the contract stays a cheap tool.

Research spends research points along a tree (carb to TBI to port to direct injection, distributor to coil-on-plug, natural aspiration to supercharger or turbo to twins, cams to VVT to variable lift, and the cooling studies).

Each rebuild throws the parts away and bolts on the next engine, from a 50 cc moped single up through a thumper, a parallel twin, an inline-three, the 2.0 L inline-four, a V6, a V8, and a V12. The drawing follows the cylinder count and, from the V6 up, the vee. Studies and Engineering Knowledge stay. Knowledge still multiplies the contract and the research (`1 + 0.12 × knowledge`).

A rebuild opens when peak horsepower, peak torque, and cruise mpg all clear that engine's sheet. The targets sit where about 60% of the parts have been bought, so a short bolt-on list is not enough and a finished engine still is. Meeting it pays one Engineering Knowledge. Each stat adds four more per time it beats its own target, up to sixteen from one rebuild, and that knowledge raises the multiplier you keep (`1 + 0.12 × knowledge`). Stock misses the power and torque numbers.

The first tools on a fresh engine are cheap (a torque wrench, an oil service, a plug gap, a valve lap) so the opening minutes have something to buy. That wider shop still lands in about one to two hours per engine.

Part prices on later engines include the smallest knowledge multiplier you can have when you arrive, and most of that engine's larger contract versus the moped. The paycheck's multiplier is in the price too, so it does not make the next engine a sprint. Each engine's bolt-on shop takes a little less time than the last: a bit over two hours on the moped, down to a bit over an hour on the V12. Further rebuilds after the V12 stay on that block and only add knowledge.

Offline earnings pay 80% of the contract and 50% of passive research, capped at 8 hours. The save is a JSON file in app storage and writes every few seconds.

## Tests

```bash
./gradlew testDebugUnitTest
```

The unit tests cover the Otto and boiling-point anchors, the stock band, energy balance, cam tradeoffs, radiator and intercooler temperature, spark versus EGT, fuel-flow limiting, knock on high compression, the income formula, offline pay, the save file, a scripted first-rebuild timing check, the per-engine pace of the horsepower / torque / mpg sheet, and a full shop run that buys every study and every part level. That full run is the balance and bug gate before an APK is published.
