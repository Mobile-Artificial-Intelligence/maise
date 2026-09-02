package com.danemadsen.maise.asr

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.annotation.RawRes
import com.danemadsen.maise.R
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Short sonification cues for the recognition lifecycle.
 *  - start.ogg before the mic opens (awaited so the tone is never captured)
 *  - stop.ogg  after a successful capture ends
 *  - error.ogg on any recognition failure except caller-busy at session start
 *
 * Skipped entirely in RINGER_MODE_SILENT. Always on; not caller-configurable.
 */
class RecognitionSounds(context: Context) {

    private val appContext   = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val mainHandler  = Handler(Looper.getMainLooper())

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /**
     * Plays the start cue and suspends until it finishes. Returns early after
     * [START_CUE_TIMEOUT_MS] even if completion never fires, so recognition can
     * never wedge on a missing callback. Throws [kotlinx.coroutines.CancellationException]
     * if the caller is cancelled — the caller must not open the mic in that case,
     * and the tone is stopped immediately.
     */
    suspend fun playStart() {
        if (isSilenced()) return
        withTimeoutOrNull(START_CUE_TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                val mp = newPlayer(R.raw.start) ?: return@withContext
                val done = AtomicBoolean(false)
                suspendCancellableCoroutine { cont ->
                    mp.setOnCompletionListener { m ->
                        if (done.compareAndSet(false, true)) {
                            runCatching { m.release() }
                            cont.resume(Unit)
                        }
                    }
                    mp.start()
                    cont.invokeOnCancellation {
                        if (done.compareAndSet(false, true)) {
                            mainHandler.post {
                                runCatching { mp.stop() }
                                runCatching { mp.release() }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Fire-and-forget stop cue (mic already closed). Non-blocking. */
    fun playStop()  = playAsync(R.raw.stop)

    /** Fire-and-forget error cue. Non-blocking. */
    fun playError() = playAsync(R.raw.error)

    // ---------------------------------------------------------------------

    private fun playAsync(@RawRes res: Int) {
        if (isSilenced()) return
        mainHandler.post {
            val mp = newPlayer(res) ?: return@post
            mp.setOnCompletionListener { m -> runCatching { m.release() } }
            mp.start()
        }
    }

    /**
     * Creates a synchronously-prepared [MediaPlayer], ready for [MediaPlayer.start].
     * Returns null if the resource fails to load. All MediaPlayer work must stay on
     * the main handler — listeners never fire if the player is built on a
     * Looper-less thread. The cues are 2-3 KB resources, so blocking [MediaPlayer.prepare]
     * on the main thread (same as [MediaPlayer.create]) is cheaper than the
     * prepareAsync prepared-state races it avoids.
     */
    private fun newPlayer(@RawRes res: Int): MediaPlayer? = runCatching {
        MediaPlayer().apply {
            setAudioAttributes(attrs)
            appContext.resources.openRawResourceFd(res).use { afd ->
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            prepare()
        }
    }.getOrNull()

    private fun isSilenced(): Boolean =
        audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT

    private companion object {
        /** ~4x the 0.41 s cue: enough headroom for slow first-prepare,
         *  short enough that recognition never waits noticeably. */
        const val START_CUE_TIMEOUT_MS = 2_000L
    }
}