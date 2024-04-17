package com.os.cvCamera


import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import com.os.cvCamera.BuildConfig.VERSION_NAME
import com.os.cvCamera.databinding.ActivityMainBinding
import org.opencv.android.CameraActivity
import org.opencv.android.CameraBridgeViewBase.CAMERA_ID_BACK
import org.opencv.android.CameraBridgeViewBase.CAMERA_ID_FRONT
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import timber.log.Timber

class MainActivity : CameraActivity(), CvCameraViewListener2 {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mRGBA: Mat
    private lateinit var mRGBAT: Mat
    private var mCameraId: Int = CAMERA_ID_BACK
    private lateinit var mCameraManager: CameraManager

    // Filters id
    private var mFilterId = -1
    private var mIsEnableFilter = false
    companion object {
        init {
            System.loadLibrary("opencv_java4")
            System.loadLibrary("cvcamera")
        }
    }

    private external fun openCVVersion(): String?

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mCameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        loadOpenCVConfigs()
        configButtons()
    }

    private fun configButtons() {
        binding.cvCameraChangeFab.setOnClickListener {
             cameraSwitch()
        }

        binding.bottomAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.btnAbout -> {
                    // Get app version and githash from BuildConfig
                    val cvVer = openCVVersion() // Get OpenCV version from native code
                    val toast: Toast = Toast.makeText(
                        this,
                        "CvCamera-Mobile - Version $VERSION_NAME - OpenCV $cvVer ",
                        Toast.LENGTH_SHORT,
                    )
                    toast.show()

                    true
                }

                R.id.btnRecognize -> {
                    mIsEnableFilter = !mIsEnableFilter
                    binding.CvCamera.updateFilterStatus(mIsEnableFilter)
//                    val btnRecognize = findViewById<Button>(R.id.btnRecognize)
                    true
                }

                R.id.btnSave -> {
                    true
                }

                else -> {
                    false
                }
            }

        }
    }

    private fun cameraSwitch() {
        mCameraId = if (mCameraId == CAMERA_ID_BACK) {
            CAMERA_ID_FRONT
        } else {
            CAMERA_ID_BACK
        }

        binding.CvCamera.disableView()
        binding.CvCamera.setCameraIndex(mCameraId)
        binding.CvCamera.enableView()
    }

    private fun loadOpenCVConfigs() {
        binding.CvCamera.setCameraIndex(mCameraId)
        binding.CvCamera.setCvCameraViewListener(this)
        binding.CvCamera.setCameraPermissionGranted()
        binding.CvCamera.enableView()
        binding.CvCamera.getCameraDevice()
    }


    override fun onCameraViewStarted(width: Int, height: Int) {
        mRGBA = Mat(height, width, CvType.CV_8UC4)
        mRGBAT = Mat()
    }

    override fun onCameraViewStopped() {
        mRGBA.release()
        mRGBAT.release()
    }

    override fun onCameraFrame(inputFrame: CvCameraViewFrame?): Mat {
        return if (inputFrame != null) {

            if (mCameraId == CAMERA_ID_BACK) {
                mRGBA = inputFrame.rgba()
                cvFilters(mRGBA)
            } else {
                mRGBA = inputFrame.rgba()
                // Flipping to show portrait mode properly
                Core.flip(mRGBA, mRGBAT, 1)
                // Release the matrix to avoid memory leaks
                mRGBA.release()
                // Check if grayscale is enabled
                cvFilters(mRGBAT)
            }

        } else {
            // return last or empty frame
            mRGBA
        }
    }

    private fun cvFilters(frame: Mat): Mat {
        if (mFilterId == -1) {
            return frame
        } else {
            return frame
        }
    }

    override fun onDestroy() {
        Timber.d("onDestroy")
        super.onDestroy()
        binding.CvCamera.disableView()
    }

    override fun onPause() {
        Timber.d("onPause")
        super.onPause()
        binding.CvCamera.disableView()
    }

    override fun onResume() {
        Timber.d("onResume")
        super.onResume()
    }
}
