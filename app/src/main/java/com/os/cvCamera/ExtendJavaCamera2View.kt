package com.os.cvCamera

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.Rect
import android.hardware.camera2.CameraDevice
import android.util.AttributeSet
import org.opencv.android.JavaCamera2View
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import timber.log.Timber
import com.os.cvCamera.PointDTO
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import java.io.File
import java.io.FileOutputStream

class ExtendJavaCamera2View(context: Context, attrs: AttributeSet? = null) :
    JavaCamera2View(context, attrs) {

    private val mMatrix: Matrix = Matrix()
    private var mCacheBitmap: Bitmap? = null
    private var mListener: CvCameraViewListener2? = null
    private var mFitToCanvas: Boolean = true
    private var mIsFilter: Boolean = false
    private var mIsFilterFirst: Boolean = true

    private var cw : Float = 720f
    private var ch : Float = 480f
    private fun updateMatrix() {
        val mw: Float = this.width.toFloat()
        val mh: Float = this.height.toFloat()
        val hw: Float = this.width.toFloat() / 2.0f
        val hh: Float = this.height.toFloat() / 2.0f
        cw = Resources.getSystem().displayMetrics.widthPixels.toFloat()
        ch = Resources.getSystem().displayMetrics.heightPixels.toFloat()
        var scale: Float = cw / mh
        val scale2: Float = ch / mw
        if (scale2 > scale) {
            scale = scale2
        }
        mMatrix.reset()
        if (mCameraIndex == CAMERA_ID_FRONT) {
            mMatrix.preScale(-1f, 1f, hw, hh)
        }

        mMatrix.preTranslate(hw, hh)
        if (mCameraIndex == CAMERA_ID_FRONT) {
            mMatrix.preRotate(270f)
        } else {
            mMatrix.preRotate(90f)
        }
        mMatrix.preTranslate(-hw, -hh)
        mMatrix.preScale(scale, scale, hw, hh)
    }

    override fun layout(l: Int, t: Int, r: Int, b: Int) {
        super.layout(l, t, r, b)
        updateMatrix()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        updateMatrix()
    }

    override fun deliverAndDrawFrame(frame: CvCameraViewFrame?) {
        deliverAndDrawFrame2(frame)
    }

    private fun detectCoordinate(
        inputFrame: Mat,
        pointsList: MutableList<Point>,
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        outX: Double,
        outY: Double,
    ) {

        var points : MutableList<PointDTO> = mutableListOf()
        for (point in pointsList) {
            val pointDTO = PointDTO(point, Point(point.x - outX, outY - point.y))
            points.add(pointDTO)
            Imgproc.circle(inputFrame, point, 2, Scalar(0.0, 250.0, 0.0), -1)
        }
        // Add edge line for whole area
        Imgproc.line(inputFrame, Point(minX, minY), Point(minX, maxY), Scalar(0.0, 250.0, 0.0), 2)
        Imgproc.line(inputFrame, Point(minX, minY), Point(maxX, minY), Scalar(0.0, 250.0, 0.0), 2)
        Imgproc.line(inputFrame, Point(maxX, minY), Point(maxX, maxY), Scalar(0.0, 250.0, 0.0), 2)
        Imgproc.line(inputFrame, Point(minX, maxY), Point(maxX, maxY), Scalar(0.0, 250.0, 0.0), 2)

        // Bottom Left point
        var lb_x = minX - outX
        if ( lb_x < 0 ) lb_x = 0.0
        if ( lb_x > 960 ) lb_x = 955.0
        var rt_x = maxX - outX
        if ( rt_x < 0 ) rt_x = 0.0
        if ( rt_x > 960 ) rt_x = 955.0
        var rt_y = outY - minY
        if ( rt_y < 0 ) rt_y = 0.0
        if ( rt_y > 720 ) rt_y = 715.0
        var lb_y = outY - maxY
        if ( lb_y < 0 ) lb_y = 0.0
        if ( lb_y > 720 ) lb_y = 715.0
        Imgproc.circle(inputFrame, Point(minX, maxY), 2, Scalar(250.0, 0.0, 0.0), -1)
        Imgproc.putText(
            inputFrame,
            "(${lb_x}, ${lb_y})",
            Point(minX + 10, maxY - 10),
            Imgproc.FONT_HERSHEY_SIMPLEX,
            0.5,
            Scalar(255.0, 0.0, 0.0),
            1,
            Imgproc.LINE_AA
        )
        // Top right point
        Imgproc.circle(inputFrame, Point(maxX, minY), 2, Scalar(250.0, 0.0, 0.0), -1)
        Imgproc.putText(
            inputFrame,
            "(${rt_x}, ${rt_y})",
            Point(maxX - 100, minY + 20),
            Imgproc.FONT_HERSHEY_SIMPLEX,
            0.5,
            Scalar(255.0, 0.0, 0.0),
            1,
            Imgproc.LINE_AA
        )
    }

    private fun detectEdges(inputFrame: Mat): Mat {
        //        val grayFrame = Mat()
        //        Imgproc.cvtColor(inputFrame, grayFrame, Imgproc.COLOR_BGR2GRAY)
        //        // Apply Gaussian blur to reduce noise and detail
        //        val blurredFrame = Mat()
        //        Imgproc.GaussianBlur(grayFrame, blurredFrame, Size(3.0, 3.0), 0.0)
        //        // Apply Canny edge detection
        //        val edges = Mat()
        //        Imgproc.Canny(blurredFrame, edges, 100.0, 250.0)

        val hsvFrame = Mat()
        Imgproc.cvtColor(inputFrame, hsvFrame, Imgproc.COLOR_BGR2HSV)
        // Threshold to find near white colors
        val lowerWhite = Scalar(0.0, 0.0, 150.0) // Lower bound for HSV values
        val upperWhite = Scalar(180.0, 50.0, 255.0) // Upper bound for near white colors
        val mask = Mat()
        Core.inRange(hsvFrame, lowerWhite, upperWhite, mask)
        // Optional: Apply Gaussian blur to the mask to smooth the edges
        Imgproc.GaussianBlur(mask, mask, Size(3.0, 3.0), 0.0)
        // Apply Canny edge detection on the mask
        val edges = Mat()
        Imgproc.Canny(mask, edges, 100.0, 250.0)

        // Find contours from edges
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        // Filter contours and extract corners
        val corners = ArrayList<Point>()
        contours.forEach { contour ->
            val contourFloat = MatOfPoint2f(*contour.toArray())
            val approxCurve = MatOfPoint2f()
            Imgproc.approxPolyDP(contourFloat, approxCurve, 0.02 * Imgproc.arcLength(contourFloat, true), true)
            // Check for rectangle or square-like shapes, which have 4 corners
            if (approxCurve.total() == 4L) {
                val points = approxCurve.toArray().toList()
                corners.addAll(points)
            }
        }

        for (point in corners) {
            Imgproc.circle(inputFrame, Point(point.x, point.y), 2, Scalar(0.0, 250.0, 0.0), 2)
        }
        //val lines = Mat()
        //Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 360, 20, 20.0, 30.0)
        //
        //val limitMinX : Double = 5.0
        //val limitMaxX = ((cw + mCacheBitmap!!.width ) / mScale / 2 - 70 * mScale).toDouble()
        //var limitMinY : Double =  60.0
        //if (mScale > 1f) limitMinY *= mScale * 2
        //var limitMaxY = (mCacheBitmap!!.height  - 60).toDouble()
        //if (mScale > 1f) limitMaxY -= mScale * 60
        //val pointsList = mutableListOf<Point>()
        //var minX = 1000.0
        //var minY = 1000.0
        //var maxX = 0.0
        //var maxY = 0.0
        //for (i in 0 until lines.rows()) {
        //    val line = lines.get(i, 0)
        //    val x1: Double = line[0]
        //    val y1: Double = line[1]
        //    if (limitMinX < x1 && x1 < limitMaxX && limitMinY < y1 && y1 < limitMaxY) {
        //        if (x1 < minX) minX = x1
        //        if (y1 < minY) minY = y1
        //        if (x1 > maxX) maxX = x1
        //        if (y1 > maxY) maxY = y1
        //        pointsList.add(Point(x1, y1))
        //    }
        //    val x2: Double = line[2]
        //    val y2: Double = line[3]
        //    if (limitMinX < x2 && x2 < limitMaxX && limitMinY < y2 && y2 < limitMaxY) {
        //        if (x2 < minX) minX = x2
        //        if (y2 < minY) minY = y2
        //        if (x2 > maxX) maxX = x2
        //        if (y2 > maxY) maxY = y2
        //        pointsList.add(Point(x2, y2))
        //    }
        //}
        //detectCoordinate(inputFrame, pointsList, minX, maxX, minY, maxY, limitMinX, mCacheBitmap!!.height - limitMinY)
        // 720 x 480
        // Imgproc.line(inputFrame, Point(5.0, 515.0), Point(650.0, 885.0), Scalar(0.0, 250.0, 0.0), 2)
        // 960 x 720
        // Imgproc.line(inputFrame, Point(5.0, 130.0), Point(855.0, 590.0), Scalar(0.0, 250.0, 0.0), 2)

        // // Invert the edges to create a mask where edge pixels are black and everything else is white
        //val invertedEdges = Mat()
        //Core.bitwise_not(edges, invertedEdges)
        //// Define transparent color (in BGR format)
        //val transparentColor = Scalar(0.0, 0.0, 0.0, 0.0) // Fully transparent black
        //// Create a copy of the original frame with transparent edges
        //val transparentFrame = Mat(inputFrame.size(), inputFrame.type(), transparentColor)
        //inputFrame.copyTo(transparentFrame, invertedEdges)
        //edges.release()
        //invertedEdges.release()
        //inputFrame.release()



        return inputFrame
    }

    private fun deliverAndDrawFrame2(frame: CvCameraViewFrame?) {
        var modified: Mat? = if (mListener != null) mListener?.onCameraFrame(frame) else {
            frame!!.rgba()
        }

        if (mIsFilter && !mIsFilterFirst) {
            return
        }

        var bmpValid = true
        if (modified != null) {
            try {
                if (mIsFilter) {
                    modified = detectEdges(modified)
                    mIsFilterFirst = false
                }
                Utils.matToBitmap(modified, mCacheBitmap)
            } catch (e: Exception) {
                Timber.e("Utils.matToBitmap() throws an exception: " + e.message)
                bmpValid = false
            }
        }

        if (bmpValid && mCacheBitmap != null) {
            val canvas: Canvas? = holder.lockCanvas()
            if (canvas != null) {
                canvas.drawColor(0, PorterDuff.Mode.CLEAR)
                val saveCount: Int = canvas.save()
                canvas.setMatrix(mMatrix)
                canvas.drawBitmap(
                    mCacheBitmap!!,
                    Rect(0, 0, mCacheBitmap!!.width, mCacheBitmap!!.height),
                    Rect(
                        ((canvas.width - mScale * mCacheBitmap!!.width) / 2).toInt(),
                        ((canvas.height - mScale * mCacheBitmap!!.height) / 2).toInt(),
                        ((canvas.width - mScale * mCacheBitmap!!.width) / 2 + mScale * mCacheBitmap!!.width).toInt(),
                        ((canvas.height - mScale * mCacheBitmap!!.height) / 2 + mScale * mCacheBitmap!!.height).toInt(),
                    ),
                    null,
                )
                // Restore canvas after draw bitmap
                canvas.restoreToCount(saveCount)
                //                if (mFpsMeter != null) {
                //                    mFpsMeter.measure()
                //                    mFpsMeter.draw(canvas, 20f, 30f)
                //                }
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }


    fun updateFilterStatus(isFilter: Boolean) {
        mIsFilter = isFilter
        mIsFilterFirst = true
    }

    override fun AllocateCache() {
        if (!mFitToCanvas) {
            super.AllocateCache()
            return
        }
        mCacheBitmap = Bitmap.createBitmap(mFrameWidth, mFrameHeight, Bitmap.Config.ARGB_8888)
    }

    override fun enableFpsMeter() {
        if (mFpsMeter == null) {
            mFpsMeter = CvFpsMeter()
            mFpsMeter.setResolution(mFrameWidth, mFrameHeight)
        }
    }

    override fun disableFpsMeter() {
        mFpsMeter = null
    }

    override fun setCvCameraViewListener(listener: CvCameraViewListener2?) {
        super.setCvCameraViewListener(listener)
        mListener = listener
    }

    fun getCameraDevice(): CameraDevice? {
        return mCameraDevice
    }
}
