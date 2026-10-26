# Sports widget module (`:app:sports`)

A reference implementation of the Wrexham AFC homepage widget, built as a club-agnostic **sports core** plus a thin **Wrexham layer**. It's a prototype on dummy data, shared so the team can read, reuse and modify it. It is not a patch for landing; the production path is bug 2084809 / FXDROID-10994.

## Layers

| Layer | File | What it does |
|---|---|---|
| Model | `Match.kt` | Teams, matches, a structured `MatchStatus` (scheduled, live, halftime, extra time, penalties, final, delayed, suspended, postponed). Facts only, no display strings. |
| Club config | `Club.kt` | What makes a widget about one club: team, links, copy. `Wrexham` is one `Club`. |
| Data | `SportsRepository.kt` | `SportsRepository` interface, `MerinoSportsRepository` (not used yet), refresh schedule, and `SportsLoader` (throttle, offline check, one request at a time). |
| API mapping | `MerinoSportsApi.kt` | Client interface, DTOs and mapping from the feed to the model. Every guess is marked ASSUMED; sources are listed in the file header. |
| Celebrations | `Celebrations.kt` | `celebrationFor()` decides goal or win from two versions of a match; `SportsFx` holds which effects are playing. |
| Effects | `KitEffects.kt` | Kit GIF player, card confetti, full-screen winner confetti, the fox runner and "GOAL!" banner. |
| Match UI | `MatchUi.kt` | Reusable match row, status tag, score pill, status line, crest. |
| Club widget | `ClubWidget.kt`, `ClubWidgetActions.kt` | The homepage card: header, menu, body, carousel, message states. |
| Demo data | `DemoScript.kt`, `WrexhamDemo.kt` | Secret Settings scenarios and scripts that fake a live feed. |

App-side glue lives in `app/src/main/java/org/mozilla/fenix/home/sports/`:
- `WrexhamWidgetSection` wires taps to app behaviour.
- `WrexhamWidgetMiddleware` loads data into the AppStore.
- `refreshWrexhamWidgetWhileResumed` triggers loads while the homepage is on screen.

## Data flow

```
HomeFragment (resumed) ── Refresh ──> AppStore ──> WrexhamWidgetMiddleware ──> SportsLoader ──> SportsRepository
                                          ^                                                        │
                                          └──────────── Loaded(ClubWidgetState) or Failed <─────────┘
AppState.wrexhamWidgetState ──> HomepageState ──> WrexhamWidgetSection ──> ClubWidget
```

The app loads on every homepage resume, then every 60s during a match, every 5 minutes on match day and every 30 minutes otherwise. A failed load keeps the last good data on screen; the error card only appears when there is nothing to show yet. Celebrations come from comparing each new version of a match with the last, so real goals and wins animate without extra code.

## Plugging in the real API

1. Implement `SportsMatchesClient` on the app-services Merino binding. Follow the World Cup widget's `AppServicesWorldCupMatchesClient` (removed in commit a91ad60e864c).
2. Check the DTOs and status rules in `MerinoSportsApi.kt` against the spec.
3. Pass a `MerinoSportsRepository` to `SportsLoader` in `Components.kt` instead of `FakeWrexhamRepository`, and drop `minIntervalMs = 0L` so the default 60s gap applies.

### API notes

What each source says, most trusted first:

1. **The shipped World Cup client code** (commit a91ad60e864c). It ran against the real endpoint, so it wins where the sources disagree.
2. **Merino's "API Contract Proposal for SportsData Suggestions" doc** ([link](https://docs.google.com/document/d/1KsTTPpmsx2v-HFlRni1Q74C9UO6KRqcYO-mEnQ-tFoQ/edit?tab=t.wb5kt1vxfgdc)).
   - Its World Cup tab is the original contract.
   - Its Suggest API tab is the regular-season (NFL, NHL, MLB) shape, which nests scores inside each team and uses long statuses like "Final - Shoot Out".
3. **SportsData.io's [soccer workflow guide](https://sportsdata.io/developers/workflow-guide/soccer)**, for the upstream feed Merino wraps.

What that means for the fields:

- **Statuses (Merino):** Scheduled, Delayed, Postponed, In Progress, Suspended, Canceled, Final, Not Necessary.
  - "Break" (halftime) appears only in the World Cup code.
  - Canceled and Not Necessary have no design state yet and show as Scheduled. That's a product question.
- **Period:**
  - "1", "2" and "Extra" (Merino doc).
  - "Regular" and "ExtraTime" (SportsData.io).
  - "PEN" or "PenaltyShootout" during a shootout (World Cup code).
- **Scores:** `home_score` is normal time only. Extra-time goals arrive in `home_extra` and shootout goals in `home_penalty`.
- **Clock:** Merino formats stoppage time as "90+3". Upstream sends it as a separate `ClockExtra`.
- **Postponed games** keep their ID and go back to Scheduled on the new date (SportsData.io).
- **Refresh:** Merino refreshes match data every 3 minutes. It also has a lighter live-only endpoint (`/api/v1/wcs/live`) that would suit polling during a match.
- **Team art:** teams come with Merino-hosted `icon_url` logos and hex `colors`, which could replace the bundled crests.
- **Competitions:** upstream uses numeric CompetitionIDs. EFL Championship is 34 and FA Cup is 27.

## Reusing the core

- **Another club:** add a `Club`.
- **A league or global widget:** follow several teams. `celebrationFor`, `goalsFor` and `wonBy` already take the team as a parameter, and `MerinoSportsRepository` takes a set of team keys.

## Trying it

In a debug build, the widget is on the homepage under the shortcuts (Settings > Homepage > Wrexham AFC turns it on or off).

To switch states, open the card's ⋮ menu and choose **Demo: switch scenario**, below the divider. The card changes straight away. "Goal celebration", "Winner celebration", "FA Cup shootout (plays out)" and "Live, Wrexham scores (plays out)" animate.

The demo item only exists in debug builds. Secret Settings > "Wrexham widget scenario" sets the same thing.

## Checks

```
./mach gradle -p mobile/android/fenix :app:sports:testDebugUnitTest
./gradlew -Pandroid-lint :fenix:detekt :fenix:spotlessCheck -PdisableAndroidComponentsTasks=true
```

## Known deviations

- **Taps are wired inside the section, not an interactor.** `WrexhamWidgetSection` connects taps straight to app services (new tab, navigation, share). Fenix homepage sections usually route through the homepage interactor and controller. Moving them there touches shared interfaces, so it's left to the real implementation.
- **"On screen" is approximate.** Demo scripts start on the card's first draw, which avoids playing behind the splash screen. A card below the fold also counts as drawn. This only affects the demo; real data needs no start signal.

## Not done yet

- Real data: needs the Merino club endpoint.
- Localization registration: new string paths are added in the android-l10n repo.
- Telemetry.
- Design review, QA and release.
- Club crests and Kit art are trademarked or internal assets, so keep builds internal.
