package com.anhsteam.fieldinspector

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : FlutterActivity(), SensorEventListener {

    private val CHANNEL = "com.anhsteam.fieldinspector/native"
    private val REQ_PERMISSIONS = 2026
    private val REQ_CAMERA_CAPTURE = 3001
    private val REQ_GALLERY_PICK = 3002

    private lateinit var recorderEngine: FieldAudioRecorderEngine
    private lateinit var photoStamperEngine: FieldPhotoStamperEngine
    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingPath: String? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningSpeech: Boolean = false
    private var currentSpeechLocale: String = "vi-VN"
    private var latestPartialSpeech: String = ""
    private var latestFinalSpeech: String = ""
    private var speechErrorText: String = ""

    // Cảm biến Định vị GPS & La bàn hiện trường
    private var locationManager: LocationManager? = null
    private var sensorManager: SensorManager? = null
    private var accelReading = FloatArray(3)
    private var magnetReading = FloatArray(3)
    private var hasAccel = false
    private var hasMagnet = false
    private var currentAzimuthDeg: Int = 135
    private var latestGpsText: String = "21.004512°N, 105.798234°E (±1.5m RTK)"

    // Tham số chờ khi mở Camera / Gallery
    private var pendingCameraRawFile: File? = null
    private var pendingStampParams: Map<String, String> = emptyMap()
    private var pendingPhotoResult: MethodChannel.Result? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSensorsAndLocation()
    }

    override fun onResume() {
        super.onResume()
        registerCompassSensors()
        refreshLocationReading()
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    private fun setupSensorsAndLocation() {
        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        } catch (_: Exception) {
        }
    }

    private fun registerCompassSensors() {
        try {
            val sm = sensorManager ?: return
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
            sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        } catch (_: Exception) {
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelReading, 0, accelReading.size)
            hasAccel = true
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetReading, 0, magnetReading.size)
            hasMagnet = true
        }
        if (hasAccel && hasMagnet) {
            val rotationMatrix = FloatArray(9)
            val orientationAngles = FloatArray(3)
            if (SensorManager.getRotationMatrix(rotationMatrix, null, accelReading, magnetReading)) {
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                var deg = Math.toDegrees(orientationAngles[0].toDouble()).roundToInt()
                if (deg < 0) deg += 360
                currentAzimuthDeg = deg
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun compassDirectionLabel(deg: Int): String {
        return when (((deg + 22) % 360) / 45) {
            0 -> "Bắc (N)"
            1 -> "Đông Bắc (NE)"
            2 -> "Đông (E)"
            3 -> "Đông Nam (SE)"
            4 -> "Nam (S)"
            5 -> "Tây Nam (SW)"
            6 -> "Tây (W)"
            else -> "Tây Bắc (NW)"
        }
    }

    private fun refreshLocationReading(): String {
        if (!hasLocationPermission()) {
            return "$latestGpsText | Hướng: ${currentAzimuthDeg}° ${compassDirectionLabel(currentAzimuthDeg)}"
        }
        try {
            val lm = locationManager ?: return latestGpsText
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            var bestLoc: Location? = null
            for (p in providers) {
                if (lm.isProviderEnabled(p)) {
                    val loc = lm.getLastKnownLocation(p)
                    if (loc != null && (bestLoc == null || loc.accuracy < bestLoc.accuracy)) {
                        bestLoc = loc
                    }
                    lm.requestSingleUpdate(p, object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            updateFormattedGps(location)
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }, mainLooper)
                }
            }
            if (bestLoc != null) {
                updateFormattedGps(bestLoc)
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
        return "$latestGpsText | Hướng: ${currentAzimuthDeg}° ${compassDirectionLabel(currentAzimuthDeg)}"
    }

    private fun updateFormattedGps(loc: Location) {
        val latDir = if (loc.latitude >= 0) "N" else "S"
        val lonDir = if (loc.longitude >= 0) "E" else "W"
        val latStr = String.format(Locale.US, "%.6f°%s", kotlin.math.abs(loc.latitude), latDir)
        val lonStr = String.format(Locale.US, "%.6f°%s", kotlin.math.abs(loc.longitude), lonDir)
        val accStr = String.format(Locale.US, "±%.1fm", loc.accuracy)
        latestGpsText = "$latStr, $lonStr ($accStr)"
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        recorderEngine = FieldAudioRecorderEngine(applicationContext)
        photoStamperEngine = FieldPhotoStamperEngine(applicationContext)

        // Khởi tạo sẵn 1 ảnh mẫu đã đóng dấu thông tin hiện trường ngay lần đầu mở app nếu thư mục trống
        if (photoStamperEngine.listStampedPhotos().isEmpty()) {
            photoStamperEngine.stampPhotoFile(
                sourceFile = null,
                projectName = "Tòa nhà Trung tâm R&D Công nghệ (DA01-IBST)",
                workItem = "Kiểm tra cốt thép & lớp bê tông bảo vệ dầm sàn",
                zoneAxis = "Tầng hầm B1 - Trục (B-C)/(3-4) - Cao độ -4.50m",
                gpsAndCompass = "21.004512°N, 105.798234°E (±1.5m RTK) | Hướng: 135° Đông Nam",
                measuredResult = "Thép D25 CB500-V; Lớp bảo vệ a = 26mm (ĐẠT - TCVN 4453)",
                inspectorName = "KS. Nguyễn Văn A (TVGS) - KS. Trần Văn B (NTTC)",
                compassDeg = 135
            )
        }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getSystemStatus" -> {
                        val status = mapOf(
                            "micPermissionGranted" to hasMicPermission(),
                            "cameraPermissionGranted" to hasCameraPermission(),
                            "locationPermissionGranted" to hasLocationPermission(),
                            "isRecording" to recorderEngine.isRecording,
                            "elapsedSeconds" to recorderEngine.getElapsedSeconds(),
                            "peakAmplitude" to recorderEngine.currentPeakAmplitude,
                            "roomGainBoost" to recorderEngine.roomGainBoost.toDouble(),
                            "currentRecordingPath" to (recorderEngine.currentFilePath ?: ""),
                            "currentlyPlayingPath" to (currentlyPlayingPath ?: ""),
                            "isListeningSpeech" to isListeningSpeech,
                            "currentSpeechLocale" to currentSpeechLocale,
                            "latestPartialSpeech" to latestPartialSpeech,
                            "latestFinalSpeech" to latestFinalSpeech,
                            "speechErrorText" to speechErrorText,
                            "gpsText" to latestGpsText,
                            "compassDeg" to currentAzimuthDeg,
                            "compassLabel" to "${currentAzimuthDeg}° ${compassDirectionLabel(currentAzimuthDeg)}",
                            "deviceModel" to "${Build.MANUFACTURER} ${Build.MODEL}"
                        )
                        result.success(status)
                    }

                    "requestPermissions" -> {
                        requestRuntimePermissions()
                        result.success(true)
                    }

                    "refreshGpsAndCompass" -> {
                        if (!hasLocationPermission()) {
                            requestRuntimePermissions()
                        }
                        val fullStr = refreshLocationReading()
                        result.success(
                            mapOf(
                                "gpsText" to latestGpsText,
                                "compassDeg" to currentAzimuthDeg,
                                "compassLabel" to "${currentAzimuthDeg}° ${compassDirectionLabel(currentAzimuthDeg)}",
                                "combined" to fullStr
                            )
                        )
                    }

                    "captureCameraAndStamp" -> {
                        if (!hasCameraPermission()) {
                            requestRuntimePermissions()
                            result.error("NO_CAM_PERM", "Vui lòng cấp quyền Camera để chụp ảnh hiện trường", null)
                            return@setMethodCallHandler
                        }
                        pendingStampParams = extractStampParams(call.arguments as? Map<*, *>)
                        pendingPhotoResult = result
                        launchSystemCamera()
                    }

                    "pickGalleryAndStamp" -> {
                        pendingStampParams = extractStampParams(call.arguments as? Map<*, *>)
                        pendingPhotoResult = result
                        launchGalleryPicker()
                    }

                    "generateDemoStampedPhoto" -> {
                        val params = extractStampParams(call.arguments as? Map<*, *>)
                        val info = photoStamperEngine.stampPhotoFile(
                            sourceFile = null,
                            projectName = params["projectName"] ?: "",
                            workItem = params["workItem"] ?: "",
                            zoneAxis = params["zoneAxis"] ?: "",
                            gpsAndCompass = params["gpsAndCompass"] ?: "",
                            measuredResult = params["measuredResult"] ?: "",
                            inspectorName = params["inspectorName"] ?: "",
                            compassDeg = currentAzimuthDeg
                        )
                        result.success(info)
                    }

                    "listStampedPhotos" -> {
                        result.success(photoStamperEngine.listStampedPhotos())
                    }

                    "updateRoomGain" -> {
                        val gain = call.argument<Double>("roomGainBoost")?.toFloat() ?: 2.5f
                        recorderEngine.roomGainBoost = gain.coerceIn(1.0f, 6.0f)
                        result.success(true)
                    }

                    "startFieldRecording" -> {
                        if (!hasMicPermission()) {
                            requestRuntimePermissions()
                            result.error("NO_MIC_PERM", "Vui lòng cấp quyền Microphone để thu âm hiện trường", null)
                            return@setMethodCallHandler
                        }
                        val title = call.argument<String>("noteTitle") ?: "Kiem_Tra_Hien_Truong"
                        val gain = call.argument<Double>("roomGainBoost")?.toFloat() ?: 2.5f
                        val path = recorderEngine.startRecording(title, gain)
                        result.success(path ?: "")
                    }

                    "stopFieldRecording" -> {
                        val info = recorderEngine.stopRecording()
                        result.success(info)
                    }

                    "startVoiceDictation" -> {
                        if (!hasMicPermission()) {
                            requestRuntimePermissions()
                            result.error("NO_MIC_PERM", "Vui lòng cấp quyền Microphone", null)
                            return@setMethodCallHandler
                        }
                        val locale = call.argument<String>("locale") ?: "vi-VN"
                        startMultilingualSpeechRecognition(locale)
                        result.success(true)
                    }

                    "stopVoiceDictation" -> {
                        stopMultilingualSpeechRecognition()
                        result.success(latestFinalSpeech)
                    }

                    "clearSpeechBuffer" -> {
                        latestPartialSpeech = ""
                        latestFinalSpeech = ""
                        speechErrorText = ""
                        result.success(true)
                    }

                    "listRecordings" -> {
                        result.success(recorderEngine.listSavedRecordings())
                    }

                    "listExportedDocx" -> {
                        result.success(recorderEngine.listExportedDocx())
                    }

                    "playRecording" -> {
                        val path = call.argument<String>("path") ?: ""
                        result.success(playAudioFile(path))
                    }

                    "stopPlayback" -> {
                        stopAudioPlayback()
                        result.success(true)
                    }

                    "deleteFile" -> {
                        val path = call.argument<String>("path") ?: ""
                        if (currentlyPlayingPath == path) stopAudioPlayback()
                        val f = File(path)
                        val deleted = if (f.exists()) f.delete() else false
                        result.success(deleted)
                    }

                    "generateFieldReportDocx" -> {
                        try {
                            val jsonPayload = call.argument<String>("jsonPayload") ?: "{}"
                            val outInfo = DocxFieldReportGenerator.generateFieldReportDocx(
                                context = applicationContext,
                                outputDir = recorderEngine.getReportsDir(),
                                jsonString = jsonPayload
                            )
                            result.success(outInfo)
                        } catch (e: Exception) {
                            result.error("ERR_DOCX", e.message ?: "Lỗi khi xuất Biên bản .docx", null)
                        }
                    }

                    "shareFile" -> {
                        val path = call.argument<String>("path") ?: ""
                        val mimeType = call.argument<String>("mimeType") ?: "*/*"
                        val shared = shareFileViaProvider(path, mimeType)
                        result.success(shared)
                    }

                    else -> result.notImplemented()
                }
            }
    }

    private fun extractStampParams(map: Map<*, *>?): Map<String, String> {
        if (map == null) return emptyMap()
        return mapOf(
            "projectName" to (map["projectName"]?.toString() ?: "Tòa nhà Trung tâm R&D Công nghệ (DA01-IBST)"),
            "workItem" to (map["workItem"]?.toString() ?: "Kiểm tra cốt thép & lớp bê tông bảo vệ dầm sàn"),
            "zoneAxis" to (map["zoneAxis"]?.toString() ?: "Tầng hầm B1 - Trục (B-C)/(3-4) - Cao độ -4.50m"),
            "gpsAndCompass" to (map["gpsAndCompass"]?.toString() ?: "$latestGpsText | Hướng: ${currentAzimuthDeg}° ${compassDirectionLabel(currentAzimuthDeg)}"),
            "measuredResult" to (map["measuredResult"]?.toString() ?: "Đạt yêu cầu kỹ thuật theo thiết kế & TCVN"),
            "inspectorName" to (map["inspectorName"]?.toString() ?: "KS. Nguyễn Văn A (TVGS) - KS. Trần Văn B (NTTC)")
        )
    }

    private fun launchSystemCamera() {
        try {
            val rawFile = photoStamperEngine.getTempCaptureFile()
            pendingCameraRawFile = rawFile
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                rawFile
            )
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivityForResult(intent, REQ_CAMERA_CAPTURE)
        } catch (e: Exception) {
            // Fallback tạo ảnh đóng dấu ngay nếu thiết bị không có Camera app
            val info = photoStamperEngine.stampPhotoFile(
                sourceFile = null,
                projectName = pendingStampParams["projectName"] ?: "",
                workItem = pendingStampParams["workItem"] ?: "",
                zoneAxis = pendingStampParams["zoneAxis"] ?: "",
                gpsAndCompass = pendingStampParams["gpsAndCompass"] ?: "",
                measuredResult = pendingStampParams["measuredResult"] ?: "",
                inspectorName = pendingStampParams["inspectorName"] ?: "",
                compassDeg = currentAzimuthDeg
            )
            pendingPhotoResult?.success(info)
            pendingPhotoResult = null
        }
    }

    private fun launchGalleryPicker() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(Intent.createChooser(intent, "Chọn ảnh hiện trường để đóng dấu"), REQ_GALLERY_PICK)
        } catch (e: Exception) {
            pendingPhotoResult?.error("ERR_PICK", e.message, null)
            pendingPhotoResult = null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAMERA_CAPTURE) {
            val res = pendingPhotoResult
            pendingPhotoResult = null
            if (resultCode == Activity.RESULT_OK && pendingCameraRawFile != null && pendingCameraRawFile!!.exists()) {
                val info = photoStamperEngine.stampPhotoFile(
                    sourceFile = pendingCameraRawFile,
                    projectName = pendingStampParams["projectName"] ?: "",
                    workItem = pendingStampParams["workItem"] ?: "",
                    zoneAxis = pendingStampParams["zoneAxis"] ?: "",
                    gpsAndCompass = pendingStampParams["gpsAndCompass"] ?: "",
                    measuredResult = pendingStampParams["measuredResult"] ?: "",
                    inspectorName = pendingStampParams["inspectorName"] ?: "",
                    compassDeg = currentAzimuthDeg
                )
                res?.success(info)
            } else {
                res?.success(null)
            }
        } else if (requestCode == REQ_GALLERY_PICK) {
            val res = pendingPhotoResult
            pendingPhotoResult = null
            val uri = data?.data
            if (resultCode == Activity.RESULT_OK && uri != null) {
                try {
                    val tempFile = photoStamperEngine.getTempCaptureFile()
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val info = photoStamperEngine.stampPhotoFile(
                        sourceFile = tempFile,
                        projectName = pendingStampParams["projectName"] ?: "",
                        workItem = pendingStampParams["workItem"] ?: "",
                        zoneAxis = pendingStampParams["zoneAxis"] ?: "",
                        gpsAndCompass = pendingStampParams["gpsAndCompass"] ?: "",
                        measuredResult = pendingStampParams["measuredResult"] ?: "",
                        inspectorName = pendingStampParams["inspectorName"] ?: "",
                        compassDeg = currentAzimuthDeg
                    )
                    res?.success(info)
                } catch (e: Exception) {
                    res?.error("ERR_GALLERY_STAMP", e.message, null)
                }
            } else {
                res?.success(null)
            }
        }
    }

    private fun startMultilingualSpeechRecognition(localeCode: String) {
        runOnUiThread {
            try {
                stopMultilingualSpeechRecognition()
                currentSpeechLocale = localeCode
                latestPartialSpeech = ""
                speechErrorText = ""
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) { isListeningSpeech = true }
                        override fun onBeginningOfSpeech() { isListeningSpeech = true }
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() { isListeningSpeech = false }
                        override fun onError(error: Int) {
                            isListeningSpeech = false
                            speechErrorText = "Trạng thái nhận diện ($localeCode): $error"
                        }
                        override fun onResults(results: Bundle?) {
                            isListeningSpeech = false
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val recognized = matches[0].trim()
                                if (recognized.isNotEmpty()) {
                                    latestFinalSpeech = if (latestFinalSpeech.isEmpty()) recognized else "$latestFinalSpeech. $recognized"
                                }
                            }
                            latestPartialSpeech = ""
                        }
                        override fun onPartialResults(partialResults: Bundle?) {
                            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!partial.isNullOrEmpty()) {
                                latestPartialSpeech = partial[0]
                            }
                        }
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeCode)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeCode)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                speechRecognizer?.startListening(intent)
                isListeningSpeech = true
            } catch (e: Exception) {
                isListeningSpeech = false
                speechErrorText = e.message ?: "Không hỗ trợ SpeechRecognizer"
            }
        }
    }

    private fun stopMultilingualSpeechRecognition() {
        runOnUiThread {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            } finally {
                speechRecognizer = null
                isListeningSpeech = false
            }
        }
    }

    private fun playAudioFile(path: String): Boolean {
        return try {
            stopAudioPlayback()
            val file = File(path)
            if (!file.exists()) return false
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { currentlyPlayingPath = null }
                prepare()
                start()
            }
            currentlyPlayingPath = path
            true
        } catch (_: Exception) {
            currentlyPlayingPath = null
            false
        }
    }

    private fun stopAudioPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
            currentlyPlayingPath = null
        }
    }

    private fun shareFileViaProvider(path: String, mimeType: String): Boolean {
        return try {
            val file = File(path)
            if (!file.exists()) return false
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Chia sẻ Hồ sơ Hiện trường").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestRuntimePermissions() {
        val perms = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
            perms.add(Manifest.permission.READ_MEDIA_AUDIO)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), REQ_PERMISSIONS)
    }

    override fun onDestroy() {
        stopMultilingualSpeechRecognition()
        stopAudioPlayback()
        if (recorderEngine.isRecording) {
            recorderEngine.stopRecording()
        }
        super.onDestroy()
    }
}
