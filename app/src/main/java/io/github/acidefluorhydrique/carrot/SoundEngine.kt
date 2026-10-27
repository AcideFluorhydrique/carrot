// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * [seconds] 是合成出來的長度，[SoundEngine] 合成時也讀這個值。
 * [minGapMs] 只對戰鬥音效（[combat]）有意義：同一個音效兩次之間至少隔多久。
 */
enum class Sfx(val key: String, val seconds: Float, val combat: Boolean = false, val minGapMs: Long = 0L) {
    SHOOT("shoot", 0.09f, combat = true, minGapMs = 80L),
    HIT("hit", 0.06f, combat = true, minGapMs = 80L),
    // 最長、低頻最重的一個。隔 250ms 讓它最多兩層重疊，否則太陽塔和炸彈一起響時會糊成持續的轟隆聲。
    EXPLODE("explode", 0.5f, combat = true, minGapMs = 250L),
    ICE("ice", 0.28f, combat = true, minGapMs = 140L),
    ZAP("zap", 0.22f, combat = true, minGapMs = 110L),
    POISON("poison", 0.34f, combat = true, minGapMs = 170L),
    DIE("die", 0.18f, combat = true, minGapMs = 90L),
    COIN("coin", 0.24f, combat = true, minGapMs = 120L),
    BUILD("build", 0.26f),
    SELL("sell", 0.26f),
    UPGRADE("upgrade", 0.44f),
    DENY("deny", 0.22f),
    HURT("hurt", 0.4f),
    WAVE("wave", 0.55f),
    WIN("win", 1.0f),
    LOSE("lose", 1.1f);

    val durationMs: Long get() = (seconds * 1000f).toLong()
}

/**
 * 全域音效入口，讓任何實體都能直接發聲。
 *
 * 後期一秒有上百次射擊、命中、爆炸，全部播出來只會糊成一團，所以戰鬥音效有三道限制：
 *
 * - 同一個音效兩次之間至少隔 [Sfx.minGapMs]。
 * - 戰鬥音效同時最多 [MAX_COMBAT_VOICES] 個，名額滿了這次就不播。
 *   介面與事件音效（建塔、升級、波次開始、蘿蔔受傷、勝負）不佔名額，永遠會播。
 * - 每次音高隨機偏移一點。同一段聲音以固定間隔、完全相同的音高疊在一起，
 *   會互相抵銷又加強，聽起來像嗡嗡作響的馬達；音高稍微錯開就聽不出來了。
 *
 * 時間用真實時間而不是遊戲影格：3x 速度下事件變三倍，耳朵能分辨的密度卻沒變。
 */
object Audio {

    @Volatile
    var engine: SoundEngine? = null

    private const val MAX_COMBAT_VOICES = 4
    private const val PITCH_SPREAD = 0.06f

    private val lastPlayed = LongArray(Sfx.values().size)
    /** 每個戰鬥音效名額預計播完的時間。 */
    private val combatVoiceEnds = LongArray(MAX_COMBAT_VOICES)

    @Synchronized
    fun play(sfx: Sfx) {
        val target = engine ?: return
        if (!sfx.combat) {
            target.play(sfx, 1f)
            return
        }
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastPlayed[sfx.ordinal] < sfx.minGapMs) return
        var voice = -1
        for (i in combatVoiceEnds.indices) {
            if (combatVoiceEnds[i] <= now) {
                voice = i
                break
            }
        }
        if (voice < 0) return
        combatVoiceEnds[voice] = now + sfx.durationMs
        lastPlayed[sfx.ordinal] = now
        target.play(sfx, 1f + (Random.nextFloat() * 2f - 1f) * PITCH_SPREAD)
    }
}

/**
 * 不需要任何二進位素材的音效引擎。
 *
 * 首次啟動時在背景執行緒合成一批 WAV 寫進 cacheDir，
 * 音效由 SoundPool 播放，背景音樂用 MediaPlayer 循環播放。
 */
class SoundEngine(context: Context) {

    private val appContext = context.applicationContext
    private val soundIds = ConcurrentHashMap<String, Int>()
    private val soundPool: SoundPool

    @Volatile
    private var musicPlayer: MediaPlayer? = null

    @Volatile
    private var soundOn: Boolean = true

    @Volatile
    private var musicOn: Boolean = true

    @Volatile
    private var musicShouldPlay = false

    @Volatile
    private var released = false

    val isSoundOn: Boolean get() = soundOn
    val isMusicOn: Boolean get() = musicOn

    init {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(12)
            .setAudioAttributes(attributes)
            .build()

        Thread {
            runCatching { prepareAssets() }
        }.apply {
            isDaemon = true
            name = "carrot-audio-prepare"
            start()
        }
    }

    // ---- 對外 API ----

    /** [rate] 是播放速率，同時改變音高（1 = 原音）。節流與名額由 [Audio] 負責。 */
    fun play(sfx: Sfx, rate: Float) {
        if (!soundOn || released) return
        val id = soundIds[sfx.key] ?: return
        val volume = when (sfx) {
            Sfx.SHOOT -> 0.35f
            Sfx.HIT -> 0.3f
            Sfx.DIE -> 0.45f
            Sfx.COIN -> 0.4f
            else -> 0.7f
        }
        // 戰鬥音效優先權較低：串流不夠時 SoundPool 先犧牲它們，不會把建塔、波次開始的聲音擠掉。
        val priority = if (sfx.combat) 0 else 1
        runCatching { soundPool.play(id, volume, volume, priority, 0, rate) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundOn = enabled
    }

    fun setMusicEnabled(enabled: Boolean) {
        musicOn = enabled
        if (enabled) {
            if (musicShouldPlay) startMusic()
        } else {
            runCatching { musicPlayer?.pause() }
        }
    }

    fun startMusic() {
        musicShouldPlay = true
        if (!musicOn) return
        val player = musicPlayer ?: return
        runCatching { if (!player.isPlaying) player.start() }
    }

    fun pauseMusic() {
        musicShouldPlay = false
        runCatching { musicPlayer?.pause() }
    }

    fun release() {
        released = true
        musicShouldPlay = false
        runCatching { musicPlayer?.release() }
        musicPlayer = null
        runCatching { soundPool.release() }
    }

    // ---- 合成與載入 ----

    private fun prepareAssets() {
        if (released) return
        val dir = File(appContext.cacheDir, "carrot_audio_$ASSET_VERSION")
        if (!dir.exists()) dir.mkdirs()

        for (sfx in Sfx.values()) {
            val file = File(dir, "${sfx.key}.wav")
            if (!file.exists()) {
                runCatching { writeWav(file, toPcm(synthesize(sfx)), SR) }
            }
            if (file.exists()) {
                val id = runCatching { soundPool.load(file.absolutePath, 1) }.getOrDefault(0)
                if (id != 0) soundIds[sfx.key] = id
            }
        }

        val musicFile = File(dir, "bgm.wav")
        if (!musicFile.exists()) {
            runCatching { writeWav(musicFile, toPcm(buildMusic()), SR) }
        }
        if (musicFile.exists() && !released) {
            runCatching {
                val player = MediaPlayer()
                player.setDataSource(musicFile.absolutePath)
                player.isLooping = true
                player.setVolume(0.32f, 0.32f)
                player.prepare()
                if (released) {
                    player.release()
                } else {
                    musicPlayer = player
                    if (musicShouldPlay && musicOn) player.start()
                }
            }
        }
    }

    private fun synthesize(sfx: Sfx): FloatArray {
        val rnd = Random(sfx.ordinal * 977 + 13)
        return when (sfx) {
            Sfx.SHOOT -> buffer(sfx.seconds).also {
                addTone(it, 0f, 0.09f, 1150f, 420f, 0.34f, SHAPE_SQUARE, 38f)
            }
            Sfx.HIT -> buffer(sfx.seconds).also {
                addNoise(it, 0f, 0.05f, 0.22f, 70f, 0.6f, rnd)
                addTone(it, 0f, 0.05f, 1900f, 1200f, 0.14f, SHAPE_SINE, 60f)
            }
            Sfx.EXPLODE -> buffer(sfx.seconds).also {
                addNoise(it, 0f, 0.45f, 0.5f, 9f, 0.16f, rnd)
                addTone(it, 0f, 0.35f, 150f, 45f, 0.4f, SHAPE_SINE, 11f)
                addTone(it, 0f, 0.12f, 700f, 180f, 0.2f, SHAPE_SAW, 26f)
            }
            Sfx.ICE -> buffer(sfx.seconds).also {
                addTone(it, 0f, 0.26f, 1500f, 2700f, 0.2f, SHAPE_SINE, 12f)
                addTone(it, 0.04f, 0.2f, 2300f, 3400f, 0.1f, SHAPE_SINE, 15f)
            }
            Sfx.ZAP -> buffer(sfx.seconds).also {
                addNoise(it, 0f, 0.18f, 0.3f, 26f, 0.55f, rnd)
                addTone(it, 0f, 0.16f, 2600f, 900f, 0.22f, SHAPE_SQUARE, 24f)
            }
            Sfx.POISON -> buffer(sfx.seconds).also {
                addTone(it, 0f, 0.32f, 420f, 210f, 0.24f, SHAPE_TRIANGLE, 9f)
                addNoise(it, 0f, 0.3f, 0.1f, 12f, 0.1f, rnd)
            }
            Sfx.DIE -> buffer(sfx.seconds).also {
                addTone(it, 0f, 0.16f, 520f, 110f, 0.32f, SHAPE_SQUARE, 16f)
            }
            Sfx.COIN -> buffer(sfx.seconds).also {
                addTone(it, 0f, 0.07f, 1318f, 1318f, 0.26f, SHAPE_SQUARE, 12f)
                addTone(it, 0.06f, 0.17f, 1976f, 1976f, 0.26f, SHAPE_SQUARE, 9f)
            }
            Sfx.BUILD -> buffer(sfx.seconds).also {
                addTone(it, 0f, 0.09f, 330f, 330f, 0.3f, SHAPE_TRIANGLE, 12f)
                addTone(it, 0.08f, 0.16f, 494f, 494f, 0.3f, SHAPE_TRIANGLE, 9f)
            }
            Sfx.SELL -> buffer(sfx.seconds).also {
                addTone(it, 0f, 0.09f, 660f, 660f, 0.26f, SHAPE_TRIANGLE, 12f)
                addTone(it, 0.08f, 0.16f, 440f, 440f, 0.26f, SHAPE_TRIANGLE, 9f)
            }
            Sfx.UPGRADE -> buffer(sfx.seconds).also {
                addTone(it, 0f, 0.1f, 523f, 523f, 0.24f, SHAPE_SQUARE, 11f)
                addTone(it, 0.09f, 0.1f, 659f, 659f, 0.24f, SHAPE_SQUARE, 11f)
                addTone(it, 0.18f, 0.24f, 784f, 784f, 0.26f, SHAPE_SQUARE, 7f)
            }
            Sfx.DENY -> buffer(sfx.seconds).also {
                addTone(it, 0f, 0.2f, 170f, 130f, 0.28f, SHAPE_SQUARE, 8f)
            }
            Sfx.HURT -> buffer(sfx.seconds).also {
                addTone(it, 0f, 0.36f, 420f, 80f, 0.4f, SHAPE_SAW, 8f)
                addNoise(it, 0f, 0.18f, 0.18f, 16f, 0.3f, rnd)
            }
            Sfx.WAVE -> buffer(sfx.seconds).also {
                addTone(it, 0f, 0.2f, 330f, 330f, 0.24f, SHAPE_SAW, 6f)
                addTone(it, 0.16f, 0.36f, 440f, 440f, 0.26f, SHAPE_SAW, 4.5f)
            }
            Sfx.WIN -> buffer(sfx.seconds).also {
                addTone(it, 0f, 0.14f, 523f, 523f, 0.22f, SHAPE_SQUARE, 9f)
                addTone(it, 0.12f, 0.14f, 659f, 659f, 0.22f, SHAPE_SQUARE, 9f)
                addTone(it, 0.24f, 0.14f, 784f, 784f, 0.22f, SHAPE_SQUARE, 9f)
                addTone(it, 0.36f, 0.6f, 1047f, 1047f, 0.26f, SHAPE_SQUARE, 4f)
                addTone(it, 0.36f, 0.6f, 659f, 659f, 0.16f, SHAPE_TRIANGLE, 4f)
            }
            Sfx.LOSE -> buffer(sfx.seconds).also {
                addTone(it, 0f, 0.2f, 392f, 392f, 0.24f, SHAPE_SAW, 6f)
                addTone(it, 0.18f, 0.2f, 349f, 349f, 0.24f, SHAPE_SAW, 6f)
                addTone(it, 0.36f, 0.2f, 311f, 311f, 0.24f, SHAPE_SAW, 6f)
                addTone(it, 0.54f, 0.55f, 262f, 240f, 0.28f, SHAPE_SAW, 3.5f)
            }
        }
    }

    /** 八小節 C - Am - F - G 循環，方波主旋律加三角波貝斯。 */
    private fun buildMusic(): FloatArray {
        val bpm = 112f
        val beat = 60f / bpm
        val bars = 8
        val buf = buffer(bars * 4 * beat + 0.1f)
        val chords = arrayOf(
            intArrayOf(60, 64, 67),
            intArrayOf(57, 60, 64),
            intArrayOf(53, 57, 60),
            intArrayOf(55, 59, 62)
        )
        val arpeggio = intArrayOf(0, 1, 2, 1, 2, 1, 0, 2)
        val rnd = Random(7)

        for (bar in 0 until bars) {
            val chord = chords[bar % chords.size]
            val barStart = bar * 4 * beat

            for (step in 0 until 8) {
                val at = barStart + step * beat * 0.5f
                val note = chord[arpeggio[step]] + 12
                addTone(buf, at, beat * 0.46f, midiFreq(note), midiFreq(note), 0.13f, SHAPE_SQUARE, 7f)
            }
            for (step in 0 until 4) {
                val at = barStart + step * beat
                val note = chord[0] - 12
                addTone(buf, at, beat * 0.8f, midiFreq(note), midiFreq(note), 0.2f, SHAPE_TRIANGLE, 3.5f)
            }
            for (step in 0 until 4) {
                val at = barStart + step * beat + beat * 0.5f
                addNoise(buf, at, 0.05f, 0.05f, 48f, 0.85f, rnd)
            }
        }
        return buf
    }

    // ---- 低階合成工具 ----

    private fun buffer(seconds: Float): FloatArray =
        FloatArray((seconds * SR).toInt().coerceAtLeast(1))

    private fun midiFreq(midi: Int): Float =
        (440.0 * Math.pow(2.0, (midi - 69) / 12.0)).toFloat()

    private fun addTone(
        buf: FloatArray,
        startSec: Float,
        durSec: Float,
        freqStart: Float,
        freqEnd: Float,
        amp: Float,
        shape: Int,
        decay: Float
    ) {
        val start = (startSec * SR).toInt()
        val count = (durSec * SR).toInt()
        if (count <= 0) return
        var phase = 0f
        for (i in 0 until count) {
            val index = start + i
            if (index < 0 || index >= buf.size) continue
            val progress = i.toFloat() / count
            val t = i.toFloat() / SR
            val freq = freqStart + (freqEnd - freqStart) * progress
            phase += freq / SR
            while (phase >= 1f) phase -= 1f
            val wave = when (shape) {
                SHAPE_SINE -> sin(phase * TWO_PI)
                SHAPE_SQUARE -> if (phase < 0.5f) 1f else -1f
                SHAPE_SAW -> phase * 2f - 1f
                else -> if (phase < 0.5f) phase * 4f - 1f else 3f - phase * 4f
            }
            val fadeIn = (t / 0.004f).coerceAtMost(1f)
            val fadeOut = ((1f - progress) / 0.06f).coerceAtMost(1f)
            buf[index] += wave * amp * exp(-t * decay) * fadeIn * fadeOut
        }
    }

    private fun addNoise(
        buf: FloatArray,
        startSec: Float,
        durSec: Float,
        amp: Float,
        decay: Float,
        smooth: Float,
        rnd: Random
    ) {
        val start = (startSec * SR).toInt()
        val count = (durSec * SR).toInt()
        if (count <= 0) return
        var last = 0f
        for (i in 0 until count) {
            val index = start + i
            if (index < 0 || index >= buf.size) continue
            val t = i.toFloat() / SR
            val raw = rnd.nextFloat() * 2f - 1f
            last += (raw - last) * smooth
            buf[index] += last * amp * exp(-t * decay)
        }
    }

    private fun toPcm(buf: FloatArray): ShortArray {
        val out = ShortArray(buf.size)
        for (i in buf.indices) {
            val v = buf[i].coerceIn(-1f, 1f)
            out[i] = (v * 31000f).toInt().toShort()
        }
        return out
    }

    private fun writeWav(file: File, data: ShortArray, sampleRate: Int) {
        val byteLength = data.size * 2
        BufferedOutputStream(FileOutputStream(file)).use { out ->
            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            writeInt(out, 36 + byteLength)
            out.write("WAVE".toByteArray(Charsets.US_ASCII))
            out.write("fmt ".toByteArray(Charsets.US_ASCII))
            writeInt(out, 16)
            writeShort(out, 1)              // PCM
            writeShort(out, 1)              // 單聲道
            writeInt(out, sampleRate)
            writeInt(out, sampleRate * 2)   // byte rate
            writeShort(out, 2)              // block align
            writeShort(out, 16)             // bits per sample
            out.write("data".toByteArray(Charsets.US_ASCII))
            writeInt(out, byteLength)

            val bytes = ByteArray(byteLength)
            for (i in data.indices) {
                val v = data[i].toInt()
                bytes[i * 2] = (v and 0xFF).toByte()
                bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
            }
            out.write(bytes)
        }
    }

    private fun writeInt(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 24) and 0xFF)
    }

    private fun writeShort(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    companion object {
        private const val SR = 22050
        private const val TWO_PI = 6.2831855f
        private const val SHAPE_SINE = 0
        private const val SHAPE_SQUARE = 1
        private const val SHAPE_SAW = 2
        private const val SHAPE_TRIANGLE = 3
        private const val ASSET_VERSION = 1
    }
}
