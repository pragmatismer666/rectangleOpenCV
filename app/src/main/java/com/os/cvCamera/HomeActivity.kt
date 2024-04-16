package com.os.cvCamera


import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import timber.log.Timber

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val btnStart = findViewById<Button>(R.id.btnStartCamera)
        btnStart.isEnabled = true // Initially disable the button

//        val handler = Handler()
//        handler.postDelayed({
//            btnStart.isEnabled = true // Enable the button
//        }, 5000)
        btnStart.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        Timber.d("onDestroy")
        super.onDestroy()
    }

    override fun onPause() {
        Timber.d("onPause")
        super.onPause()
    }

    override fun onResume() {
        Timber.d("onResume")
        super.onResume()
    }
}
