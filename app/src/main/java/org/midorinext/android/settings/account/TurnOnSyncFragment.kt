/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings.account

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import mozilla.components.concept.sync.AccountObserver
import mozilla.components.concept.sync.AuthType
import mozilla.components.concept.sync.OAuthAccount
import mozilla.components.service.fxa.manager.SCOPE_PROFILE
import mozilla.components.service.fxa.manager.SCOPE_SYNC
import mozilla.components.support.ktx.android.content.hasCamera
import mozilla.components.support.ktx.android.content.isPermissionGranted
import mozilla.components.support.ktx.android.view.hideKeyboard
import org.midorinext.android.HomeActivity
import org.midorinext.android.R
import org.midorinext.android.components.MidoriSnackbar
import org.midorinext.android.databinding.FragmentTurnOnSyncBinding
import org.midorinext.android.ext.requireComponents
import org.midorinext.android.ext.settings
import org.midorinext.android.ext.showToolbar

class TurnOnSyncFragment : Fragment(), AccountObserver {

    private val args by navArgs<TurnOnSyncFragmentArgs>()
    private lateinit var interactor: DefaultSyncInteractor

    private var shouldLoginJustWithEmail = false
    private var pairWithEmailStarted = false

    private val signInClickListener = View.OnClickListener {
        navigateToPairWithEmail()
    }

    private val paringClickListener = View.OnClickListener {
        if (requireContext().settings().shouldShowCameraPermissionPrompt) {
            navigateToPairFragment()
        } else {
            if (requireContext().isPermissionGranted(Manifest.permission.CAMERA)) {
                navigateToPairFragment()
            } else {
                interactor.onCameraPermissionsNeeded()
                view?.hideKeyboard()
            }
        }
        view?.hideKeyboard()
        requireContext().settings().setCameraPermissionNeededState = false
    }

    private var _binding: FragmentTurnOnSyncBinding? = null
    private val binding get() = _binding!!

    private fun navigateToPairFragment() {
        val directions = TurnOnSyncFragmentDirections.actionTurnOnSyncFragmentToPairFragment(
            entrypoint = args.entrypoint,
        )
        requireView().findNavController().navigate(directions)
    }

    private val createAccountClickListener = View.OnClickListener {
        navigateToPairWithEmail()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireComponents.backgroundServices.accountManager.register(this, owner = this)

        // App can be installed on devices with no camera modules. Like Android TV boxes.
        // Let's skip presenting the option to sign in by scanning a qr code in this case
        // and default to login with email and password.
        shouldLoginJustWithEmail = !requireContext().hasCamera()
        if (shouldLoginJustWithEmail) {
            navigateToPairWithEmail()
        }
    }

    override fun onResume() {
        super.onResume()

        if (pairWithEmailStarted ||
            requireComponents.backgroundServices.accountManager.authenticatedAccount() != null
        ) {
            findNavController().popBackStack()
            return
        }

        if (shouldLoginJustWithEmail) {
            // Next time onResume is called, after returning from pairing with email this Fragment will be popped.
            pairWithEmailStarted = true
        } else {
            requireComponents.backgroundServices.accountManager.register(this, owner = this)
            showToolbar(getString(R.string.preferences_sync_2))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (shouldLoginJustWithEmail) {
            // Headless fragment. Don't need UI if we're taking the user to another screen.
            return null
        }
        _binding = FragmentTurnOnSyncBinding.inflate(inflater, container, false)

        binding.signInScanButton.setOnClickListener(paringClickListener)
        binding.signInEmailButton.setOnClickListener(signInClickListener)
        binding.signInInstructions.text = HtmlCompat.fromHtml(
            getString(R.string.sign_in_instructions),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        interactor = DefaultSyncInteractor(
            DefaultSyncController(activity = activity as HomeActivity)
        )

        binding.createAccount.apply {
            text = HtmlCompat.fromHtml(
                getString(R.string.sign_in_create_account_text),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
            setOnClickListener(createAccountClickListener)
        }
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }

    override fun onAuthenticated(account: OAuthAccount, authType: AuthType) {
        // If we're in a `shouldLoginJustWithEmail = true` state, we won't have a view available,
        // and can't display a snackbar.
        if (view == null) {
            return
        }
        val snackbarText = requireContext().getString(R.string.sync_syncing_in_progress)
        val snackbarLength = MidoriSnackbar.LENGTH_SHORT

        // Since the snackbar can be presented in BrowserFragment or in SettingsFragment we must
        // base our display method on the padSnackbar argument
        MidoriSnackbar.make(
            view = requireView(),
            duration = snackbarLength,
            isDisplayedWithBrowserToolbar = args.padSnackbar
        )
            .setText(snackbarText)
            .show()
    }

    private fun navigateToPairWithEmail() {
        requireComponents.services.accountsAuthFeature.beginAuthentication(
            requireContext(),
            entrypoint = args.entrypoint,
            setOf(SCOPE_PROFILE, SCOPE_SYNC),
        )
        // TODO The sign-in web content populates session history,
        // so pressing "back" after signing in won't take us back into the settings screen, but rather up the
        // session history stack.
        // We could auto-close this tab once we get to the end of the authentication process?
        // Via an interceptor, perhaps.
    }
}
