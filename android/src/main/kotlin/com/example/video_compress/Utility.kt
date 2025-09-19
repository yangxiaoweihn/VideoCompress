package com.example.video_compress

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject
import java.io.File

class Utility(private val channelName: String) {

    fun isLandscapeImage(orientation: Int) = orientation != 90 && orientation != 270

    fun deleteFile(file: File) {
        if (file.exists()) {
            file.delete()
        }
    }

    fun timeStrToTimestamp(time: String): Long {
        val timeArr = time.split(":")
        val hour = Integer.parseInt(timeArr[0])
        val min = Integer.parseInt(timeArr[1])
        val secArr = timeArr[2].split(".")
        val sec = Integer.parseInt(secArr[0])
        val mSec = Integer.parseInt(secArr[1])

        val timeStamp = (hour * 3600 + min * 60 + sec) * 1000 + mSec
        return timeStamp.toLong()
    }

    fun getMediaInfoJson(context: Context, path: String): JSONObject {
        val file = File(path)
        val retriever = MediaMetadataRetriever()

        retriever.setDataSource(context, Uri.fromFile(file))

        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
        val author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR) ?: ""
        val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val duration = java.lang.Long.parseLong(durationStr)
        var width = java.lang.Long.parseLong(widthStr)
        var height = java.lang.Long.parseLong(heightStr)
        val filesize = file.length()
        val orientation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        } else {
            null
        }
        val ori = orientation?.toIntOrNull()
        if (ori != null && isLandscapeImage(ori)) {
            val tmp = width
            width = height
            height = tmp
        }

        retriever.release()

        val json = JSONObject()

        json.put("path", path)
        json.put("title", title)
        json.put("author", author)
        json.put("width", width)
        json.put("height", height)
        json.put("duration", duration)
        json.put("filesize", filesize)
        if (ori != null) {
            json.put("orientation", ori)
        }

        return json
    }

    fun getBitmap(path: String, position: Long, maxWidth: Int, maxHeight: Int, result: MethodChannel.Result): Bitmap {
        var bitmap: Bitmap? = null
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(path)
            bitmap = retriever.getFrameAtTime(position, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (ex: IllegalArgumentException) {
            result.error(channelName, "Assume this is a corrupt video file", null)
        } catch (ex: RuntimeException) {
            result.error(channelName, "Assume this is a corrupt video file", null)
        } finally {
            try {
                retriever.release()
            } catch (ex: RuntimeException) {
                result.error(channelName, "Ignore failures while cleaning up", null)
            }
        }

        if (bitmap == null) result.error(channelName, "Assume this is a corrupt video file", null)

        val width = bitmap!!.width
        val height = bitmap.height
        var targetWidth = maxWidth
        var targetHeight = maxHeight
        if (targetWidth != 0 || targetHeight != 0) {
            if (targetHeight != 0 && targetWidth != 0) {
                bitmap = Bitmap.createScaledBitmap(bitmap, targetHeight, targetWidth, true)
                Log.d(channelName, String.format("original w:%d, h:%d => %d, %d", width, height, targetWidth, targetHeight))
            } else {
                if (targetWidth == 0) {
                    targetWidth = Math.round((targetHeight.toFloat() / height) * width)
                }
                if (targetHeight == 0) {
                    targetHeight = Math.round((targetWidth.toFloat() / width) * height)
                }
                bitmap = Bitmap.createScaledBitmap(bitmap, targetHeight, targetWidth, true)
                Log.d(channelName, String.format("original w:%d, h:%d => %d, %d", width, height, targetWidth, targetHeight))
            }
        }

//        val max = Math.max(width, height)
//        if (max > 512) {
//            val scale = 512f / max
//            val w = Math.round(scale * width)
//            val h = Math.round(scale * height)
//            bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true)
//        }

        return bitmap!!
    }

    fun getFileNameWithGifExtension(path: String): String {
        val file = File(path)
        var fileName = ""
        val gifSuffix = "gif"
        val dotGifSuffix = ".$gifSuffix"

        if (file.exists()) {
            val name = file.name
            fileName = name.replaceAfterLast(".", gifSuffix)

            if (!fileName.endsWith(dotGifSuffix)) {
                fileName += dotGifSuffix
            }
        }
        return fileName
    }

    fun deleteAllCache(context: Context, result: MethodChannel.Result) {
        val dir = context.getExternalFilesDir("video_compress")
        result.success(dir?.deleteRecursively())
    }
}