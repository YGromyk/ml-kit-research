package com.gromyk.mlkit

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.*
import android.widget.ImageButton
import android.widget.Toast
import androidx.camera.core.CameraX
import androidx.camera.core.Preview
import androidx.camera.core.PreviewConfig
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions
import kotlinx.android.synthetic.main.fragment_face_detection_camera.*
import java.util.*
import kotlin.concurrent.timerTask


class FaceDetectionCameraFragment : Fragment() {
    private var lensFacing = CameraX.LensFacing.FRONT

    private var textureViewRotation: Int? = null
    private var bufferDimens: Size = Size(0, 0)
    private var textureViewDimens: Size = Size(0, 0)

    /** Internal variable used to keep track of the use-case's output rotation */
    private var bufferRotation: Int = 0

    private lateinit var timer: Timer

    private var isPhotoInProcessing = false
    private lateinit var faceGraphic: FaceContourGraphic

    private val options: FirebaseVisionFaceDetectorOptions = FirebaseVisionFaceDetectorOptions.Builder()
        .setPerformanceMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
        .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
        .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
        .setMinFaceSize(0.15f)
        .build()

    private val detector = FirebaseVision.getInstance().getVisionFaceDetector(options)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_face_detection_camera, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        checkPermissions()
    }

    private fun initView() {
        textureView.apply {
            faceGraphic = FaceContourGraphic(graphicOverlay).apply {
                isDrawBoundingBox = true
                isDrawFaceContour = true
                isDrawSmilingProbability = true
            }
            post {
                initControls()
                startCamera()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        timer = Timer()
        timer.schedule(
            timerTask {
                runFaceContourDetection(
                    FirebaseVisionImage.fromBitmap(
                        textureView.bitmap ?: return@timerTask
                    )
                )
            },
            1L,
            500L
        )
    }

    override fun onPause() {
        timer.cancel()
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        CameraX.unbindAll()
    }

    private fun startCamera() {
        CameraX.unbindAll()
        val metrics = DisplayMetrics().also { textureView.display.getRealMetrics(it) }
        //val screenSize = Size(metrics.widthPixels, metrics.heightPixels)
        val screenSize = Size(720, 1280)
        val screenAspectRatio = Rational(metrics.widthPixels, metrics.heightPixels)

        val previewConfig = PreviewConfig.Builder()
            .setLensFacing(lensFacing)
            .setTargetResolution(screenSize)
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(textureView.display.rotation)
            .build()

        val preview = Preview(previewConfig)

        preview.setOnPreviewOutputUpdateListener {
            val textureView = textureView
            val parent = textureView.parent as ViewGroup
            parent.removeView(textureView)
            parent.addView(textureView, 0)
            val texture = it.surfaceTexture
            textureView.surfaceTexture = texture
            bufferRotation = it.rotationDegrees
            val rotation = getDisplaySurfaceRotation(textureView.display)
            updateTransform(textureView, rotation, it.textureSize, textureViewDimens)
        }
        CameraX.bindToLifecycle(this, preview)
    }

    @SuppressLint("RestrictedApi")
    private fun initControls() {
        viewContainer.findViewById<ConstraintLayout>(R.id.camera_ui_container)?.let {
            viewContainer.removeView(it)
        }

        val controlsView: View = View.inflate(requireContext(), R.layout.camera_controls_container, viewContainer)

        controlsView.findViewById<ImageButton>(R.id.cameraSwitchButton).setOnClickListener {
            lensFacing =
                if (CameraX.LensFacing.FRONT == lensFacing)
                    CameraX.LensFacing.BACK
                else
                    CameraX.LensFacing.FRONT

            try {
                CameraX.getCameraWithLensFacing(lensFacing)
                graphicOverlay.clear()
                startCamera()
            } catch (exc: Throwable) {
            }
        }
    }

    private fun updateTransform(
        textureView: TextureView?, rotation: Int?, newBufferDimens: Size,
        newTextureViewDimens: Size
    ) {
        if (textureView != null) {
            textureViewRotation = getDisplaySurfaceRotation(textureView.display) ?: 0
            bufferDimens = newBufferDimens
            textureViewDimens = newTextureViewDimens
            if (rotation == textureViewRotation &&
                Objects.equals(newBufferDimens, bufferDimens) &&
                Objects.equals(newTextureViewDimens, textureViewDimens)
            ) {
                // Nothing has changed, no need to transform output again
                return
            }

            if (rotation == null) {
                // Invalid rotation - wait for valid inputs before setting matrix
                return
            } else {
                // Update internal field with new inputs
                textureViewRotation = rotation
            }

            if (newBufferDimens.width == 0 || newBufferDimens.height == 0) {
                // Invalid buffer dimens - wait for valid inputs before setting matrix
                return
            } else {
                // Update internal field with new inputs
                bufferDimens = newBufferDimens
            }
            if (newTextureViewDimens.width == 0 || newTextureViewDimens.height == 0) {
                // Invalid view finder dimens - wait for valid inputs before setting matrix
                return
            } else {
                // Update internal field with new inputs
                textureViewDimens = newTextureViewDimens
            }

            val matrix = Matrix()

            // Compute the center of the view finder
            val centerX = textureViewDimens.width / 2f
            val centerY = textureViewDimens.height / 2f

            // Correct preview output to account for display rotation
            matrix.postRotate(-textureViewRotation!!.toFloat(), centerX, centerY)

            // Buffers are rotated relative to the device's 'natural' orientation: swap width and height
            val bufferRatio = bufferDimens.height / bufferDimens.width.toFloat()

            val scaledWidth: Int
            val scaledHeight: Int
            // Match longest sides together -- i.e. apply center-crop transformation
            if (textureViewDimens.width > textureViewDimens.height) {
                scaledHeight = textureViewDimens.width
                scaledWidth = Math.round(textureViewDimens.width * bufferRatio)
            } else {
                scaledHeight = textureViewDimens.height
                scaledWidth = Math.round(textureViewDimens.height * bufferRatio)
            }

            // Compute the relative scale value
            val xScale = scaledWidth / textureViewDimens.width.toFloat()
            val yScale = scaledHeight / textureViewDimens.height.toFloat()

            // Scale input buffers to fill the view finder
            matrix.preScale(xScale, yScale, centerX, centerY)

            // Finally, apply transformations to our TextureView
            textureView.setTransform(matrix)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                textureView.post { startCamera() }
            } else {
                showToast("Permissions not granted by the user.")
            }
        }
    }

    private fun checkPermissions() {
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                activity!!,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }


    private fun allPermissionsGranted(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            val permissionStatus = ContextCompat.checkSelfPermission(activity!!, permission)
            if (permissionStatus != PackageManager.PERMISSION_GRANTED)
                return false
        }
        return true
    }

    private fun runFaceContourDetection(image: FirebaseVisionImage) {
        if (isPhotoInProcessing) return
        Log.d(DETECTION_TIME_TAG, "Image is started to proceed ${System.currentTimeMillis()}")
        isPhotoInProcessing = true
        detector.detectInImage(image)
            .addOnSuccessListener { faces ->
                processFaceContourDetectionResult(faces ?: emptyList())
                isPhotoInProcessing = false
            }
    }

    private fun processFaceContourDetectionResult(faces: List<FirebaseVisionFace>) {
        // Task completed successfully
        graphicOverlay.clear()
        if (faces.isEmpty()) {
            showToast("No face found")
            return
        }
        for (face in faces) {
            graphicOverlay.add(faceGraphic)
            faceGraphic.updateFace(face)
        }
        Log.d(DETECTION_TIME_TAG, "Image proceeded ${System.currentTimeMillis()}")
        Log.d(DETECTION_TIME_TAG, "____________________________")
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val DETECTION_TIME_TAG = "Detection time"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

        fun newInstance() = FaceDetectionCameraFragment().apply {
        }

        fun getDisplaySurfaceRotation(display: Display?) = when (display?.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> null
        }

    }
}