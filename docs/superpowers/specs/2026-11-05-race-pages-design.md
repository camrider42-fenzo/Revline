# Race pages

## Goal

The Race tab stays in the bottom bar. Drag and Circuit are two pages inside it. Each page has its own setup, tires, and parts. The circuit result card stays on Circuit. A finished drag meet still writes the shared `raceNote` string, and both pages show that string. The shared car and the driver-hire roster sit at the bottom of both. Racing, the economy, saves, and the 1.93.1 circuit map stay as they are.

## Pages

The bottom bar stays BENCH, PARTS, TUNE, STUDY, RACE, REBUILD. The app still starts on BENCH. The Race tab opens on Drag until the player picks Circuit. That choice lasts until the race screen's UI state is discarded, as defined below.

Under the title, a DRAG | CIRCUIT row uses the same `LineButton` treatment as TUNE's ENGINE | DRAG row: the open page is the olive button, and the other is the lime button that switches. Ink stays black, monospace, and the existing lime, white, cyan, amber, red, and olive. Each page is one vertical column, including on the unfolded Fold at 18:21.6 (690×830 dp). There is no swipe pager.

Both pages start with the current header: the RACE title, the tab row, the chassis / mass / dyno / pace line, and the place / drive / ducts / fade / fit line.

Both pages then show `UiModel.raceNote` when it is non-null. That value stays one shared string. It is the text a finished drag meet writes, and it is also the refusal and hire text the race actions already write. This split does not add a per-mode note, and it does not change when the view model sets or clears the string.

Two existing sentences point at the other tire list as "above" or "below". On Drag, the drag-tire sentence names the Circuit page. On Circuit, the circuit-tire sentence names the Drag page. Other copy stays.

### Drag

1. Distance, with the current copy and chips.
2. Strip driver toggle (`GameState.driverId`): drive myself, or a hired driver.
3. Quick, Ladder, and Stake.
4. Drag tires.
5. Drag parts.
6. Shared block below.

Quick, Ladder, and Stake still replace the scaffold with `RaceMeet` until `finishMeet`. The meet still writes its result into `raceNote`. Drag is the page underneath, so that note is on screen when the meet closes.

### Circuit

1. The current circuit block, in its current order: intro, venue chips, championship line, circuit facts, blurb, static map, favors, fit, circuit driver chips, entry and prizes, championship table, forecast, pit wall, field, Run race, and the result card.
2. Circuit tires.
3. Circuit parts.
4. Shared block below.

The result card is `circuitOutcome` (place or DNF, times, prize, season line, qualifying, lap log, Watch the race). It is not `raceNote`. A refused entry stays `raceNote` only.

Run race still calls `runCircuit` once per click. The replay stays the full-screen `CircuitRaceScreen` dialog. Where the width is at least three quarters of the height, including the unfolded Fold held upright, the map stays beside the dials. Pit revise stays on that dialog.

The static map on this page is the existing preview. The 1.93.1 drawing stays: wider roads, crowded bends eased apart on the picture only, measured lap and Suzuka's bridge unchanged.

### Shared block

The same composable, with the same view-model calls, at the bottom of both pages:

1. Chassis, including the mule and the ladder groups.
2. Gearbox.
3. Final drive, including the gear-time line.
4. Driveline.
5. Layout.
6. Ducts.
7. Driver hire roster.

The strip driver toggle stays on Drag. The circuit driver chips stay in the circuit block and still do not write `driverId`. Hiring on either page is the same hire action.

Drag tires and drag parts are only on Drag. Circuit tires and circuit parts are only on Circuit. Fitted values stay in `GameState`, so a part bought on one page still changes the other mode by the same rules as now. TUNE's DRAG knobs stay on TUNE.

## State and lifecycle

`GameState`, the save file, and the save version do not change.

This UI state is not part of the save:

- The open page, Drag or Circuit.
- That page's scroll offset, and the other page's scroll offset. They are independent.
- Circuit selections: venue, local driver pick, championship table open, pit orders, lap-log toggles, and whether the replay is armed or open.

A Drag ↔ Circuit switch keeps all of the above. Rotation keeps it too, and Race shows the restored page the next time it is composed. The bottom-bar tab is unchanged, so rotation still brings the app up on BENCH. Leaving Race for another bottom-bar tab drops this UI state before any later rotation, which is the race screen's current lifetime; the next visit starts on Drag. It is not copied into `GameState` to outlive that.

A new `circuitOutcome` still moves the venue chips to the championship's next round once, including when the result arrives while Drag is showing. Showing the circuit page again does not move the venue a second time. A chip picked after that still sticks.

`circuitBusy`, the in-flight race, `circuitOutcome`, and `circuitBase` stay in the view model. A page switch does not cancel the race, start another, or call `reviseCircuit`.

Run race sets the one-shot arm and calls `runCircuit` only from that click. While `circuitBusy` is true the button reads "Racing..." and ignores clicks, including on a later visit. When the circuit page is shown, the arm is still held, and the outcome has a lap log, the replay opens once and the arm clears. A result that arrives on Drag does not change the open page and does not open the dialog there. The next showing of Circuit, while that arm is still held, opens it once. Leaving Race drops the arm with the rest of the race UI state, so a later visit does not open the replay by itself; Watch the race still opens the existing result.

Leaving Circuit while the dialog is open takes the dialog down with the page. Returning while the replay is still marked open shows it again, with no new sim call. Close still only clears that open flag.

While `ui.strip` is set, `RaceMeet` still covers the scaffold, tabs included. The page choice underneath is left as it was.

Keeping the page choice, the two scroll offsets, and the circuit selections in a `SaveableStateHolder` is enough. Another holder that retains those values while a page is off-screen is also enough. The holder type is not required.

## Boundaries

In scope: the two pages inside `RaceScreen`, one shared block used by both, the two tire sentences that point at the other page, and the README passage that walks the race tab as one sequence (distance, then the circuit, then chassis and both part lists). That passage names the two pages and which controls sit on which. No new version heading.

Out of scope: racing math, economy, prizes, repair costs, rival fields, drag tune, the save schema, save migration, the view-model entry and settle rules, `RaceMeet` driving, the circuit replay layout, and circuit map geometry. This change does not bump the version and does not build a release zip. A later package from this work uses the existing sideload certificate (SHA256 prefix `8f332a4c`) and does not retune balance or saves.

## Acceptance

- Drag shows distance, Quick, drag tires, and drag parts. It does not show circuit tires, circuit parts, or Run race.
- Circuit shows the venue chips, Run race, circuit tires, and circuit parts. It does not show distance, Quick, or drag tires.
- Both pages end with chassis, then the rest of the shared block, then the hire roster.
- Scroll one page, switch, scroll the other, and switch back. Each offset is unchanged.
- Pick a circuit and a pit plan, switch to Drag, and switch back. The venue and the plan are unchanged.
- Finish a circuit race while Drag is showing. The venue moves to the championship's next round once. Opening Circuit again does not move it again, and the replay opens once.
- Start a circuit race and switch to Drag before it finishes. Come back to one result and one replay. The button does not enter a second time. Close and Watch the race behave as they do now.
- Finish a drag meet. The meet is unchanged, and the same `raceNote` string is what the Drag page shows.
- At `w690dp-h830dp-xhdpi`, both setup pages are one column. The existing circuit replay shot still shows the map beside the dials.
- Existing circuit and drag screenshot tests pass with no change to map or car drawing.
- `testDebugUnitTest` passes. No economy or save test is edited to excuse a UI move.
