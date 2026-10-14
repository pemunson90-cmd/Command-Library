# Card keyword local rules handoff

Card details expose each known keyword as a48dp action only when the complete local rules capability exists. Selecting one clears the former search/detail, submits the literal keyword to the existing bounded local rules search, and navigates to Rules. Missing corpus remains NOT_INSTALLED; missing capability retains literal text. There is no implicit import, acquisition initialization, guessed RuleId or claim of canonical keyword-to-rule mapping.

Focused local gate passed37s: RulesControllerTest12 and CardKeywordUiTest3, two XML reports with zero failures/errors/skips; shell Android test Kotlin and actual app Kotlin compiled. Compose coverage uses synthetic local ports, checks navigation, original query, minimum target height, no import, absent corpus and absent capability. Controller coverage also rejects an overlong handoff after clearing old selected detail/results. Log /tmp/card-keyword-rule-lookup.log. This evidence is newer than the frozen4a4241b whole1108 run and is not device or full-corpus acceptance.

Plan: docs/superpowers/plans/2026-10-14-card-keyword-rule-lookup.md. Direct canonical keyword-to-RuleId mapping remains separately incomplete.
