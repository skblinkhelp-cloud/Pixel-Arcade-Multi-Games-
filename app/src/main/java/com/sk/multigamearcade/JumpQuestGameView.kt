package com.sk.multigamearcade

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.content.ContextCompat
import kotlin.math.sin
import kotlin.random.Random

class JumpQuestGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback, Runnable {

    private var gameThread: Thread? = null
    @Volatile
    private var isRunning = false

    // Game state callbacks
    var onScoreChanged: ((score: Int, high: Int, lives: Int) -> Unit)? = null
    var onGameOver: ((score: Int, high: Int) -> Unit)? = null

    // Game Entities & Parameters
    private var screenWidth = 1080f
    private var screenHeight = 1920f

    // Player (Dino Hero)
    private var playerX = 500f
    private var playerY = 1200f
    private var playerVx = 0f
    private var playerVy = 0f
    private val playerWidth = 84f
    private val playerHeight = 84f
    private var lives = 3
    private var score = 0
    private var highScore = 0
    private var invulnerableFrames = 0
    private var jetpackFrames = 0

    // Squash & Stretch visual feedback
    private var scaleX = 1.0f
    private var scaleY = 1.0f

    // Responsive Physics & Auto Jump parameters
    private val gravity = 0.82f
    private val normalJumpVel = -24.5f
    private val springJumpVel = -39f
    private val jetpackVel = -30f
    private val moveSpeed = 15f

    // Input States
    private var isLeftPressed = false
    private var isRightPressed = false

    // Camera & World
    private var cameraY = 0f
    private var highestY = 1200f

    // Lists
    private val platforms = mutableListOf<Platform>()
    private val coins = mutableListOf<Coin>()
    private val powerUps = mutableListOf<PowerUp>()
    private val enemies = mutableListOf<Enemy>()
    private val clouds = mutableListOf<Cloud>()
    private val particles = mutableListOf<Particle>()
    private val scorePopups = mutableListOf<ScorePopup>()

    // Paints & Drawables
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var dinoDrawable: Drawable? = null
    private var beeDrawable: Drawable? = null
    private var cloudDrawable: Drawable? = null

    // Vibrant Arcade Colors
    private val colorBgTop = Color.parseColor("#8E24AA")       // Deep Purple Sky
    private val colorBgBottom = Color.parseColor("#F48FB1")    // Pastel Pink Dawn
    private val colorGrassTop = Color.parseColor("#4CAF50")     // Grass Green
    private val colorGrassHighlight = Color.parseColor("#81C784")
    private val colorDirt = Color.parseColor("#6D4C41")
    private val colorDirtDark = Color.parseColor("#3E2723")

    private var isGameActive = false

    enum class PlatformType {
        NORMAL,
        MOVING,
        SPRING,
        BREAKABLE
    }

    data class Platform(
        var x: Float,
        var y: Float,
        var width: Float,
        val height: Float = 32f,
        val type: PlatformType = PlatformType.NORMAL,
        var vx: Float = 0f,
        var isBroken: Boolean = false,
        var springCompress: Float = 0f
    ) {
        val isMoving: Boolean get() = type == PlatformType.MOVING
    }

    data class Coin(
        var x: Float,
        var y: Float,
        val radius: Float = 18f,
        val isStar: Boolean = false,
        var isCollected: Boolean = false
    )

    enum class PowerUpType {
        JETPACK
    }

    data class PowerUp(
        var x: Float,
        var y: Float,
        val type: PowerUpType,
        var isCollected: Boolean = false
    )

    data class Enemy(
        var x: Float,
        var startY: Float,
        var y: Float,
        var vx: Float,
        val width: Float = 52f,
        val height: Float = 40f,
        var flightPhase: Float = Random.nextFloat() * 6.28f
    )

    data class Cloud(
        var x: Float,
        var y: Float,
        val width: Float,
        val height: Float,
        val speed: Float
    )

    data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var color: Int,
        var size: Float,
        var alpha: Int = 255,
        var life: Int = 22
    )

    data class ScorePopup(
        var text: String,
        var x: Float,
        var y: Float,
        var color: Int,
        var alpha: Int = 255,
        var life: Int = 32
    )

    init {
        holder.addCallback(this)
        dinoDrawable = ContextCompat.getDrawable(context, R.drawable.arcade_dino)
        beeDrawable = ContextCompat.getDrawable(context, R.drawable.arcade_bee)
        cloudDrawable = ContextCompat.getDrawable(context, R.drawable.arcade_pixel_cloud)

        // Decor background clouds
        repeat(7) {
            clouds.add(
                Cloud(
                    x = Random.nextFloat() * 1000f,
                    y = Random.nextFloat() * 2000f - 1000f,
                    width = Random.nextFloat() * 100f + 120f,
                    height = Random.nextFloat() * 40f + 50f,
                    speed = Random.nextFloat() * 0.5f + 0.2f
                )
            )
        }
    }

    fun startNewGame() {
        score = 0
        lives = 3
        playerX = screenWidth / 2f - playerWidth / 2f
        playerY = screenHeight - 350f
        playerVx = 0f
        playerVy = normalJumpVel
        cameraY = 0f
        highestY = playerY
        invulnerableFrames = 0
        jetpackFrames = 0
        scaleX = 1.0f
        scaleY = 1.0f
        isGameActive = true

        generateInitialLevel()
        onScoreChanged?.invoke(score, highScore, lives)
    }

    private fun generateInitialLevel() {
        platforms.clear()
        coins.clear()
        powerUps.clear()
        enemies.clear()
        particles.clear()
        scorePopups.clear()

        // Starting base platform right under dino (sleek, not a full screen board)
        val startPlatWidth = 180f
        val startPlatX = screenWidth / 2f - startPlatWidth / 2f
        platforms.add(Platform(x = startPlatX, y = screenHeight - 240f, width = startPlatWidth, type = PlatformType.NORMAL))

        // Stacked platforms with clean, uncluttered vertical spacing (180f - 240f apart)
        var currentY = screenHeight - 440f
        while (currentY > -3000f) {
            spawnPlatformRow(currentY)
            currentY -= Random.nextFloat() * 60f + 180f
        }
    }

    private fun spawnPlatformRow(y: Float) {
        val platW = Random.nextFloat() * 40f + 120f // Sleek 120f to 160f width
        val platX = Random.nextFloat() * (screenWidth - platW - 80f) + 40f

        // Platform type selection
        val rand = Random.nextFloat()
        val type = when {
            rand < 0.20f -> PlatformType.MOVING
            rand < 0.32f -> PlatformType.SPRING
            rand < 0.42f && y < screenHeight - 1200f -> PlatformType.BREAKABLE
            else -> PlatformType.NORMAL
        }

        val moveVx = if (type == PlatformType.MOVING) (Random.nextFloat() * 2.5f + 1.8f) * (if (Random.nextBoolean()) 1 else -1) else 0f
        val platform = Platform(platX, y, platW, type = type, vx = moveVx)
        platforms.add(platform)

        // Collectibles on platform
        if (type != PlatformType.SPRING && type != PlatformType.BREAKABLE) {
            val cRand = Random.nextFloat()
            if (cRand < 0.50f) {
                // Gold Coin
                coins.add(Coin(platX + platW / 2f, y - 36f, isStar = false))
            } else if (cRand < 0.65f) {
                // Rare Rainbow Star
                coins.add(Coin(platX + platW / 2f, y - 40f, isStar = true))
            } else if (cRand < 0.68f && y < screenHeight - 1500f) {
                // Rare Jetpack Rocket power-up
                powerUps.add(PowerUp(platX + platW / 2f, y - 45f, PowerUpType.JETPACK))
            }
        }

        // Flying Bees (Enemies) - Spawn from LEFT or RIGHT across full screen width!
        if (y < screenHeight - 800f && Random.nextFloat() < 0.12f) {
            val beeX = Random.nextFloat() * (screenWidth - 100f) + 20f
            val beeVx = if (Random.nextBoolean()) (Random.nextFloat() * 1.5f + 2.5f) else -(Random.nextFloat() * 1.5f + 2.5f)
            enemies.add(Enemy(x = beeX, startY = y - 70f, y = y - 70f, vx = beeVx))
        }
    }

    fun setLeftPressed(pressed: Boolean) {
        isLeftPressed = pressed
    }

    fun setRightPressed(pressed: Boolean) {
        isRightPressed = pressed
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        screenWidth = width.toFloat().coerceAtLeast(1080f)
        screenHeight = height.toFloat().coerceAtLeast(1920f)
        startNewGame()
        resume()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenWidth = width.toFloat()
        screenHeight = height.toFloat()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        pause()
    }

    fun resume() {
        if (!isRunning) {
            isRunning = true
            gameThread = Thread(this).apply { start() }
        }
    }

    fun pause() {
        isRunning = false
        try {
            gameThread?.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    override fun run() {
        var lastTime = System.nanoTime()
        val targetNs = 1_000_000_000L / 60

        while (isRunning) {
            val now = System.nanoTime()
            val delta = now - lastTime
            if (delta >= targetNs) {
                lastTime = now
                if (isGameActive) {
                    updateGame()
                }
                drawGame()
            } else {
                try {
                    Thread.sleep(1)
                } catch (_: Exception) {}
            }
        }
    }

    private fun updateGame() {
        // Horizontal Physics & Air Control
        if (isLeftPressed) {
            playerVx = -moveSpeed
        } else if (isRightPressed) {
            playerVx = moveSpeed
        } else {
            playerVx *= 0.82f // Friction
        }

        playerX += playerVx

        // Screen Wrap-around
        if (playerX < -playerWidth / 2f) {
            playerX = screenWidth - playerWidth / 2f
        } else if (playerX > screenWidth - playerWidth / 2f) {
            playerX = -playerWidth / 2f
        }

        // Active Jetpack Rocket Boost
        if (jetpackFrames > 0) {
            jetpackFrames--
            playerVy = jetpackVel
            // Flame particles
            addParticles(playerX + playerWidth / 2f, playerY + playerHeight, Color.parseColor("#FF5722"), 3)
            addParticles(playerX + playerWidth / 2f, playerY + playerHeight, Color.parseColor("#FFEB3B"), 2)
        } else {
            // Apply Normal Gravity
            playerVy += gravity
        }

        val nextY = playerY + playerVy

        // AUTOMATIC JUMP / BOUNCE MECHANIC:
        // When dino is falling down (playerVy > 0) and feet land on a platform top
        if (playerVy > 0f && jetpackFrames == 0) {
            val playerBottom = playerY + playerHeight
            val nextPlayerBottom = nextY + playerHeight

            for (plat in platforms) {
                if (!plat.isBroken) {
                    val platTop = plat.y
                    // Horizontal overlap check
                    if (playerX + playerWidth - 12f > plat.x && playerX + 12f < plat.x + plat.width) {
                        // Vertical crossing check
                        if (playerBottom <= platTop + 20f && nextPlayerBottom >= platTop) {
                            // Land on Platform! Auto-bounce!
                            playerY = platTop - playerHeight

                            when (plat.type) {
                                PlatformType.SPRING -> {
                                    playerVy = springJumpVel
                                    plat.springCompress = 16f
                                    addParticles(plat.x + plat.width / 2f, platTop, Color.parseColor("#FFD700"), 12)
                                    addScorePopup("SUPER JUMP! ⭐", playerX, playerY - 30f, Color.parseColor("#FFD700"))
                                }
                                PlatformType.BREAKABLE -> {
                                    playerVy = normalJumpVel
                                    plat.isBroken = true
                                    addParticles(plat.x + plat.width / 2f, platTop, Color.parseColor("#8D6E63"), 14)
                                }
                                else -> {
                                    playerVy = normalJumpVel
                                    addParticles(playerX + playerWidth / 2f, playerY + playerHeight, Color.parseColor("#88E052"), 6)
                                }
                            }

                            // Juice: Squish on impact
                            scaleX = 1.25f
                            scaleY = 0.75f
                            break
                        }
                    }
                }
            }
        }

        playerY += playerVy

        // Stretch visual recovery
        scaleX += (1.0f - scaleX) * 0.15f
        scaleY += (1.0f - scaleY) * 0.15f

        // Camera Follows Player Upward
        if (playerY < highestY) {
            val diff = highestY - playerY
            score += (diff / 8).toInt()
            if (score > highScore) highScore = score
            highestY = playerY
            onScoreChanged?.invoke(score, highScore, lives)
        }

        // Smooth Camera Shift
        val targetCamY = highestY - screenHeight * 0.55f
        cameraY += (targetCamY - cameraY) * 0.12f

        // Update Background Clouds
        for (c in clouds) {
            c.x += c.speed
            if (c.x > screenWidth + 100f) {
                c.x = -200f
            }
        }

        // Update Platforms
        for (plat in platforms) {
            if (plat.isMoving) {
                plat.x += plat.vx
                if (plat.x < 20f || plat.x + plat.width > screenWidth - 20f) {
                    plat.vx = -plat.vx
                }
            }
            if (plat.springCompress > 0f) {
                plat.springCompress *= 0.8f
            }
        }

        // Update Bees (Enemies) - Sine wave flying movement across both left & right sides
        for (enemy in enemies) {
            enemy.flightPhase += 0.08f
            enemy.y = enemy.startY + sin(enemy.flightPhase) * 16f
            enemy.x += enemy.vx

            // Screen boundary bounce
            if (enemy.x < 10f || enemy.x + enemy.width > screenWidth - 10f) {
                enemy.vx = -enemy.vx
            }
        }

        // Recycle offscreen platforms, coins, enemies & spawn new ones above
        val lowestVisibleY = cameraY + screenHeight + 250f
        platforms.removeAll { it.y > lowestVisibleY || it.isBroken }
        coins.removeAll { it.y > lowestVisibleY || it.isCollected }
        powerUps.removeAll { it.y > lowestVisibleY || it.isCollected }
        enemies.removeAll { it.y > lowestVisibleY }

        var minY = platforms.minOfOrNull { it.y } ?: (playerY - 200f)
        while (minY > cameraY - 800f) {
            minY -= Random.nextFloat() * 60f + 180f
            spawnPlatformRow(minY)
        }

        // Check Coin Collectibles
        for (coin in coins) {
            if (!coin.isCollected) {
                val dx = (playerX + playerWidth / 2f) - coin.x
                val dy = (playerY + playerHeight / 2f) - coin.y
                if (dx * dx + dy * dy < 55f * 55f) {
                    coin.isCollected = true
                    val points = if (coin.isStar) 300 else 100
                    score += points
                    if (score > highScore) highScore = score
                    onScoreChanged?.invoke(score, highScore, lives)

                    val popText = if (coin.isStar) "+300 STAR!" else "+100"
                    val popColor = if (coin.isStar) Color.parseColor("#FFD700") else Color.parseColor("#FFEB3B")
                    addScorePopup(popText, coin.x, coin.y, popColor)
                    addParticles(coin.x, coin.y, popColor, 10)
                }
            }
        }

        // Check PowerUp Collectibles
        for (pu in powerUps) {
            if (!pu.isCollected) {
                val dx = (playerX + playerWidth / 2f) - pu.x
                val dy = (playerY + playerHeight / 2f) - pu.y
                if (dx * dx + dy * dy < 60f * 60f) {
                    pu.isCollected = true
                    jetpackFrames = 120 // 2 seconds rocket boost!
                    addScorePopup("ROCKET BOOST! 🚀", playerX, playerY - 40f, Color.parseColor("#FF5722"))
                    addParticles(pu.x, pu.y, Color.parseColor("#FF5722"), 16)
                }
            }
        }

        // Check Invulnerability & Enemy Collision
        if (invulnerableFrames > 0) {
            invulnerableFrames--
        } else if (jetpackFrames == 0) {
            for (enemy in enemies) {
                val pRect = RectF(playerX + 12f, playerY + 12f, playerX + playerWidth - 12f, playerY + playerHeight - 12f)
                val eRect = RectF(enemy.x + 6f, enemy.y + 6f, enemy.x + enemy.width - 6f, enemy.y + enemy.height - 6f)
                if (RectF.intersects(pRect, eRect)) {
                    lives--
                    invulnerableFrames = 75 // 1.25s invulnerability
                    playerVy = -18f // Bounce back on hit
                    onScoreChanged?.invoke(score, highScore, lives)
                    addParticles(playerX + playerWidth / 2f, playerY + playerHeight / 2f, Color.parseColor("#FF1744"), 15)

                    if (lives <= 0) {
                        isGameActive = false
                        onGameOver?.invoke(score, highScore)
                        break
                    }
                }
            }
        }

        // Check Fall Off Screen (Game Over)
        if (playerY > cameraY + screenHeight + 120f) {
            lives = 0
            isGameActive = false
            onGameOver?.invoke(score, highScore)
        }

        // Update Particles
        val pIter = particles.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            p.x += p.vx
            p.y += p.vy
            p.alpha -= 10
            p.life--
            if (p.life <= 0 || p.alpha <= 0) {
                pIter.remove()
            }
        }

        // Update Score Popups
        val popIter = scorePopups.iterator()
        while (popIter.hasNext()) {
            val pop = popIter.next()
            pop.y -= 2.0f
            pop.alpha -= 8
            pop.life--
            if (pop.life <= 0 || pop.alpha <= 0) {
                popIter.remove()
            }
        }
    }

    private fun addParticles(x: Float, y: Float, color: Int, count: Int) {
        repeat(count) {
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = (Random.nextFloat() - 0.5f) * 10f,
                    vy = (Random.nextFloat() - 0.5f) * 10f,
                    color = color,
                    size = Random.nextFloat() * 6f + 6f
                )
            )
        }
    }

    private fun addScorePopup(text: String, x: Float, y: Float, color: Int) {
        scorePopups.add(ScorePopup(text, x, y, color))
    }

    private fun drawGame() {
        val canvas = holder.lockCanvas() ?: return

        try {
            // 1. Draw Jump Quest Sky Background
            paint.color = colorBgTop
            canvas.drawRect(0f, 0f, screenWidth, screenHeight, paint)

            paint.color = colorBgBottom
            paint.alpha = 180
            canvas.drawRect(0f, screenHeight * 0.35f, screenWidth, screenHeight, paint)
            paint.alpha = 255

            // Save and Translate Camera
            canvas.save()
            canvas.translate(0f, -cameraY)

            // 2. Draw Clouds
            for (cloud in clouds) {
                cloudDrawable?.let {
                    it.setBounds(cloud.x.toInt(), cloud.y.toInt(), (cloud.x + cloud.width).toInt(), (cloud.y + cloud.height).toInt())
                    it.alpha = 180
                    it.draw(canvas)
                }
            }

            // 3. Draw Platforms
            for (plat in platforms) {
                if (plat.isBroken) continue

                val pX = plat.x
                val pY = plat.y
                val pW = plat.width
                val pH = plat.height

                when (plat.type) {
                    PlatformType.SPRING -> {
                        // Spring platform body
                        paint.color = Color.parseColor("#E65100")
                        canvas.drawRoundRect(pX, pY + 10f, pX + pW, pY + pH, 6f, 6f, paint)

                        paint.color = Color.parseColor("#FFB300")
                        canvas.drawRoundRect(pX, pY, pX + pW, pY + 14f, 6f, 6f, paint)

                        // Spring coils on top
                        val compress = plat.springCompress
                        paint.color = Color.parseColor("#FF1744")
                        val springX = pX + pW / 2f - 18f
                        val springY = pY - 18f + compress
                        canvas.drawRoundRect(springX, springY, springX + 36f, pY + 4f, 4f, 4f, paint)
                    }
                    PlatformType.MOVING -> {
                        // Cyan Blue Moving Platform
                        paint.color = Color.parseColor("#01579B")
                        canvas.drawRoundRect(pX, pY + 10f, pX + pW, pY + pH, 6f, 6f, paint)

                        paint.color = Color.parseColor("#0288D1")
                        canvas.drawRoundRect(pX, pY, pX + pW, pY + 14f, 6f, 6f, paint)

                        paint.color = Color.parseColor("#80DEEA")
                        canvas.drawRect(pX + 6f, pY + 2f, pX + pW - 6f, pY + 6f, paint)
                    }
                    PlatformType.BREAKABLE -> {
                        // Crag Brown Breakable Platform
                        paint.color = Color.parseColor("#4E342E")
                        canvas.drawRoundRect(pX, pY + 8f, pX + pW, pY + pH, 4f, 4f, paint)

                        paint.color = Color.parseColor("#8D6E63")
                        canvas.drawRoundRect(pX, pY, pX + pW, pY + 12f, 4f, 4f, paint)

                        // Crack lines
                        paint.color = Color.parseColor("#3E2723")
                        canvas.drawLine(pX + pW * 0.3f, pY + 2f, pX + pW * 0.45f, pY + 12f, paint)
                        canvas.drawLine(pX + pW * 0.6f, pY + 2f, pX + pW * 0.7f, pY + 10f, paint)
                    }
                    else -> {
                        // Normal Grass Platform
                        paint.color = colorDirtDark
                        canvas.drawRoundRect(pX, pY + 10f, pX + pW, pY + pH, 6f, 6f, paint)

                        paint.color = colorDirt
                        canvas.drawRoundRect(pX, pY + 8f, pX + pW, pY + pH - 2f, 6f, 6f, paint)

                        paint.color = colorGrassTop
                        canvas.drawRoundRect(pX, pY, pX + pW, pY + 14f, 6f, 6f, paint)

                        paint.color = colorGrassHighlight
                        canvas.drawRect(pX + 6f, pY + 2f, pX + pW - 6f, pY + 6f, paint)
                    }
                }
            }

            // 4. Draw Coins & Rainbow Stars
            for (coin in coins) {
                if (!coin.isCollected) {
                    if (coin.isStar) {
                        // Star
                        paint.color = Color.parseColor("#FFD700")
                        canvas.drawCircle(coin.x, coin.y, coin.radius + 3f, paint)

                        paint.color = Color.parseColor("#FFF59D")
                        canvas.drawCircle(coin.x, coin.y, coin.radius, paint)
                    } else {
                        // Gold Coin
                        paint.color = Color.parseColor("#E65100")
                        canvas.drawCircle(coin.x, coin.y, coin.radius + 2f, paint)

                        paint.color = Color.parseColor("#FFD700")
                        canvas.drawCircle(coin.x, coin.y, coin.radius, paint)

                        paint.color = Color.parseColor("#FFF59D")
                        canvas.drawCircle(coin.x - 4f, coin.y - 4f, coin.radius * 0.4f, paint)
                    }
                }
            }

            // 5. Draw PowerUps (Jetpack Rocket)
            for (pu in powerUps) {
                if (!pu.isCollected) {
                    paint.color = Color.parseColor("#FF5722")
                    canvas.drawRoundRect(pu.x - 14f, pu.y - 20f, pu.x + 14f, pu.y + 20f, 6f, 6f, paint)

                    paint.color = Color.parseColor("#FFEB3B")
                    canvas.drawRect(pu.x - 10f, pu.y - 12f, pu.x + 10f, pu.y + 4f, paint)
                }
            }

            // 6. Draw Flying Bees (Enemies) - Flying smoothly facing left or right
            for (enemy in enemies) {
                beeDrawable?.let {
                    canvas.save()
                    // Flip bee horizontally if moving left so it faces left!
                    if (enemy.vx < 0) {
                        canvas.scale(-1f, 1f, enemy.x + enemy.width / 2f, enemy.y + enemy.height / 2f)
                    }
                    it.setBounds(enemy.x.toInt(), enemy.y.toInt(), (enemy.x + enemy.width).toInt(), (enemy.y + enemy.height).toInt())
                    it.draw(canvas)
                    canvas.restore()
                }
            }

            // 7. Draw Particles
            for (p in particles) {
                paint.color = p.color
                paint.alpha = p.alpha.coerceIn(0, 255)
                canvas.drawCircle(p.x, p.y, p.size, paint)
            }
            paint.alpha = 255

            // 8. Draw Dino Hero with Squash & Stretch
            if (invulnerableFrames % 6 < 3) {
                canvas.save()
                val centerX = playerX + playerWidth / 2f
                val centerY = playerY + playerHeight / 2f
                canvas.scale(scaleX, scaleY, centerX, centerY)

                dinoDrawable?.let {
                    // Mirror dino horizontally when moving left
                    if (playerVx < -1f) {
                        canvas.scale(-1f, 1f, centerX, centerY)
                    }
                    it.setBounds(playerX.toInt(), playerY.toInt(), (playerX + playerWidth).toInt(), (playerY + playerHeight).toInt())
                    it.draw(canvas)
                } ?: run {
                    paint.color = colorGrassTop
                    canvas.drawRect(playerX, playerY, playerX + playerWidth, playerY + playerHeight, paint)
                }

                // Rocket flame effect when jetpack active
                if (jetpackFrames > 0) {
                    paint.color = Color.parseColor("#FF5722")
                    canvas.drawRect(playerX + 20f, playerY + playerHeight, playerX + playerWidth - 20f, playerY + playerHeight + 25f, paint)
                }

                canvas.restore()
            }

            // 9. Draw Floating Score Popups
            for (pop in scorePopups) {
                paint.color = pop.color
                paint.alpha = pop.alpha.coerceIn(0, 255)
                paint.textSize = 34f
                paint.isFakeBoldText = true
                canvas.drawText(pop.text, pop.x - 40f, pop.y, paint)
            }
            paint.alpha = 255

            canvas.restore()

        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                performClick()
                if (event.x < width / 2f) {
                    isLeftPressed = true
                    isRightPressed = false
                } else {
                    isLeftPressed = false
                    isRightPressed = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isLeftPressed = false
                isRightPressed = false
            }
        }
        return true
    }
}
