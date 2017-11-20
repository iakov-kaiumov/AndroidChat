package helfi2012.chat.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

class AudioPlayer {
    companion object {
        private val TAG = "AudioPlayerClass"
    }
    private var audioTrack: AudioTrack? = findTrack()

    fun write(buffer: ByteArray) {
        audioTrack!!.write(buffer, 0, buffer.size)
    }

    fun play() = audioTrack!!.play()

    fun stop() = audioTrack!!.stop()

    private fun findTrack(): AudioTrack? {
        var track: AudioTrack? = null
        Log.d(TAG, "===== Initializing AudioTrack API ====")
        val mBufferSize = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (mBufferSize != AudioTrack.ERROR_BAD_VALUE) {
            track = AudioTrack(AudioManager.STREAM_VOICE_CALL, 48000,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, mBufferSize,
                    AudioTrack.MODE_STREAM)

            if (track.state == AudioTrack.STATE_UNINITIALIZED) {
                Log.e(TAG, "===== AudioTrack Uninitialized =====")
                return null
            }
        }
        return track
    }
}
