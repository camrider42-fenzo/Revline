# Race pages implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the Race tab into Drag and Circuit pages with shared car and hire controls at the bottom of both, then ship that as signed 1.93.2 (versionCode 124).

**Architecture:** `RaceScreen` keeps one column per page. The open page, both scroll offsets, and the circuit venue live in `RaceScreen` so they survive a page switch and rotation. Circuit pit choices, the local driver pick, and the replay arm live in `CircuitSection` under `rememberSaveableStateHolder`. A `circuitOutcome` that arrives while Drag is showing is handled by an effect in `RaceScreen`, which stays composed. `GameViewModel`, `RaceMeet`, `CircuitRaceScreen`, and the map geometry are not edited.

**Tech Stack:** Kotlin, Jetpack Compose, Robolectric 4.14, `androidx.compose.ui:ui-test-junit4`, Gradle `testDebugUnitTest`, `assembleRelease`, `scripts/pack-release.sh`.

## Global Constraints

- Race stays one bottom-bar tab: BENCH, PARTS, TUNE, STUDY, RACE, REBUILD. The app still starts on BENCH.
- DRAG | CIRCUIT uses `LineButton` the way TUNE uses ENGINE | DRAG: the open page is the olive disabled button, the other is the lime button.
- Drag order: header, `raceNote`, distance, strip driver, Quick, Ladder, Stake, drag tires, drag parts, shared block.
- Circuit order: header, `raceNote`, current circuit block, circuit tires, circuit parts, shared block.
- Shared block order: chassis, gearbox, final drive, driveline, layout, ducts, hire roster.
- `UiModel.raceNote` stays one string. Do not add a per-mode note or change when the view model sets it.
- Circuit result card stays `circuitOutcome`. Replay stays `CircuitRaceScreen`. Drag meet stays `RaceMeet` over the scaffold.
- Page, scroll offsets, and circuit UI state survive Drag ↔ Circuit and rotation. Leaving Race drops them. They are not written to `GameState`.
- A new outcome moves the venue to `Season.next` once, including while Drag is showing, and arms one replay. It does not call `runCircuit` or `reviseCircuit` again.
- No racing, economy, save schema, or 1.93.1 map changes.
- Setup pages stay one column at 690×830 dp (18:21.6). Replay wide layout stays width ≥ 0.75 × height.
- Cameron authorized the package this spec deferred: versionName `1.93.2`, versionCode `124`, existing `app/sideload.keystore` (SHA256 prefix `8f332a4c`). No new key.
- PR base is `cursor/circuit-display-width-72d3` (PR 23, unmerged). Do not target `main`.

---

### Task 1: Mode isolation

**Files:**
- Test: `app/src/test/java/com/revline/idle/ui/RacePagesTest.kt`
- Modify: `app/src/main/java/com/revline/idle/ui/RaceScreen.kt`

**Interfaces:**
- Consumes: `RaceScreen(ui, onStudy, onChassis, onPart, onSlots, onFinal, onLength, onQuick, onLadder, onStake, onHire, onPick, onCircuit, onRevise)`
- Produces: initial page Drag; Circuit page reached by the exact text button `CIRCUIT`

- [ ] **Step 1: Write the failing test**

```kotlin
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w690dp-h830dp-xhdpi")
class RacePagesTest {
    @get:Rule val rule = createComposeRule()

    @Test fun dragAndCircuitShowOnlyTheirOwnControls() {
        rule.setContent { RaceScreen(ui(), {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, { _, _, _ -> }, { _, _ -> }) }
        rule.onNodeWithText("DISTANCE").assertIsDisplayed()
        rule.onNodeWithText("DRAG TIRES").assertIsDisplayed()
        rule.onNodeWithText("Run race").assertDoesNotExist()
        rule.onNodeWithText("CIRCUIT TIRES").assertDoesNotExist()
        rule.onNodeWithText("CIRCUIT", substring = false).assertIsEnabled().performClick()
        rule.onNodeWithText("Run race").assertIsDisplayed()
        rule.onNodeWithText("CIRCUIT TIRES").assertExists()
        rule.onNodeWithText("DISTANCE").assertDoesNotExist()
        rule.onNodeWithText("DRAG TIRES").assertDoesNotExist()
        rule.onNodeWithText("The till is short.").assertIsDisplayed()
        rule.onNodeWithText("on the Drag page").assertExists()
        rule.onNodeWithText("DRAG", substring = false).performClick()
        rule.onNodeWithText("The till is short.").assertIsDisplayed()
        rule.onNodeWithText("on the Circuit page").assertExists()
    }
}
```

`ui()` is `UiModel(Logic.newGame(), Logic.sheet(Logic.newGame()), 800.0, DynoPhase.IDLE, null, raceNote = "The till is short.")`. Use `createComposeRule`, imports from `androidx.compose.ui.test`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.revline.idle.ui.RacePagesTest.dragAndCircuitShowOnlyTheirOwnControls`

Expected: FAIL because `Run race` exists on the single page.

- [ ] **Step 3: Split the pages**

In `RaceScreen`, `var page by rememberSaveable { mutableStateOf("DRAG") }`. Header row:

```kotlin
Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    listOf("DRAG", "CIRCUIT").forEach { tab ->
        LineButton(tab, page != tab) { page = tab }
    }
}
```

Drag column keeps distance through Stake, then drag tires and drag parts. Circuit column keeps `CircuitSection`, then circuit tires and circuit parts. Both end with the existing chassis-through-ducts block and `DriverRoster`. Change only these two sentences:

- Drag tires: `Circuit races use the circuit tires on the Circuit page, whatever is fitted here.`
- Circuit tires: `Strips use the drag tires on the Drag page.`

`rememberScrollState()` stays inside the page column for this task.

- [ ] **Step 4: Run the test to verify it passes**

Same command. Expected: PASS.

- [ ] **Step 5: Commit**

`git commit -m "Split Race into Drag and Circuit pages."`

### Task 2: Scroll, selection, rotation, leaving Race

**Files:**
- Modify: `app/src/test/java/com/revline/idle/ui/RacePagesTest.kt`
- Modify: `app/src/main/java/com/revline/idle/ui/RaceScreen.kt`

**Interfaces:**
- Consumes: Task 1 page switch
- Produces: `ScrollState` per page via `rememberSaveable(saver = ScrollState.Saver)`, circuit UI restored by `rememberSaveableStateHolder()`

- [ ] **Step 1: Write the failing tests**

`scrollOffsetsStayIndependent`: on Drag, `onNode(hasScrollAction()).performScrollToNode(hasText("A duct lets heat out"))`, assert that text displayed and `DISTANCE` not displayed. Click CIRCUIT. Assert `A few laps round a real circuit` displayed and the duct sentence not displayed. Scroll to the duct sentence. Click DRAG. Duct sentence displayed, `DISTANCE` not. Click CIRCUIT. Duct sentence displayed.

`circuitPickSurvivesTheOtherPage`: hire nothing extra. Open Circuit, click `MONACO`, assert `Free race.`. Click `PUSH`, assert `Quicker every lap.`. Switch to Drag and back. Both strings displayed. `StateRestorationTester.emulateSavedInstanceStateRestore()` then both still displayed and `DISTANCE` absent. Then `rule.setContent {}` and set `RaceScreen` again: `DISTANCE` displayed, `Free race.` absent.

- [ ] **Step 2: Run those two tests and confirm they fail**

Expected: FAIL on the second visit (scroll reset, Monaco and Push reset).

- [ ] **Step 3: Keep the state**

```kotlin
val dragScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
val circuitScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
val holder = rememberSaveableStateHolder()
holder.SaveableStateProvider(page) {
    if (page == "DRAG") DragPage(scroll = dragScroll, ...)
    else CircuitPage(scroll = circuitScroll, ...)
}
```

Each page column uses `verticalScroll(scroll)`. Do not put `circuitId` in the view model.

- [ ] **Step 4: Re-run the two tests**

Expected: PASS. Task 1 test still PASS.

- [ ] **Step 5: Commit**

`git commit -m "Keep each race page's scroll and circuit picks."`

### Task 3: One offscreen result

**Files:**
- Modify: `app/src/test/java/com/revline/idle/ui/RacePagesTest.kt`
- Modify: `app/src/main/java/com/revline/idle/ui/RaceScreen.kt` (`CircuitSection` gains `circuitId: String, onCircuitId: (String) -> Unit`)

**Interfaces:**
- Consumes: `Circuits.Outcome`, `Season.next`
- Produces: venue id owned by `RaceScreen`

- [ ] **Step 1: Write the failing test**

State is `mutableStateOf`. Game is `Logic.newGame().copy(devMode = true, hiredDrivers = setOf("dusty"))`. Count `onCircuit` and `onRevise`.

Open Circuit, click `MONACO`, click `Run race` (count is 1). Click DRAG. Set `circuitBusy = true`. Click CIRCUIT, click `Racing...` if it is enabled (it must not be), count stays 1. Click DRAG. Replace ui with `circuitBusy = false` and `circuitOutcome = outcomeOfMonaco()` where `outcomeOfMonaco` is `Circuits.Outcome(Circuits.race(...), 0.0, 0.0, 0.0, 0.0, "")` using the same car and driver shape as `RaceScreenShotTest` but any legal result whose `log.rows` is not empty. Stay on Drag: `Close` does not exist, `onCircuit` is 1, `onRevise` is 0. Click CIRCUIT. `Close` exists once. Count stays 1 and 0. Click DRAG: `Close` does not exist. Click CIRCUIT: `Close` exists again and both counts stay unchanged. Championship text is `CHAMPIONSHIP ROUND`. Click `Close`, then `MONACO`, then switch to Drag and back with the same outcome object. `Free race.` is displayed and `Close` is not, until `Watch the race` is clicked, which shows `Close` again without incrementing either count.

Set `mainClock.autoAdvance = false` before the dialog can open, and `advanceTimeBy(300)` after the click that should open it. Do not advance long enough to need the replay to finish.

- [ ] **Step 2: Run it and confirm it fails**

Expected: FAIL because the venue stays on Monaco, or `Close` never appears, or a second `onCircuit` fires. Not a compile failure.

- [ ] **Step 3: Handle the outcome above the page switch**

```kotlin
var circuitId by rememberSaveable { mutableStateOf(Season.next(ui.game).id) }
val handled = remember { arrayOf(ui.circuitOutcome) }
LaunchedEffect(ui.circuitOutcome) {
    val outcome = ui.circuitOutcome
    if (outcome != null && outcome !== handled[0]) circuitId = Season.next(ui.game).id
    handled[0] = outcome
}
```

Pass `circuitId` into `CircuitSection` and delete that function's own venue state and follow effect. Keep `armed`, `watching`, pit orders, and `driverPick` where they are. The Run race click stays the only `onRun` call.

- [ ] **Step 4: Re-run Task 3 and Task 2 tests**

Expected: PASS.

- [ ] **Step 5: Commit**

`git commit -m "Apply a circuit result once when Drag is showing."`

### Task 4: README, Fold shots, release

**Files:**
- Modify: `README.md` (the sentence `The race tab is a separate car.` and the 1.84.0 drag-parts sentence that says the parts sit after the circuit parts; add `## 1.93.2` above `## 1.93.1`; install line version)
- Modify: `app/build.gradle.kts` `versionCode = 124`, `versionName = "1.93.2"`
- Test: `app/src/test/java/com/revline/idle/ui/RacePagesShotTest.kt`

**Interfaces:**
- Consumes: finished `RaceScreen`
- Produces: `dist/revline.zip` and PNGs under `app/build/reports/race-pages/`

- [ ] **Step 1: README**

After `The race tab is a separate car.` add: `Drag and Circuit are separate pages under that tab. Drag has the distance, the strip driver, Quick, Ladder, Stake, drag tires and drag parts. Circuit has the venue, the championship, the pit wall, Run race, the result, circuit tires and circuit parts. Chassis, gearbox, final drive, driveline, layout, ducts and the hire roster sit at the bottom of both.` Change the 1.84.0 sentence so drag parts are on the Drag page, not after the circuit parts on one list.

`## 1.93.2` says the Race tab is those two pages and the strip, replay, payouts, and saves are unchanged. Install instructions say 1.93.2.

- [ ] **Step 2: Screenshots**

`RacePagesShotTest` uses qualifiers `w690dp-h830dp-xhdpi` and writes, by drawing the decor view the way `RaceScreenShotTest` does:

- `drag-fold.png`, `circuit-fold.png` at the top of each page
- `drag-fold-shared.png`, `circuit-fold-shared.png` after `performScrollToNode` on `CHASSIS`

A second method with qualifiers `w411dp-h731dp-xhdpi` writes `drag-phone.png` and `circuit-phone.png`. Directory: `app/build/reports/race-pages/`. `autoAdvance = false`. Copy the six files to `/opt/cursor/artifacts/` after the run.

- [ ] **Step 3: One full check, then the zip**

Run `./gradlew testDebugUnitTest` once. Expected: BUILD SUCCESSFUL, 0 failures.

Then set version 1.93.2 / 124, run `./gradlew assembleRelease` and `./scripts/pack-release.sh`. `keytool -list` on `app/sideload.keystore` must show SHA256 prefix `8F:33:2A:4C`. `apksigner verify --print-certs dist/revline.apk` must show the same prefix. `unzip -t dist/revline.zip` passes and the only entry is `revline.apk`.

- [ ] **Step 4: Commit and PR**

Commit the README, version, zip, and shot test. Push `cursor/race-pages-design-cc52`. Open the PR with base `cursor/circuit-display-width-72d3`. Subscribe to CI once; do not poll.

## Self-review

- Spec pages, shared block, `raceNote`, lifecycle, one venue move, one replay, Fold column, and unchanged meet/replay/map each have a task above.
- The version heading is the packaging Cameron added after the spec; it is Task 4 only.
- No TBD. Scroll saver, page strings, and `circuitId` types match across tasks.
