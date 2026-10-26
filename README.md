# Revline

An offline idle shop where the contract pays from a running engine model. You buy parts, study a tech tree, and occasionally scrap the long-block for Engineering Knowledge. Nothing here phones home. There are no ads and no API keys.

The screen is pure black (`#000000`) with thin instrument lines: white structure, lime for money and horsepower, cyan for coolant and boost, amber for torque and oil, red for redline and trouble.

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

Income is not a flat number on the part. Every tick uses the current dyno sheet:

```
powerScore      = HP^1.72 * (1 + torqueLbFt / 950)
economyScore    = (MPG / 28)^0.58
durabilityScore = (reliability / 100)^1.28 * (redline / 6500)^0.24
dollars/second  = 0.0030 * powerScore * economyScore * durabilityScore * multiplier
multiplier      = 1 + 0.12 * EngineeringKnowledge
```

Horsepower, torque, cruise MPG, redline, and reliability all come from the engine model. A hot, knocking, or fuel-starved build loses money because those show up as less power or less reliability, not because the part has a hidden penalty.

Research points tick passively (`0.22 + HP/850` per second, more with the Dyno habit perk). A dyno pull adds `8 + 0.055 * HP` and then waits 35 seconds. Pulls do not happen while the app is closed.

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

Parts change the inputs above. A turbo does not add a horsepower sticker. It adds boost, backpressure, heat, and a fuel-flow demand, and the sheet moves accordingly.

Research spends research points along a tree (carb to TBI to port to direct injection, distributor to coil-on-plug, natural aspiration to supercharger or turbo to twins, cams to VVT to variable lift, and the cooling studies).

Rebuild resets money, the parts on this block, and the fuel back to 87. Studied nodes and perks stay. You need about $175,000 earned on the current block. The first rebuild is tuned to land around 30–60 minutes if you keep buying what the sheet says is worth it and pull the dyno when the rollers are free.

Offline earnings pay 80% of the contract and 50% of passive research, capped at 8 hours. The save is a JSON file in app storage and writes every few seconds.

## Tests

```bash
./gradlew testDebugUnitTest
```

The unit tests cover the Otto and boiling-point anchors, the stock band, energy balance, cam tradeoffs, radiator and intercooler temperature, spark versus EGT, fuel-flow limiting, knock on high compression, the income formula, offline pay, the save file, and a scripted first-rebuild timing check.
