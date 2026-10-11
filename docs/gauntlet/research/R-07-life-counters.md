# R-07 Life counter inventory

Path: `docs/gauntlet/research/R-07-life-counters.md`. Completed source audit: 2026-10-11. Every source label below includes that access date. Store and developer claims are documented features, not hands-on verification.

## Question

What do LifeElk, Lifetap and two other highly rated Magic life counters offer, and which features should Command Library build, adapt or skip?

## Findings

### Source register and interpretation

All links in this register were accessed 2026-10-11. Matrix citations inherit that explicit date. `U` means UNVERIFIED: the linked store/developer descriptions were checked but do not establish that precise capability. It never means the app lacks the feature. `iOS` marks evidence confined to Apple releases; Android parity is not assumed. Recommendations and reasons are proposed product decisions against local GAUNTLET.md 2.G/Appendix A, read 2026-10-11, not competitor claims.

- [E] LifeElk Google Play: https://play.google.com/store/apps/details?hl=en&id=com.bartollesina.lifetap (accessed 2026-10-11).
- [EW] LifeElk developer site: https://lifeelk.com/ (accessed 2026-10-11).
- [EA] LifeElk US Apple listing/release history: https://apps.apple.com/us/app/mtg-life-counter-lifeelk/id6443751449 (accessed 2026-10-11).
- [T] Lifetap Google Play: https://play.google.com/store/apps/details?hl=en&id=com.lifetap (accessed 2026-10-11).
- [TW] Lifetap developer site: https://getlifetap.com/ (accessed 2026-10-11).
- [TA] Lifetap US Apple release history: https://apps.apple.com/us/app/lifetap-life-counter-for-mtg/id1508241754 (accessed 2026-10-11).
- [L] Lotus Google Play: https://play.google.com/store/apps/details?hl=en&id=com.vanilla.mtgcounter (accessed 2026-10-11).
- [LW] Lotus developer feature/pricing page: https://lifecounter.app/pricing/ (accessed 2026-10-11).
- [M] Mox Google Play: https://play.google.com/store/apps/details?hl=en&id=com.quince.magiccounterapp (accessed 2026-10-11).
- [MW] BabyDragon developer homepage: https://babydragondevelopment.com/ (accessed 2026-10-11). Its linked Mox detail page timed out, then returned 404: https://babydragondevelopment.com/our-subjects/magic-the-life-counter/ . No extra capability inferred.

### Store metadata, population and selection

The intended sample was English Google Play, with explicit US locale where possible. Opening `hl=en&gl=US` failed for the initial four attempted listings; `hl=en` succeeded and footer locale generally showed US English. One Lifetap response instead had PEGI and a different score. Thus values below are the last readable page snapshots, not a synchronized global ranking or an exact count of unique raters. Header review totals differ from device-filtered review sections. Search-index snapshots can be older even when accessed today.

| App | Developer | Header rating / displayed reviews | Installs band | Updated on | Version evidence | Source/date |
|---|---|---|---|---|---|---|
| LifeElk | CardElk d.o.o. | 4.8 / 7.54K; phone section 7.2K | 500K+ | Aug 12, 2026 | Android U: not exposed in readable listing. iOS 2.61, Sep 8, year not printed | [E], [EA], 2026-10-11 |
| Lifetap | Michael Bourkas | 4.9 / 7.25K; phone 6.58K. Earlier connector response 5.0 / 7.24K | 500K+ | Oct 6, 2026 | Android U. iOS 1.3.10, relative “5d ago”, compatible with October 6 but not independently timestamped | [T], [TA], 2026-10-11 |
| Lotus | Vanilla B.V. | 4.8 / 4.98K; phone 4.58K | 100K+ | Oct 10, 2026 | Android U: readable listing omits version | [L], 2026-10-11 |
| Mox MTG Life Counter | BabyDragon Development; developer Matteo Dragoni | 4.7 / 825; phone 801 | 100K+ | Feb 26, 2026 | Android U: readable listing omits version | [M], 2026-10-11 |

Additional sampled candidates:

| Candidate | Rating / reviews | Installs | Updated | Selection implication | Source/date |
|---|---|---|---|---|---|
| Life Total, talisman.games (`lifetotal.usa`) | 4.6 / 1.24K | 100K+ | Sep 24, 2026 | Lower displayed rating than Lotus/Mox | https://play.google.com/store/apps/details?hl=en&id=com.snowleopardgames.lifetotal.usa , 2026-10-11 |
| Magic Life Auditor | 4.9 / 20; phone 18 | 1K+ | Dec 5, 2023 | Higher stars but tiny population and older update | https://play.google.com/store/apps/details?id=com.shnackaran.magiclifeauditor , 2026-10-11 |
| Rhystic | Indexed 4.9 / 11; open page omitted rating | 500+ | Aug 17, 2026 | Higher indexed stars but tiny population; locale opened Germany English | https://play.google.com/store/apps/details?hl=en-US&id=com.rhystictracker.rhystic , 2026-10-11 |
| Life Counter, Daniel Boberg | Developer claims 4.6, store rating/count U | Downloads U; “100k+ matches” is not installs | U | Site-only claim insufficient to outrank confirmed Play metadata | https://www.life-counter.de/ , 2026-10-11 |
| Carbon | U | U | U | Referenced listing unavailable through connector; cannot rank | https://play.google.com/store/apps/details?id=com.getcarbonapp.carbonv4 , 2026-10-11 |

Select **Lotus and Mox as the two highest-rated additional established apps in this sampled cohort**, defining established as at least 100K+ displayed installs and 500 displayed reviews, ordered by stars then review population. This is an analyst-selected comparability rule, not a Google ranking. No exhaustive Play catalog or stable global top-two ranking was available. Literal stars-only ranking would favor Auditor/Rhystic; their populations do not support a stronger quality inference. Pat can rule differently. The matrix therefore covers LifeElk, Lifetap, Lotus, Mox and explicitly discloses this limitation.

### Baseline confirmation, platform availability and paid features

GAUNTLET.md section 10's LifeElk summary is confirmed by [E]: six-player ceiling, commander/EDH, infect/energy, hidden extras, dice, coin, backgrounds, custom life, tap/hold and autosave. [EW] adds per-opponent damage, rotated seat panels, specific dice and first-player picking. [EA] proves a one-player mode and optional commander-to-life deduction on iOS; this prevents relying on old user reviews to claim those are missing today. Android behavior still needs checking.

LifeElk is free with ads/IAP [E]. US iOS lists “Remove ads” products at $4.99 and $0.99 [EA]; purchase period, Android price and any other paid feature are U. Lifetap developer explicitly advertises free/no ads/no account [TW]. Lotus listing states no ads and no locked features; IAP is voluntary support [L], and the developer offers Patreon support [LW]. Mox is ad-supported with IAP [M]; developer's Portuguese Play text says Premium removes ads: https://play.google.com/store/apps/details?hl=pt_BR&id=com.quince.magiccounterapp (accessed 2026-10-11). Exact Android Premium price, period and additional paywalls are U. Do not apply Austrian/Bahraini iOS pricing to Android.

Windows: no official native Windows release for LifeElk/Lifetap was established. Their sites advertise Android/iOS [EW], [TW]. Searches for `site:apps.microsoft.com LifeElk Lifetap` and name-plus-Microsoft-Store produced no verifiable listing; native Windows availability is U, not a proved absence. A Play “Desktop” review tab does not establish a native Windows build. Lotus supplies a browser app and spectator URL usable from a laptop [LW]; native Windows installer and offline browser cache guarantees are U. Mox homepage/store links established mobile distribution [MW], [M]; Windows U.

### Disagreements and freshness

- LifeElk site advertises App Store 4.6 [EW], while US Apple page shows 4.7/755 [EA]. Record both; likely region/time/population differences, cause U.
- Lifetap site advertises Google Play 4.9/6.8K+ [TW], while Play header currently shows 4.9/7.25K [T], with an earlier 5.0 response. These are not one common population.
- Lifetap site says 86 planes/65 schemes [TW]; current iOS releases describe newer set filters and custom decks [TA]. Lotus listing says 207 planes [L]. Counts and definitions differ, not proof of missing support. Command Library should derive such inventories from data rather than copy counts.
- Lotus site's Bounty description [LW] is broader than the Play feature heading, which names Planechase/Archenemy [L]. This is a site-supported addition, not necessarily a contradiction.

### Terms and licensing

LifeElk terms quote: “Unauthorized copying, modification of the Application, any part of the Application, or our trademarks is strictly prohibited.” They also prohibit source extraction/derivative versions and note some functions require internet. Source: https://lifeelk.com/terms (accessed 2026-10-11). Recommendation: independently implement behavior, not copy proprietary code/assets/branding. Offline support for every LifeElk function is U; local game storage alone does not prove it.

LifeElk privacy says local game/configuration data and optional ads/purchases with third-party services. Source: https://lifeelk.com/privacy-policy (accessed 2026-10-11). Lifetap policy quote: “Information you enter into the app ... is stored locally on your device.” It discusses optional future account/cloud features and crash diagnostics, not a blanket no-network promise. Source: https://getlifetap.com/privacy (accessed 2026-10-11; ellipsis shortens original quote).

BabyDragon terms quote: “Not approved/endorsed by Wizards.” No permissive source-code reuse license was found. Source: https://babydragondevelopment.com/terms-of-service/ (accessed 2026-10-11). Lotus listing likewise declares unofficial Fan Content [L]; that declaration is not permission for Command Library to reuse Lotus assets. App-specific redistribution/reverse-engineering licenses for Lifetap/Lotus/Mox are U after checking linked public pages. Card-art attribution/licensing is a separate R-02 decision; appearance in a competitor does not establish rights.

## Parity matrix

Each cell's source is dated 2026-10-11 in the register. U means precise behavior unverified after reading that app's sources. `Build` preserves the mandated baseline; `Adapt` means independently build with the stated behavior refinement; `Skip` means recommend omitting the optional extra from initial scope, subject to Pat's gate ruling.

| Feature | LifeElk | Lifetap | Lotus | Mox | Recommendation | Reason |
|---|---|---|---|---|---|---|
| 1 player | iOS yes [EA] | Yes [T] | U [L] | Solo EDH [M] | Build | Practice mode required |
| 2, 3, 4, 5, 6 players | Up to 6 [E] | 2-6 [T] | 2-10 [L] | 2-6 [M] | Build | Every required table size |
| Seat-oriented panels | Rotated [EW] | Layouts/camera iOS [TA] | Rotated [L] | Layouts [M]; rotation U | Adapt | Seat mode plus operator-facing desktop mode |
| Tap +1/-1 | One [EW] | Tap [TW]; unit U | Buttons [LW]; unit U | Tap [M]; unit U | Build | Predictable fast adjustment |
| Hold auto-repeat | Fast counting [EW] | Accelerates [TW] | Long tap +10/-10 [LW] | Fast adjustment [M] | Adapt | Repeat with visible delta, cancel on release |
| Configurable step | U [E] | U [T] | Fixed hold-10 [LW]; configuration U | U [M] | Build | Explicit baseline |
| Starting life 20/25/30/40 | All described [EW] | All [T] | Custom [L]; presets U | Custom [M]; presets U | Build | Format setup |
| Custom starting life | Yes [E] | Yes [TW] | Yes [L] | Yes [M] | Build | House rules |
| Damage per opposing commander | Per opponent [EW] | Per source [TW] | Multiple sources [LW] | Opponents [M] | Build | Correct loss attribution |
| Partners tracked separately | U [E] | Partners [T] | Partners [L] | Partners [M] | Build | Separate commander identity |
| Optional auto life deduction | iOS setting [EA] | User review [T]; option U | U [L] | U [M] | Adapt | Toggle prevents accidental double subtraction |
| Poison | Infect [E] | Infect [T] | Yes [L] | Yes [M] | Build | Loss condition |
| Energy | Yes [E] | Yes [T] | Yes [L] | Yes [M] | Build | Baseline counter |
| Experience | U [E] | Yes [T] | Yes [L] | Yes [M] | Build | Baseline counter |
| Rad | U [E] | Yes [TW] | U [L] | U [M] | Build | Baseline counter |
| Commander tax | U [E] | Yes [T] | Yes [L] | Yes [M] | Build | Per commander casts and tax |
| Storm | U [E] | Yes [T] | Yes [L] | U [M] | Build | Turn scoped |
| User-defined counters | U [E] | U [T] | Custom [LW] | U [M] | Build | Deck-specific needs |
| Hide each counter | Extras hidden [E] | U [T] | Toggle counters [LW] | U [M] | Adapt | Visibility per seat and counter |
| Monarch | U [E] | Yes [T] | Yes [L] | Yes [M] | Build | Single shared holder |
| Initiative | U [E] | Yes [T] | Yes [L] | Yes [M] | Build | Holder plus editable state |
| Day/Night | U [E] | Yes [T] | Yes [L] | Yes [M] | Build | Shared game state |
| City's Blessing | U [E] | Yes [T] | U [L] | Yes [M] | Build | Per player state |
| Dice any sides | Standard [EW]; custom U | Custom sides [TW] | Custom [L] | Dice [M]; custom U | Build | Validated arbitrary sides |
| Dice any count | U [E] | U [TW] | Multiple [LW]; limit U | U [M] | Adapt | Choose count with practical display limits |
| Coin flip | Yes [E] | Yes [T] | Yes [L] | Yes [M] | Build | Baseline randomizer |
| Random first player | Yes [EW] | Player picker [T] | High roll [LW] | U [M] | Adapt | Fair selection plus optional high roll |
| Seat randomizer | U [E] | Yes [T] | U [L] | U [M] | Build | Baseline setup tool |
| Turn counter | U [E] | iOS count-only [TA] | Turn tracker [LW] | U [M] | Build | Separate turns from timing |
| Turn timer | U [E] | Multiple modes [T] | Per player timing [LW] | U [M] | Adapt | Pause/resume, warnings, undo advancement |
| Game timer | U [E] | U [T] | Pausable stopwatch [LW] | U [M] | Build | Persistent elapsed game time |
| Per-player backgrounds | Colors [EW] | Art/colors [T] | Images/colors [L] | Colors/landscapes [M] | Adapt | Readable overlay and low-motion fallback |
| Card-art background | U [E] | Scryfall [TW] | Custom image [LW] | Avatars/landscapes [M]; arbitrary art U | Adapt | Reuse own DB/cache, preserve attribution |
| Game-over detection | U [E] | U [T] | Kill/unkill [LW]; automatic U | U [M] | Adapt | Explain threshold, allow overrides |
| Undo life/counter changes | U [E] | U [T]; timer undo iOS [TA] | U [L] | U [M] | Build | Reversible events prevent miscounts |
| Autosave | Yes [E] | Crash-safe [T] | Progress saved [LW] | Profiles [M]; game state U | Build | Atomic local event/state writes |
| Resume after restart | Yes [EW] | Yes [T] | Progress saved [LW] | U [M] | Build | No lost match |
| Game log | U [E] | Turn history [TA] | Every life change [L] | U [M] | Adapt | Append-only events, undo recorded explicitly |
| Keep screen awake | U [E] | U [T] | U [L] | U [M] | Build | Opt-in during active match |
| Deck linked to seat, W/L | U [E] | Deck stats [TW]; binding U | Winner/history [L]; deck binding U | Profile [M]; W/L U | Build | Own deck IDs and match results |
| Foldable tabletop posture | U [E] | U [T] | U [L] | U [M] | Build | Hinge-aware required layout |
| Offline life-counter operation | Local storage; full offline U [E] | Local policy; full offline U [T] | Full offline U [L] | Full offline U [M] | Build | No reference proves entire baseline offline |
| Tablet sizing | Phones/tablets [E] | Tablets [T] | U [L] | Mobile layout [M]; tablet U | Build | Adaptive sizes already required |
| Format/team presets | All formats [E] | Three-Headed Giant [T] | Format history [L] | EDH [M] | Adapt | Local presets; explicit team rules if accepted |
| Random player/opponent | First player [EW]; opponent U | Both [T] | High roll [LW]; opponent U | U [M] | Adapt | Exclude eliminated seats and record selection |
| 3/5-player layout picker | U [E] | iOS picker [TA] | Layout choice [L] | Layouts [M]; picker U | Adapt | Seat arrangement should fit actual table |
| Timer undo | U [E] | iOS long press [TA] | U [L] | U [M] | Adapt | Correct turn and priority advances |
| Shared profile transport | U [E] | U [T] | Share profiles [L] | U [M] | Adapt | Local export/import, cloud deferred |
| Over 6 players | Ceiling 6 [E] | Ceiling 6 [T] | Up to 10 [L] | Ceiling 6 [M] | Skip | Beyond approved 1-6 baseline |
| Saved player profiles | U [E] | Names/art/colors [T] | Names/art/colors [L] | Avatars/colors/commander [M] | Adapt | Reusable setup stored locally |
| Two commander artworks | U [E] | Partners/backgrounds [T] | Partners [L]; dual art U | Partners [M]; dual art U | Adapt | Clear commander source selection |
| Mana pool | U [E] | Yes [T] | Yes [L] | U [M] | Adapt | Typed custom preset, reset policy explicit |
| Treasure | U [E] | Yes [T] | U [L] | Yes [M] | Adapt | Optional counter preset |
| Charge | U [E] | U [T] | Yes [L] | U [M] | Adapt | Optional counter preset |
| Blood/Food/Clue/Ring | U [E] | U [T] | Custom [LW]; named U | Named [M] | Adapt | Optional presets without UI clutter |
| Planeswalker/Saga/Siege | U [E] | U [T] | Custom [LW]; named U | Named [M] | Adapt | Object-specific counters, avoid false player semantics |
| Direct numeric life input | U [E] | U [T] | Keypad [LW] | U [M] | Adapt | Confirm exact total and record delta |
| Eliminate/restore player | U [E] | U [T] | Kill/unkill [LW] | U [M] | Adapt | Recover mistakes without resetting table |
| Defeat/victory messages | U [E] | U [T] | Custom [LW] | U [M] | Skip | Optional cosmetic scope |
| Planechase | U [E] | Yes [T] | Yes [L] | U [M] | Adapt | Optional data-driven game module, Pat ruling |
| Archenemy | U [E] | Yes [T] | Yes [L] | U [M] | Adapt | Optional module, no competitor asset copying |
| Bounty | U [E] | OTJ [T] | Yes [LW] | U [M] | Adapt | Optional versioned rule/cards module |
| Custom variant decks/presets | U [E] | iOS five presets [TA] | U [L] | U [M] | Adapt | Local selection if variant module accepted |
| CSV/JSON game export | U [E] | iOS export [TA] | Full backup [L]; format U | U [M] | Adapt | Own stable event schema, exclude secrets |
| Game history search | U [E] | Yes [TA] | History [L]; search U | U [M] | Adapt | Filter by player/deck/date |
| Draw results | U [E] | iOS W/D/L [TA] | U [L] | U [M] | Adapt | Do not force every game to have winner |
| Countdown/chess clock/alerts | U [E] | Yes [T] | Timer [LW]; modes U | U [M] | Adapt | Optional modes reuse baseline timer model |
| Camera/operator orientation | U [E] | iOS camera [TA] | Spectator upright [LW] | U [M] | Adapt | All upright on desktop/operator mode |
| Drag reorder seats | U [E] | Yes [TW] | Spectator ordering [LW] | U [M] | Adapt | Preserve identities, commander relations |
| Art crop/contain/cover | U [E] | iOS controls [TA] | Custom [LW]; crop U | U [M] | Adapt | Readable responsive art |
| Languages | iOS 10 [EA] | 12 [T] | U [L] | U [M] | Adapt | Localization-ready architecture; languages Pat rules |
| OLED/dark appearance | U [E] | iOS option [TA] | Dark [L] | U [M] | Adapt | Use app-wide light/dark tokens |
| Disable dice animation | U [E] | iOS setting [TA] | U [L] | U [M] | Adapt | Respect reduced-motion preference |
| Background music | U [E] | U [T] | U [L] | Themed [M] | Skip | Licensing/audio scope with low utility |
| Flavor text in menu | U [E] | U [T] | U [L] | Random [M] | Skip | Not core life-counter function |
| Companion/shop promotion | Token shop iOS [EA] | U [T] | Related sites [LW] | Separate brewer [MW] | Skip | Avoid unrelated promotion |
| Card search/prices/legalities | U [E] | iOS Scryfall query [TA] | Yes [L] | U [M] | Adapt | Reuse Command Library database/search |
| Live TV/spectator sharing | U [E] | U [T] | Code/QR [LW] | U [M] | Skip | Network shared state deferred |
| Shared recap links/voting/chat | U [E] | U [T] | Yes [LW] | U [M] | Skip | Online scope deferred; local history retained |
| Account/cloud sync | U [E] | Policy mentions optional; shipped U | Account sync [L] | U [M] | Skip | Sync post-release per brief |
| Full backup/import | U [E] | CSV/JSON iOS [TA]; full restore U | New backup [L] | U [M] | Build | Integrate required app backup, not account dependency |
| Ads/paid ad removal | IAP [E]/[EA] | No ads [TW] | No ads [L] | Premium ads [M] | Skip | No ad system needed for personal app |

## Options

1. Build the entire 2.G baseline, independently adapting the best documented usability details. This is the recommended reading of Appendix A's accepted parity-plus scope.
2. Add optional Planechase/Archenemy/Bounty modules now if Pat explicitly accepts the extra scope; investigate data completeness/terms separately.
3. Keep reference inventory to LifeElk/Lifetap plus Lotus/Mox under the disclosed established-app rule, or have Pat replace Mox with Rhystic/Auditor for a stars-only sample. Do not advertise these results as an exhaustive market ranking.

## Recommendation

Build every mandated baseline row even where all four competitors are U. Use stable player/commander/deck IDs, a local resumable event log, reversible adjustments, readable seat layouts and an upright desktop mode. Validate loss thresholds against the rules engine, show why a loss was detected, and permit overrides. Keep posture/size adaptation and offline gameplay as acceptance tests, not claims borrowed from store descriptions.

Adapt profiles, numeric entry, mana/object counter presets, draw results, timing modes and accessible crop/reduced-motion settings. Recommend optional variant modules for Pat's ruling; keep music, ads, cloud accounts, spectators and promotion out of initial scope. This recommendation is an independent design proposal, not a claim those features are unavailable in reference apps.

## Risks

- “Top two” remains UNVERIFIED as a worldwide/live ranking. The sample, establishment threshold, rating population and alternate higher-star candidates are disclosed above.
- Current Android versions and exact paid entitlement maps are UNVERIFIED because readable listings omit them; iOS releases do not prove Android parity. No app was installed or exercised.
- Absence of a feature in marketing text is not evidence of absence. All such cells explicitly say U, with the checked source/date.
- Offline full feature operation, arbitrary dice counts, general undo, partner handling in LifeElk Android, screen-awake and foldable posture require hands-on checks. Do not cut mandatory features because reference evidence is incomplete.
- Generic internet clauses coexist with locally stored game data. Card-art fetching and optional sharing must not block Command Library's offline life counter.
- Competitor card counts and screenshots are not reusable licensing grants. Independent implementation and source attribution remain required; R-02 controls data/asset rights.

## Confidence

High for the documented LifeElk section 10 feature list and explicit public descriptions in the cited dated listings. Medium for app metadata as a current locale snapshot and documented iOS extras. Low for a universal ranking, Android paid/version specifics, native Windows availability, and real offline/posture behavior. No confirmed conflict with GAUNTLET.md section 10 or Appendix A; their baseline remains intact. The established-cohort choice for the two extra apps is a transparent proposed interpretation for Pat's ruling.

[E]: https://play.google.com/store/apps/details?hl=en&id=com.bartollesina.lifetap
[EW]: https://lifeelk.com/
[EA]: https://apps.apple.com/us/app/mtg-life-counter-lifeelk/id6443751449
[T]: https://play.google.com/store/apps/details?hl=en&id=com.lifetap
[TW]: https://getlifetap.com/
[TA]: https://apps.apple.com/us/app/lifetap-life-counter-for-mtg/id1508241754
[L]: https://play.google.com/store/apps/details?hl=en&id=com.vanilla.mtgcounter
[LW]: https://lifecounter.app/pricing/
[M]: https://play.google.com/store/apps/details?hl=en&id=com.quince.magiccounterapp
[MW]: https://babydragondevelopment.com/
