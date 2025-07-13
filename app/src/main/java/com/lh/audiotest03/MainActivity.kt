package com.lh.audiotest03

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.lh.audiotest03.utils.LC3Utils
import com.lh.audiotest03.utils.WavUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
        
        // 音频配置
        private const val SAMPLE_RATE = 48000 // 采样率
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO // 单声道
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT // 16位PCM
        private const val FRAME_DURATION_US = 10000 // 10ms帧长
        private const val OUTPUT_BYTE_COUNT = 120 // 编码后每帧字节数
    }
    
    // UI组件
    private lateinit var btnRecord: Button
    private lateinit var btnEncode: Button
    private lateinit var btnDecode: Button
    private lateinit var btnPlay: Button
    private lateinit var tvLog: TextView
    
    // 文件路径
    private lateinit var rawWavFile: File
    private lateinit var encodedFile: File
    private lateinit var decodedPcmFile: File
    
    // 线程池
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    
    // LC3编解码器
    private val lc3Codec = LC3Codec()
    
    // 权限标志
    private var permissionGranted = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        // 初始化UI组件
        initViews()
        
        // 初始化文件路径
        initFilePaths()
        
        // 请求录音权限
        requestAudioPermission()
        
        // 设置按钮点击事件
        setupButtonListeners()
    }
    
    private fun initViews() {
        btnRecord = findViewById(R.id.btnRecord)
        btnEncode = findViewById(R.id.btnEncode)
        btnDecode = findViewById(R.id.btnDecode)
        btnPlay = findViewById(R.id.btnPlay)
        tvLog = findViewById(R.id.tvLog)
        
        // 初始状态下，只有录音按钮可用
        btnEncode.isEnabled = false
        btnDecode.isEnabled = false
        btnPlay.isEnabled = false
    }
    
    private fun initFilePaths() {
        val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        rawWavFile = File(dir, "raw_audio.wav")
        encodedFile = File(dir, "encoded.lc3")
        decodedPcmFile = File(dir, "decoded.pcm")
    }
    
    private fun requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        } else {
            permissionGranted = true
        }
    }
    
    private fun setupButtonListeners() {
        btnRecord.setOnClickListener {
            if (!permissionGranted) {
                Toast.makeText(this, "需要录音权限", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // 禁用所有按钮，防止重复点击
            setButtonsEnabled(false)
            
            // 清空日志
            tvLog.text = ""
            
            // 开始录音
            executor.execute {
                recordAudio()
                
                // 录音完成后，启用编码按钮
                runOnUiThread {
                    btnRecord.isEnabled = true
                    btnEncode.isEnabled = true
                    btnDecode.isEnabled = false
                    btnPlay.isEnabled = false
                }
            }
        }
        
        btnEncode.setOnClickListener {
            // 禁用所有按钮，防止重复点击
            setButtonsEnabled(false)
            
            // 开始编码
            executor.execute {
                encodeWavToLC3()
                
                // 编码完成后，启用解码按钮
                runOnUiThread {
                    btnRecord.isEnabled = true
                    btnEncode.isEnabled = true
                    btnDecode.isEnabled = true
                    btnPlay.isEnabled = false
                }
            }
        }
        
        btnDecode.setOnClickListener {
            // 禁用所有按钮，防止重复点击
            setButtonsEnabled(false)
            
            // 开始解码
            executor.execute {
                decodeLC3ToWav()
                
                // 解码完成后，启用播放按钮
                runOnUiThread {
                    btnRecord.isEnabled = true
                    btnEncode.isEnabled = true
                    btnDecode.isEnabled = true
                    btnPlay.isEnabled = true
                }
            }
        }
        
        btnPlay.setOnClickListener {
            // 禁用所有按钮，防止重复点击
            setButtonsEnabled(false)
            
            // 开始播放
            executor.execute {
                playAudio()
                
                // 播放完成后，启用所有按钮
                runOnUiThread {
                    setButtonsEnabled(true)
                }
            }
        }
    }
    
    private fun setButtonsEnabled(enabled: Boolean) {
        btnRecord.isEnabled = enabled
        btnEncode.isEnabled = enabled
        btnDecode.isEnabled = enabled
        btnPlay.isEnabled = enabled
    }
    
    private fun recordAudio() {
        appendLog("开始录音...")
        
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
        
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            appendLog("错误: 无法初始化AudioRecord")
            return
        }
        
        // 创建临时PCM文件用于存储原始数据
        val tempPcmFile = File(rawWavFile.parent, "temp_raw.pcm")
        val outputStream: FileOutputStream
        try {
            if (tempPcmFile.exists()) {
                tempPcmFile.delete()
            }
            outputStream = FileOutputStream(tempPcmFile)
        } catch (e: IOException) {
            appendLog("错误: 无法创建临时文件: ${e.message}")
            return
        }
        
        // 开始录音
        val buffer = ByteArray(bufferSize)
        audioRecord.startRecording()
        
        appendLog("正在录音...按下返回键停止录音")
        
        // 录制3秒钟
        val recordDurationMs = 3000
        val startTime = System.currentTimeMillis()
        var totalBytesRecorded = 0
        
        try {
            while (System.currentTimeMillis() - startTime < recordDurationMs) {
                val read = audioRecord.read(buffer, 0, bufferSize)
                if (read > 0) {
                    outputStream.write(buffer, 0, read)
                    totalBytesRecorded += read
                }
            }
        } catch (e: IOException) {
            appendLog("错误: 录音时发生错误: ${e.message}")
        } finally {
            // 停止录音并释放资源
            audioRecord.stop()
            audioRecord.release()
            
            try {
                outputStream.close()
            } catch (e: IOException) {
                appendLog("错误: 关闭文件时发生错误: ${e.message}")
            }
        }
        
        // 将PCM数据转换为WAV格式
        try {
            appendLog("正在将PCM数据转换为WAV格式...")
            convertPcmToWav(tempPcmFile, rawWavFile, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            // 删除临时PCM文件
            tempPcmFile.delete()
        } catch (e: IOException) {
            appendLog("错误: 转换为WAV格式时发生错误: ${e.message}")
            return
        }
        
        appendLog("录音完成，文件保存在: ${rawWavFile.absolutePath}")
        appendLog("文件大小: ${rawWavFile.length()} 字节")
    }
    
    /**
     * 将PCM数据转换为WAV格式
     */
    private fun convertPcmToWav(pcmFile: File, wavFile: File, sampleRate: Int, channelConfig: Int, audioFormat: Int) {
        // 这个方法现在只是调用WavUtils中的同名方法，因为功能已经移到了WavUtils类中
        WavUtils.convertPcmToWav(pcmFile, wavFile, sampleRate, channelConfig, audioFormat, this::appendLog)
    }
    
    // 使用WavUtils类处理WAV文件
    
    /**
     * 将WAV文件编码为LC3格式
     */
    private fun encodeWavToLC3() {
        // 使用LC3Utils工具类进行WAV到LC3的编码
        LC3Utils.encodeWavToLC3(
            wavFile = rawWavFile,
            encodedFile = encodedFile,
            frameDurationUs = FRAME_DURATION_US,
            sampleRate = SAMPLE_RATE,
            outputByteCount = OUTPUT_BYTE_COUNT,
            lc3Codec = lc3Codec,
            logger = this::appendLog
        )
    }
    
    /**
     * 将LC3格式解码为WAV
     */
    private fun decodeLC3ToWav() {
        // 使用LC3Utils工具类进行LC3到WAV的解码
        LC3Utils.decodeLC3ToWav(
            encodedFile = encodedFile,
            decodedWavFile = decodedPcmFile,
            frameDurationUs = FRAME_DURATION_US,
            sampleRate = SAMPLE_RATE,
            outputByteCount = OUTPUT_BYTE_COUNT,
            channelConfig = CHANNEL_CONFIG,
            audioFormat = AUDIO_FORMAT,
            lc3Codec = lc3Codec,
            logger = this::appendLog
        )
    }
    
    private fun playAudio() {
        appendLog("开始播放解码后的音频...")
        
        if (!decodedPcmFile.exists()) {
            appendLog("错误: 解码后的文件不存在，请先进行编码和解码")
            return
        }
        
        try {
            // 读取解码后的PCM数据
            val decodedData = decodedPcmFile.readBytes()
            appendLog("读取解码后的PCM数据: ${decodedData.size} 字节")
            
            // 创建AudioTrack
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AUDIO_FORMAT
            )
            
            val audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AUDIO_FORMAT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
            
            // 开始播放
            audioTrack.play()
            
            // 写入数据
            audioTrack.write(decodedData, 0, decodedData.size)
            
            // 等待播放完成
            Thread.sleep(decodedData.size * 1000L / (SAMPLE_RATE * 2))
            
            // 停止并释放资源
            audioTrack.stop()
            audioTrack.release()
            
            appendLog("播放完成")
            
        } catch (e: Exception) {
            appendLog("错误: 播放过程中发生异常: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun appendLog(message: String) {
        Log.d(TAG, message)
        runOnUiThread {
            tvLog.append("$message\n")
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            permissionGranted = grantResults.isNotEmpty() && 
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
            
            if (!permissionGranted) {
                Toast.makeText(this, "需要录音权限才能使用此功能", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        lc3Codec.release()
    }
}