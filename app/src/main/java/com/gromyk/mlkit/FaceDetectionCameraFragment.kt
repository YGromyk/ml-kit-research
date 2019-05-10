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
import java.util.*

class FaceDetectionCameraFragment : Fragment() {
    private var lensFacing = CameraX.LensFacing.BACK

    private lateinit var container: ConstraintLayout
    private lateinit var textureView: TextureView

    var textureViewRotation: Int? = null
    var bufferDimens: Size = Size(0, 0)
    var textureViewDimens: Size = Size(0, 0)

    /** Internal variable used to keep track of the use-case's output rotation */
    private var bufferRotation: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mark this as a retain fragment, so the lifecycle does not get restarted on config change
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
        container = view as ConstraintLayout
        textureView = container.findViewById(R.id.textureView)

        textureView.apply {
            post {
                initControls()
                startCamera()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Turn off all camera operations when we navigate away
        CameraX.unbindAll()
    }

    private fun startCamera() {

        // Make sure that there are no other use cases bound to CameraX
        CameraX.unbindAll()

        val metrics = DisplayMetrics().also { textureView.display.getRealMetrics(it) }
        val screenSize = Size(metrics.widthPixels, metrics.heightPixels)
        val screenAspectRatio = Rational(metrics.widthPixels, metrics.heightPixels)
        Log.d(javaClass.simpleName, "Metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        // Set up the view finder use case to display camera preview
        val previewConfig = PreviewConfig.Builder().apply {
            setLensFacing(lensFacing)
            // We request a specific resolution matching screen size
            setTargetResolution(screenSize)
            // We also provide an aspect ratio in case the exact resolution is not available
            setTargetAspectRatio(screenAspectRatio)
            setTargetRotation(textureView.display.rotation)
        }.build()

        // Use the auto-fit preview builder to automatically handle size and orientation changes
        val preview = Preview(previewConfig)

        preview.setOnPreviewOutputUpdateListener {
            val textureView = textureView

            // To update the SurfaceTexture, we have to remove it and re-add it
            val parent = textureView.parent as ViewGroup
            parent.removeView(textureView)
            parent.addView(textureView, 0)

            textureView.surfaceTexture = it.surfaceTexture
            bufferRotation = it.rotationDegrees
            val rotation = getDisplaySurfaceRotation(textureView.display)
            updateTransform(textureView, rotation, it.textureSize, textureViewDimens!!)
        }


        // Apply declared configs to CameraX using the same lifecycle owner
        CameraX.bindToLifecycle(this, preview)
    }

    @SuppressLint("RestrictedApi")
    private fun initControls() {
        container.findViewById<ConstraintLayout>(R.id.camera_ui_container)?.let {
            container.removeView(it)
        }

        val controlsView: View = View.inflate(requireContext(), R.layout.camera_controls_container, container)

        controlsView.findViewById<ImageButton>(R.id.camera_switch_button).setOnClickListener {
            lensFacing =
                if (CameraX.LensFacing.FRONT == lensFacing)
                    CameraX.LensFacing.BACK
                else
                    CameraX.LensFacing.FRONT

            try {
                // Only bind use cases if we can query a camera with this orientation
                CameraX.getCameraWithLensFacing(lensFacing)
                startCamera()
            } catch (exc: Exception) {
                // Do nothing
            }
        }
    }

    private fun updateTransform(
        textureView: TextureView?, rotation: Int?, newBufferDimens: Size,
        newTextureViewDimens: Size
    ) {
        // This should happen anyway, but now the linter knows
        val textureView = textureView ?: return
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

    private fun updateTransform1() {
        val matrix = Matrix()

        // Compute the center of the view finder
        val centerX = textureView.width / 2f
        val centerY = textureView.height / 2f

        // Correct preview output to account for display rotation
        val rotationDegrees = when (textureView.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)

        // Finally, apply transformations to our TextureView
        textureView.setTransform(matrix)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                textureView.post { startCamera() }
            } else {
                Toast.makeText(
                    activity,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
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

    companion object {
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