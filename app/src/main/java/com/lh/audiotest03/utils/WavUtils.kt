package com.lh.audiotest03.utils

import android.media.AudioFormat
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * WAV文件工具类，用于处理WAV文件的解析、转换等操作
 */
class WavUtils {
    companion object {
        private const val TAG = "WavUtils"
        
        /**
         * WAV文件数据类
         */
        data class WavData(
            val channels: Int,
            val sampleRate: Int,
            val bitsPerSample: Int,
            val pcmData: ByteArray
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
                
                other as WavData
                
                if (channels != other.channels) return false
                if (sampleRate != other.sampleRate) return false
                if (bitsPerSample != other.bitsPerSample) return false
                if (!pcmData.contentEquals(other.pcmData)) return false
                
                return true
            }
            
            override fun hashCode(): Int {
                var result = channels
                result = 31 * result + sampleRate
                result = 31 * result + bitsPerSample
                result = 31 * result + pcmData.contentHashCode()
                return result
            }
        }
        
        /**
         * 解析WAV文件，提取PCM数据和格式信息
         */
        fun parseWavFile(wavFile: File, logger: ((String) -> Unit)? = null): WavData? {
            try {
                val inputStream = FileInputStream(wavFile)
                
                // 读取RIFF头
                val riffHeader = ByteArray(4)
                inputStream.read(riffHeader)
                if (String(riffHeader) != "RIFF") {
                    logger?.invoke("错误: 不是有效的WAV文件 (RIFF标识符不匹配)")
                    Log.e(TAG, "错误: 不是有效的WAV文件 (RIFF标识符不匹配)")
                    inputStream.close()
                    return null
                }
                
                // 读取文件大小（减去8字节的RIFF头和ChunkSize）
                val fileSize = readInt(inputStream) + 8
                
                // 读取WAVE标识符
                val waveHeader = ByteArray(4)
                inputStream.read(waveHeader)
                if (String(waveHeader) != "WAVE") {
                    logger?.invoke("错误: 不是有效的WAV文件 (WAVE标识符不匹配)")
                    Log.e(TAG, "错误: 不是有效的WAV文件 (WAVE标识符不匹配)")
                    inputStream.close()
                    return null
                }
                
                // 读取fmt子块
                val fmtHeader = ByteArray(4)
                inputStream.read(fmtHeader)
                if (String(fmtHeader) != "fmt ") {
                    logger?.invoke("错误: 不是有效的WAV文件 (fmt标识符不匹配)")
                    Log.e(TAG, "错误: 不是有效的WAV文件 (fmt标识符不匹配)")
                    inputStream.close()
                    return null
                }
                
                // 读取fmt子块大小
                val fmtSize = readInt(inputStream)
                
                // 读取音频格式（1表示PCM）
                val audioFormat = readShort(inputStream)
                if (audioFormat != 1) {
                    logger?.invoke("错误: 不支持的音频格式 (仅支持PCM格式)")
                    Log.e(TAG, "错误: 不支持的音频格式 (仅支持PCM格式)")
                    inputStream.close()
                    return null
                }
                
                // 读取声道数
                val channels = readShort(inputStream)
                
                // 读取采样率
                val sampleRate = readInt(inputStream)
                
                // 读取字节率
                val byteRate = readInt(inputStream)
                
                // 读取块对齐
                val blockAlign = readShort(inputStream)
                
                // 读取位深度
                val bitsPerSample = readShort(inputStream)
                
                // 跳过fmt子块中的额外数据（如果有）
                if (fmtSize > 16) {
                    inputStream.skip((fmtSize - 16).toLong())
                }
                
                // 查找data子块
                var dataHeader = ByteArray(4)
                while (true) {
                    if (inputStream.read(dataHeader) < 4) {
                        logger?.invoke("错误: 未找到data子块")
                        Log.e(TAG, "错误: 未找到data子块")
                        inputStream.close()
                        return null
                    }
                    
                    if (String(dataHeader) == "data") {
                        break
                    }
                    
                    // 跳过非data子块
                    val chunkSize = readInt(inputStream)
                    inputStream.skip(chunkSize.toLong())
                }
                
                // 读取data子块大小
                val dataSize = readInt(inputStream)
                
                // 读取PCM数据
                val pcmData = ByteArray(dataSize)
                inputStream.read(pcmData)
                
                inputStream.close()
                
                return WavData(channels, sampleRate, bitsPerSample, pcmData)
                
            } catch (e: Exception) {
                logger?.invoke("错误: 解析WAV文件时发生异常: ${e.message}")
                Log.e(TAG, "错误: 解析WAV文件时发生异常: ${e.message}")
                e.printStackTrace()
                return null
            }
        }
        
        /**
         * 将PCM数据转换为WAV格式
         */
        fun convertPcmToWav(
            pcmFile: File, 
            wavFile: File, 
            sampleRate: Int, 
            channelConfig: Int, 
            audioFormat: Int,
            logger: ((String) -> Unit)? = null
        ) {
            try {
                val channels = if (channelConfig == AudioFormat.CHANNEL_IN_MONO) 1 else 2
                val bitsPerSample = if (audioFormat == AudioFormat.ENCODING_PCM_16BIT) 16 else 8
                
                val pcmData = pcmFile.readBytes()
                val pcmDataLength = pcmData.size
                
                // 创建WAV文件
                val wavOutputStream = FileOutputStream(wavFile)
                
                // 写入WAV文件头
                // RIFF头
                wavOutputStream.write("RIFF".toByteArray()) // ChunkID
                writeInt(wavOutputStream, 36 + pcmDataLength) // ChunkSize: 4 + (8 + SubChunk1Size) + (8 + SubChunk2Size)
                wavOutputStream.write("WAVE".toByteArray()) // Format
                
                // fmt子块
                wavOutputStream.write("fmt ".toByteArray()) // Subchunk1ID
                writeInt(wavOutputStream, 16) // Subchunk1Size: 16 for PCM
                writeShort(wavOutputStream, 1) // AudioFormat: 1 for PCM
                writeShort(wavOutputStream, channels) // NumChannels
                writeInt(wavOutputStream, sampleRate) // SampleRate
                writeInt(wavOutputStream, sampleRate * channels * bitsPerSample / 8) // ByteRate
                writeShort(wavOutputStream, channels * bitsPerSample / 8) // BlockAlign
                writeShort(wavOutputStream, bitsPerSample) // BitsPerSample
                
                // data子块
                wavOutputStream.write("data".toByteArray()) // Subchunk2ID
                writeInt(wavOutputStream, pcmDataLength) // Subchunk2Size
                
                // 写入PCM数据
                wavOutputStream.write(pcmData)
                wavOutputStream.close()
                
                logger?.invoke("PCM转WAV成功: ${wavFile.absolutePath}")
                Log.d(TAG, "PCM转WAV成功: ${wavFile.absolutePath}")
            } catch (e: IOException) {
                logger?.invoke("错误: PCM转WAV失败: ${e.message}")
                Log.e(TAG, "错误: PCM转WAV失败: ${e.message}")
                e.printStackTrace()
            }
        }
        
        /**
         * 读取32位整数（小端序）
         */
        private fun readInt(input: InputStream): Int {
            val bytes = ByteArray(4)
            input.read(bytes)
            return bytes[0].toInt() and 0xFF or
                   ((bytes[1].toInt() and 0xFF) shl 8) or
                   ((bytes[2].toInt() and 0xFF) shl 16) or
                   ((bytes[3].toInt() and 0xFF) shl 24)
        }
        
        /**
         * 读取16位整数（小端序）
         */
        private fun readShort(input: InputStream): Int {
            val bytes = ByteArray(2)
            input.read(bytes)
            return bytes[0].toInt() and 0xFF or
                   ((bytes[1].toInt() and 0xFF) shl 8)
        }
        
        /**
         * 写入32位整数（小端序）
         */
        private fun writeInt(output: FileOutputStream, value: Int) {
            output.write(value and 0xFF)
            output.write((value shr 8) and 0xFF)
            output.write((value shr 16) and 0xFF)
            output.write((value shr 24) and 0xFF)
        }
        
        /**
         * 写入16位整数（小端序）
         */
        private fun writeShort(output: FileOutputStream, value: Int) {
            output.write(value and 0xFF)
            output.write((value shr 8) and 0xFF)
        }
    }
}