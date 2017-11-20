package helfi2012.chat.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import helfi2012.chat.udpconnection.UDPClient

class AudioRecorder {
    companion object {
        private val TAG = "AudioRecorderClass"
    }
    private var recorder: AudioRecord? = null
    private var bufferSize = AudioRecord.getMinBufferSize(
            48000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT)
    private val buffer = ByteArray(bufferSize)
    private var mClient: UDPClient? = null
    private var thread: Thread? = null

    fun start(client: UDPClient) {
        Log.d(TAG, "BUFFER SIZE: " + bufferSize.toString())
        if (setUp()) {
            mClient = client
            thread = Thread {
                while (!Thread.currentThread().isInterrupted) {
                    recorder!!.read(buffer, 0, buffer.size)
                    mClient!!.sendByteArray(buffer, UDPClient.AUDIO_STATE)
                }
            }
            thread!!.start()
        }
    }

    fun stop() {
        if (thread != null) {
            thread!!.interrupt()
        }
        if (recorder != null && recorder!!.state == AudioRecord.STATE_INITIALIZED) {
            recorder!!.stop()
            recorder!!.release()
            recorder = null
        }
    }

    private fun setUp(): Boolean {
        recorder = findAudioRecord()
        if (recorder == null) {
            Log.e(TAG, "======== findAudioRecord : Returned Error! =========== ")
            return false
        }

        if (recorder!!.state == AudioRecord.STATE_INITIALIZED) {
            recorder!!.startRecording()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                try {
                    if (AutomaticGainControl.isAvailable()) {
                        val automaticGainControl = AutomaticGainControl.create(recorder!!.audioSessionId)
                        if (automaticGainControl != null)
                            automaticGainControl.enabled = false
                    } else {
                        Log.d(TAG, "AutomaticGainControl is not available on this device :(")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                try {
                    if (NoiseSuppressor.isAvailable()) {
                        val noiseSuppressor = NoiseSuppressor.create(recorder!!.audioSessionId)
                        if (noiseSuppressor != null)
                            noiseSuppressor.enabled = true
                    } else {
                        Log.d(TAG, "NoiseSuppressor is not available on this device :(")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                try {
                    if (AcousticEchoCanceler.isAvailable()) {
                        val acousticEchoCanceler = AcousticEchoCanceler.create(recorder!!.audioSessionId)
                        if (acousticEchoCanceler != null)
                            acousticEchoCanceler.enabled = true
                    } else {
                        Log.d(TAG, "AcousticEchoCanceler is not available on this device")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }
            Log.d(TAG, "========= Recorder Started... =========")
        } else {
            Log.d(TAG, "==== Initialization failed for AudioRecord or AudioTrack =====")
            return false
        }
        return true
    }

    private fun findAudioRecord(): AudioRecord? {
        var recorder: AudioRecord? = null
        Log.d(TAG, "===== Initializing AudioRecord API =====")

        if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {
            recorder = AudioRecord(MediaRecorder.AudioSource.MIC, 48000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufferSize)

            if (recorder.state == AudioRecord.STATE_UNINITIALIZED) {
                Log.e(TAG, "====== AudioRecord UnInitialised ====== ")
                return null
            }
        }
        return recorder
    }
}