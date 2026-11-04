package com.example.sanivend

import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {

    override fun setContentView(layoutResID: Int) {
        val root = FrameLayout(this)
        root.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // Inflate the activity's main layout
        val content = layoutInflater.inflate(layoutResID, root, false)
        root.addView(content)

        // Create the floating back button - PNG style (no background)
        val backButton = ImageButton(this).apply {
            // Set size to 100dp for a more standard button size
            val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100f, resources.displayMetrics).toInt()
            // Standard margin from the edges
            val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics).toInt()

            val lp = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, margin, margin)
            }
            layoutParams = lp

            // Use the backbutton.png provided by user
            setImageResource(R.drawable.backbutton)
            
            // Ensure NO background or shadow
            background = null 
            elevation = 0f
            
            scaleType = ImageView.ScaleType.FIT_CENTER
            
            setOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }
        }

        root.addView(backButton)
        super.setContentView(root)
    }
}
