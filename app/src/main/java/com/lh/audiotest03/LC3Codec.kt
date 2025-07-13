package com.lh.audiotest03

/**
 * LC3编解码器的Java封装类
 * 提供LC3音频编解码功能的接口
 */
class LC3Codec {
    companion object {
        // 加载本地库
        init {
            System.loadLibrary("audiotest03")
        }
        
        // PCM格式常量
        const val PCM_FORMAT_S16 = 0
        
        // 常用帧长（微秒）
        const val FRAME_DURATION_10MS = 10000
        const val FRAME_DURATION_7_5MS = 7500
        
        // 常用采样率（Hz）
        const val SAMPLE_RATE_8K = 8000
        const val SAMPLE_RATE_16K = 16000
        const val SAMPLE_RATE_24K = 24000
        const val SAMPLE_RATE_32K = 32000
        const val SAMPLE_RATE_48K = 48000
    }
    
    // 编码器和解码器句柄
    private var encoderHandle: Long = 0
    private var decoderHandle: Long = 0
    
    // 帧长和采样率
    private var frameDurationUs: Int = 0
    private var sampleRateHz: Int = 0
    
    // 单帧采样数和字节数
    private var frameSamples: Int = 0
    private var frameBytes: Int = 0
    
    /**
     * 初始化编码器
     * @param dtUs 帧长（微秒）
     * @param srHz 采样率（Hz）
     * @param outputByteCount 编码后每帧的字节数
     * @return 是否成功初始化
     */
    fun initEncoder(dtUs: Int, srHz: Int, outputByteCount: Int): Boolean {
        // 释放之前的编码器（如果有）
        releaseEncoder()
        
        frameDurationUs = dtUs
        sampleRateHz = srHz
        frameBytes = outputByteCount
        
        // 获取单帧采样数
        frameSamples = getFrameSamples(dtUs, srHz)
        if (frameSamples <= 0) {
            return false
        }
        
        // 设置编码器
        encoderHandle = setupEncoder(dtUs, srHz)
        return encoderHandle != 0L
    }
    
    /**
     * 初始化解码器
     * @param dtUs 帧长（微秒）
     * @param srHz 采样率（Hz）
     * @param outputByteCount 编码后每帧的字节数
     * @return 是否成功初始化
     */
    fun initDecoder(dtUs: Int, srHz: Int, outputByteCount: Int): Boolean {
        // 释放之前的解码器（如果有）
        releaseDecoder()
        
        frameDurationUs = dtUs
        sampleRateHz = srHz
        frameBytes = outputByteCount
        
        // 获取单帧采样数
        frameSamples = getFrameSamples(dtUs, srHz)
        if (frameSamples <= 0) {
            return false
        }
        
        // 设置解码器
        decoderHandle = setupDecoder(dtUs, srHz)
        return decoderHandle != 0L
    }
    
    /**
     * 编码一帧PCM数据
     * @param inputBuffer 输入PCM数据
     * @param outputBuffer 输出编码后的数据
     * @return 0表示成功，-1表示失败
     */
    fun encode(inputBuffer: ByteArray, outputBuffer: ByteArray): Int {
        if (encoderHandle == 0L) {
            return -1
        }
        
        // 计算输入缓冲区大小（每个采样2字节）
        val inputSize = frameSamples * 2
        
        return encode(encoderHandle, inputBuffer, inputSize, frameBytes, outputBuffer)
    }
    
    /**
     * 解码一帧数据
     * @param inputBuffer 输入编码数据
     * @param outputBuffer 输出PCM数据
     * @return 0表示成功，1表示执行了PLC（丢包隐藏），-1表示失败
     */
    fun decode(inputBuffer: ByteArray, outputBuffer: ByteArray): Int {
        if (decoderHandle == 0L) {
            return -1
        }
        
        // 计算输出缓冲区大小（每个采样2字节）
        val outputSize = frameSamples * 2
        
        return decode(decoderHandle, inputBuffer, frameBytes, outputBuffer, outputSize)
    }
    
    /**
     * 执行丢包隐藏（PLC）
     * @param outputBuffer 输出PCM数据
     * @return 0表示成功，1表示执行了PLC，-1表示失败
     */
    fun decodePlc(outputBuffer: ByteArray): Int {
        if (decoderHandle == 0L) {
            return -1
        }
        
        // 计算输出缓冲区大小（每个采样2字节）
        val outputSize = frameSamples * 2
        
        // 传入null作为输入缓冲区，表示执行PLC
        return decode(decoderHandle, null, 0, outputBuffer, outputSize)
    }
    
    /**
     * 释放编码器资源
     */
    fun releaseEncoder() {
        if (encoderHandle != 0L) {
            releaseEncoder(encoderHandle)
            encoderHandle = 0
        }
    }
    
    /**
     * 释放解码器资源
     */
    fun releaseDecoder() {
        if (decoderHandle != 0L) {
            releaseDecoder(decoderHandle)
            decoderHandle = 0
        }
    }
    
    /**
     * 释放所有资源
     */
    fun release() {
        releaseEncoder()
        releaseDecoder()
    }
    
    /**
     * 获取单帧采样数
     */
    fun getFrameSamplesCount(): Int {
        return frameSamples
    }
    
    /**
     * 获取单帧字节数（PCM格式，每个采样2字节）
     */
    fun getFrameBytesCount(): Int {
        return frameSamples * 2
    }
    
    /**
     * 获取编码后的字节数
     */
    fun getEncodedBytesCount(): Int {
        return frameBytes
    }
    
    // 本地方法
    private external fun getFrameSamples(dtUs: Int, srHz: Int): Int
    private external fun getEncoderSize(dtUs: Int, srHz: Int): Int
    private external fun getDecoderSize(dtUs: Int, srHz: Int): Int
    private external fun setupEncoder(dtUs: Int, srHz: Int): Long
    private external fun setupDecoder(dtUs: Int, srHz: Int): Long
    private external fun encode(encoderHandle: Long, inputBuffer: ByteArray, inputSize: Int, 
                               outputByteCount: Int, outputBuffer: ByteArray): Int
    private external fun decode(decoderHandle: Long, inputBuffer: ByteArray?, inputSize: Int, 
                               outputBuffer: ByteArray, outputSize: Int): Int
    private external fun releaseEncoder(encoderHandle: Long)
    private external fun releaseDecoder(decoderHandle: Long)
}