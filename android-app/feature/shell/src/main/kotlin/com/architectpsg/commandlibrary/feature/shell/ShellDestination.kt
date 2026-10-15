package com.architectpsg.commandlibrary.feature.shell

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/** Only destinations whose screens work in this version; unbuilt features are absent. */
internal enum class ShellDestination(@param:StringRes val label: Int, @param:DrawableRes val icon: Int, val tag: String) {
    CARDS(R.string.shell_nav_cards, R.drawable.shell_ic_cards, "shell-nav-cards"),
    DECKS(R.string.decks_title, R.drawable.shell_ic_decks, "shell-nav-decks"),
    COLLECTION(R.string.collection_title, R.drawable.shell_ic_cards, "shell-nav-collection"),
    CATALOG(R.string.shell_nav_catalog, R.drawable.shell_ic_catalog, "shell-nav-catalog"),
    RULES(R.string.rules_title, R.drawable.shell_ic_catalog, "shell-nav-rules"),
    PROFILES(R.string.profiles_nav, R.drawable.shell_ic_profiles, "shell-nav-profiles"),
    SETTINGS(R.string.shell_nav_settings, R.drawable.shell_ic_settings, "shell-nav-settings"),
}
