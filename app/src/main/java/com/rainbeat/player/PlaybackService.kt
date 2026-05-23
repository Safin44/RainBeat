package com.rainbeat.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.rainbeat.R
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    companion object {
        const val CUSTOM_COMMAND_CLOSE = "com.rainbeat.CLOSE"
        const val CUSTOM_COMMAND_FORWARD_10 = "com.rainbeat.FORWARD_10"
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setSeekForwardIncrementMs(10000)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val closeCommand = SessionCommand(CUSTOM_COMMAND_CLOSE, Bundle.EMPTY)

        val closeButton = CommandButton.Builder()
            .setDisplayName("Close")
            .setIconResId(R.drawable.ic_close)
            .setSessionCommand(closeCommand)
            .build()

        val forwardCommand = SessionCommand(CUSTOM_COMMAND_FORWARD_10, Bundle.EMPTY)
        val forwardButton = CommandButton.Builder()
            .setDisplayName("Forward 10s")
            .setIconResId(R.drawable.ic_forward_10)
            .setSessionCommand(forwardCommand)
            .build()

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                    .buildUpon()
                    .add(closeCommand)
                    .add(forwardCommand)
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .build()
            }

            override fun onMediaButtonEvent(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                intent: Intent
            ): Boolean {
                val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            session.player.seekToPreviousMediaItem()
                            return true
                        }
                    }
                }
                return super.onMediaButtonEvent(session, controller, intent)
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                if (customCommand.customAction == CUSTOM_COMMAND_CLOSE) {
                    session.player.stop()
                    session.player.clearMediaItems()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else if (customCommand.customAction == CUSTOM_COMMAND_FORWARD_10) {
                    session.player.seekForward()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        val intent = Intent(this, com.rainbeat.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val forwardingPlayer = object : ForwardingPlayer(player) {
            override fun seekToPrevious() {
                seekToPreviousMediaItem()
            }
        }

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setCallback(callback)
            .setSessionActivity(pendingIntent)
            .setCustomLayout(ImmutableList.of(closeButton, forwardButton))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && (!player.playWhenReady || player.mediaItemCount == 0)) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
