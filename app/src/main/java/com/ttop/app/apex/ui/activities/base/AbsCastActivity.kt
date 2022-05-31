package com.ttop.app.apex.ui.activities.base

import android.os.Bundle
import com.ttop.app.apex.cast.CastHelper
import com.ttop.app.apex.cast.ApexSessionManagerListener
import com.ttop.app.apex.cast.RetroWebServer
import com.ttop.app.apex.helper.MusicPlayerRemote
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import org.koin.android.ext.android.inject


abstract class AbsCastActivity : AbsSlidingMusicPanelActivity() {

    private var mCastSession: CastSession? = null
    private lateinit var sessionManager: SessionManager
    private val webServer: RetroWebServer by inject()

    private var playServicesAvailable: Boolean = false

    private val sessionManagerListener by lazy {
        object : ApexSessionManagerListener {
            override fun onSessionStarting(castSession: CastSession) {
                webServer.start()
            }

            override fun onSessionStarted(castSession: CastSession, p1: String) {
                invalidateOptionsMenu()
                mCastSession = castSession
                loadCastQueue()
                MusicPlayerRemote.isCasting = true
                setAllowDragging(false)
                collapsePanel()
            }

            override fun onSessionEnding(castSession: CastSession) {
                MusicPlayerRemote.isCasting = false
                castSession.remoteMediaClient?.let {
                    val position = it.mediaQueue.indexOfItemWithId(it.currentItem?.itemId ?: 0)
                    val progress = it.approximateStreamPosition
                    MusicPlayerRemote.position = position
                    MusicPlayerRemote.seekTo(progress.toInt())
                }
            }

            override fun onSessionEnded(castSession: CastSession, p1: Int) {
                invalidateOptionsMenu()
                if (mCastSession == castSession) {
                    mCastSession = null
                }
                setAllowDragging(true)
                webServer.stop()
            }

            override fun onSessionResumed(castSession: CastSession, p1: Boolean) {
                invalidateOptionsMenu()
                mCastSession = castSession
                webServer.start()
                mCastSession?.remoteMediaClient?.let {
                    loadCastQueue(it.mediaQueue.indexOfItemWithId(it.currentItem?.itemId ?: 0), it.approximateStreamPosition)
                }

                MusicPlayerRemote.isCasting = true
                setAllowDragging(false)
                collapsePanel()
            }

            override fun onSessionSuspended(castSession: CastSession, p1: Int) {
                invalidateOptionsMenu()
                if (mCastSession == castSession) {
                    mCastSession = null
                }
                MusicPlayerRemote.isCasting = false
                setAllowDragging(true)
                webServer.stop()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playServicesAvailable = try {
            GoogleApiAvailability
                .getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS
        } catch (e: Exception) {
            false
        }
        if (playServicesAvailable) {
            setupCast()
        }
    }

    private fun setupCast() {
        sessionManager = CastContext.getSharedInstance(this).sessionManager
    }

    override fun onResume() {
        super.onResume()
        if (playServicesAvailable) {
            sessionManager.addSessionManagerListener(
                sessionManagerListener,
                CastSession::class.java
            )
            if (mCastSession == null) {
                mCastSession = sessionManager.currentCastSession
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (playServicesAvailable) {
            sessionManager.removeSessionManagerListener(
                sessionManagerListener,
                CastSession::class.java
            )
            mCastSession = null
        }
    }

    fun loadCastQueue(
        position: Int = MusicPlayerRemote.position,
        progress: Long = MusicPlayerRemote.songProgressMillis.toLong(),
    ) {
        mCastSession?.let {
            if (!MusicPlayerRemote.playingQueue.isNullOrEmpty()) {
                CastHelper.castQueue(
                    it,
                    MusicPlayerRemote.playingQueue,
                    position,
                    progress
                )
            }
        }
    }

    override fun onQueueChanged() {
        super.onQueueChanged()
        if (playServicesAvailable) {
            loadCastQueue()
        }
    }
}