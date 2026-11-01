# Notices

Revline uses the following third-party data.

## bacinger/f1-circuits

The circuit centrelines in `app/src/main/java/com/revline/idle/ui/CircuitData.kt` are derived from the GeoJSON files in https://github.com/bacinger/f1-circuits (Monza, Monaco, Silverstone, Spa-Francorchamps, Hungaroring, Suzuka, Montreal and Marina Bay). They were projected to metres, resampled, smoothed and packed into compact strings. The source is licensed under the MIT License:

```
Copyright (c) 2019-2025 Tomislav Bacinger

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Gridpedia

Turn order, turn sides and start/finish positions were read from Gridpedia circuit maps as a reference. No Gridpedia artwork is included.
