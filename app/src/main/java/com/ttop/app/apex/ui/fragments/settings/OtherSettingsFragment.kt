/*
 * Copyright (c) 2020 Hemanth Savarla.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
package com.ttop.app.apex.ui.fragments.settings

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import com.ttop.app.appthemehelper.common.prefs.supportv7.ATEListPreference
import com.ttop.app.apex.ui.fragments.LibraryViewModel
import com.ttop.app.apex.ui.fragments.ReloadType.HomeSections
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.ttop.app.apex.*
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import java.util.*

/**
 * @author Hemanth S (h4h13).
 */

class OtherSettingsFragment : AbsSettingsFragment() {
    private val libraryViewModel by sharedViewModel<LibraryViewModel>()

    override fun invalidateSettings() {
        val languagePreference: ATEListPreference? = findPreference(LANGUAGE_NAME)
        languagePreference?.isEnabled = false
        languagePreference?.isVisible = false
        /*languagePreference?.setOnPreferenceChangeListener { _, _ ->
            println("Invalidated")
            restartActivity()
            return@setOnPreferenceChangeListener true
        }*/

        val whitelist: TwoStatePreference? = findPreference(WHITELIST_MUSIC)
        whitelist?.setOnPreferenceChangeListener { _, _ ->
            requireView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }

        val pause: TwoStatePreference? = findPreference(PAUSE_ON_ZERO_VOLUME)
        pause?.setOnPreferenceChangeListener { _, _ ->
            requireView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }

        val keepScreenOn: TwoStatePreference? = findPreference(KEEP_SCREEN_ON)
        keepScreenOn?.setOnPreferenceChangeListener { _, _ ->
            requireView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_advanced)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val preference: Preference? = findPreference(LAST_ADDED_CUTOFF)
        preference?.setOnPreferenceChangeListener { lastAdded, newValue ->
            setSummary(lastAdded, newValue)
            libraryViewModel.forceReload(HomeSections)
            true
        }
        val languagePreference: Preference? = findPreference(LANGUAGE_NAME)
        languagePreference?.setOnPreferenceChangeListener { prefs, newValue ->
            setSummary(prefs, newValue)
            val code = newValue.toString()
            val manager = SplitInstallManagerFactory.create(requireContext())
            if (code != "auto") {
                // Try to download language resources
                val request =
                    SplitInstallRequest.newBuilder().addLanguage(Locale.forLanguageTag(code))
                        .build()
                manager.startInstall(request)
                    // Recreate the activity on download complete
                    .addOnCompleteListener {
                        restartActivity()
                    }
            } else {
                requireActivity().recreate()
            }
            true
        }
    }
}
