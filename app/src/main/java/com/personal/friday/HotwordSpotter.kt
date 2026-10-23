package com.personal.friday

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class HotwordSpotter(
    private val context: Context,
    private val onHotwordSpotted: (String) -> Unit
) {
    private val TAG = "HotwordSpotter"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var keywordSpotter: KeywordSpotter? = null
    private var onlineStream: OnlineStream? = null
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    private val sampleRate = 16000

    companion object {
        private var librariesLoaded = false

        fun loadLibraries() {
            if (librariesLoaded) return
            try {
                System.loadLibrary("onnxruntime")
                System.loadLibrary("sherpa-onnx-c-api")
                System.loadLibrary("sherpa-onnx-cxx-api")
                System.loadLibrary("sherpa-onnx-jni")
                librariesLoaded = true
                Log.i("HotwordSpotter", "Sherpa-ONNX native libraries loaded successfully")
            } catch (t: Throwable) {
                Log.e("HotwordSpotter", "Notice on loading native libraries", t)
            }
        }
    }

    private fun copyAssetToFile(assetPath: String, destFile: File) {
        destFile.parentFile?.mkdirs()
        try {
            context.assets.open(assetPath).use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Copied asset $assetPath to ${destFile.absolutePath} (${destFile.length()} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed copying asset $assetPath", e)
        }
    }

    @Synchronized
    private fun initEngineIfNeeded(): Boolean {
        if (keywordSpotter != null) return true

        return try {
            loadLibraries()

            val kwsDir = File(context.filesDir, "kws")
            val v2Marker = File(kwsDir, "model_v3_fixed.marker")
            if (!v2Marker.exists()) {
                Log.i(TAG, "Upgrading KWS model to verified non-mobile build...")
                kwsDir.deleteRecursively()
                kwsDir.mkdirs()
            }

            val encoderFile = File(kwsDir, "encoder.onnx")
            val decoderFile = File(kwsDir, "decoder.onnx")
            val joinerFile = File(kwsDir, "joiner.onnx")
            val tokensFile = File(kwsDir, "tokens.txt")
            val keywordsFile = File(kwsDir, "keywords.txt")

            if (!encoderFile.exists()) copyAssetToFile("kws/encoder.onnx", encoderFile)
            if (!decoderFile.exists()) copyAssetToFile("kws/decoder.onnx", decoderFile)
            if (!joinerFile.exists()) copyAssetToFile("kws/joiner.onnx", joinerFile)
            if (!tokensFile.exists()) copyAssetToFile("kws/tokens.txt", tokensFile)
            if (!keywordsFile.exists()) copyAssetToFile("kws/keywords.txt", keywordsFile)

            if (!v2Marker.exists()) {
                try { v2Marker.createNewFile() } catch (_: Exception) {}
            }

            if (!encoderFile.exists() || !decoderFile.exists() || !joinerFile.exists() || !tokensFile.exists() || !keywordsFile.exists()) {
                Log.e(TAG, "Model files missing from storage")
                return false
            }

            val transducer = OnlineTransducerModelConfig().apply {
                encoder = encoderFile.absolutePath
                decoder = decoderFile.absolutePath
                joiner = joinerFile.absolutePath
            }

            val modelConfig = OnlineModelConfig().apply {
                this.transducer = transducer
                tokens = tokensFile.absolutePath
                numThreads = 1
                debug = false
                provider = "cpu"
                modelType = ""
            }

            val featConfig = FeatureConfig().apply {
                sampleRate = this@HotwordSpotter.sampleRate
                featureDim = 80
            }

            val kwsConfig = KeywordSpotterConfig().apply {
                this.featConfig = featConfig
                this.modelConfig = modelConfig
                this.keywordsFile = keywordsFile.absolutePath
                maxActivePaths = 4
                keywordsScore = 1.0f
                keywordsThreshold = 0.25f
                numTrailingBlanks = 1
            }

            // Passing null AssetManager activates newFromFile in Sherpa-ONNX JNI
            keywordSpotter = KeywordSpotter(null, kwsConfig)
            onlineStream = keywordSpotter?.createStream()
            Log.i(TAG, "Sherpa-ONNX file-based KWS engine initialized successfully!")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize Sherpa-ONNX KWS engine", t)
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun createAndStartAudioRecord(): Boolean {
        releaseAudioRecord()
        return try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufferSize, sampleRate / 5) // 200ms buffer

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                false
            } else {
                audioRecord?.startRecording()
                Log.i(TAG, "AudioRecord started successfully")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting AudioRecord", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning.get() && !isPaused.get()) {
            return
        }

        if (isPaused.get()) {
            resume()
            return
        }

        isRunning.set(true)
        isPaused.set(false)

        recordingThread = Thread({
            try {
                if (!initEngineIfNeeded()) {
                    isRunning.set(false)
                    return@Thread
                }

                val shortBuffer = ShortArray(1600) // 100ms chunks at 16kHz
                val floatBuffer = FloatArray(1600)
                var activeSpeechHangover = 0
                val silenceThresholdRms = 300.0 // Noise floor threshold for VAD

                while (isRunning.get()) {
                    if (isPaused.get()) {
                        try {
                            Thread.sleep(120)
                        } catch (e: InterruptedException) {
                            break
                        }
                        continue
                    }

                    if (audioRecord == null) {
                        if (!createAndStartAudioRecord()) {
                            try { Thread.sleep(200) } catch (_: InterruptedException) { break }
                            continue
                        }
                    }

                    val record = audioRecord ?: continue
                    val samplesRead = record.read(shortBuffer, 0, shortBuffer.size)
                    if (samplesRead > 0) {
                        // Calculate RMS Energy (Voice Activity Detection)
                        var sumSquares = 0.0
                        for (i in 0 until samplesRead) {
                            val s = shortBuffer[i].toDouble()
                            sumSquares += s * s
                            floatBuffer[i] = shortBuffer[i] / 32768.0f
                        }
                        val rms = Math.sqrt(sumSquares / samplesRead)

                        if (Math.random() < 0.05) {
                            Log.d(TAG, "Microphone RMS: ${rms.toInt()} (hangover: $activeSpeechHangover)")
                        }

                        // If sound is above silence threshold, keep active window open for next 1.5 seconds (15 chunks)
                        if (rms >= silenceThresholdRms) {
                            activeSpeechHangover = 15
                        } else if (activeSpeechHangover > 0) {
                            activeSpeechHangover--
                        }

                        val stream = onlineStream ?: continue
                        val spotter = keywordSpotter ?: continue

                        stream.acceptWaveform(floatBuffer.copyOf(samplesRead), sampleRate)

                        // ONLY decode when someone is actually speaking (Zero CPU during silence, eliminates phone heating!)
                        if (activeSpeechHangover > 0) {
                            while (spotter.isReady(stream)) {
                                spotter.decode(stream)
                            }

                            val result = spotter.getResult(stream)
                            if (!result.keyword.isNullOrBlank()) {
                                val detected = result.keyword
                                Log.d(TAG, "🔥 SHERPA-ONNX WAKE WORD DETECTED: $detected")
                                spotter.reset(stream)
                                activeSpeechHangover = 0

                                pause()
                                mainHandler.post {
                                    onHotwordSpotted("FRIDAY")
                                }
                            }
                        } else {
                            // In silence: discard accumulated silent frames so buffer never lags
                            if (spotter.isReady(stream)) {
                                spotter.reset(stream)
                            }
                        }
                    } else if (samplesRead < 0) {
                        Log.w(TAG, "AudioRecord read error: $samplesRead")
                        Thread.sleep(50)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error in continuous audio loop", t)
            } finally {
                releaseAudioRecord()
                Log.i(TAG, "Continuous audio recording thread stopped")
            }
        }, "FridayHotwordThread").apply {
            priority = Thread.NORM_PRIORITY
            start()
        }
    }

    fun pause() {
        isPaused.set(true)
        releaseAudioRecord()
        Log.d(TAG, "HotwordSpotter paused & hardware mic released (Green Dot OFF)")
    }

    fun resume() {
        if (!isRunning.get()) {
            start()
            return
        }
        isPaused.set(false)
        onlineStream?.let { keywordSpotter?.reset(it) }
    }

    fun stop() {
        isRunning.set(false)
        isPaused.set(false)
        recordingThread?.interrupt()
        recordingThread = null

        releaseAudioRecord()

        try {
            onlineStream?.release()
            onlineStream = null
            keywordSpotter?.release()
            keywordSpotter = null
        } catch (t: Throwable) {
            Log.w(TAG, "Error releasing Sherpa-ONNX spotter", t)
        }
    }

    private fun releaseAudioRecord() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (t: Throwable) {
            Log.w(TAG, "Error releasing AudioRecord", t)
        } finally {
            audioRecord = null
        }
    }
}
