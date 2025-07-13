#include "lib/include/lc3.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <jni.h>
#include <android/log.h>

#define TAG "LC3_JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// 获取单帧的采样数
extern "C"
JNIEXPORT jint JNICALL
Java_com_lh_audiotest03_LC3Codec_getFrameSamples(JNIEnv *env, jobject thiz, jint dt_us, jint sr_hz) {
    return lc3_frame_samples(dt_us, sr_hz);
}

// 获取编码器需要的内存大小
extern "C"
JNIEXPORT jint JNICALL
Java_com_lh_audiotest03_LC3Codec_getEncoderSize(JNIEnv *env, jobject thiz, jint dt_us, jint sr_hz) {
    return lc3_encoder_size(dt_us, sr_hz);
}

// 获取解码器需要的内存大小
extern "C"
JNIEXPORT jint JNICALL
Java_com_lh_audiotest03_LC3Codec_getDecoderSize(JNIEnv *env, jobject thiz, jint dt_us, jint sr_hz) {
    return lc3_decoder_size(dt_us, sr_hz);
}

// 设置编码器
extern "C"
JNIEXPORT jlong JNICALL
Java_com_lh_audiotest03_LC3Codec_setupEncoder(JNIEnv *env, jobject thiz, jint dt_us, jint sr_hz) {
    unsigned encodeSize = lc3_encoder_size(dt_us, sr_hz);
    if (encodeSize == 0) {
        LOGE("Invalid encoder parameters: dt_us=%d, sr_hz=%d", dt_us, sr_hz);
        return 0;
    }
    
    void* encMem = malloc(encodeSize);
    if (encMem == NULL) {
        LOGE("Failed to allocate memory for encoder");
        return 0;
    }
    
    lc3_encoder_t encoder = lc3_setup_encoder(dt_us, sr_hz, 0, encMem);
    if (encoder == NULL) {
        LOGE("Failed to setup encoder");
        free(encMem);
        return 0;
    }
    
    return (jlong)encoder;
}

// 设置解码器
extern "C"
JNIEXPORT jlong JNICALL
Java_com_lh_audiotest03_LC3Codec_setupDecoder(JNIEnv *env, jobject thiz, jint dt_us, jint sr_hz) {
    unsigned decodeSize = lc3_decoder_size(dt_us, sr_hz);
    if (decodeSize == 0) {
        LOGE("Invalid decoder parameters: dt_us=%d, sr_hz=%d", dt_us, sr_hz);
        return 0;
    }
    
    void* decMem = malloc(decodeSize);
    if (decMem == NULL) {
        LOGE("Failed to allocate memory for decoder");
        return 0;
    }
    
    lc3_decoder_t decoder = lc3_setup_decoder(dt_us, sr_hz, 0, decMem);
    if (decoder == NULL) {
        LOGE("Failed to setup decoder");
        free(decMem);
        return 0;
    }
    
    return (jlong)decoder;
}

// 编码
extern "C"
JNIEXPORT jint JNICALL
Java_com_lh_audiotest03_LC3Codec_encode(JNIEnv *env, jobject thiz, jlong encoder_handle, 
                                       jbyteArray input_buffer, jint input_size, 
                                       jint output_byte_count, jbyteArray output_buffer) {
    if (encoder_handle == 0 || input_buffer == NULL || output_buffer == NULL) {
        LOGE("Invalid parameters for encode");
        return -1;
    }
    
    lc3_encoder_t encoder = (lc3_encoder_t)encoder_handle;
    
    // 获取Java字节数组
    jbyte* input_data = env->GetByteArrayElements(input_buffer, NULL);
    jbyte* output_data = env->GetByteArrayElements(output_buffer, NULL);
    
    if (input_data == NULL || output_data == NULL) {
        LOGE("Failed to get byte array elements");
        if (input_data) env->ReleaseByteArrayElements(input_buffer, input_data, JNI_ABORT);
        if (output_data) env->ReleaseByteArrayElements(output_buffer, output_data, JNI_ABORT);
        return -1;
    }
    
    // 执行编码
    // 检查输入数据是否有效
    if (input_size <= 0) {
        LOGE("Invalid input size: %d", input_size);
        env->ReleaseByteArrayElements(input_buffer, input_data, JNI_ABORT);
        env->ReleaseByteArrayElements(output_buffer, output_data, JNI_ABORT);
        return -1;
    }
    
    // 检查输出字节数是否有效
    if (output_byte_count <= 0) {
        LOGE("Invalid output byte count: %d", output_byte_count);
        env->ReleaseByteArrayElements(input_buffer, input_data, JNI_ABORT);
        env->ReleaseByteArrayElements(output_buffer, output_data, JNI_ABORT);
        return -1;
    }
    
    // 记录输入数据的前几个字节用于调试
    LOGD("Input data first 8 bytes: %02X %02X %02X %02X %02X %02X %02X %02X",
         (unsigned char)input_data[0], (unsigned char)input_data[1],
         (unsigned char)input_data[2], (unsigned char)input_data[3],
         (unsigned char)input_data[4], (unsigned char)input_data[5],
         (unsigned char)input_data[6], (unsigned char)input_data[7]);
    
    // 执行编码 - stride参数为1表示连续的PCM数据
    // 参数说明：
    // encoder: 编码器句柄
    // LC3_PCM_FORMAT_S16: PCM格式为有符号16位整数
    // (const int16_t*)input_data: 输入PCM数据
    // 1: stride参数，表示连续的PCM数据
    // output_byte_count: 输出缓冲区大小（字节数）
    // output_data: 输出缓冲区
    int result = lc3_encode(encoder, LC3_PCM_FORMAT_S16, (const int16_t*)input_data, 1, 
                           output_byte_count, output_data);
    
    // 检查编码结果
    if (result != 0) {
        LOGE("LC3 encoding failed with result: %d", result);
    }
    
    // 记录编码结果
    LOGD("Encode result: %d", result);
    
    // 记录输出数据的前几个字节用于调试
    if (result >= 0) {
        LOGD("Output data first 8 bytes: %02X %02X %02X %02X %02X %02X %02X %02X",
             (unsigned char)output_data[0], (unsigned char)output_data[1],
             (unsigned char)output_data[2], (unsigned char)output_data[3],
             (unsigned char)output_data[4], (unsigned char)output_data[5],
             (unsigned char)output_data[6], (unsigned char)output_data[7]);
    }
    
    // 释放Java字节数组
    env->ReleaseByteArrayElements(input_buffer, input_data, JNI_ABORT);
    env->ReleaseByteArrayElements(output_buffer, output_data, 0); // 0表示将修改后的数据复制回Java数组
    
    return result;
}

// 解码
extern "C"
JNIEXPORT jint JNICALL
Java_com_lh_audiotest03_LC3Codec_decode(JNIEnv *env, jobject thiz, jlong decoder_handle, 
                                       jbyteArray input_buffer, jint input_size, 
                                       jbyteArray output_buffer, jint output_size) {
    if (decoder_handle == 0 || output_buffer == NULL) {
        LOGE("Invalid parameters for decode");
        return -1;
    }
    
    lc3_decoder_t decoder = (lc3_decoder_t)decoder_handle;
    
    // 获取Java字节数组
    jbyte* input_data = NULL;
    if (input_buffer != NULL) {
        input_data = env->GetByteArrayElements(input_buffer, NULL);
        if (input_data == NULL) {
            LOGE("Failed to get input byte array elements");
            return -1;
        }
    }
    
    jbyte* output_data = env->GetByteArrayElements(output_buffer, NULL);
    if (output_data == NULL) {
        LOGE("Failed to get output byte array elements");
        if (input_data) env->ReleaseByteArrayElements(input_buffer, input_data, JNI_ABORT);
        return -1;
    }
    
    // 执行解码
    // 参数说明：
    // decoder: 解码器句柄
    // input_data: 输入编码数据，可以为NULL（执行PLC）
    // input_size: 输入数据大小（字节数）
    // LC3_PCM_FORMAT_S16: PCM格式为有符号16位整数
    // output_data: 输出PCM数据
    // 1: stride参数，表示连续的PCM数据
    int result = lc3_decode(decoder, input_data, input_size, LC3_PCM_FORMAT_S16, 
                           output_data, 1);
    
    // 检查解码结果
    if (result < 0) {
        LOGE("LC3 decoding failed with result: %d", result);
    } else if (result == 1) {
        LOGD("LC3 decoding performed PLC (Packet Loss Concealment)");
    }
    
    // 记录解码结果
    LOGD("Decode result: %d", result);
    
    // 记录输出数据的前几个字节用于调试
    LOGD("Output data first 8 bytes: %02X %02X %02X %02X %02X %02X %02X %02X",
         (unsigned char)output_data[0], (unsigned char)output_data[1],
         (unsigned char)output_data[2], (unsigned char)output_data[3],
         (unsigned char)output_data[4], (unsigned char)output_data[5],
         (unsigned char)output_data[6], (unsigned char)output_data[7]);

    
    // 释放Java字节数组
    if (input_data) env->ReleaseByteArrayElements(input_buffer, input_data, JNI_ABORT);
    env->ReleaseByteArrayElements(output_buffer, output_data, 0); // 0表示将修改后的数据复制回Java数组
    
    return result;
}

// 释放编码器
extern "C"
JNIEXPORT void JNICALL
Java_com_lh_audiotest03_LC3Codec_releaseEncoder(JNIEnv *env, jobject thiz, jlong encoder_handle) {
    if (encoder_handle != 0) {
        lc3_encoder_t encoder = (lc3_encoder_t)encoder_handle;
        free(encoder); // 释放编码器内存
    }
}

// 释放解码器
extern "C"
JNIEXPORT void JNICALL
Java_com_lh_audiotest03_LC3Codec_releaseDecoder(JNIEnv *env, jobject thiz, jlong decoder_handle) {
    if (decoder_handle != 0) {
        lc3_decoder_t decoder = (lc3_decoder_t)decoder_handle;
        free(decoder); // 释放解码器内存
    }
}