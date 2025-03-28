/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.tabstray.browser.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.tabstray.SelectableTabViewHolder
import org.midorinext.android.compose.ComposeViewHolder
import org.midorinext.android.theme.MidoriTheme
import org.midorinext.android.theme.Theme

/**
 * [RecyclerView.ViewHolder] used for Jetpack Compose UI content .
 *
 * @param composeView [ComposeView] which will be populated with Jetpack Compose UI content.
 * @param viewLifecycleOwner [LifecycleOwner] life cycle owner for the view.
 */
abstract class ComposeAbstractTabViewHolder(
    private val composeView: ComposeView,
    private val viewLifecycleOwner: LifecycleOwner,
) : SelectableTabViewHolder(composeView) {

    /**
     * Composable that contains the content for a specific [ComposeViewHolder] implementation.
     */
    @Composable
    abstract fun Content(tab: TabSessionState)

    /**
     * Binds the a  composable to the [composeView].
     */
    fun bind(tab: TabSessionState) {
        composeView.setContent {
            MidoriTheme(theme = Theme.getTheme(allowPrivateTheme = tab.content.private)) {
                Content(tab)
            }
        }

        composeView.setViewTreeLifecycleOwner(viewLifecycleOwner)
        composeView.setViewTreeSavedStateRegistryOwner(
            viewLifecycleOwner as SavedStateRegistryOwner,
        )
    }
}
