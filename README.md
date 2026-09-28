# Recycling Buyback Calculator

A passion project to calculate how much you can earn by recycling CRV (California Redemption Value) cans and bottles.
It's a plain Java backend (no frameworks, no build tool) serving a small web page.

## How payouts are calculated

- **CRV per container:** 5¢ for containers under 24 oz, 10¢ for 24 oz or more. Non-CRV containers earn nothing.
- **Count vs. weight:** each material is split into **regular** and **large** groups, and each group is paid on its own.
  A group of **50 or fewer** refundable containers is paid **by count**. A larger group is paid **by weight** at that
  material's per-pound rate, like California recycling centers do. For example, 40 regular and 60 large PET bottles
  means the regular ones are paid by count and the large ones by weight.
- **Glass colors:** clear, green and brown/dark glass are sorted and treated as separate materials, so each color
  (and each size within it) has its own count limit and weight.
- **Weight:** enter the actual weight (in pounds) of each group if you've weighed it. Otherwise the calculator estimates it
  from typical empty-container weights. A group with a weight but no count is paid by weight.
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
(`aluminum`, `bimetal`, `glass_clear`, `glass_green`, `glass_brown`, `pet`, `hdpe`, `pvc`, `ldpe`, `pp`, `ps`, `other`):
`{key}_regular`, `{key}_large`, `{key}_nonrefundable` (counts) and `{key}_regular_weight`, `{key}_large_weight`
(pounds, optional). The response has one result per material and size group.

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
