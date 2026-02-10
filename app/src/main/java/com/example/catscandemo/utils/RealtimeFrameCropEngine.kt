package com.example.catscandemo.utils

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.opencv.core.Rect as CvRect

object NativeBarcodeDetector {

    data class DetectionConfig(
        var minAreaScore: Double = 0.0,
        var minAspectScore: Double = 0.0,
        var minSolidityScore: Double = 0.0,
        var minGradScore: Double = 0.0
    )

    data class ConfidenceDetails(
        val aspectScore: Double,
        val solidityScore: Double,
        val areaScore: Double,
        val gradScore: Double
    )

    data class BarcodeResult(
        val index: Int,
        val boundingBox: Rect,
        val area: Double,
        val aspectRatio: Double,
        val solidity: Double,
        val confidence: Float,
        val confidenceDetails: ConfidenceDetails
    )
}

object DetectionConfigHolder {
    const val DEFAULT_MIN_AREA_SCORE = 10.0
    const val DEFAULT_MIN_ASPECT_SCORE = 70.0
    const val DEFAULT_MIN_SOLIDITY_SCORE = 50.0
    const val DEFAULT_MIN_GRAD_SCORE = 15.0

    var config = NativeBarcodeDetector.DetectionConfig(
        minAreaScore = DEFAULT_MIN_AREA_SCORE,
        minAspectScore = DEFAULT_MIN_ASPECT_SCORE,
        minSolidityScore = DEFAULT_MIN_SOLIDITY_SCORE,
        minGradScore = DEFAULT_MIN_GRAD_SCORE
    )
}

object RealtimeFrameCropEngine {

    const val MASK_TOP_RATIO = 0.40f
    const val MASK_BOTTOM_RATIO = 0.40f
    const val MASK_LEFT_RATIO = 0.20f
    const val MASK_RIGHT_RATIO = 0.20f

    private val lastProcessTimeMs = AtomicLong(0)

    @Volatile
    private var opencvLoaded = false

    private enum class Axis { X, Y }
    private enum class CropType { DETECTION_BOX, CENTER_BAND }

    data class FrameConfig(
        val detectionConfig: NativeBarcodeDetector.DetectionConfig = defaultDetectionConfig(),
        val minProcessIntervalMs: Long = 60L,
        val cropPaddingPx: Int = 20,
        val maxOutputs: Int = Int.MAX_VALUE,
        val enableStabilizer: Boolean = true
    ) {
        init {
            require(minProcessIntervalMs >= 0L) { "minProcessIntervalMs must be >= 0" }
            require(cropPaddingPx >= 0) { "cropPaddingPx must be >= 0" }
            require(maxOutputs >= 0) { "maxOutputs must be >= 0" }
        }
    }

    data class CropOutput(
        val sourceIndex: Int,
        val sourceBox: Rect,
        val cropBox: Rect,
        val cropType: String,
        val confidence: Float,
        val confidenceDetails: NativeBarcodeDetector.ConfidenceDetails,
        val bitmap: Bitmap
    )

    data class FrameOutput(
        val roiBitmap: Bitmap,
        val detections: List<NativeBarcodeDetector.BarcodeResult>,
        val crops: List<CropOutput>,
        val frameWidth: Int,
        val frameHeight: Int,
        val roiLeft: Int,
        val roiTop: Int
    )

    private val stabilizer = MultiBoxStabilizer(
        iouMatchThreshold = 0.25f,
        smoothAlpha = 0.25f,
        maxMiss = 2
    )

    @Synchronized
    fun init() {
        if (opencvLoaded) return
        System.loadLibrary("opencv_java4")
        opencvLoaded = true
    }

    fun processNv21Frame(
        nv21: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        config: FrameConfig = FrameConfig()
    ): FrameOutput? {
        val now = System.currentTimeMillis()
        if (now - lastProcessTimeMs.get() < config.minProcessIntervalMs) return null
        lastProcessTimeMs.set(now)

        var yuvMat: Mat? = null
        var bgrMat: Mat? = null
        var rotatedMat: Mat? = null
        var roiMat: Mat? = null
        var previewRgba: Mat? = null

        return try {
            init()

            yuvMat = Mat(height + height / 2, width, CvType.CV_8UC1)
            yuvMat.put(0, 0, nv21)

            bgrMat = Mat()
            Imgproc.cvtColor(yuvMat, bgrMat, Imgproc.COLOR_YUV2BGR_NV21)
            yuvMat.release()
            yuvMat = null

            rotatedMat = rotateBgrByDegrees(bgrMat, rotationDegrees)
            bgrMat.release()
            bgrMat = null

            val frameW = rotatedMat.cols()
            val frameH = rotatedMat.rows()

            val roiWidth = (frameW * (1f - MASK_LEFT_RATIO - MASK_RIGHT_RATIO)).roundToInt().coerceAtLeast(1)
            val roiHeight = (frameH * (1f - MASK_TOP_RATIO - MASK_BOTTOM_RATIO)).roundToInt().coerceAtLeast(1)
            val roiLeft = (frameW * MASK_LEFT_RATIO).roundToInt().coerceIn(0, frameW - roiWidth)
            val roiTop = (frameH * MASK_TOP_RATIO).roundToInt().coerceIn(0, frameH - roiHeight)

            roiMat = Mat(rotatedMat, CvRect(roiLeft, roiTop, roiWidth, roiHeight))

            val raw = detectBarcodeOnBgr(roiMat, config.detectionConfig)
            val detections = if (config.enableStabilizer) stabilizer.update(raw) else renumberByConfidence(raw)

            previewRgba = Mat()
            Imgproc.cvtColor(roiMat, previewRgba, Imgproc.COLOR_BGR2RGBA)
            val roiBitmap = Bitmap.createBitmap(roiWidth, roiHeight, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(previewRgba, roiBitmap)

            val crops = buildCrops(
                source = roiBitmap,
                detections = detections,
                paddingPx = config.cropPaddingPx,
                maxOutputs = config.maxOutputs
            )

            FrameOutput(
                roiBitmap = roiBitmap,
                detections = detections,
                crops = crops,
                frameWidth = frameW,
                frameHeight = frameH,
                roiLeft = roiLeft,
                roiTop = roiTop
            )
        } catch (e: Throwable) {
            null
        } finally {
            previewRgba?.release()
            roiMat?.release()
            rotatedMat?.release()
            bgrMat?.release()
            yuvMat?.release()
        }
    }

    private fun rotateBgrByDegrees(src: Mat, rotationDegrees: Int): Mat {
        val normalized = ((rotationDegrees % 360) + 360) % 360
        if (normalized == 0) return src.clone()

        val out = Mat()
        when (normalized) {
            90 -> Core.rotate(src, out, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(src, out, Core.ROTATE_180)
            270 -> Core.rotate(src, out, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> src.copyTo(out)
        }
        return out
    }

    private fun buildCrops(
        source: Bitmap,
        detections: List<NativeBarcodeDetector.BarcodeResult>,
        paddingPx: Int,
        maxOutputs: Int
    ): List<CropOutput> {
        if (maxOutputs == 0) return emptyList()

        val outputs = mutableListOf<CropOutput>()
        detections
            .sortedByDescending { it.confidence }
            .take(maxOutputs)
            .forEach { det ->
                val detectionBox = Rect(det.boundingBox)

                // Secondary crop #1: expanded detection box.
                val expandedBox = expandAndClamp(detectionBox, source.width, source.height, paddingPx)
                if (expandedBox != null) {
                    outputs += createCropOutput(source, det, detectionBox, expandedBox, CropType.DETECTION_BOX)
                }

                // Secondary crop #2: center band focused on 1D barcode strokes.
                val centerBandBox = buildCenterBandCrop(
                    box = detectionBox,
                    imageWidth = source.width,
                    imageHeight = source.height,
                    horizontalPaddingPx = (paddingPx * 1.8f).toInt(),
                    minBandHeightPx = 28
                )
                if (centerBandBox != null && (expandedBox == null || !sameRect(expandedBox, centerBandBox))) {
                    outputs += createCropOutput(source, det, detectionBox, centerBandBox, CropType.CENTER_BAND)
                }
            }
        return outputs
    }

    private fun expandAndClamp(box: Rect, width: Int, height: Int, paddingPx: Int): Rect? {
        if (width <= 1 || height <= 1) return null
        val left = (box.left - paddingPx).coerceIn(0, width - 1)
        val top = (box.top - paddingPx).coerceIn(0, height - 1)
        val right = (box.right + paddingPx).coerceIn(left + 1, width)
        val bottom = (box.bottom + paddingPx).coerceIn(top + 1, height)
        return if (right > left && bottom > top) Rect(left, top, right, bottom) else null
    }

    private fun buildCenterBandCrop(
        box: Rect,
        imageWidth: Int,
        imageHeight: Int,
        horizontalPaddingPx: Int,
        minBandHeightPx: Int
    ): Rect? {
        if (imageWidth <= 1 || imageHeight <= 1) return null

        val centerY = (box.top + box.bottom) / 2
        val boxHeight = (box.bottom - box.top).coerceAtLeast(1)
        val targetBandHeight = max(minBandHeightPx, (boxHeight * 0.55f).roundToInt())
        val halfBand = targetBandHeight / 2

        val left = (box.left - horizontalPaddingPx).coerceIn(0, imageWidth - 1)
        val right = (box.right + horizontalPaddingPx).coerceIn(left + 1, imageWidth)
        val top = (centerY - halfBand).coerceIn(0, imageHeight - 1)
        val bottom = (centerY + halfBand).coerceIn(top + 1, imageHeight)

        return if (right > left && bottom > top) Rect(left, top, right, bottom) else null
    }

    private fun createCropOutput(
        source: Bitmap,
        det: NativeBarcodeDetector.BarcodeResult,
        detectionBox: Rect,
        cropBox: Rect,
        cropType: CropType
    ): CropOutput {
        val crop = Bitmap.createBitmap(
            source,
            cropBox.left,
            cropBox.top,
            cropBox.width(),
            cropBox.height()
        )
        return CropOutput(
            sourceIndex = det.index,
            sourceBox = detectionBox,
            cropBox = cropBox,
            cropType = cropType.name,
            confidence = det.confidence,
            confidenceDetails = det.confidenceDetails,
            bitmap = crop
        )
    }

    private fun sameRect(a: Rect, b: Rect): Boolean {
        return a.left == b.left && a.top == b.top && a.right == b.right && a.bottom == b.bottom
    }

    private fun detectBarcodeOnBgr(
        src: Mat,
        config: NativeBarcodeDetector.DetectionConfig,
        tryBothAxes: Boolean = true
    ): List<NativeBarcodeDetector.BarcodeResult> {
        val xResults = detectBarcodeAxis(src, config, Axis.X)
        if (xResults.isNotEmpty() || !tryBothAxes) return xResults
        return detectBarcodeAxis(src, config, Axis.Y)
    }

    private fun detectBarcodeAxis(
        src: Mat,
        config: NativeBarcodeDetector.DetectionConfig,
        axis: Axis
    ): List<NativeBarcodeDetector.BarcodeResult> {
        val imgArea = src.rows().toDouble() * src.cols().toDouble()
        val minAreaAbs = max(100.0, imgArea * 0.00022)
        val minWidthAbs = max(30, (src.cols() * 0.055).roundToInt())
        val aspectMin = 1.8
        val aspectMax = 65.0
        val solidityMin = 0.10

        val gray = Mat()
        if (src.channels() == 3) {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
        } else if (src.channels() == 4) {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGRA2GRAY)
        } else {
            src.copyTo(gray)
        }

        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
        gray.release()

        val grad = Mat()
        when (axis) {
            Axis.X -> Imgproc.Sobel(blurred, grad, CvType.CV_64F, 1, 0, 3)
            Axis.Y -> Imgproc.Sobel(blurred, grad, CvType.CV_64F, 0, 1, 3)
        }

        val absGrad = Mat()
        Core.convertScaleAbs(grad, absGrad)
        grad.release()
        blurred.release()

        val binary = Mat()
        Imgproc.threshold(absGrad, binary, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)

        val closeKx = odd(max(21, (src.cols() * 0.055).roundToInt()))
        val closeKy = odd(max(3, (src.rows() * 0.010).roundToInt()))
        val kernelClose = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(closeKx.toDouble(), closeKy.toDouble())
        )

        val closed = Mat()
        Imgproc.morphologyEx(binary, closed, Imgproc.MORPH_CLOSE, kernelClose)
        binary.release()
        kernelClose.release()

        val kernelDilate = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val dilated = Mat()
        Imgproc.dilate(closed, dilated, kernelDilate, org.opencv.core.Point(-1.0, -1.0), 1)
        closed.release()
        kernelDilate.release()

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            dilated,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )
        dilated.release()

        val results = mutableListOf<NativeBarcodeDetector.BarcodeResult>()
        var index = 0

        for (contour in contours) {
            try {
                val area = Imgproc.contourArea(contour)
                if (area < minAreaAbs) continue

                val bbox = Imgproc.boundingRect(contour)
                val x = bbox.x
                val y = bbox.y
                val w = bbox.width
                val h = bbox.height
                if (w <= 0 || h <= 0) continue

                val aspectRatio = w.toDouble() / h.toDouble()
                if (aspectRatio < aspectMin || aspectRatio > aspectMax) continue
                if (w < minWidthAbs) continue

                val rectArea = w.toDouble() * h.toDouble()
                val solidity = if (rectArea > 0.0) area / rectArea else 0.0
                if (solidity < solidityMin) continue

                val idealAspect = 8.5
                val aspectScore = max(0.0, 1.0 - abs(aspectRatio - idealAspect) / idealAspect)
                val solidityScore = min(1.0, solidity / 0.8)
                val areaRatio = area / imgArea
                val areaScore = min(1.0, areaRatio / 0.1)

                val rx = max(0, min(x, absGrad.cols() - 1))
                val ry = max(0, min(y, absGrad.rows() - 1))
                val rw = max(1, min(w, absGrad.cols() - rx))
                val rh = max(1, min(h, absGrad.rows() - ry))

                val roiGrad = Mat(absGrad, CvRect(rx, ry, rw, rh))
                val gradMean = Core.mean(roiGrad).`val`[0]
                roiGrad.release()

                val gradScore = min(1.0, gradMean / 100.0)
                val confidence =
                    (aspectScore * 0.3 + solidityScore * 0.3 + areaScore * 0.2 + gradScore * 0.2) * 100.0

                if (areaScore * 100.0 < config.minAreaScore) continue
                if (aspectScore * 100.0 < config.minAspectScore) continue
                if (solidityScore * 100.0 < config.minSolidityScore) continue
                if (gradScore * 100.0 < config.minGradScore) continue

                index++
                results.add(
                    NativeBarcodeDetector.BarcodeResult(
                        index = index,
                        boundingBox = Rect(x, y, x + w, y + h),
                        area = area,
                        aspectRatio = aspectRatio,
                        solidity = solidity,
                        confidence = confidence.toFloat(),
                        confidenceDetails = NativeBarcodeDetector.ConfidenceDetails(
                            aspectScore = aspectScore * 100.0,
                            solidityScore = solidityScore * 100.0,
                            areaScore = areaScore * 100.0,
                            gradScore = gradScore * 100.0
                        )
                    )
                )
            } finally {
                contour.release()
            }
        }

        absGrad.release()
        hierarchy.release()
        return results
    }

    private fun renumberByConfidence(
        list: List<NativeBarcodeDetector.BarcodeResult>
    ): List<NativeBarcodeDetector.BarcodeResult> {
        return list.sortedByDescending { it.confidence }.mapIndexed { idx, result ->
            result.copy(index = idx + 1)
        }
    }

    private fun odd(n: Int): Int = if (n % 2 == 1) n else n + 1

    private fun defaultDetectionConfig(): NativeBarcodeDetector.DetectionConfig {
        return NativeBarcodeDetector.DetectionConfig(
            minAreaScore = DetectionConfigHolder.config.minAreaScore,
            minAspectScore = DetectionConfigHolder.config.minAspectScore,
            minSolidityScore = DetectionConfigHolder.config.minSolidityScore,
            minGradScore = DetectionConfigHolder.config.minGradScore
        )
    }

    private class MultiBoxStabilizer(
        private val iouMatchThreshold: Float,
        private val smoothAlpha: Float,
        private val maxMiss: Int
    ) {
        private data class Track(
            var rect: RectF,
            var last: NativeBarcodeDetector.BarcodeResult,
            var miss: Int = 0
        )

        private val tracks = mutableListOf<Track>()

        fun update(
            detections: List<NativeBarcodeDetector.BarcodeResult>
        ): List<NativeBarcodeDetector.BarcodeResult> {
            if (tracks.isEmpty()) {
                tracks += detections.map { Track(it.boundingBox.toRectF(), it, 0) }
                return renumberByConfidence(tracks.map { it.last.copy(boundingBox = it.rect.toRect()) })
            }

            val usedDet = BooleanArray(detections.size)
            val usedTrack = BooleanArray(tracks.size)

            data class Pairing(val ti: Int, val di: Int, val iou: Float)
            val pairs = ArrayList<Pairing>()

            for (ti in tracks.indices) {
                val tr = tracks[ti].rect
                for (di in detections.indices) {
                    val dr = detections[di].boundingBox.toRectF()
                    val i = iou(tr, dr)
                    if (i >= iouMatchThreshold) pairs += Pairing(ti, di, i)
                }
            }
            pairs.sortByDescending { it.iou }

            for (p in pairs) {
                if (usedTrack[p.ti] || usedDet[p.di]) continue
                usedTrack[p.ti] = true
                usedDet[p.di] = true

                val track = tracks[p.ti]
                val det = detections[p.di]
                val detRect = det.boundingBox.toRectF()

                track.rect = lerp(track.rect, detRect, smoothAlpha)
                track.last = det.copy(boundingBox = track.rect.toRect())
                track.miss = 0
            }

            for (ti in tracks.indices) {
                if (!usedTrack[ti]) tracks[ti].miss++
            }

            for (di in detections.indices) {
                if (!usedDet[di]) {
                    val det = detections[di]
                    tracks += Track(det.boundingBox.toRectF(), det, 0)
                }
            }

            val iterator = tracks.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().miss > maxMiss) iterator.remove()
            }

            val out = tracks.map { it.last.copy(boundingBox = it.rect.toRect()) }
            return renumberByConfidence(out)
        }

        private fun lerp(a: RectF, b: RectF, t: Float): RectF {
            return RectF(
                a.left + (b.left - a.left) * t,
                a.top + (b.top - a.top) * t,
                a.right + (b.right - a.right) * t,
                a.bottom + (b.bottom - a.bottom) * t
            )
        }

        private fun iou(a: RectF, b: RectF): Float {
            val interL = maxOf(a.left, b.left)
            val interT = maxOf(a.top, b.top)
            val interR = minOf(a.right, b.right)
            val interB = minOf(a.bottom, b.bottom)
            val iw = (interR - interL).coerceAtLeast(0f)
            val ih = (interB - interT).coerceAtLeast(0f)
            val inter = iw * ih
            val areaA = (a.right - a.left).coerceAtLeast(0f) * (a.bottom - a.top).coerceAtLeast(0f)
            val areaB = (b.right - b.left).coerceAtLeast(0f) * (b.bottom - b.top).coerceAtLeast(0f)
            val union = areaA + areaB - inter
            return if (union <= 0f) 0f else inter / union
        }

        private fun Rect.toRectF(): RectF {
            return RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        }

        private fun RectF.toRect(): Rect {
            return Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())
        }
    }
}

