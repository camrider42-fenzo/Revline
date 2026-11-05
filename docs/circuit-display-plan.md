# Displayed circuit maps

Physical centerlines stay in `CircuitLine` (metres, lap length, corner arcs, Suzuka's bridge). Race times, rewards, balance and saves do not read the picture.

`CircuitDraw` is a second closed loop with the same sample count and the same arc parameter as that line. Sample `i` is still physical distance `i * step`, so a lap fraction, a corner, the start line and the bridge stay on the same fraction of the lap. Positions are shifted only where two parts at least 30 layout units apart would paint on top of each other. Adjacent samples on one bend are not pushed apart. The bridge window does not move, so Suzuka still crosses. Offsets are smoothed around the loop, including the seam.

`CircuitGeo` fits the physical bounding box, then applies those offsets. It does not fit again, so the gap is not scaled away. Each sample's shift is capped by the padding already reserved for the runoff, so the road edge stays on the canvas. Road width is `roadShare` of the picture; lanes, cars, the racing line and kerbs stay fractions of that width.

`point`, `heading` and the displayed bend all come from `CircuitDraw`. Cars, kerbs, grid boxes, the start line, pit graphics and the follow camera already go through those, so they use one tangent and one normal. `CircuitRadii` keeps using `CircuitLine`.

Shares: Monza 0.040, Monaco 0.027, Silverstone 0.042, Spa 0.034, Hungaroring 0.042, Suzuka 0.030, Montreal 0.032, Singapore 0.036.
