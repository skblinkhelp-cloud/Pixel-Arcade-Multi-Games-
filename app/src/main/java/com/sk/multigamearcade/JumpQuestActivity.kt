package com.sk.multigamearcade

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class JumpQuestActivity : AppCompatActivity() {

    private lateinit var gameView: JumpQuestGameView
    private lateinit var tvScore: TextView
    private lateinit var tvLives: TextView
    private lateinit var tvHighScore: TextView
    private lateinit var layoutGameOver: LinearLayout
    private lateinit var tvFinalScore: TextView
    private lateinit var btnRestart: Button
    private lateinit var btnBack: ImageButton
    private lateinit var btnLeft: Button
    private lateinit var btnRight: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_jump_quest)

        gameView = findViewById(R.id.gameView)
        tvScore = findViewById(R.id.tvScore)
        tvLives = findViewById(R.id.tvLives)
        tvHighScore = findViewById(R.id.tvHighScore)
        layoutGameOver = findViewById(R.id.layoutGameOver)
        tvFinalScore = findViewById(R.id.tvFinalScore)
        btnRestart = findViewById(R.id.btnRestart)
        btnBack = findViewById(R.id.btnBack)
        btnLeft = findViewById(R.id.btnLeft)
        btnRight = findViewById(R.id.btnRight)

        btnBack.setOnClickListener {
            finish()
        }

        setupControlListeners()

        gameView.onScoreChanged = { score, highScore, lives ->
            runOnUiThread {
                tvScore.text = "SCORE: $score"
                tvHighScore.text = "BEST: $highScore"
                val heartStr = "❤".repeat(lives.coerceAtLeast(0))
                tvLives.text = "LIVES: $heartStr"
            }
        }

        gameView.onGameOver = { score, highScore ->
            runOnUiThread {
                tvFinalScore.text = "FINAL SCORE: $score\nHIGH SCORE: $highScore"
                layoutGameOver.visibility = View.VISIBLE
            }
        }

        btnRestart.setOnClickListener {
            layoutGameOver.visibility = View.GONE
            gameView.startNewGame()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupControlListeners() {
        btnLeft.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    gameView.setLeftPressed(true)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    gameView.setLeftPressed(false)
                    true
                }
                else -> false
            }
        }

        btnRight.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    gameView.setRightPressed(true)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    gameView.setRightPressed(false)
                    true
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        gameView.resume()
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
    }
}
