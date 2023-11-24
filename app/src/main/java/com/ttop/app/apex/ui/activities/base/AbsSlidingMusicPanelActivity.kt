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
package com.ttop.app.apex.ui.activities.base

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.animation.doOnEnd
import androidx.core.view.*
import androidx.fragment.app.commit
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.*
import com.ttop.app.apex.*
import com.ttop.app.apex.databinding.SlidingMusicPanelLayoutBinding
import com.ttop.app.apex.extensions.*
import com.ttop.app.apex.helper.MusicPlayerRemote
import com.ttop.app.apex.model.CategoryInfo
import com.ttop.app.apex.ui.activities.AppIntroActivity
import com.ttop.app.apex.ui.fragments.LibraryViewModel
import com.ttop.app.apex.ui.fragments.NowPlayingScreen
import com.ttop.app.apex.ui.fragments.NowPlayingScreen.*
import com.ttop.app.apex.ui.fragments.base.AbsPlayerFragment
import com.ttop.app.apex.ui.fragments.other.MiniPlayerFragment
import com.ttop.app.apex.ui.fragments.player.adaptive.AdaptiveFragment
import com.ttop.app.apex.ui.fragments.player.blur.BlurPlayerFragment
import com.ttop.app.apex.ui.fragments.player.card.CardFragment
import com.ttop.app.apex.ui.fragments.player.color.ColorFragment
import com.ttop.app.apex.ui.fragments.player.flat.FlatPlayerFragment
import com.ttop.app.apex.ui.fragments.player.gradient.GradientPlayerFragment
import com.ttop.app.apex.ui.fragments.player.md3.MD3PlayerFragment
import com.ttop.app.apex.ui.fragments.player.normal.PlayerFragment
import com.ttop.app.apex.ui.fragments.player.peek.PeekPlayerFragment
import com.ttop.app.apex.ui.fragments.player.plain.PlainPlayerFragment
import com.ttop.app.apex.ui.fragments.player.simple.SimplePlayerFragment
import com.ttop.app.apex.ui.fragments.player.tiny.TinyPlayerFragment
import com.ttop.app.apex.ui.fragments.queue.PlayingQueueFragment
import com.ttop.app.apex.util.*
import com.ttop.app.appthemehelper.util.VersionUtils
import org.koin.androidx.viewmodel.ext.android.viewModel


abstract class AbsSlidingMusicPanelActivity : AbsMusicServiceActivity(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        val TAG: String = AbsSlidingMusicPanelActivity::class.java.simpleName
    }

    var fromNotification = false
    private var windowInsets: WindowInsetsCompat? = null
    protected val libraryViewModel by viewModel<LibraryViewModel>()
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>
    private lateinit var playerFragment: AbsPlayerFragment
    private var miniPlayerFragment: MiniPlayerFragment? = null
    private var nowPlayingScreen: NowPlayingScreen? = null
    private var taskColor: Int = 0
    private var paletteColor: Int = Color.WHITE
    private var navigationBarColor = 0

    private val panelState: Int
        get() = bottomSheetBehavior.state
    private lateinit var binding: SlidingMusicPanelLayoutBinding
    private var isInOneTabMode = false

    private var navigationBarColorAnimator: ValueAnimator? = null
    private val argbEvaluator: ArgbEvaluator = ArgbEvaluator()

    private val bottomSheetCallbackList by lazy {
        object : BottomSheetBehavior.BottomSheetCallback() {

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                setMiniPlayerAlphaProgress(slideOffset)
                navigationBarColorAnimator?.cancel()
                setNavigationBarColorPreOreo(
                    argbEvaluator.evaluate(
                        slideOffset,
                        surfaceColor(),
                        navigationBarColor
                    ) as Int
                )
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    STATE_EXPANDED -> {
                        onPanelExpanded()
                        if (PreferenceUtil.lyricsScreenOn && PreferenceUtil.showLyrics) {
                            keepScreenOn(true)
                        }
                    }
                    STATE_COLLAPSED -> {
                        onPanelCollapsed()
                        if ((PreferenceUtil.lyricsScreenOn && PreferenceUtil.showLyrics) || !PreferenceUtil.isScreenOnEnabled) {
                            keepScreenOn(false)
                        }

                        if (PreferenceUtil.isWidgetPanel && ApexUtil.isTablet) {
                            PreferenceUtil.isWidgetPanel = false
                            postRecreate()
                        }
                    }
                    STATE_SETTLING, STATE_DRAGGING -> {
                        if (fromNotification) {
                            binding.navigationView.bringToFront()
                            fromNotification = false
                        }
                    }
                    STATE_HIDDEN -> {
                        MusicPlayerRemote.clearQueue()
                    }
                    else -> {
                        logD("Do a flip")
                    }
                }
            }
        }
    }

    fun getBottomSheetBehavior() = bottomSheetBehavior

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (VersionUtils.hasR()) {
            if (!hasPermissions()) {
                startActivity(
                    Intent(
                        this@AbsSlidingMusicPanelActivity,
                        AppIntroActivity()::class.java
                    )
                )
                finish()
            } else {
                if (!IntroPrefs(applicationContext).hasIntroShown) {
                    startActivity(
                        Intent(
                            this@AbsSlidingMusicPanelActivity,
                            AppIntroActivity()::class.java
                        )
                    )
                    finish()
                }
            }
        } else {
            if (!IntroPrefs(applicationContext).hasIntroShown) {
                startActivity(
                    Intent(
                        this@AbsSlidingMusicPanelActivity,
                        AppIntroActivity()::class.java
                    )
                )
                finish()
            }
        }

        binding = SlidingMusicPanelLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.setOnApplyWindowInsetsListener { _, insets ->
            windowInsets = WindowInsetsCompat.toWindowInsetsCompat(insets)
            insets
        }
        chooseFragmentForTheme()
        setupSlidingUpPanel()
        setupBottomSheet()
        updateColor()

        binding.slidingPanel.backgroundTintList = ColorStateList.valueOf(darkAccentColor())
        navigationView.backgroundTintList = ColorStateList.valueOf(darkAccentColor())

        navigationBarColor = surfaceColor()

        //val layoutParams = (binding.linearLayout?.layoutParams as? ViewGroup.MarginLayoutParams)
        //layoutParams?.setMargins(0, ApexUtil.statusBarHeight, 0, 0)
        //binding.linearLayout?.layoutParams = layoutParams
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior = from(binding.slidingPanel)
        bottomSheetBehavior.addBottomSheetCallback(bottomSheetCallbackList)
        bottomSheetBehavior.isHideable = PreferenceUtil.dismissMethod == "swipe"
        bottomSheetBehavior.significantVelocityThreshold = 300
        setMiniPlayerAlphaProgress(0F)
    }

    override fun onResume() {
        super.onResume()
        PreferenceUtil.registerOnSharedPreferenceChangedListener(this)
        if (nowPlayingScreen != PreferenceUtil.nowPlayingScreen) {
            postRecreate()
        }

        if (bottomSheetBehavior.state == STATE_EXPANDED) {
            setMiniPlayerAlphaProgress(1f)
        }

        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!handleBackPress()) {
                    remove()
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        bottomSheetBehavior.removeBottomSheetCallback(bottomSheetCallbackList)
        PreferenceUtil.unregisterOnSharedPreferenceChangedListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            DISMISS_METHOD -> {
                bottomSheetBehavior.isHideable = PreferenceUtil.dismissMethod == "swipe"
                recreate()
            }
            TOGGLE_ADD_CONTROLS -> {
                miniPlayerFragment?.setUpButtons()
            }
            NOW_PLAYING_SCREEN_ID -> {
                chooseFragmentForTheme()
                binding.slidingPanel.updateLayoutParams<ViewGroup.LayoutParams> {
                    height = if (nowPlayingScreen != Peek) {
                        ViewGroup.LayoutParams.MATCH_PARENT
                    } else {
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                    onServiceConnected()
                }
            }
            ALBUM_COVER_TRANSFORM, CAROUSEL_EFFECT,
            ALBUM_COVER_STYLE, EXTRA_SONG_INFO, CIRCLE_PLAY_BUTTON,
            -> {
                chooseFragmentForTheme()
                onServiceConnected()
            }
            SWIPE_ANYWHERE_NOW_PLAYING -> {
                playerFragment.addSwipeDetector()
            }
            ADAPTIVE_COLOR_APP-> {
                if (PreferenceUtil.nowPlayingScreen in listOf(
                        Adaptive,
                        Card,
                        Flat,
                        Gradient,
                        MD3,
                        Normal,
                        Peek,
                        Plain
                    )
                ) {
                    chooseFragmentForTheme()
                    onServiceConnected()
                }
            }
            PLAYER_BACKGROUND,
            COLOR_ANIMATE-> {
                if (PreferenceUtil.nowPlayingScreen in listOf(
                        MD3,
                        Normal,
                        Peek,
                        Plain,
                        Simple
                    )
                ) {
                    chooseFragmentForTheme()
                    onServiceConnected()
                }
            }
            LIBRARY_CATEGORIES -> {
                updateTabs()
                refreshTabs()
            }
            TAB_TEXT_MODE -> {
                navigationView.labelVisibilityMode = PreferenceUtil.tabTitleMode
            }
            TOGGLE_USER_NAME,
            TOGGLE_FULL_SCREEN,
            PROGRESS_BAR_ALIGNMENT,
            PROGRESS_BAR_STYLE,
            EXTENDED_ACCENT,
            DISMISS_FAILSAFE,
            LYRICS,
            EMBED_LYRICS,
            QUEUE_STYLE,
            QUEUE_STYLE_LAND,
            LIMIT_CATEGORIES -> {
                recreate()
            }
            SCREEN_ON_LYRICS -> {
                keepScreenOn(bottomSheetBehavior.state == STATE_EXPANDED && PreferenceUtil.lyricsScreenOn && PreferenceUtil.showLyrics || PreferenceUtil.isScreenOnEnabled)
            }
            KEEP_SCREEN_ON -> {
                maybeSetScreenOn()
            }
            SYNCED_LYRICS -> {
                playerFragment.showSyncedLyrics()
            }
            AUTO_ROTATE -> {
                requestedOrientation = if (ApexUtil.isTablet) {
                    if (PreferenceUtil.isAutoRotate) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
            SHOW_UPDATE -> {
                MusicPlayerRemote.updateNotification()
            }
        }
    }

    fun collapsePanel() {
        bottomSheetBehavior.state = STATE_COLLAPSED
    }

    fun expandPanel() {
        bottomSheetBehavior.state = STATE_EXPANDED
    }

    private fun setMiniPlayerAlphaProgress(progress: Float) {
        if (progress < 0) return
        val alpha = 1 - progress
        miniPlayerFragment?.view?.alpha = 1 - (progress / 0.2F)
        miniPlayerFragment?.view?.isGone = alpha == 0f
        binding.navigationView.translationY = progress * 500
        binding.navigationView.alpha = alpha
        //if (!isLandscape) {
       //     binding.navigationView.translationY = progress * 500
        //    binding.navigationView.alpha = alpha
        //}
        binding.playerFragmentContainer.alpha = (progress - 0.2F) / 0.2F
    }

    private fun animateNavigationBarColor(color: Int) {
        if (VersionUtils.hasOreo()) return
        navigationBarColorAnimator?.cancel()
        navigationBarColorAnimator = ValueAnimator
            .ofArgb(window.navigationBarColor, color).apply {
                duration = ViewUtil.APEX_MUSIC_ANIM_TIME.toLong()
                interpolator = PathInterpolator(0.4f, 0f, 1f, 1f)
                addUpdateListener { animation: ValueAnimator ->
                    setNavigationBarColorPreOreo(
                        animation.animatedValue as Int
                    )
                }
                start()
            }
    }

    open fun onPanelCollapsed() {
        setMiniPlayerAlphaProgress(0F)
        // restore values
        animateNavigationBarColor(surfaceColor())
        setLightStatusBarAuto()
        setLightNavigationBarAuto()
        setTaskDescriptionColor(taskColor)
        //playerFragment?.onHide()
    }

    open fun onPanelExpanded() {
        setMiniPlayerAlphaProgress(1F)
        onPaletteColorChanged()
        //playerFragment?.onShow()
    }

    private fun setupSlidingUpPanel() {
        binding.slidingPanel.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.slidingPanel.viewTreeObserver.removeOnGlobalLayoutListener(this)
                if (nowPlayingScreen != Peek) {
                    binding.slidingPanel.updateLayoutParams<ViewGroup.LayoutParams> {
                        height = ViewGroup.LayoutParams.MATCH_PARENT
                    }
                }

                when (panelState) {
                    STATE_EXPANDED -> onPanelExpanded()
                    STATE_COLLAPSED -> onPanelCollapsed()
                    else -> {
                        // playerFragment!!.onHide()
                    }
                }
            }
        })
    }

    val navigationView get() = binding.navigationView

    val slidingPanel get() = binding.slidingPanel

    val isBottomNavVisible get() = navigationView.isVisible && navigationView is BottomNavigationView

    override fun onServiceConnected() {
        super.onServiceConnected()
        hideBottomSheet(false)
    }

    fun refreshTabs() {
        binding.navigationView.menu.getItem(1).isChecked = true
        binding.navigationView.menu.getItem(0).isChecked = true
    }

    override fun onQueueChanged() {
        super.onQueueChanged()
        // Mini player should be hidden in Playing Queue
        // it may pop up if hideBottomSheet is called
        if (currentFragment(R.id.fragment_container) !is PlayingQueueFragment) {
            hideBottomSheet(MusicPlayerRemote.playingQueue.isEmpty())
        }
    }

    private fun handleBackPress(): Boolean {
        if (bottomSheetBehavior.peekHeight != 0 && playerFragment.onBackPressed()) return true
        if (panelState == STATE_EXPANDED) {
            collapsePanel()
            return true
        }
        return false
    }

    private fun onPaletteColorChanged() {
        if (panelState == STATE_EXPANDED) {
            navigationBarColor = surfaceColor()
            setTaskDescColor(paletteColor)
            val isColorLight = paletteColor.isColorLight
            if (PreferenceUtil.isAdaptiveColor && (nowPlayingScreen == Normal || nowPlayingScreen == Flat)) {
                setLightNavigationBar(true)
                setLightStatusBar(isColorLight)
            } else if (nowPlayingScreen == Card || nowPlayingScreen == Blur) {
                animateNavigationBarColor(Color.BLACK)
                navigationBarColor = Color.BLACK
                setLightStatusBar(false)
                setLightNavigationBar(true)
            } else if (nowPlayingScreen == Tiny || nowPlayingScreen == Gradient) {
                animateNavigationBarColor(paletteColor)
                navigationBarColor = paletteColor
                setLightNavigationBar(isColorLight)
                setLightStatusBar(isColorLight)
            }
        }
    }

    private fun setTaskDescColor(color: Int) {
        taskColor = color
        if (panelState == STATE_COLLAPSED) {
            setTaskDescriptionColor(color)
        }
    }

    fun updateTabs() {
        binding.navigationView.menu.clear()
        val currentTabs: List<CategoryInfo> = PreferenceUtil.libraryCategory
        for (tab in currentTabs) {
            if (tab.visible) {
                val menu = tab.category
                binding.navigationView.menu.add(0, menu.id, 0, menu.stringRes)
                    .setIcon(menu.icon)
            }
        }
        if (binding.navigationView.menu.size() == 1) {
            isInOneTabMode = true
            binding.navigationView.isVisible = false
        } else {
            isInOneTabMode = false
        }
    }

    private fun updateColor() {
        libraryViewModel.paletteColor.observe(this) { color ->
            this.paletteColor = color
            onPaletteColorChanged()
        }
    }

    fun setBottomNavVisibility(
        visible: Boolean,
        animate: Boolean = false,
        hideBottomSheet: Boolean = MusicPlayerRemote.playingQueue.isEmpty(),
    ) {
        if (isInOneTabMode) {
            hideBottomSheet(
                hide = hideBottomSheet,
                animate = animate,
                isBottomNavVisible = false
            )
            return
        }
        if (visible xor navigationView.isVisible) {
            val mAnimate = animate && bottomSheetBehavior.state == STATE_COLLAPSED
            if (mAnimate) {
                if (visible) {
                    binding.navigationView.bringToFront()
                    binding.navigationView.show()
                } else {
                    binding.navigationView.hide()
                }
            } else {
                binding.navigationView.isVisible = visible
                if (visible && bottomSheetBehavior.state != STATE_EXPANDED) {
                    binding.navigationView.bringToFront()
                }
            }
        }
        hideBottomSheet(
            hide = hideBottomSheet,
            animate = animate,
            isBottomNavVisible = visible && navigationView is BottomNavigationView
        )
    }

    fun hideBottomSheet(
        hide: Boolean,
        animate: Boolean = false,
        isBottomNavVisible: Boolean = navigationView.isVisible && navigationView is BottomNavigationView,
    ) {
        val heightOfBar = windowInsets.getBottomInsets() + dip(R.dimen.mini_player_height)

        val heightOfBarWithTabs = heightOfBar + dip(R.dimen.bottom_nav_height)
        if (hide) {
            bottomSheetBehavior.peekHeight = -windowInsets.getBottomInsets()
            bottomSheetBehavior.state = STATE_COLLAPSED
            libraryViewModel.setFabMargin(
                this,
                if (isBottomNavVisible) dip(R.dimen.bottom_nav_height) else 0
            )
        } else {
            if (MusicPlayerRemote.playingQueue.isNotEmpty()) {
                binding.slidingPanel.elevation = 0F
                binding.navigationView.elevation = 5F
                if (isBottomNavVisible) {
                    logD("List")
                    if (animate) {
                        bottomSheetBehavior.peekHeightAnimate(heightOfBarWithTabs)
                    } else {
                        bottomSheetBehavior.peekHeight = heightOfBarWithTabs
                    }
                    libraryViewModel.setFabMargin(
                        this,
                        dip(R.dimen.bottom_nav_mini_player_height)
                    )
                } else {
                    logD("Details")
                    if (animate) {
                        bottomSheetBehavior.peekHeightAnimate(heightOfBar).doOnEnd {
                            binding.slidingPanel.bringToFront()
                        }
                    } else {
                        bottomSheetBehavior.peekHeight = heightOfBar
                        binding.slidingPanel.bringToFront()
                    }
                    libraryViewModel.setFabMargin(this, dip(R.dimen.mini_player_height))
                }
            }
        }
    }

    fun setAllowDragging(allowDragging: Boolean) {
        bottomSheetBehavior.isDraggable = allowDragging
        hideBottomSheet(false)
    }

    private fun chooseFragmentForTheme() {
        nowPlayingScreen = PreferenceUtil.nowPlayingScreen

        val fragment: AbsPlayerFragment = when (nowPlayingScreen) {
            Blur -> BlurPlayerFragment()
            Adaptive -> AdaptiveFragment()
            Normal -> PlayerFragment()
            Card -> CardFragment()
            Flat -> FlatPlayerFragment()
            Plain -> PlainPlayerFragment()
            Gradient -> GradientPlayerFragment()
            Tiny -> TinyPlayerFragment()
            Peek -> PeekPlayerFragment()
            MD3 -> MD3PlayerFragment()
            Simple -> SimplePlayerFragment()
            else -> PlayerFragment()
        } // must extend AbsPlayerFragment
        supportFragmentManager.commit(allowStateLoss = true) {
            replace(R.id.playerFragmentContainer, fragment)
        }
        supportFragmentManager.executePendingTransactions()
        playerFragment = whichFragment(R.id.playerFragmentContainer)
        miniPlayerFragment = whichFragment<MiniPlayerFragment>(R.id.miniPlayerFragment)
        //miniPlayerFragment?.view?.setOnClickListener { expandPanel() }
    }
}