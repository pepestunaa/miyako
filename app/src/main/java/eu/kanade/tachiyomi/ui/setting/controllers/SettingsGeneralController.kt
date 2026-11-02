package eu.kanade.tachiyomi.ui.setting.controllers

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.BuildConfig
import yokai.i18n.MR
import yokai.util.lang.getString
import eu.kanade.tachiyomi.data.updater.AppDownloadInstallJob
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import eu.kanade.tachiyomi.ui.setting.ThemePreference
import eu.kanade.tachiyomi.ui.setting.bindTo
import eu.kanade.tachiyomi.ui.setting.defaultValue
import eu.kanade.tachiyomi.ui.setting.intListPreference
import eu.kanade.tachiyomi.ui.setting.listPreference
import eu.kanade.tachiyomi.ui.setting.onChange
import eu.kanade.tachiyomi.ui.setting.onClick
import eu.kanade.tachiyomi.ui.setting.preference
import eu.kanade.tachiyomi.ui.setting.preferenceCategory
import eu.kanade.tachiyomi.ui.setting.summaryMRes as summaryRes
import eu.kanade.tachiyomi.ui.setting.switchPreference
import eu.kanade.tachiyomi.ui.setting.titleMRes as titleRes
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import yokai.domain.base.BasePreferences
import java.util.*
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsGeneralController : SettingsLegacyController() {

    private val isUpdaterEnabled = BuildConfig.INCLUDE_UPDATER

    var lastThemeXLight: Int? = null
    var lastThemeXDark: Int? = null
    var themePreference: ThemePreference? = null

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = MR.strings.general

        intListPreference(activity) {
            key = Keys.startingTab
            titleRes = MR.strings.starting_screen
            summaryRes = when (preferences.startingTab().get()) {
                -1 -> MR.strings.library
                -2 -> MR.strings.recents
                -3 -> MR.strings.browse
                else -> MR.strings.last_used_library_recents
            }
            entriesRes = arrayOf(
                MR.strings.last_used_library_recents,
                MR.strings.library,
                MR.strings.recents,
                MR.strings.browse,
            )
            entryValues = (0 downTo -3).toList()
            defaultValue = 0
            customSelectedValue = when (val value = preferences.startingTab().get()) {
                in -3..-1 -> value
                else -> 0
            }

            onChange { newValue ->
                summaryRes = when (newValue) {
                    0, 1 -> MR.strings.last_used_library_recents
                    -1 -> MR.strings.library
                    -2 -> MR.strings.recents
                    -3 -> MR.strings.browse
                    else -> MR.strings.last_used_library_recents
                }
                customSelectedValue = when (newValue) {
                    in -3..-1 -> newValue as Int
                    else -> 0
                }
                true
            }
        }

        switchPreference {
            key = Keys.backToStart
            titleRes = MR.strings.back_to_start
            summaryRes = MR.strings.pressing_back_to_start
            defaultValue = true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            preference {
                key = "pref_manage_notifications"
                titleRes = MR.strings.pref_manage_notifications
                onClick {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    startActivity(intent)
                }
            }
        }

        preferenceCategory {
            titleRes = MR.strings.app_shortcuts

            switchPreference {
                key = Keys.showSeriesInShortcuts
                titleRes = MR.strings.show_recent_series
                summaryRes = MR.strings.includes_recently_read_updated_added
                defaultValue = true
            }

            switchPreference {
                key = Keys.showSourcesInShortcuts
                titleRes = MR.strings.show_recent_sources
                defaultValue = true
            }

            switchPreference {
                key = Keys.openChapterInShortcuts
                titleRes = MR.strings.series_opens_new_chapters
                summaryRes = MR.strings.no_new_chapters_open_details
                defaultValue = true
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isUpdaterEnabled) {
            preferenceCategory {
                titleRes = MR.strings.auto_updates

                intListPreference(activity) {
                    key = Keys.shouldAutoUpdate
                    titleRes = MR.strings.auto_update_app
                    entryRange = 0..2
                    entriesRes = arrayOf(MR.strings.over_any_network, MR.strings.over_wifi_only, MR.strings.dont_auto_update)
                    defaultValue = AppDownloadInstallJob.ONLY_ON_UNMETERED
                }
            }
        }

        preferenceCategory {
            titleRes = MR.strings.locale
            listPreference(activity) {
                key = Keys.dateFormat
                titleRes = MR.strings.date_format
                entryValues = listOf("", "MM/dd/yy", "dd/MM/yy", "yyyy-MM-dd")
                entries = entryValues.map { value ->
                    if (value == "") {
                        context.getString(MR.strings.system_default)
                    } else {
                        value
                    }
                }
                defaultValue = ""
            }
        }

        preferenceCategory {
            titleRes = MR.strings.navigation

            listPreference(activity) {
                bindTo(basePreferences.longTapRecentsNavBehaviour())
                titleRes = MR.strings.recents_long_tap

                val values = BasePreferences.LongTapRecents.entries.toList()
                entriesRes = values.map { it.titleResId }.toTypedArray()
                entryValues = values.map { it.name }.toTypedArray().toList()
            }

            listPreference(activity) {
                bindTo(basePreferences.longTapBrowseNavBehaviour())
                titleRes = MR.strings.browse_long_tap

                val values = BasePreferences.LongTapBrowse.entries.toList()
                entriesRes = values.map { it.titleResId }.toTypedArray()
                entryValues = values.map { it.name }.toTypedArray().toList()
            }
        }
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        themePreference = null
    }

    override fun onSaveViewState(view: View, outState: Bundle) {
        outState.putInt(::lastThemeXLight.name, themePreference?.lastScrollPostionLight ?: 0)
        outState.putInt(::lastThemeXDark.name, themePreference?.lastScrollPostionDark ?: 0)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreViewState(view: View, savedViewState: Bundle) {
        super.onRestoreViewState(view, savedViewState)
        lastThemeXLight = savedViewState.getInt(::lastThemeXLight.name)
        lastThemeXDark = savedViewState.getInt(::lastThemeXDark.name)
        themePreference?.lastScrollPostionLight = lastThemeXLight
        themePreference?.lastScrollPostionDark = lastThemeXDark
    }
}
