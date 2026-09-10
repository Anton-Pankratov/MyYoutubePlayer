package kg.dev.videoplayer.playback

/** MediaSessionService lifecycle policy; Media3 owns all audio-focus state and user intent. */
class AndroidAudioInterruptionPolicy {
    fun shouldStopServiceAfterTaskRemoval(playbackOngoing: Boolean): Boolean = !playbackOngoing
}
