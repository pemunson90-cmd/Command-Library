# DESIGN SYSTEM

Gate 2 normative design specification. Targets below are design choices, not evidence of rendered UI. Applies to every screen in SCREENS.

## Principles

Text and actions stay useful offline. Card identity and acquisition cost are easy to scan; rarity/art never competes with legality or user state. Show uncertainty where it matters. Separate provider context from game rules. Use progressive disclosure for advanced options while retaining equivalent keyboard/screen-reader actions. Important game controls must never sit beneath a hinge.

## Tokens

Logical pixels/dp, never raw hardware resolution. Base spacing4; scale4/8/12/16/24/32/48. Screen gutters compact16, medium24, expanded+32; minimum content separation8. Corners: input8, tile12, surface16, dialogs20; counter controls may use pill shape but maintain full target. Borders1, focus ring2 with2 offset. Elevation0/1/3/6 corresponding flat/row-hover/popover/dialog; contrast never depends on shadow.

| Token | Light | Dark | Role |
|---|---|---|---|
| canvas | #F6F5F1 | #101718 | Window background |
| surface | #FFFFFF | #192324 | Cards/dialogs |
| text | #1C242A | #E5ECE9 | Body/headings |
| secondary | #4B555C | #B6C4C0 | Supporting labels |
| accent | #166B5C | #70D9BC | Primary action/link |
| onAccent | #FFFFFF | #101718 | Filled action text |
| error | #B42318 | #FFB4A9 | Error with label/icon |
| outline | #67757D | #899C97 | Interactive boundary |
| focus | #155E91 | #9BCEFF | Visible keyboard focus |

Typography: platform sans family with script fallback; never custom font required for app startup. Body16 line24, compact supporting14 line20, title22 line28, section18 line24, hero28 line36. Counts use tabular numerals, counters default48 adjustable by available height. Respect OS text scale through200% and beyond via reflow; no forced fixed-height labels, clipping or text-scale disabling. Metadata smaller than14 only within nonessential art decoration, never critical prices/rules.

Motion: immediate state feedback,100ms hover/press,180ms panel transitions,240ms dialogs; no bounce or autoplay decorative animation. Honor reduced motion and disable nonessential transitions; streaming never continuously scrolls away from user-selected reading position.

## Themes, identity and mana

Light/dark/system selectable; Windows high-contrast mode (v2, D-19) substitutes system palette while retaining focus/labels. Color accents W #EEE6B9, U #3C7FB3, B #605266, R #B84336, G #357B4B, colorless #64757D, multicolor neutral surface plus segmented identity markers. These accents are decorative unless paired with text/icon; they are not assumed valid body-text backgrounds. White/colorless names stay distinct by label/symbol. Actual mana symbols need audited attribution/licensing; text fallback such as {W} remains legible. Icons use consistent stroke/size20-24, explicit accessible names, no unlabeled custom glyph font.

## Component contract and state inventory

| Component | Default and content | Hover/focus/pressed | Disabled/error/loading |
|---|---|---|---|
| Button/icon action | Verb plus appropriate icon; target >=48 Android logical pixels, >=44 Windows (v2, D-19) | Hover tint; focus2 ring; pressed clear immediate response | Disabled explains dependency; busy retains label and cancel where safe; error adjacent, no inert handler |
| Text/select/number input | Persistent label, helper/unit, selection preserved | Standard caret/keyboard; focus independent of validation | Error text references field; retain input; loading select permits manual provider model |
| Card row/grid | Name, cost (n), identity/type, selected printing/finish, ownership | Entire row focusable; secondary menu separate target; hover reveals market quote without hiding n | Text/no-art fallback; unknown price label, not blank zero; chosen state includes check/label |
| Pane/navigation | Stable route labels, selected marker, context-sensitive back | Tab/focus order matches reading order; visible resize divider | Unbuilt destination absent; empty uses actual next action, not dummy sample |
| Chip/legality/bracket badge | Text plus icon for state | Focus if actionable; noninteractive badge no false button semantics | Unknown distinct from legal; intent questions and source date reachable |
| Inline quote | n displayed with unit context; market/source details nearby | Hover/tap opens market/statistic/date/unknown details | Unknown uses (unknown); partial deck total shows known subtotal + missing quantity |
| Toast/banner/dialog | Plain outcome, primary recovery/dismiss action | Focus trap only true modal; return focus to origin | Alert persists if loss/data error needs response; no automatic dismissal of important error |
| Progress/download | Phase, bytes/records where measured, total when known | Pause/cancel actual operation; keyboard accessible | Indeterminate if total unknown; no fake percent; failures retain resumable data |
| Life tile | Upright labels/seat orientation, life plus status, commander/counter entry | +/- controls clear; hold state shows repeat | Separate undo/log; loss has reason/override; hidden counters keep values |
| Data table/chart | Headers, totals, legends and textual summary | Sort explicit and announced; keyboard row actions | Unknown segments separate; chart never sole way to read analysis |
| Conversation | Mode/provider/model, attributed partial/final text, tappable cards | Select/copy text, pause autoscroll, focused cards | Interrupted/error labeled; retry explicit; no generated advice applied on token arrival |

All components must have default/hover/focus/pressed/disabled/error where applicable; loading/empty/partial/offline/stale distinct. A decorative element need not pretend to handle input.

## Card rendering

List: name/quantity/(n) primary; printing/finish/language and legality secondary. Grid: fixed art aspect when available, name and price readable outside image. Detail: Oracle text/rulings always primary offline; face selector preserves selected printing and supports transform/split/adventure/flip/meld explicitly. List aggregation never loses per-printing inventory identity. Category headers count entries once per physical deck demand. Price unit/source labels appear in header and quote disclosure; parentheses alone cannot communicate unknown or policy.

## Layout rules

Width classes compact<600, medium600-839, expanded840-1199, large1200-1599, extra-large>=1600. Height override compact<480: collapse tertiary areas and allow vertical scrolling instead of crushing targets. See SCREENS for every screen across these classes.

Compact uses one active pane, bottom navigation plus More only if overflow; medium navigation rail and one pane with optional modal detail; expanded list/detail two panes; large adds supporting pane; XL adds supporting panes without widening text beyond80 characters. Rule text maximum80-character measure, centered or docked in reading pane. Game seats use all usable regions rather than reading-column cap.

Hinge region treated as excluded geometry including safety inset8. Tabletop places gameplay tiles on lower usable region and log/options upper; book allocates separate side regions; single-screen feature spans only safe regions. Never draw a drag handle or modal primary action across hinge. If safe regions cannot fit baseline targets, show one seat at a time with explicit seat switching while preserving totals and event state. Hinge detection does not follow width alone; manual override is available when platform signals cannot be obtained.

## Desktop interaction

Applies in version 1 on Android with external keyboard, mouse, large screens, foldables and resizable windows. Windows window chrome (v2, D-19).

Ctrl+K search, Ctrl+N new deck, Ctrl+S flush/save current edit, Ctrl+Z undo within focused feature, Ctrl+Shift+Z redo where supported, Esc dismiss/cancel current transient UI, F1 help, Alt+Left route back. Do not override editing shortcuts inside inputs. Right-click menus duplicate visible actions. Drag/drop has command equivalents and never commits imported file without preview. Window minimum usable320x360 logical pixels; below that constrain supported resize or scroll, no silent lost controls.

## Accessibility and audit

Proposed minimum contrast: normal text4.5:1, large text3:1, control/focus boundary3:1; validate every rendered color pairing including art overlays, states and identity accents. Targets >=48x48 Android, >=44x44 Windows desktop (v2, D-19); adjacent hit regions do not overlap. Screen-reader labels include card name/quantity/acquisition cost policy, seat/player/counter and exact action. Counters announce grouped adjustments rather than every hold tick; streamed tokens are not read individually. Announce final response or user-requested passage.

TalkBack (Narrator: v2, D-19) must perform search, card detail/face, deck edit, inventory, life adjustments, import preview and key entry on release-like packages. Tab traversal follows visible reading order; modal trap exits and returns focus. Focus survives pane reparenting/fold changes. Golden screenshots alone cannot verify readers/real sensors. ui-auditor records tokens, all states, text200%, compact-height, five widths and tabletop/book for each screen; attach outcomes to milestone reports.
