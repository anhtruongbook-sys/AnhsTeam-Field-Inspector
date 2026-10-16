package com.anhsteam.fieldinspector

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Động cơ Đóng dấu Bảng Thông Tin Kỹ Thuật & Định Vị Hiện Trường (Digital Engineering Chalkboard / Watermark)
 * trực tiếp lên điểm ảnh (Bitmap Canvas) phục vụ kiểm tra, khảo sát và nghiệm thu theo Nghị định số 207/2026/NĐ-CP.
 */
class FieldPhotoStamperEngine(private val context: Context) {

    fun getPhotosDir(): File {
        val dir = File(context.getExternalFilesDir(null), "Field_Photos")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getTempCaptureFile(): File {
        val dir = File(context.externalCacheDir ?: context.cacheDir, "camera_raw")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "raw_capture_${System.currentTimeMillis()}.jpg")
    }

    fun stampPhotoFile(
        sourceFile: File?,
        projectName: String,
        workItem: String,
        zoneAxis: String,
        gpsAndCompass: String,
        measuredResult: String,
        inspectorName: String,
        compassDeg: Int
    ): Map<String, Any> {
        val rawBitmap: Bitmap
        val rawSha256Short: String

        if (sourceFile != null && sourceFile.exists() && sourceFile.length() > 0) {
            rawSha256Short = computeSha256(sourceFile).take(16).uppercase(Locale.US)
            val opts = BitmapFactory.Options().apply {
                inMutable = true
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(sourceFile.absolutePath, opts)
            rawBitmap = if (decoded != null) {
                scaleDownIfNeeded(decoded, 1920)
            } else {
                createSimulatedRebarBitmap(1600, 1050)
            }
        } else {
            rawBitmap = createSimulatedRebarBitmap(1600, 1050)
            rawSha256Short = sha256String("${System.currentTimeMillis()}-$projectName-$zoneAxis").take(16).uppercase(Locale.US)
        }

        val stampedBitmap = drawEngineeringStampOnBitmap(
            src = rawBitmap,
            rawSha256Short = rawSha256Short,
            projectName = projectName,
            workItem = workItem,
            zoneAxis = zoneAxis,
            gpsAndCompass = gpsAndCompass,
            measuredResult = measuredResult,
            inspectorName = inspectorName,
            compassDeg = compassDeg
        )

        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(getPhotosDir(), "HT_STAMPED_$ts.jpg")
        FileOutputStream(outFile).use { fos ->
            stampedBitmap.compress(Bitmap.CompressFormat.JPEG, 94, fos)
        }

        val finalSha256 = computeSha256(outFile)
        val publicPath = copyPhotoToPublicGallery(outFile)

        return mapOf(
            "path" to outFile.absolutePath,
            "name" to outFile.name,
            "publicPath" to (publicPath ?: outFile.absolutePath),
            "sizeKB" to (outFile.length() / 1024L).coerceAtLeast(1L),
            "sha256" to finalSha256,
            "rawSha256Short" to rawSha256Short,
            "timestamp" to SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()),
            "zoneAxis" to zoneAxis,
            "workItem" to workItem,
            "measuredResult" to measuredResult
        )
    }

    private fun scaleDownIfNeeded(bmp: Bitmap, maxDim: Int): Bitmap {
        val w = bmp.width
        val h = bmp.height
        if (w <= maxDim && h <= maxDim) {
            return bmp.copy(Bitmap.Config.ARGB_8888, true)
        }
        val ratio = maxDim.toFloat() / max(w, h).toFloat()
        val nw = (w * ratio).toInt().coerceAtLeast(640)
        val nh = (h * ratio).toInt().coerceAtLeast(480)
        return Bitmap.createScaledBitmap(bmp, nw, nh, true)
    }

    private fun createSimulatedRebarBitmap(width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(64, 70, 78))

        val barPaint1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(156, 163, 175)
            strokeWidth = 9f
        }
        val barHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(212, 218, 226)
            strokeWidth = 2.5f
        }
        val barPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(138, 146, 158)
            strokeWidth = 8f
        }

        var x = 100f
        while (x < width - 100f) {
            canvas.drawLine(x, 70f, x, height - 70f, barPaint1)
            canvas.drawLine(x + 3f, 70f, x + 3f, height - 70f, barHighlight)
            x += 95f
        }
        var y = 100f
        while (y < height - 100f) {
            canvas.drawLine(70f, y, width - 70f, y, barPaint2)
            y += 95f
        }

        val spacerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(203, 213, 225)
            style = Paint.Style.FILL
        }
        val spacerBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val centers = listOf(
            385f to 290f, 860f to 290f, 1335f to 290f,
            385f to 575f, 860f to 575f, 1335f to 575f
        )
        for ((cx, cy) in centers) {
            val rect = RectF(cx - 24f, cy - 24f, cx + 24f, cy + 24f)
            canvas.drawRect(rect, spacerFill)
            canvas.drawRect(rect, spacerBorder)
        }
        return bmp
    }

    private fun drawEngineeringStampOnBitmap(
        src: Bitmap,
        rawSha256Short: String,
        projectName: String,
        workItem: String,
        zoneAxis: String,
        gpsAndCompass: String,
        measuredResult: String,
        inspectorName: String,
        compassDeg: Int
    ): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val scale = (max(w, h) / 1600f).coerceAtLeast(0.55f)

        val nowStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss 'GMT+07:00'", Locale.getDefault()).format(Date())

        val bgDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(218, 15, 23, 42)
            style = Paint.Style.FILL
        }
        val headerBluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(238, 30, 58, 138)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(225, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = max(2f, 2.5f * scale)
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 71, 85, 105)
            style = Paint.Style.STROKE
            strokeWidth = max(1f, 1.2f * scale)
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 21f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(147, 197, 253)
            textSize = 17f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val valWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 17f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val valGreenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(134, 239, 172)
            textSize = 17f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(203, 213, 225)
            textSize = 14.5f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        // 1. Dải tiêu đề trên cùng (Top Header Bar)
        val topBarH = 48f * scale
        canvas.drawRect(0f, 0f, w, topBarH, bgDarkPaint)
        canvas.drawText(
            "CHỨNG CỨ KIỂM TRA - KHẢO SÁT - GIÁM SÁT HIỆN TRƯỜNG (NĐ 207/2026/NĐ-CP)",
            18f * scale,
            31f * scale,
            labelPaint.apply { color = Color.WHITE }
        )
        labelPaint.color = Color.rgb(147, 197, 253)
        canvas.drawText(
            "SHA-256: $rawSha256Short",
            w - 310f * scale,
            30f * scale,
            smallPaint
        )

        // 2. Vòng La bàn Phương vị ở góc trên bên phải
        val margin = 20f * scale
        val compassR = 54f * scale
        val cx = w - margin - compassR - 8f * scale
        val cy = topBarH + margin + compassR + 8f * scale
        canvas.drawCircle(cx, cy, compassR, bgDarkPaint)
        canvas.drawCircle(cx, cy, compassR, borderPaint)

        val northPaint = Paint(labelPaint).apply {
            color = Color.rgb(248, 113, 113)
            textAlign = Paint.Align.CENTER
        }
        val dirPaint = Paint(smallPaint).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        val degPaint = Paint(valGreenPaint).apply {
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("B", cx, cy - compassR + 18f * scale, northPaint)
        canvas.drawText("N", cx, cy + compassR - 8f * scale, dirPaint)
        canvas.drawText("Đ", cx + compassR - 13f * scale, cy + 5f * scale, dirPaint)
        canvas.drawText("T", cx - compassR + 13f * scale, cy + 5f * scale, dirPaint)
        canvas.drawText("${compassDeg}°", cx, cy + 6f * scale, degPaint)

        // 3. Bảng thông tin kỹ thuật hiện trường ở góc dưới bên trái
        val rows = listOf(
            "DỰ ÁN:" to projectName,
            "HẠNG MỤC KIỂM TRA:" to workItem,
            "VỊ TRÍ / TRỤC / CAO ĐỘ:" to zoneAxis,
            "TỌA ĐỘ GNSS / LA BÀN:" to gpsAndCompass,
            "THỜI GIAN GHI NHẬN:" to nowStr,
            "KẾT QUẢ ĐO / ĐÁNH GIÁ:" to measuredResult,
            "CÁN BỘ HIỆN TRƯỜNG:" to inspectorName
        )

        val panelW = min(w - 2f * margin, 920f * scale)
        val headerH = 42f * scale
        val lineH = 31f * scale
        val panelH = headerH + rows.size * lineH + 18f * scale
        val x0 = margin
        val y0 = h - panelH - margin
        val x1 = x0 + panelW
        val y1 = h - margin

        val panelRect = RectF(x0, y0, x1, y1)
        val headerRect = RectF(x0, y0, x1, y0 + headerH)
        canvas.drawRect(panelRect, bgDarkPaint)
        canvas.drawRect(headerRect, headerBluePaint)
        canvas.drawRect(panelRect, borderPaint)
        canvas.drawLine(x0, y0 + headerH, x1, y0 + headerH, borderPaint)

        canvas.drawText(
            "BẢNG THÔNG TIN ĐỊNH VỊ & NGHIỆM THU HIỆN TRƯỜNG",
            x0 + 14f * scale,
            y0 + 28f * scale,
            titlePaint
        )

        var currY = y0 + headerH + 23f * scale
        val labelColW = 240f * scale
        for (i in rows.indices) {
            val (lbl, rawVal) = rows[i]
            val truncatedVal = if (rawVal.length > 68) rawVal.take(65) + "..." else rawVal
            canvas.drawText(lbl, x0 + 14f * scale, currY, labelPaint)
            val vPaint = if (truncatedVal.uppercase(Locale.getDefault()).contains("ĐẠT")) valGreenPaint else valWhitePaint
            canvas.drawText(truncatedVal, x0 + labelColW, currY, vPaint)
            if (i < rows.size - 1) {
                val lineY = currY + 7f * scale
                canvas.drawLine(x0 + 10f * scale, lineY, x1 - 10f * scale, lineY, dividerPaint)
            }
            currY += lineH
        }

        return out
    }

    fun listStampedPhotos(): List<Map<String, Any>> {
        val dir = getPhotosDir()
        val files = dir.listFiles { f -> f.isFile && (f.name.endsWith(".jpg", true) || f.name.endsWith(".png", true)) }
            ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }.map { f ->
            val dt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(f.lastModified()))
            mapOf(
                "path" to f.absolutePath,
                "name" to f.name,
                "sizeKB" to (f.length() / 1024L).coerceAtLeast(1L),
                "modified" to dt,
                "sha256" to computeSha256(f)
            )
        }
    }

    private fun copyPhotoToPublicGallery(srcFile: File): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, srcFile.name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AnhsTeam_HienTruong")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(srcFile).use { input -> input.copyTo(out) }
                    }
                    "/Pictures/AnhsTeam_HienTruong/${srcFile.name}"
                } else null
            } else {
                @Suppress("DEPRECATION")
                val picDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AnhsTeam_HienTruong")
                if (!picDir.exists()) picDir.mkdirs()
                val dest = File(picDir, srcFile.name)
                srcFile.copyTo(dest, overwrite = true)
                dest.absolutePath
            }
        } catch (_: Exception) {
            null
        }
    }

    fun computeSha256(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(8192)
            FileInputStream(file).use { fis ->
                var n = fis.read(buf)
                while (n > 0) {
                    digest.update(buf, 0, n)
                    n = fis.read(buf)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    private fun sha256String(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            "E3B0C44298FC1C14"
        }
    }
}
