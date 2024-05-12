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
package com.ttop.app.apex.ui.fragments.player.classic

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.ContentUris
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.view.MenuItem
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.h6ah4i.android.widget.advrecyclerview.animator.DraggableItemAnimator
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager
import com.h6ah4i.android.widget.advrecyclerview.touchguard.RecyclerViewTouchActionGuardManager
import com.ttop.app.apex.EXTRA_ALBUM_ID
import com.ttop.app.apex.R
import com.ttop.app.apex.adapter.song.PlayingQueueAdapter
import com.ttop.app.apex.databinding.FragmentClassicPlayerBinding
import com.ttop.app.apex.dialogs.AddToPlaylistDialog
import com.ttop.app.apex.dialogs.CreatePlaylistDialog
import com.ttop.app.apex.dialogs.DeleteSongsDialog
import com.ttop.app.apex.dialogs.PlaybackSpeedDialog
import com.ttop.app.apex.dialogs.SleepTimerDialog
import com.ttop.app.apex.dialogs.SongDetailDialog
import com.ttop.app.apex.dialogs.SongShareDialog
import com.ttop.app.apex.extensions.darkAccentColor
import com.ttop.app.apex.extensions.drawAboveSystemBars
import com.ttop.app.apex.extensions.keepScreenOn
import com.ttop.app.apex.extensions.showToast
import com.ttop.app.apex.extensions.surfaceColor
import com.ttop.app.apex.helper.MusicPlayerRemote
import com.ttop.app.apex.model.Song
import com.ttop.app.apex.repository.RealRepository
import com.ttop.app.apex.ui.activities.tageditor.AbsTagEditorActivity
import com.ttop.app.apex.ui.activities.tageditor.SongTagEditorActivity
import com.ttop.app.apex.ui.fragments.base.AbsPlayerFragment
import com.ttop.app.apex.ui.fragments.base.goToArtist
import com.ttop.app.apex.ui.fragments.player.PlayerAlbumCoverFragment
import com.ttop.app.apex.util.ApexUtil
import com.ttop.app.apex.util.MusicUtil
import com.ttop.app.apex.util.NavigationUtil
import com.ttop.app.apex.util.PreferenceUtil
import com.ttop.app.apex.util.RingtoneManager
import com.ttop.app.apex.util.ViewUtil
import com.ttop.app.apex.util.color.MediaNotificationProcessor
import com.ttop.app.apex.views.DrawableGradient
import com.ttop.app.appthemehelper.util.ATHUtil
import com.ttop.app.appthemehelper.util.ColorUtil
import com.ttop.app.appthemehelper.util.ToolbarContentTintHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.get
import java.lang.StringBuilder


class ClassicPlayerFragment : AbsPlayerFragment(R.layout.fragment_classic_player) {

    private var lastColor: Int = 0
    private var toolbarColor: Int =0
    override val paletteColor: Int
        get() = lastColor

    private lateinit var controlsFragment: ClassicPlaybackControlsFragment
    private var valueAnimator: ValueAnimator? = null
    private lateinit var wrappedAdapter: RecyclerView.Adapter<*>
    private var recyclerViewDragDropManager: RecyclerViewDragDropManager? = null
    private var recyclerViewTouchActionGuardManager: RecyclerViewTouchActionGuardManager? = null
    private var playingQueueAdapter: PlayingQueueAdapter? = null
    private lateinit var linearLayoutManager: LinearLayoutManager
    private var _binding: FragmentClassicPlayerBinding? = null
    private val binding get() = _binding!!

    private val embed: TextView get() = binding.embedded
    private val scroll: ScrollView get() = binding.scroll

    override fun onShow() {
        controlsFragment.show()
    }

    override fun onHide() {
        controlsFragment.hide()
        onBackPressed()
    }

    override fun onBackPressed(): Boolean {
        return false
    }

    override fun toolbarIconColor(): Int {
        return if (PreferenceUtil.isAdaptiveColor) {
            toolbarColor
        }else {
            ATHUtil.resolveColor(requireContext(), androidx.appcompat.R.attr.colorControlNormal)
        }
    }

    override fun onColorChanged(color: MediaNotificationProcessor) {
        controlsFragment.setColor(color)
        lastColor = color.backgroundColor
        toolbarColor = color.secondaryTextColor
        libraryViewModel.updateColor(color.backgroundColor)

        ToolbarContentTintHelper.colorizeToolbar(
            binding.playerToolbar,
            toolbarIconColor(),
            requireActivity()
        )

        if (PreferenceUtil.isAdaptiveColor) {
            colorize(color.backgroundColor)

            if (PreferenceUtil.isColorAnimate) {
                val animator =
                    binding.colorGradientBackground.let { controlsFragment.createRevealAnimator(it) }
                animator.doOnEnd {
                    _binding?.root?.setBackgroundColor(color.backgroundColor)
                }
                animator.start()
            }
        }

        playingQueueAdapter?.setTextColor(color.secondaryTextColor)
        val colorBg = ATHUtil.resolveColor(requireContext(), android.R.attr.colorBackground)

        if (PreferenceUtil.materialYou) {
            if (PreferenceUtil.isAdaptiveColor) {
                scroll.setBackgroundColor(color.backgroundColor)
                embed.setTextColor(color.secondaryTextColor)
            }else {
                scroll.setBackgroundColor(requireContext().darkAccentColor())

                if (ColorUtil.isColorLight(colorBg)) {
                    embed.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_black_1000))
                }else {
                    embed.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_white_1000))
                }
            }
        }else {
            if (PreferenceUtil.isAdaptiveColor) {
                scroll.setBackgroundColor(color.backgroundColor)
                embed.setTextColor(color.secondaryTextColor)
            }else {
                if (ApexUtil.isTablet) {
                    when (PreferenceUtil.baseTheme) {
                        "light" -> {
                            embed.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_black_1000))
                        }
                        "dark" -> {
                            if (PreferenceUtil.isBlackMode) {
                                embed.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_white_1000))
                            }else {
                                embed.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_white_1000))
                            }
                        }
                        "auto" -> {
                            when (requireContext().resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)) {
                                Configuration.UI_MODE_NIGHT_YES -> {
                                    if (PreferenceUtil.isBlackMode) {
                                        embed.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_white_1000))
                                    }else {
                                        embed.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_white_1000))
                                    }
                                }
                                Configuration.UI_MODE_NIGHT_NO,
                                Configuration.UI_MODE_NIGHT_UNDEFINED-> {
                                    embed.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_black_1000))
                                }
                            }
                        }
                    }

                }else {
                    embed.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_white_1000))
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (PreferenceUtil.isEmbedLyricsActivated) {
                if (ApexUtil.isTablet) {
                    binding.playerQueueSheet.visibility = View.GONE
                    scroll.visibility = View.VISIBLE
                }else {
                    binding.playerQueueSheet.visibility = View.GONE
                    scroll.visibility = View.VISIBLE

                    binding.playerAlbumCoverFragment.alpha = 0f

                    playerToolbar().menu?.findItem(R.id.action_queue)?.isEnabled = false
                }
            }
        }else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (PreferenceUtil.isEmbedLyricsActivated) {
                if (ApexUtil.isTablet) {
                    binding.playerQueueSheet.visibility = View.GONE
                    scroll.visibility = View.VISIBLE
                }else {
                    binding.playerQueueSheet.visibility = View.GONE
                    scroll.visibility = View.VISIBLE

                    binding.playerAlbumCoverFragment.alpha = 0f

                    playerToolbar().menu?.findItem(R.id.action_queue)?.isEnabled = false
                }
            }
        }
    }

    private fun colorize(i: Int) {
        if (PreferenceUtil.isPlayerBackgroundType) {
            //GRADIENT
            if (valueAnimator != null) {
                valueAnimator?.cancel()
            }

            valueAnimator = ValueAnimator.ofObject(
                ArgbEvaluator(),
                surfaceColor(),
                i
            )
            valueAnimator?.addUpdateListener { animation ->
                if (isAdded) {
                    val drawable = DrawableGradient(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(
                            animation.animatedValue as Int,
                            surfaceColor()
                        ), 0
                    )
                    binding.colorGradientBackground.background = drawable
                }
            }
            valueAnimator?.setDuration(ViewUtil.APEX_MUSIC_ANIM_TIME.toLong())?.start()
        }else {
            //SINGLE COLOR
            binding.colorGradientBackground.setBackgroundColor(i)
        }
    }

    override fun toggleFavorite(song: Song) {
        super.toggleFavorite(song)
        if (song.id == MusicPlayerRemote.currentSong.id) {
            updateIsFavorite()
        }
    }

    override fun onFavoriteToggled() {
        toggleFavorite(MusicPlayerRemote.currentSong)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentClassicPlayerBinding.bind(view)
        setUpSubFragments()
        setupRecyclerView()
        setUpPlayerToolbar()
        playerToolbar().drawAboveSystemBars()

        embed.textSize = 24f

        if (PreferenceUtil.isEmbedLyricsActivated) {
            if (ApexUtil.isTablet) {
                binding.playerQueueSheet.visibility = View.GONE
                scroll.visibility = View.VISIBLE
            }else {
                binding.playerQueueSheet.visibility = View.GONE
                scroll.visibility = View.VISIBLE

                binding.playerAlbumCoverFragment.alpha = 0f

                playerToolbar().menu?.findItem(R.id.action_queue)?.isEnabled = false
            }
        }

        if (PreferenceUtil.lyricsMode == "disabled" || PreferenceUtil.lyricsMode == "synced") {
            playerToolbar().menu?.findItem(R.id.action_go_to_lyrics)?.isVisible = false
        }else {
            playerToolbar().menu?.findItem(R.id.action_go_to_lyrics)?.isVisible = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val song = MusicPlayerRemote.currentSong
        if (!PreferenceUtil.isHapticFeedbackDisabled) {
            requireView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
        when (item.itemId) {
            R.id.action_playback_speed -> {
                PlaybackSpeedDialog.newInstance().show(childFragmentManager, "PLAYBACK_SETTINGS")
                return true
            }
            R.id.action_toggle_favorite -> {
                toggleFavorite(song)
                if (!PreferenceUtil.isHapticFeedbackDisabled) {
                    requireView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                return true
            }
            R.id.action_share -> {
                SongShareDialog.create(song).show(childFragmentManager, "SHARE_SONG")
                return true
            }
            R.id.action_go_to_drive_mode -> {
                NavigationUtil.gotoDriveMode(requireActivity())
                return true
            }
            R.id.action_reorder -> {
                if (binding.playerQueueSheet.visibility == View.VISIBLE) {
                    playingQueueAdapter?.setButtonsActivate()
                }
                return true
            }
            R.id.action_delete_from_device -> {
                DeleteSongsDialog.create(song).show(childFragmentManager, "DELETE_SONGS")
                return true
            }
            R.id.action_add_to_playlist -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val playlists = get<RealRepository>().fetchPlaylists()
                    withContext(Dispatchers.Main) {
                        AddToPlaylistDialog.create(playlists, song)
                            .show(childFragmentManager, "ADD_PLAYLIST")
                    }
                }
                return true
            }
            R.id.action_clear_playing_queue -> {
                MusicPlayerRemote.clearQueue()
                return true
            }
            R.id.action_save_playing_queue -> {
                CreatePlaylistDialog.create(ArrayList(MusicPlayerRemote.playingQueue))
                    .show(childFragmentManager, "ADD_TO_PLAYLIST")
                return true
            }
            R.id.action_tag_editor -> {
                val intent = Intent(activity, SongTagEditorActivity::class.java)
                intent.putExtra(AbsTagEditorActivity.EXTRA_ID, song.id)
                startActivity(intent)
                return true
            }
            R.id.action_details -> {
                SongDetailDialog.create(song).show(childFragmentManager, "SONG_DETAIL")
                return true
            }
            R.id.action_go_to_album -> {
                //Hide Bottom Bar First, else Bottom Sheet doesn't collapse fully
                mainActivity.setBottomNavVisibility(false)
                mainActivity.collapsePanel()
                requireActivity().findNavController(R.id.fragment_container).navigate(
                    R.id.albumDetailsFragment,
                    bundleOf(EXTRA_ALBUM_ID to song.albumId)
                )
                return true
            }
            R.id.action_go_to_artist -> {
                goToArtist(requireActivity())
                return true
            }
            R.id.action_equalizer -> {
                NavigationUtil.openEqualizer(requireActivity())
                return true
            }
            R.id.action_sleep_timer -> {
                SleepTimerDialog().show(parentFragmentManager, "SLEEP_TIMER")
                return true
            }
            R.id.action_set_as_ringtone -> {
                requireContext().run {
                    if (RingtoneManager.requiresDialog(this)) {
                        RingtoneManager.showDialog(this)
                    } else {
                        RingtoneManager.setRingtone(this, song)
                    }
                }

                return true
            }
            R.id.action_go_to_genre -> {
                val retriever = MediaMetadataRetriever()
                val trackUri =
                    ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        song.id
                    )
                retriever.setDataSource(activity, trackUri)
                var genre: String? =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                if (genre == null) {
                    genre = "Not Specified"
                }
                showToast(genre)
                return true
            }
            R.id.action_queue -> {
                if (!ApexUtil.isTablet) {
                    if (binding.playerQueueSheet.visibility == View.VISIBLE){
                        binding.playerQueueSheet.visibility = View.GONE
                        binding.playerAlbumCoverFragment.alpha = 1f
                    }else{
                        binding.playerQueueSheet.visibility = View.VISIBLE
                        binding.playerAlbumCoverFragment.alpha = 0f
                    }
                }
            }
            R.id.action_go_to_lyrics -> {
                if (ApexUtil.isTablet) {
                    if (binding.playerQueueSheet.visibility == View.VISIBLE){
                        binding.playerQueueSheet.visibility = View.GONE
                        scroll.visibility = View.VISIBLE
                        if (!PreferenceUtil.isLyricsMessageDisabled) {
                            showToast(getString(R.string.lyrics_message_enabled))
                        }

                        if (PreferenceUtil.lyricsScreenOn) {
                            mainActivity.keepScreenOn(true)
                        }else {
                            mainActivity.keepScreenOn(false)
                        }
                        if (!PreferenceUtil.isHapticFeedbackDisabled) {
                            requireView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                        PreferenceUtil.isEmbedLyricsActivated = true
                    }else{
                        binding.playerQueueSheet.visibility = View.VISIBLE
                        scroll.visibility = View.GONE
                        if (!PreferenceUtil.isLyricsMessageDisabled) {
                            showToast(getString(R.string.lyrics_message_disabled))
                        }
                        mainActivity.keepScreenOn(false)
                        if (!PreferenceUtil.isHapticFeedbackDisabled) {
                            requireView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                        PreferenceUtil.isEmbedLyricsActivated = false
                    }
                }else {
                    binding.playerQueueSheet.visibility = View.GONE
                    if (scroll.visibility == View.GONE){
                        scroll.visibility = View.VISIBLE
                        if (!PreferenceUtil.isLyricsMessageDisabled) {
                            showToast(getString(R.string.lyrics_message_enabled))
                        }
                        playerToolbar().menu?.findItem(R.id.action_queue)?.isEnabled = false

                        if (PreferenceUtil.lyricsScreenOn) {
                            mainActivity.keepScreenOn(true)
                        }else {
                            mainActivity.keepScreenOn(false)
                        }

                        binding.playerAlbumCoverFragment.alpha = 0f

                        if (!PreferenceUtil.isHapticFeedbackDisabled) {
                            requireView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                        PreferenceUtil.isEmbedLyricsActivated = true
                    }else{
                        scroll.visibility = View.GONE
                        if (!PreferenceUtil.isLyricsMessageDisabled) {
                            showToast(getString(R.string.lyrics_message_disabled))
                        }
                        playerToolbar().menu?.findItem(R.id.action_queue)?.isEnabled = true

                        binding.playerAlbumCoverFragment.alpha = 1f

                        mainActivity.keepScreenOn(false)
                        if (!PreferenceUtil.isHapticFeedbackDisabled) {
                            requireView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                        PreferenceUtil.isEmbedLyricsActivated = false
                    }
                }
            }
        }
        return false
    }

    private fun setUpSubFragments() {
        controlsFragment =
            childFragmentManager.findFragmentById(R.id.playbackControlsFragment) as ClassicPlaybackControlsFragment
        val playerAlbumCoverFragment =
            childFragmentManager.findFragmentById(R.id.playerAlbumCoverFragment) as PlayerAlbumCoverFragment
        playerAlbumCoverFragment.setCallbacks(this)
    }

    private fun setUpPlayerToolbar() {
        binding.playerToolbar.inflateMenu(R.menu.menu_player)

        //binding.playerToolbar.menu.setUpWithIcons()
        binding.playerToolbar.setNavigationIcon(R.drawable.ic_keyboard_arrow_down_black)
        binding.playerToolbar.setNavigationOnClickListener {
            if (!PreferenceUtil.isHapticFeedbackDisabled) {
                requireView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.playerToolbar.setOnMenuItemClickListener(this)

        ToolbarContentTintHelper.colorizeToolbar(
            binding.playerToolbar,
            toolbarIconColor(),
            requireActivity()
        )
    }

    private fun setupRecyclerView() {
        playingQueueAdapter = if (ApexUtil.isTablet) {
            if(ApexUtil.isLandscape) {
                when (PreferenceUtil.queueStyleLand) {
                    "normal" -> {
                        PlayingQueueAdapter(
                            requireActivity() as AppCompatActivity,
                            MusicPlayerRemote.playingQueue.toMutableList(),
                            MusicPlayerRemote.position,
                            R.layout.item_queue_player_plain
                        )
                    }
                    "duo" -> {
                        PlayingQueueAdapter(
                            requireActivity() as AppCompatActivity,
                            MusicPlayerRemote.playingQueue.toMutableList(),
                            MusicPlayerRemote.position,
                            R.layout.item_queue_duo
                        )
                    }
                    "trio" -> {
                        PlayingQueueAdapter(
                            requireActivity() as AppCompatActivity,
                            MusicPlayerRemote.playingQueue.toMutableList(),
                            MusicPlayerRemote.position,
                            R.layout.item_queue_trio
                        )
                    }
                    else -> {
                        PlayingQueueAdapter(
                            requireActivity() as AppCompatActivity,
                            MusicPlayerRemote.playingQueue.toMutableList(),
                            MusicPlayerRemote.position,
                            R.layout.item_queue_player_plain
                        )
                    }
                }
            }else {
                when (PreferenceUtil.queueStyle) {
                    "normal" -> {
                        PlayingQueueAdapter(
                            requireActivity() as AppCompatActivity,
                            MusicPlayerRemote.playingQueue.toMutableList(),
                            MusicPlayerRemote.position,
                            R.layout.item_queue_player_plain
                        )
                    }
                    "duo" -> {
                        PlayingQueueAdapter(
                            requireActivity() as AppCompatActivity,
                            MusicPlayerRemote.playingQueue.toMutableList(),
                            MusicPlayerRemote.position,
                            R.layout.item_queue_duo
                        )
                    }
                    "trio" -> {
                        PlayingQueueAdapter(
                            requireActivity() as AppCompatActivity,
                            MusicPlayerRemote.playingQueue.toMutableList(),
                            MusicPlayerRemote.position,
                            R.layout.item_queue_trio
                        )
                    }
                    else -> {
                        PlayingQueueAdapter(
                            requireActivity() as AppCompatActivity,
                            MusicPlayerRemote.playingQueue.toMutableList(),
                            MusicPlayerRemote.position,
                            R.layout.item_queue_player_plain
                        )
                    }
                }
            }
        }else {
            PlayingQueueAdapter(
                requireActivity() as AppCompatActivity,
                MusicPlayerRemote.playingQueue.toMutableList(),
                MusicPlayerRemote.position,
                R.layout.item_queue_player
            )
        }

        linearLayoutManager = LinearLayoutManager(requireContext())
        recyclerViewTouchActionGuardManager = RecyclerViewTouchActionGuardManager()
        recyclerViewDragDropManager = RecyclerViewDragDropManager()

        val animator = DraggableItemAnimator()
        animator.supportsChangeAnimations = false
        wrappedAdapter =
            recyclerViewDragDropManager?.createWrappedAdapter(playingQueueAdapter!!) as RecyclerView.Adapter<*>
        binding.recyclerView.layoutManager = linearLayoutManager
        binding.recyclerView.adapter = wrappedAdapter
        binding.recyclerView.itemAnimator = animator
        binding.recyclerView.let { recyclerViewTouchActionGuardManager?.attachRecyclerView(it) }
        binding.recyclerView.let { recyclerViewDragDropManager?.attachRecyclerView(it) }

        linearLayoutManager.scrollToPositionWithOffset(MusicPlayerRemote.position + 1, 0)
    }

    private fun updateQueuePosition() {
        playingQueueAdapter?.setCurrent(MusicPlayerRemote.position)
        resetToCurrentPosition()
    }

    private fun updateQueue() {
        playingQueueAdapter?.swapDataSet(MusicPlayerRemote.playingQueue, MusicPlayerRemote.position)
        resetToCurrentPosition()
    }

    private fun resetToCurrentPosition() {
        binding.recyclerView.stopScroll()
        linearLayoutManager.scrollToPositionWithOffset(MusicPlayerRemote.position + 1, 0)
    }

    override fun onQueueChanged() {
        super.onQueueChanged()
        updateQueue()

        val string = StringBuilder()
        string.append(MusicUtil.getLyrics(MusicPlayerRemote.currentSong)).append("\n")
        embed.text = string.toString()
    }

    override fun onServiceConnected() {
        updateIsFavorite()
        updateQueue()

        val string = StringBuilder()
        string.append(MusicUtil.getLyrics(MusicPlayerRemote.currentSong)).append("\n")
        embed.text = string.toString()
    }

    override fun onPlayingMetaChanged() {
        updateIsFavorite()
        updateQueuePosition()

        val string = StringBuilder()
        string.append(MusicUtil.getLyrics(MusicPlayerRemote.currentSong)).append("\n")
        embed.text = string.toString()
        scroll.scrollTo(0,0)
    }

    override fun playerToolbar(): Toolbar {
        return binding.playerToolbar
    }

    companion object {

        fun newInstance(): ClassicPlayerFragment {
            return ClassicPlayerFragment()
        }
    }
}
