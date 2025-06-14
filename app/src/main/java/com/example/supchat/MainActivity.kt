
package com.example.supchat

import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import com.example.supchat.ui.auth.LoginActivity
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialiser le détecteur de gestes
        gestureDetector = GestureDetector(this, SwipeGestureListener())
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return gestureDetector.onTouchEvent(event!!) || super.onTouchEvent(event)
    }

    // Classe pour gérer les gestes de swipe
    private inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {

        private val SWIPE_THRESHOLD = 100 // Distance minimum pour déclencher le swipe
        private val SWIPE_VELOCITY_THRESHOLD = 100 // Vitesse minimum

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false

            val diffY = e1.y - e2.y
            val diffX = e1.x - e2.x

            // Vérifier si c'est un swipe vertical vers le haut
            if (abs(diffY) > abs(diffX)) {
                if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) {
                        // Swipe vers le haut détecté !
                        onSwipeUp()
                        return true
                    }
                }
            }
            return false
        }
    }

    // Fonction appelée quand on swipe vers le haut
    private fun onSwipeUp() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        // Animation personnalisée : la page se lève vers le haut
        overridePendingTransition(R.anim.slide_up_in, R.anim.slide_up_out)
    }
}