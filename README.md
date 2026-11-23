# Recycling Buyback Calculator

A passion project to calculate how much you can earn by recycling CRV (California Redemption Value) cans and bottles.
It's a plain Java backend (no frameworks, no build tool) serving a small web page.

## How payouts are calculated

The page first asks what you're recycling (**cans**, **glass**, **plastic**, or any combination), then shows count and
weight fields for just those categories. Use **Change categories** to go back; fields for categories you deselect
are left out of the calculation.

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

## Accounts and saved calculations

Use **Create account** at the top of the page to sign up (username, email, and your password typed twice), or **Log in** if you already have an account. Once you're signed in, a **Save** box appears under
each result: give the calculation a name to store it. Your saved calculations are listed at the top of the page. **Open**
restores the categories, the form and the results; **Delete** removes it. The calculator itself works without an account.

### Two-step verification (email codes)

Signing up and logging in both take two steps: after the password, the server emails a 6-digit code, and you type it
in to finish. Codes expire after 10 minutes, and only the newest code works. **Send a new code** is available after
30 seconds (up to 5 codes per attempt). Five wrong codes cancel the attempt; on login they also count toward the
username lockout. Accounts created before email codes existed are asked to add an email address the next time they log in.

To send real emails, copy `mail.properties.example` to `mail.properties` and fill in your SMTP settings (for Gmail,
use an [app password](https://myaccount.google.com/apppasswords), not your normal password). `mail.properties` is
ignored by git. Use `--mail-config <file>` to read it from somewhere else. **Without `mail.properties`, codes are
printed in the server window instead of emailed**, which is handy for testing.

- Everything is stored in a SQLite database file, `data/recycling.db` by default (change it with `--db <file>`). Back up
  that file to keep your history. It's ignored by git.
- Passwords are stored as salted PBKDF2-SHA256 hashes, never as plain text. Logins last 7 days and use an HttpOnly,
  SameSite=Strict cookie. Five wrong passwords in a row lock that username for 5 minutes.
- Saved results are recalculated on the server, so they always match the inputs that were saved.
- The run scripts download the SQLite JDBC driver ([sqlite-jdbc](https://github.com/xerial/sqlite-jdbc)) into `lib/`
  on the first run and check its SHA-256 checksum. If the driver is missing, or you pass `--no-db`, the server starts
  without accounts.
- The server is plain HTTP and listens only on `127.0.0.1` by default. Don't expose it to the internet with `--host 0.0.0.0`
  without putting HTTPS in front of it.

## Running

Requires JDK 17 or newer (`java` and `javac` on your `PATH`). Run from the repository root:

```sh
./run.sh                 # macOS / Linux
```

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1    # Windows
```

Then open <http://127.0.0.1:8080/>. Options: `--port 9000`, `--host 0.0.0.0` (to allow other devices on your network),
`--webroot <dir>`, `--db <file>`, `--no-db`, `--mail-config <file>`.

Run the tests with `./run.sh test` or `.\run.ps1 test`. The first run needs internet access to download the SQLite driver.

## API

| Endpoint | Description |
| --- | --- |
| `GET /` | The calculator page |
| `GET /api/materials` | Categories, materials, per-pound rates and estimated container weights |
| `GET` or `POST /api/calculate` | Payout for a load |
| `POST /api/register` | Start sign-up (`username`, `email`, `password`); emails a code and returns `{"challenge", "sentTo"}` |
| `POST /api/login` | Start login (`username`, `password`); returns `{"challenge", "sentTo"}`, or `{"challenge", "needsEmail": true}` for accounts without an email |
| `POST /api/verify` | Finish sign-up or login (`challenge`, `code`); sets the session cookie |
| `POST /api/verify/resend`, `POST /api/verify/email` | Send a new code (`challenge`); add an email to an older account (`challenge`, `email`) |
| `POST /api/logout`, `GET /api/me` | Sign out; check who is signed in and whether accounts are enabled |
| `GET /api/calculations` | Your saved calculations, newest first |
| `POST /api/calculations` | Save: `name` plus the same parameters as `/api/calculate` |
| `GET` or `DELETE /api/calculations/{id}` | Open (inputs and results) or delete a saved calculation |

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
  Database.java             SQLite tables for users, sessions and saved calculations
  AuthService.java          sign-up, login, email verification codes, sessions and lockout
  Mailer.java, SmtpMailer.java   sending verification emails (SMTP, or printed to the console)
  PasswordHasher.java       salted PBKDF2 password hashes
  CalculationStore.java     save/list/open/delete calculations, per user
  Main.java                 entry point
Newproj/test/               dependency-free tests
BuybackCalculatorFrontend/src/main/webapp/MainPage.html   the web page
mail.properties.example   template for email (SMTP) settings
```
