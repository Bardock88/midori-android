/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.components

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.StrictMode
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import com.google.android.play.core.review.ReviewManagerFactory
import mozilla.components.feature.addons.AddonManager
import mozilla.components.feature.addons.amo.AddonCollectionProvider
import mozilla.components.feature.addons.migration.DefaultSupportedAddonsChecker
import mozilla.components.feature.addons.update.DefaultAddonUpdater
import mozilla.components.feature.autofill.AutofillConfiguration
import mozilla.components.lib.publicsuffixlist.PublicSuffixList
import mozilla.components.support.base.android.NotificationsDelegate
import mozilla.components.support.base.worker.Frequency
import mozilla.components.support.locale.LocaleManager
import org.midorinext.android.BuildConfig
import org.midorinext.android.Config
import org.midorinext.android.R
import org.midorinext.android.autofill.AutofillConfirmActivity
import org.midorinext.android.autofill.AutofillSearchActivity
import org.midorinext.android.autofill.AutofillUnlockActivity
import org.midorinext.android.components.appstate.AppState
import org.midorinext.android.ext.asRecentTabs
import org.midorinext.android.ext.components
import org.midorinext.android.ext.filterState
import org.midorinext.android.ext.sort
import org.midorinext.android.home.blocklist.BlocklistHandler
import org.midorinext.android.home.blocklist.BlocklistMiddleware
import org.midorinext.android.perf.AppStartReasonProvider
import org.midorinext.android.perf.StartupActivityLog
import org.midorinext.android.perf.StartupStateProvider
import org.midorinext.android.perf.StrictModeManager
import org.midorinext.android.perf.lazyMonitored
import org.midorinext.android.utils.ClipboardHandler
import org.midorinext.android.utils.Settings
import org.midorinext.android.wallpapers.WallpaperDownloader
import org.midorinext.android.wallpapers.WallpaperFileManager
import org.midorinext.android.wallpapers.WallpaperManager
import org.midorinext.android.wifi.WifiConnectionMonitor
import java.util.concurrent.TimeUnit

private const val AMO_COLLECTION_MAX_CACHE_AGE = 2 * 24 * 60L // Two days in minutes

/**
 * Provides access to all components. This class is an implementation of the Service Locator
 * pattern, which helps us manage the dependencies in our app.
 *
 * Note: these aren't just "components" from "android-components": they're any "component" that
 * can be considered a building block of our app.
 */
class Components(private val context: Context) {
    val backgroundServices by lazyMonitored {
        BackgroundServices(
            context,
            push,
            analytics.crashReporter,
            core.lazyHistoryStorage,
            core.lazyBookmarksStorage,
            core.lazyPasswordsStorage,
            core.lazyRemoteTabsStorage,
            core.lazyAutofillStorage,
            strictMode
        )
    }
    val services by lazyMonitored { Services(context, backgroundServices.accountManager) }
    val core by lazyMonitored { Core(context, analytics.crashReporter, strictMode) }
    @Suppress("Deprecation")
    val useCases by lazyMonitored {
        UseCases(
            context,
            core.engine,
            core.store,
            core.webAppShortcutManager,
            core.topSitesStorage,
            core.bookmarksStorage,
            core.historyStorage
        )
    }

    private val notificationManagerCompat = NotificationManagerCompat.from(context)

    val notificationsDelegate: NotificationsDelegate by lazyMonitored {
        NotificationsDelegate(
            notificationManagerCompat,
        )
    }

    val intentProcessors by lazyMonitored {
        IntentProcessors(
            context,
            core.store,
            useCases.sessionUseCases,
            useCases.tabsUseCases,
            useCases.customTabsUseCases,
            useCases.searchUseCases,
            core.webAppManifestStorage,
            core.engine,
        )
    }

    val addonCollectionProvider by lazyMonitored {
        // Use build config if specified
        if (!BuildConfig.AMO_COLLECTION_USER.isNullOrEmpty() &&
            !BuildConfig.AMO_COLLECTION_NAME.isNullOrEmpty()
        ) {
            AddonCollectionProvider(
                context,
                core.client,
                serverURL = BuildConfig.AMO_SERVER_URL,
                collectionUser = BuildConfig.AMO_COLLECTION_USER,
                collectionName = BuildConfig.AMO_COLLECTION_NAME,
                maxCacheAgeInMinutes = AMO_COLLECTION_MAX_CACHE_AGE
            )
        }
        // Fall back to defaults otherwise
        else {
            AddonCollectionProvider(context, core.client, maxCacheAgeInMinutes = AMO_COLLECTION_MAX_CACHE_AGE)
        }
    }

    @Suppress("MagicNumber")
    val addonUpdater by lazyMonitored {
        DefaultAddonUpdater(context, Frequency(12, TimeUnit.HOURS), notificationsDelegate)
    }

    @Suppress("MagicNumber")
    val supportedAddonsChecker by lazyMonitored {
        DefaultSupportedAddonsChecker(
            context,
            Frequency(12, TimeUnit.HOURS)
        )
    }

    val addonManager by lazyMonitored {
        AddonManager(core.store, core.engine, addonCollectionProvider, addonUpdater)
    }

    val analytics by lazyMonitored { Analytics(context) }
    val publicSuffixList by lazyMonitored { PublicSuffixList(context) }
    val clipboardHandler by lazyMonitored { ClipboardHandler(context) }
    val performance by lazyMonitored { PerformanceComponent() }
    val push by lazyMonitored { Push(context, analytics.crashReporter) }
    val wifiConnectionMonitor by lazyMonitored { WifiConnectionMonitor(context as Application) }
    val strictMode by lazyMonitored { StrictModeManager(Config, this) }

    val wallpaperManager by lazyMonitored {
        val currentLocale = strictMode.resetAfter(StrictMode.allowThreadDiskReads()) {
            LocaleManager.getCurrentLocale(context)?.toLanguageTag()
                ?: LocaleManager.getSystemDefault().toLanguageTag()
        }
        strictMode.resetAfter(StrictMode.allowThreadDiskReads()) {
            WallpaperManager(
                settings,
                appStore,
                WallpaperDownloader(context, core.client),
                WallpaperFileManager(context.filesDir),
                currentLocale
            )
        }
    }

    val settings by lazyMonitored { Settings(context) }

    val reviewPromptController by lazyMonitored {
        ReviewPromptController(
            manager = ReviewManagerFactory.create(context),
            reviewSettings = MidoriReviewSettings(settings)
        )
    }

    @delegate:SuppressLint("NewApi")
    val autofillConfiguration by lazyMonitored {
        AutofillConfiguration(
            storage = core.passwordsStorage,
            publicSuffixList = publicSuffixList,
            unlockActivity = AutofillUnlockActivity::class.java,
            confirmActivity = AutofillConfirmActivity::class.java,
            searchActivity = AutofillSearchActivity::class.java,
            applicationName = context.getString(R.string.app_name),
            httpClient = core.client
        )
    }

    val appStartReasonProvider by lazyMonitored { AppStartReasonProvider() }
    val startupActivityLog by lazyMonitored { StartupActivityLog() }
    val startupStateProvider by lazyMonitored { StartupStateProvider(startupActivityLog, appStartReasonProvider) }

    val appStore by lazyMonitored {
        val blocklistHandler = BlocklistHandler(settings)

        AppStore(
            initialState = AppState(
                collections = core.tabCollectionStorage.cachedTabCollections,
                expandedCollections = emptySet(),
                topSites = core.topSitesStorage.cachedTopSites.sort(),
                recentBookmarks = emptyList(),
                showCollectionPlaceholder = settings.showCollectionsPlaceholderOnHome,
                // Provide an initial state for recent tabs to prevent re-rendering on the home screen.
                //  This will otherwise cause a visual jump as the section gets rendered from no state
                //  to some state.
                recentTabs = if (settings.showRecentTabsFeature) {
                    core.store.state.asRecentTabs()
                } else {
                    emptyList()
                },
                recentHistory = emptyList()
            ).run { filterState(blocklistHandler) },
            middlewares = listOf(
                BlocklistMiddleware(blocklistHandler)
            )
        )
    }
}

/**
 * Returns the [Components] object from within a [Composable].
 */
val components: Components
    @Composable
    get() = LocalContext.current.components