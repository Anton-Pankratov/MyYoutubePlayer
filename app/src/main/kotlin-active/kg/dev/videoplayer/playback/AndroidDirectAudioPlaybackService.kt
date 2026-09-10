package kg.dev.videoplayer.playback

import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionError
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kg.dev.shared.feature.player.DirectPlaybackCommandCallbacks

/** The sole Android ExoPlayer owner for eligible Direct audio. */
@UnstableApi
class AndroidDirectAudioPlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .build()
        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallbacks)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onDestroy() {
        session.release()
        player.release()
        super.onDestroy()
    }

    private object SessionCallbacks : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(NEXT_COMMAND)
                    .add(PREVIOUS_COMMAND)
                    .add(STOP_COMMAND)
                    .build()
            )
            .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
            .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> = when (customCommand.customAction) {
            ACTION_NEXT -> {
                AndroidDirectAudioCommandRegistry.callbacks?.next()
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            ACTION_PREVIOUS -> {
                AndroidDirectAudioCommandRegistry.callbacks?.previous()
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            ACTION_STOP -> {
                AndroidDirectAudioCommandRegistry.callbacks?.stop()
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            else -> Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
        }
    }

    companion object {
        const val ACTION_NEXT = "kg.dev.videoplayer.playback.NEXT"
        const val ACTION_PREVIOUS = "kg.dev.videoplayer.playback.PREVIOUS"
        const val ACTION_STOP = "kg.dev.videoplayer.playback.STOP"
        val NEXT_COMMAND = SessionCommand(ACTION_NEXT, Bundle.EMPTY)
        val PREVIOUS_COMMAND = SessionCommand(ACTION_PREVIOUS, Bundle.EMPTY)
        val STOP_COMMAND = SessionCommand(ACTION_STOP, Bundle.EMPTY)
    }
}

/** Application-scoped callback registration; it never retains an Activity or Compose UI. */
object AndroidDirectAudioCommandRegistry {
    var callbacks: DirectPlaybackCommandCallbacks? = null
}
