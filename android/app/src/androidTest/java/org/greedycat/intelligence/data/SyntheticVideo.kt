package org.greedycat.intelligence.data

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import java.io.File

/** Encodes five generated gray frames. No camera, screen or user media is read. */
fun syntheticVideo(output: File) {
    // A test fixture must not depend on a vendor hardware encoder's size limits.
    val encoder = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.first {
        it.isEncoder && it.supportedTypes.any { type -> type.equals("video/avc", ignoreCase = true) } &&
            (it.name.startsWith("c2.android.") || it.name.startsWith("OMX.google."))
    }
    val codec = MediaCodec.createByCodecName(encoder.name)
    val muxer = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    var muxerStarted = false
    var codecStarted = false
    try {
        val format = MediaFormat.createVideoFormat("video/avc", 320, 240).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 256000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 15)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start(); codecStarted = true
        val frame = ByteArray(320*240*3/2) { 128.toByte() }
        var inputCount = 0; var track = -1; var done = false
        val info = MediaCodec.BufferInfo()
        val deadline = SystemClock.elapsedRealtime() + 30000
        while (!done && SystemClock.elapsedRealtime() < deadline) {
            if (inputCount <= 5) {
                val index = codec.dequeueInputBuffer(10000)
                if (index >= 0) {
                    val buffer = requireNotNull(codec.getInputBuffer(index)); buffer.clear()
                    if (inputCount < 5) buffer.put(frame)
                    codec.queueInputBuffer(index, 0, if(inputCount < 5) frame.size else 0,
                        inputCount*66667L, if(inputCount == 5) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                    inputCount++
                }
            }
            val index = codec.dequeueOutputBuffer(info, 10000)
            if(index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                track = muxer.addTrack(codec.outputFormat); muxer.start(); muxerStarted = true
            } else if(index >= 0) {
                if(info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                    check(muxerStarted)
                    val buffer = requireNotNull(codec.getOutputBuffer(index))
                    buffer.position(info.offset); buffer.limit(info.offset+info.size)
                    muxer.writeSampleData(track, buffer, info)
                }
                done = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                codec.releaseOutputBuffer(index, false)
            }
        }
        check(done) { "Synthetic video encoding timed out." }
    } finally {
        if(codecStarted) codec.stop()
        codec.release()
        if(muxerStarted) muxer.stop()
        muxer.release()
    }
}
