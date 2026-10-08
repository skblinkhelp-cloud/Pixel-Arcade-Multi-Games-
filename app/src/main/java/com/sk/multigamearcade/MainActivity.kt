package com.sk.multigamearcade

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    private lateinit var gameScrollView: ScrollView
    private lateinit var gameGrid: GridLayout

    private lateinit var btnHomeTop: ImageButton
    private lateinit var btnGamesTop: ImageButton
    private lateinit var btnFavoritesTop: ImageButton
    private lateinit var btnSettingsTop: ImageButton

    // Shared Preferences & Persistence
    private val prefs by lazy { getSharedPreferences("PixelArcadeLauncherPrefs", Context.MODE_PRIVATE) }
    private val favoritesSet = mutableSetOf<String>()

    private var soundEnabled = true
    private var vibrationEnabled = true
    private var currentFilter = "ALL" // ALL, RACING, ACTION, PUZZLE, FAVORITES

    // Game Definitions
    private data class GameItem(
        val viewId: Int,
        val title: String,
        val category: String, // RACING, ACTION, PUZZLE, PLATFORMER
        val activityClass: Class<*>
    )

    private val allGames = listOf(
        GameItem(R.id.gamePixelMoto, "Pixel Moto Rider", "RACING", PixelMotoActivity::class.java),
        GameItem(R.id.gameJigsawPuzzle, "Farm Jigsaw Puzzle", "PUZZLE", JigsawActivity::class.java),
        GameItem(R.id.gameJumpQuest, "Jump Quest", "PLATFORMER", JumpQuestActivity::class.java),
        GameItem(R.id.gameSpeedRush, "Pixel Car Rush", "RACING", CarSpeedRushActivity::class.java),
        GameItem(R.id.gameSpaceBlast, "Space Blast", "ACTION", SpaceBlastActivity::class.java),
        GameItem(R.id.gameSpeedJump, "Speed Jump", "RACING", SpeedRushActivity::class.java),
        GameItem(R.id.gameSlot7, "Puzzle Pop", "PUZZLE", PuzzlePopNewActivity::class.java),
        GameItem(R.id.gamePixelSnake, "Pixel Snake Arcade", "ACTION", PixelSnakeActivity::class.java)
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        soundEnabled = prefs.getBoolean("SOUND_ENABLED", true)
        vibrationEnabled = prefs.getBoolean("VIBRATION_ENABLED", true)
        favoritesSet.addAll(prefs.getStringSet("FAVORITES_SET", emptySet()) ?: emptySet())

        gameScrollView = findViewById(R.id.gameScrollView)
        gameGrid = findViewById(R.id.gameGrid)

        btnHomeTop = findViewById(R.id.btnHomeTop)
        btnGamesTop = findViewById(R.id.btnGamesTop)
        btnFavoritesTop = findViewById(R.id.btnFavoritesTop)
        btnSettingsTop = findViewById(R.id.btnSettingsTop)

        setupTopNavButtons()
        setupGameCardListeners()

        findViewById<ImageView>(R.id.ivPlayDiscoverCard)?.setOnClickListener {
            playArcadeClickSound()
            triggerHapticFeedback()
            startActivity(Intent(this, CarSpeedRushActivity::class.java))
        }

        // Coming Soon Slots
        val comingSoonIds = listOf(
            R.id.gameSlot9,
            R.id.gameSlot10,
            R.id.gameSlot11,
            R.id.gameSlot12
        )

        comingSoonIds.forEach { id ->
            findViewById<View>(id)?.setOnClickListener {
                playArcadeClickSound()
                triggerHapticFeedback()
                Toast.makeText(this, "🔒 Slot Coming Soon in Next Update!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupTopNavButtons() {
        // 1. HOME BUTTON
        btnHomeTop.setOnClickListener {
            playArcadeClickSound()
            triggerHapticFeedback()
            currentFilter = "ALL"
            applyGameFilter("ALL")
            gameScrollView.smoothScrollTo(0, 0)
            Toast.makeText(this, "🏠 Pixel Arcade Home", Toast.LENGTH_SHORT).show()
        }

        // 2. GAMES CATEGORY BUTTON
        btnGamesTop.setOnClickListener {
            playArcadeClickSound()
            triggerHapticFeedback()
            showGamesCategoryDialog()
        }

        // 3. FAVORITES BUTTON
        btnFavoritesTop.setOnClickListener {
            playArcadeClickSound()
            triggerHapticFeedback()
            showFavoritesDialog()
        }

        // 4. SETTINGS BUTTON
        btnSettingsTop.setOnClickListener {
            playArcadeClickSound()
            triggerHapticFeedback()
            showSettingsDialog()
        }
    }

    private fun setupGameCardListeners() {
        allGames.forEach { game ->
            val cardView = findViewById<View>(game.viewId)
            cardView?.setOnClickListener {
                playArcadeClickSound()
                triggerHapticFeedback()
                startActivity(Intent(this, game.activityClass))
            }

            // Long Press to Bookmark / Favorite!
            cardView?.setOnLongClickListener {
                triggerHapticFeedback()
                toggleFavoriteGame(game.title)
                true
            }
        }
    }

    private fun toggleFavoriteGame(gameTitle: String) {
        if (favoritesSet.contains(gameTitle)) {
            favoritesSet.remove(gameTitle)
            Toast.makeText(this, "💔 Removed '$gameTitle' from Favorites", Toast.LENGTH_SHORT).show()
        } else {
            favoritesSet.add(gameTitle)
            Toast.makeText(this, "⭐ Saved '$gameTitle' to Favorites!", Toast.LENGTH_SHORT).show()
        }
        prefs.edit().putStringSet("FAVORITES_SET", favoritesSet).apply()

        if (currentFilter == "FAVORITES") {
            applyGameFilter("FAVORITES")
        }
    }

    private fun applyGameFilter(filter: String) {
        currentFilter = filter
        allGames.forEach { game ->
            val view = findViewById<View>(game.viewId)
            if (view != null) {
                val matches = when (filter) {
                    "ALL" -> true
                    "FAVORITES" -> favoritesSet.contains(game.title)
                    else -> game.category == filter
                }
                view.visibility = if (matches) View.VISIBLE else View.GONE
            }
        }
    }

    // ==========================================
    // DIALOG 1: GAMES CATEGORY FILTER (GAMES BUTTON)
    // ==========================================
    @SuppressLint("SetTextI18n")
    private fun showGamesCategoryDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#121629"))
            elevation = 16f
        }

        val title = TextView(this).apply {
            text = "🎮 PIXEL ARCADE CATEGORIES"
            setTextColor(Color.parseColor("#FFCC00"))
            textSize = 18f
            setPadding(0, 0, 0, 32)
            gravity = Gravity.CENTER
        }
        layout.addView(title)

        val categories = listOf(
            "ALL" to "🕹️ ALL GAMES",
            "RACING" to "🏎️ RACING & SPEED",
            "ACTION" to "🚀 ACTION & SPACE",
            "PUZZLE" to "🧩 PUZZLE & JIGSAW",
            "PLATFORMER" to "🏃 PLATFORMER JUMP"
        )

        categories.forEach { (catKey, catLabel) ->
            val btn = Button(this).apply {
                text = catLabel
                setTextColor(if (currentFilter == catKey) Color.BLACK else Color.WHITE)
                setBackgroundColor(if (currentFilter == catKey) Color.parseColor("#FFCC00") else Color.parseColor("#1E2542"))
                setOnClickListener {
                    playArcadeClickSound()
                    triggerHapticFeedback()
                    applyGameFilter(catKey)
                    Toast.makeText(this@MainActivity, "Filter: $catLabel", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 8) }
            layout.addView(btn, params)
        }

        dialog.setContentView(layout)
        dialog.show()
    }

    // ==========================================
    // DIALOG 2: FAVORITES MANAGER (FAVORITES BUTTON)
    // ==========================================
    @SuppressLint("SetTextI18n")
    private fun showFavoritesDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#121629"))
            elevation = 16f
        }

        val title = TextView(this).apply {
            text = "⭐ FAVORITE GAMES"
            setTextColor(Color.parseColor("#FFEA00"))
            textSize = 18f
            setPadding(0, 0, 0, 24)
            gravity = Gravity.CENTER
        }
        layout.addView(title)

        if (favoritesSet.isEmpty()) {
            val emptyTxt = TextView(this).apply {
                text = "No favorite games bookmarked yet!\n\n💡 Tip: Long-press any game card in Pixel Arcade to save it as a Favorite!"
                setTextColor(Color.LTGRAY)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 24)
            }
            layout.addView(emptyTxt)
        } else {
            allGames.filter { favoritesSet.contains(it.title) }.forEach { game ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(12, 12, 12, 12)
                    gravity = Gravity.CENTER_VERTICAL
                }

                val gameTxt = TextView(this).apply {
                    text = "⭐ ${game.title}"
                    setTextColor(Color.WHITE)
                    textSize = 15f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val playBtn = Button(this).apply {
                    text = "PLAY 🏁"
                    setTextColor(Color.BLACK)
                    setBackgroundColor(Color.parseColor("#00E5FF"))
                    setOnClickListener {
                        playArcadeClickSound()
                        triggerHapticFeedback()
                        dialog.dismiss()
                        startActivity(Intent(this@MainActivity, game.activityClass))
                    }
                }

                row.addView(gameTxt)
                row.addView(playBtn)
                layout.addView(row)
            }
        }

        val filterBtn = Button(this).apply {
            text = if (currentFilter == "FAVORITES") "SHOW ALL GAMES" else "FILTER BY FAVORITES ONLY"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#FFCC00"))
            setOnClickListener {
                playArcadeClickSound()
                triggerHapticFeedback()
                if (currentFilter == "FAVORITES") {
                    applyGameFilter("ALL")
                } else {
                    applyGameFilter("FAVORITES")
                }
                dialog.dismiss()
            }
        }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 24, 0, 0)
        }
        layout.addView(filterBtn, params)

        dialog.setContentView(layout)
        dialog.show()
    }

    // ==========================================
    // DIALOG 3: ARCADE SETTINGS (SETTINGS BUTTON)
    // ==========================================
    @SuppressLint("SetTextI18n")
    private fun showSettingsDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#121629"))
            elevation = 16f
        }

        val title = TextView(this).apply {
            text = "⚙️ ARCADE LAUNCHER SETTINGS"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 18f
            setPadding(0, 0, 0, 24)
            gravity = Gravity.CENTER
        }
        layout.addView(title)

        // Sound FX Toggle
        val soundBtn = Button(this).apply {
            text = "🔊 ARCADE SOUND FX: " + if (soundEnabled) "ON ✅" else "OFF ❌"
            setTextColor(Color.WHITE)
            setBackgroundColor(if (soundEnabled) Color.parseColor("#1E2542") else Color.parseColor("#3B1824"))
            setOnClickListener {
                playArcadeClickSound()
                triggerHapticFeedback()
                soundEnabled = !soundEnabled
                prefs.edit().putBoolean("SOUND_ENABLED", soundEnabled).apply()
                text = "🔊 ARCADE SOUND FX: " + if (soundEnabled) "ON ✅" else "OFF ❌"
            }
        }
        layout.addView(soundBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 8, 0, 8) })

        // Vibration Toggle
        val vibBtn = Button(this).apply {
            text = "📳 HAPTIC VIBRATION: " + if (vibrationEnabled) "ON ✅" else "OFF ❌"
            setTextColor(Color.WHITE)
            setBackgroundColor(if (vibrationEnabled) Color.parseColor("#1E2542") else Color.parseColor("#3B1824"))
            setOnClickListener {
                playArcadeClickSound()
                triggerHapticFeedback()
                vibrationEnabled = !vibrationEnabled
                prefs.edit().putBoolean("VIBRATION_ENABLED", vibrationEnabled).apply()
                text = "📳 HAPTIC VIBRATION: " + if (vibrationEnabled) "ON ✅" else "OFF ❌"
            }
        }
        layout.addView(vibBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 8, 0, 8) })

        // Reset Favorites
        val resetBtn = Button(this).apply {
            text = "🗑️ CLEAR ALL FAVORITES"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#8B0000"))
            setOnClickListener {
                playArcadeClickSound()
                triggerHapticFeedback()
                favoritesSet.clear()
                prefs.edit().remove("FAVORITES_SET").apply()
                Toast.makeText(this@MainActivity, "Cleared All Favorite Games!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        layout.addView(resetBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 8, 0, 16) })

        // Close
        val closeBtn = Button(this).apply {
            text = "CLOSE"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#FFCC00"))
            setOnClickListener {
                playArcadeClickSound()
                triggerHapticFeedback()
                dialog.dismiss()
            }
        }
        layout.addView(closeBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        dialog.setContentView(layout)
        dialog.show()
    }

    // ==========================================
    // AUDIO SYNTHESIZER & HAPTIC VIBRATION
    // ==========================================
    private fun playArcadeClickSound() {
        if (!soundEnabled) return
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val sampleRate = 22050
                val durationMs = 40
                val numSamples = durationMs * sampleRate / 1000
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val progress = i.toFloat() / numSamples
                    val freq = 800f - progress * 400f
                    val angle = 2.0 * Math.PI * freq * i / sampleRate
                    buffer[i] = (sin(angle) * (1.0f - progress) * 0.25f * Short.MAX_VALUE).toInt().toShort()
                }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(numSamples * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, numSamples)
                track.play()
                Thread.sleep(durationMs.toLong() + 20)
                track.stop()
                track.release()
            } catch (_: Exception) {}
        }
    }

    private fun triggerHapticFeedback() {
        if (!vibrationEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(25)
                }
            }
        } catch (_: Exception) {}
    }
}
