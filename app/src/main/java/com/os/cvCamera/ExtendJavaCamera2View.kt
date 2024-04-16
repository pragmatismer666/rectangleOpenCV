package com.os.cvCamera

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.Rect
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.util.AttributeSet
import org.opencv.android.JavaCamera2View
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Core.inRange
import org.opencv.core.CvType
import org.opencv.core.CvType.CV_8UC1
import org.opencv.core.CvType.CV_8UC3
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import timber.log.Timber


class ExtendJavaCamera2View(context: Context, attrs: AttributeSet? = null) :
    JavaCamera2View(context, attrs) {

    private val mMatrix: Matrix = Matrix()
    private var mCacheBitmap: Bitmap? = null
    private var mListener: CvCameraViewListener2? = null
    private var mFitToCanvas: Boolean = true
    private var mIsFilter: Boolean = false;
    private var lastFilteredFrameTime: Long = 0
    private fun updateMatrix() {
        val mw: Float = this.width.toFloat()
        val mh: Float = this.height.toFloat()
        val hw: Float = this.width.toFloat() / 2.0f
        val hh: Float = this.height.toFloat() / 2.0f
        val cw = Resources.getSystem().displayMetrics.widthPixels.toFloat()
        val ch = Resources.getSystem().displayMetrics.heightPixels.toFloat()
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
        if (mFitToCanvas) updateMatrix()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (mFitToCanvas) updateMatrix()
    }

    override fun deliverAndDrawFrame(frame: CvCameraViewFrame?) {
        if (!mFitToCanvas)
            super.deliverAndDrawFrame(frame)
        else
            deliverAndDrawFrame2(frame)
    }

    fun detectEdges(inputFrame: Mat): Mat {
        val grayFrame = Mat()
        Imgproc.cvtColor(inputFrame, grayFrame, Imgproc.COLOR_BGR2GRAY)
        // Apply Gaussian blur to reduce noise and detail
        val blurredFrame = Mat()
        Imgproc.GaussianBlur(grayFrame, blurredFrame, Size(3.0, 3.0), 0.0)
        // Apply Canny edge detection
        val edges = Mat()
        Imgproc.Canny(blurredFrame, edges, 100.0, 250.0)
        // Invert the edges to create a mask where edge pixels are black and everything else is white
        val invertedEdges = Mat()
        Core.bitwise_not(edges, invertedEdges)
        // Define transparent color (in BGR format)
        val transparentColor = Scalar(0.0, 0.0, 0.0, 0.0) // Fully transparent black
        // Create a copy of the original frame with transparent edges
        val transparentFrame = Mat(inputFrame.size(), inputFrame.type(), transparentColor)
        inputFrame.copyTo(transparentFrame, invertedEdges)

        grayFrame.release()
        blurredFrame.release()
        edges.release()
        invertedEdges.release()
        inputFrame.release()
        
        return transparentFrame
    }

    private fun deliverAndDrawFrame2(frame: CvCameraViewFrame?) {
        var modified: Mat? = if (mListener != null) mListener?.onCameraFrame(frame) else {
            frame!!.rgba()
        }

        var bmpValid = true
        if (modified != null) {
            try {
                if (mIsFilter) {
                    val edgeDetectedFrame = detectEdges(modified)
                    modified.release()
                    modified = edgeDetectedFrame
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
                if (mFpsMeter != null) {
                    mFpsMeter.measure()
                    mFpsMeter.draw(canvas, 20f, 30f)
                }
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }


    fun updateFilterStatus(isFilter: Boolean) {
        mIsFilter = isFilter
        lastFilteredFrameTime = 0
    }

    fun setFitToCanvas(fitToCanvas: Boolean) {
        mFitToCanvas = fitToCanvas
    }

    fun getFitToCanvas(): Boolean {
        return mFitToCanvas
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

    override fun enableView() {
        super.enableView()
    }

    override fun disableView() {
        super.disableView()
    }

    override fun setCvCameraViewListener(listener: CvCameraViewListener2?) {
        super.setCvCameraViewListener(listener)
        mListener = listener
    }

    fun getCameraDevice(): CameraDevice? {
        return mCameraDevice
    }

    fun turnOnFlashlight() {
        val captureRequestBuilder =
            mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
        mCaptureSession!!.setRepeatingRequest(
            mPreviewRequestBuilder.build(),
            null,
            mBackgroundHandler
        )
    }
}
