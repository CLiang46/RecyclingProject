# Recycling Buyback Calculator

A passion project to calculate how much you can earn by recycling CRV (California Redemption Value) cans and bottles.
It's a plain Java backend (no frameworks, no build tool) serving a small web page.

## How payouts are calculated

- **CRV per container:** 5¢ for containers under 24 oz, 10¢ for 24 oz or more. Non-CRV containers earn nothing.
- **Count vs. weight:** for each material, loads of **50 or fewer** refundable containers are paid **by count**. Larger loads are paid **by weight**
  at that material's per-pound rate, like California recycling centers do.
- **Weight:** enter the actual weight (in pounds) if you've weighed your load. Otherwise the calculator estimates it from typical
  empty-container weights.
- The results show both the by-count and by-weight values, so you can see which way the rules apply.

Rates and typical container weights are in [`Material.java`](Newproj/src/com/recycleproj/Material.java). Update them there when
CalRecycle changes its rates.

## Running

Requires JDK 17 or newer (`java` and `javac` on your `PATH`). Run from the repository root:

```sh
./run.sh                 # macOS / Linux
```

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1    # Windows
```

Then open <http://127.0.0.1:8080/>. Options: `--port 9000`, `--host 0.0.0.0` (to allow other devices on your network),
`--webroot <dir>`.

Run the tests with `./run.sh test` or `.\run.ps1 test`.

## API

| Endpoint | Description |
| --- | --- |
| `GET /` | The calculator page |
| `GET /api/materials` | Materials, per-pound rates and estimated container weights |
| `GET` or `POST /api/calculate` | Payout for a load |

`/api/calculate` takes query-string or form-encoded parameters for each material key
(`aluminum`, `bimetal`, `glass`, `pet`, `hdpe`, `pvc`, `ldpe`, `pp`, `ps`, `other`):
`{key}_regular`, `{key}_large`, `{key}_nonrefundable` (counts) and `{key}_weight` (pounds, optional).

```sh
curl "http://127.0.0.1:8080/api/calculate?aluminum_regular=30&pet_regular=80"
```

## Project layout

```
Newproj/src/com/recycleproj/
  Recycle.java              base class for all containers (size, CRV, refundable)
  Can.java, Bottle.java     container families
  AluminumCan, BimetalCan, GlassBottle, PlasticBottle and its resin subclasses (PETBottle, HDPE, PVC, LDPE, PP, PS, OtherPlastic)
  Material.java             per-material rates and typical container weights
  BuybackCalculator.java    count/weight payout rules
  WebServer.java            HTTP server (JDK built-in com.sun.net.httpserver)
  Main.java                 entry point
Newproj/test/               dependency-free tests
BuybackCalculatorFrontend/src/main/webapp/MainPage.html   the web page
```
