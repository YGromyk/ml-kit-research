package com.gromyk.mlkit

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initFragment()
    }

    private fun initFragment() {
        val fragment = FaceDetectionCameraFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .add(R.id.fragmentContainer, fragment, null)
            .commit()
    }
}
