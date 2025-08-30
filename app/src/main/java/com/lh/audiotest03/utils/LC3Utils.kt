package com.lh.audiotest03.utils

import android.util.Log
import com.lh.audiotest03.LC3Codec
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * LC3编解码工具类，用于处理LC3音频编解码相关操作
 */
class LC3Utils {
    companion object {
        private const val TAG = "LC3Utils"
        
        /**
         * 将WAV文件编码为LC3格式
         * 
         * @param wavFile 输入WAV文件
         * @param encodedFile 输出LC3编码文件
         * @param frameDurationUs 帧长（微秒）
         * @param sampleRate 采样率（Hz）
         * @param outputByteCount 编码后每帧的字节数
         * @param lc3Codec LC3编解码器实例
         * @param logger 日志记录回调
         * @return 是否成功编码
         */
        fun encodeWavToLC3(
            wavFile: File,
            encodedFile: File,
            frameDurationUs: Int,
            sampleRate: Int,
            outputByteCount: Int,
            lc3Codec: LC3Codec,
            logger: ((String) -> Unit)? = null
        ): Boolean {
            logger?.invoke("开始编码...")
            
            if (!wavFile.exists()) {
                logger?.invoke("错误: WAV文件不存在")
                return false
            }
            
            // 初始化LC3编码器
            if (!lc3Codec.initEncoder(frameDurationUs, sampleRate, outputByteCount)) {
                logger?.invoke("错误: 无法初始化LC3编码器")
                return false
            }
            
            logger?.invoke("LC3编码器初始化成功")
            logger?.invoke("帧长: ${frameDurationUs / 1000.0} ms")
            logger?.invoke("采样率: $sampleRate Hz")
            logger?.invoke("每帧采样数: ${lc3Codec.getFrameSamplesCount()}")
            logger?.invoke("每帧PCM字节数: ${lc3Codec.getFrameBytesCount()}")
            logger?.invoke("每帧编码后字节数: ${lc3Codec.getEncodedBytesCount()}")
            
            try {
                // 解析WAV文件，提取PCM数据和格式信息
                val wavData = WavUtils.parseWavFile(wavFile, logger)
                if (wavData == null) {
                    logger?.invoke("错误: 无法解析WAV文件")
                    lc3Codec.releaseEncoder()
                    return false
                }
                
                // 显示WAV文件信息
                logger?.invoke("WAV文件解析成功:")
                logger?.invoke("- 声道数: ${wavData.channels}")
                logger?.invoke("- 采样率: ${wavData.sampleRate} Hz")
                logger?.invoke("- 位深度: ${wavData.bitsPerSample} 位")
                logger?.invoke("- PCM数据大小: ${wavData.pcmData.size} 字节")
                
                // 检查声道数，LC3编码器目前只支持单声道
                if (wavData.channels != 1) {
                    logger?.invoke("错误: LC3编码器目前只支持单声道音频，当前声道数: ${wavData.channels}")
                    lc3Codec.releaseEncoder()
                    return false
                }
                
                // 创建输入流从PCM数据读取
                val pcmInputStream = ByteArrayInputStream(wavData.pcmData)
                
                // 创建输出文件
                if (encodedFile.exists()) encodedFile.delete()
                val encodedOutputStream = FileOutputStream(encodedFile)
                
                // 获取每帧PCM数据的字节数
                val framePcmBytes = lc3Codec.getFrameBytesCount()
                val frameEncodedBytes = lc3Codec.getEncodedBytesCount()
                
                // 创建缓冲区
                val inputBuffer = ByteArray(framePcmBytes)
                val encodedBuffer = ByteArray(frameEncodedBytes)
                
                var frameCount = 0
                var bytesRead: Int
                
                // 逐帧处理
                logger?.invoke("开始逐帧处理PCM数据...")
                logger?.invoke("每帧PCM字节数: $framePcmBytes, 每帧编码后字节数: $frameEncodedBytes")
                
                // 检查输入缓冲区大小是否正确
                if (inputBuffer.size != framePcmBytes) {
                    logger?.invoke("错误: 输入缓冲区大小不匹配，预期: $framePcmBytes, 实际: ${inputBuffer.size}")
                    lc3Codec.releaseEncoder()
                    pcmInputStream.close()
                    encodedOutputStream.close()
                    return false
                }
                
                // 检查输出缓冲区大小是否正确
                if (encodedBuffer.size != frameEncodedBytes) {
                    logger?.invoke("错误: 输出缓冲区大小不匹配，预期: $frameEncodedBytes, 实际: ${encodedBuffer.size}")
                    lc3Codec.releaseEncoder()
                    pcmInputStream.close()
                    encodedOutputStream.close()
                    return false
                }
                
                while (pcmInputStream.read(inputBuffer).also { bytesRead = it } == framePcmBytes) {
                    // 编码前记录前几个字节用于调试
                    if (frameCount == 0) {
                        val hexString = inputBuffer.take(16).joinToString("") { "%02X ".format(it) }
                        logger?.invoke("第一帧PCM数据前16字节: $hexString")
                    }
                    
                    // 编码
                    val encodeResult = lc3Codec.encode(inputBuffer, encodedBuffer)
                    
                    if (encodeResult < 0) {
                        logger?.invoke("警告: 帧 $frameCount 编码失败，错误码: $encodeResult")
                        continue
                    } else {
                        // 成功编码，记录详细信息
                        if (frameCount == 0 || frameCount % 100 == 0) {
                            logger?.invoke("成功编码帧 $frameCount, 返回值: $encodeResult")
                            
                            // 记录编码后的前几个字节用于调试
                            if (frameCount == 0) {
                                val hexString = encodedBuffer.take(16).joinToString("") { "%02X ".format(it) }
                                logger?.invoke("第一帧编码后数据前16字节: $hexString")
                            }
                        }
                    }
                    
                    // 写入编码数据
                    encodedOutputStream.write(encodedBuffer)
                    
                    frameCount++
                }
                
                logger?.invoke("PCM数据读取完成，最后一次读取字节数: $bytesRead")
                if (bytesRead > 0 && bytesRead < framePcmBytes) {
                    logger?.invoke("注意: 最后一帧数据不完整，已跳过")
                }
                
                // 关闭文件
                pcmInputStream.close()
                encodedOutputStream.close()
                
                // 释放编码器资源
                lc3Codec.releaseEncoder()
                
                logger?.invoke("编码完成")
                logger?.invoke("处理帧数: $frameCount")
                
                val encodedFileSize = encodedFile.length()
                logger?.invoke("编码文件大小: $encodedFileSize 字节")
                logger?.invoke("原始WAV大小: ${wavFile.length()} 字节")
                
                // 检查编码文件大小
                if (encodedFileSize == 0L) {
                    logger?.invoke("错误: 编码文件大小为0，编码失败")
                    return false
                }
                
                if (frameCount == 0) {
                    logger?.invoke("错误: 没有成功编码任何帧")
                    return false
                }
                
                logger?.invoke("压缩比: ${String.format("%.2f", wavFile.length().toFloat() / encodedFileSize)}")
                
                return true
                
            } catch (e: IOException) {
                logger?.invoke("错误: 处理文件时发生错误: ${e.message}")
                Log.e(TAG, "处理文件时发生错误", e)
                lc3Codec.releaseEncoder()
                return false
            }
        }
        
        /**
         * 将LC3编码文件解码为WAV文件
         * 
         * @param encodedFile 输入LC3编码文件
         * @param wavFile 输出WAV文件
         * @param frameDurationUs 帧长（微秒）
         * @param sampleRate 采样率（Hz）
         * @param outputByteCount 编码后每帧的字节数
         * @param channelConfig 声道配置
         * @param audioFormat 音频格式
         * @param lc3Codec LC3编解码器实例
         * @param logger 日志记录回调
         * @return 是否成功解码
         */
        fun decodeLC3ToWav(
            encodedFile: File,
            wavFile: File,
            frameDurationUs: Int,
            sampleRate: Int,
            outputByteCount: Int,
            channelConfig: Int,
            audioFormat: Int,
            lc3Codec: LC3Codec,
            logger: ((String) -> Unit)? = null
        ): Boolean {
            logger?.invoke("开始LC3到WAV解码...")
            
            if (!encodedFile.exists()) {
                logger?.invoke("错误: 编码文件不存在")
                return false
            }
            
            // 初始化LC3解码器
            if (!lc3Codec.initDecoder(frameDurationUs, sampleRate, outputByteCount)) {
                logger?.invoke("错误: 无法初始化LC3解码器")
                return false
            }
            
            logger?.invoke("LC3解码器初始化成功")
            logger?.invoke("帧长: ${frameDurationUs / 1000.0} ms")
            logger?.invoke("采样率: $sampleRate Hz")
            logger?.invoke("每帧采样数: ${lc3Codec.getFrameSamplesCount()}")
            logger?.invoke("每帧PCM字节数: ${lc3Codec.getFrameBytesCount()}")
            
            try {
                // 读取编码文件
                val encodedData = encodedFile.readBytes()
                logger?.invoke("读取LC3编码数据: ${encodedData.size} 字节")
                
                // 创建临时PCM文件用于存储解码后的PCM数据
                val tempPcmFile = File(wavFile.parent, "temp_decoded.pcm")
                if (tempPcmFile.exists()) tempPcmFile.delete()
                val decodedOutputStream = FileOutputStream(tempPcmFile)
                
                // 获取每帧数据的字节数
                val framePcmBytes = lc3Codec.getFrameBytesCount()
                val frameEncodedBytes = lc3Codec.getEncodedBytesCount()
                
                // 创建缓冲区
                val encodedBuffer = ByteArray(frameEncodedBytes)
                val outputBuffer = ByteArray(framePcmBytes)
                
                var frameCount = 0
                var offset = 0
                
                // 逐帧处理
                logger?.invoke("开始逐帧处理编码数据...")
                logger?.invoke("每帧编码字节数: $frameEncodedBytes, 每帧PCM字节数: $framePcmBytes")
                
                // 检查输入缓冲区大小是否正确
                if (encodedBuffer.size != frameEncodedBytes) {
                    logger?.invoke("错误: 输入缓冲区大小不匹配，预期: $frameEncodedBytes, 实际: ${encodedBuffer.size}")
                    lc3Codec.releaseDecoder()
                    decodedOutputStream.close()
                    return false
                }
                
                // 检查输出缓冲区大小是否正确
                if (outputBuffer.size != framePcmBytes) {
                    logger?.invoke("错误: 输出缓冲区大小不匹配，预期: $framePcmBytes, 实际: ${outputBuffer.size}")
                    lc3Codec.releaseDecoder()
                    decodedOutputStream.close()
                    return false
                }
                
                while (offset + frameEncodedBytes <= encodedData.size) {
                    // 复制当前帧的编码数据
                    System.arraycopy(encodedData, offset, encodedBuffer, 0, frameEncodedBytes)
                    
                    // 记录第一帧编码数据用于调试
                    if (frameCount == 0) {
                        val hexString = encodedBuffer.take(16).joinToString("") { "%02X ".format(it) }
                        logger?.invoke("第一帧编码数据前16字节: $hexString")
                    }
                    
                    // 解码
                    val decodeResult = lc3Codec.decode(encodedBuffer, outputBuffer)
                    if (decodeResult < 0) {
                        logger?.invoke("解码错误，帧 $frameCount, 错误码: $decodeResult")
                        continue
                    } else {
                        // 成功解码，记录详细信息
                        if (frameCount == 0 || frameCount % 100 == 0) {
                            logger?.invoke("成功解码帧 $frameCount, 返回值: $decodeResult")
                            
                            // 记录解码后的前几个字节用于调试
                            if (frameCount == 0) {
                                val hexString = outputBuffer.take(16).joinToString("") { "%02X ".format(it) }
                                logger?.invoke("第一帧解码后数据前16字节: $hexString")
                            }
                        }
                    }
                    
                    // 将解码后的PCM数据写入文件
                    decodedOutputStream.write(outputBuffer)
                    
                    offset += frameEncodedBytes
                    frameCount++
                }
                
                // 关闭PCM文件流
                decodedOutputStream.close()
                
                // 将PCM数据转换为WAV格式
                logger?.invoke("正在将解码后的PCM数据转换为WAV格式...")
                if (wavFile.exists()) wavFile.delete()
                WavUtils.convertPcmToWav(tempPcmFile, wavFile, sampleRate, channelConfig, audioFormat, logger)
                
                // 删除临时PCM文件
                tempPcmFile.delete()
                
                // 释放解码器资源
                lc3Codec.releaseDecoder()
                
                logger?.invoke("LC3到WAV解码完成")
                logger?.invoke("处理帧数: $frameCount")
                logger?.invoke("解码文件大小: ${wavFile.length()} 字节")
                
                return true
                
            } catch (e: IOException) {
                logger?.invoke("错误: 处理文件时发生错误: ${e.message}")
                Log.e(TAG, "处理文件时发生错误", e)
                lc3Codec.releaseDecoder()
                return false
            }
        }
    }
}