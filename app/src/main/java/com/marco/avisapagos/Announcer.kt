package com.marco.avisapagos

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Hace sonar el "ding" y dice el monto en voz alta, como el parlante de Izipay. */
class Announcer private constructor(ctx: Context) : TextToSpeech.OnInitListener {

    companion object {
        @Volatile private var instance: Announcer? = null
        fun get(ctx: Context): Announcer =
            instance ?: synchronized(this) { instance ?: Announcer(ctx).also { instance = it } }
    }

    private val app = ctx.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val tts = TextToSpeech(app, this)
    private var ready = false
    private val pending = mutableListOf<String>()
    private var savedVolume = -1
    private var speaking = 0

    override fun onInit(status: Int) {
        main.post { setup(status) }
    }

    private fun setup(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        if (tts.setLanguage(Locale("es", "PE")) < 0) tts.setLanguage(Locale("es"))
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { main.post { finished() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { finished() } }
        })
        ready = true
        pending.forEach { speakNow(it) }
        pending.clear()
    }

    fun announce(p: Payment) {
        val prefs = Prefs(app)
        if (!prefs.voiceOn) return
        val text = PaymentParser.speech(p, prefs.sayName)
        say(text)
        if (prefs.repeatTwice) say(text)
    }

    fun say(text: String) {
        main.post { if (ready) speakNow(text) else pending.add(text) }
    }

    private fun speakNow(text: String) {
        val prefs = Prefs(app)
        if (prefs.maxVolume && savedVolume < 0) {
            savedVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(
                AudioManager.STREAM_MUSIC, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0
            )
        }
        speaking++
        val delay = if (prefs.beep && speaking == 1) { ding(); 550L } else 0L
        main.postDelayed({
            tts.speak(text, TextToSpeech.QUEUE_ADD, Bundle(), "pago-" + System.nanoTime())
        }, delay)
    }

    private fun ding() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 450)
            main.postDelayed({ tg.release() }, 1000)
        } catch (_: Exception) { }
    }

    private fun finished() {
        speaking = (speaking - 1).coerceAtLeast(0)
        if (speaking == 0 && savedVolume >= 0) {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0)
            savedVolume = -1
        }
    }
}
