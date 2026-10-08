package com.sk.multigamearcade.speedrush

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sk.multigamearcade.R
import kotlinx.coroutines.isActive
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// --- Game Constants & Colors ---
private val ColorSkyTop = Color(0xFF170B20)
private val ColorSkyBottom = Color(0xFF381452)
private val ColorGridNeon = Color(0xFF00E5FF)
private val ColorGroundDark = Color(0xFF10051A)
private val ColorAccentPink = Color(0xFFFF007F)
private val ColorAccentCyan = Color(0xFF00FFCC)
private val ColorAccentYellow = Color(0xFFFFE500)
private val ColorAccentPurple = Color(0xFF9D4EDD)

enum class GameState {
    START,
    PLAYING,
    GAME_OVER
}

enum class ObstacleType {
    LOW_HURDLE,
    HIGH_BARRIER,
    DOUBLE_HURDLE,
    FLYING_DRONE,
    COIN_BOX
}

data class Obstacle(
    val id: String = UUID.randomUUID().toString(),
    val type: ObstacleType,
    var x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    var isPassed: Boolean = false,
    var isCollected: Boolean = false
)

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    var size: Float,
    var alpha: Float = 1.0f,
    var life: Float = 1.0f
)

data class FloatingText(
    val text: String,
    var x: Float,
    var y: Float,
    val color: Color,
    var alpha: Float = 1.0f,
    var vy: Float = -60f
)

@Composable
fun SpeedRushGameScreen(
    onExitGame: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("SpeedRushPrefs", Context.MODE_PRIVATE) }
    var highScore by remember { mutableIntStateOf(sharedPrefs.getInt("HIGH_SCORE", 0)) }

    var gameState by remember { mutableStateOf(GameState.START) }
    var score by remember { mutableFloatStateOf(0f) }
    var coinsCollected by remember { mutableIntStateOf(0) }
    var isNewHighScore by remember { mutableStateOf(false) }

    // Speed mechanics: 10% increase every 5 seconds
    var elapsedTime by remember { mutableFloatStateOf(0f) }
    var speedMultiplier by remember { mutableFloatStateOf(1.0f) }
    var speedUpAnnouncement by remember { mutableStateOf<String?>(null) }
    var speedUpAlpha by remember { mutableFloatStateOf(0f) }
    var lastSpeedLevel by remember { mutableIntStateOf(0) }

    // Screen shake on collision
    var screenShakeOffset by remember { mutableStateOf(Offset.Zero) }
    var shakeDuration by remember { mutableFloatStateOf(0f) }

    // Player State
    var playerY by remember { mutableFloatStateOf(0f) }
    var playerVy by remember { mutableFloatStateOf(0f) }
    var isGrounded by remember { mutableStateOf(true) }
    var canDoubleJump by remember { mutableStateOf(true) }
    var jumpStretch by remember { mutableFloatStateOf(1.0f) }
    var playerAnimFrame by remember { mutableFloatStateOf(0f) }

    // Game Lists
    val obstacles = remember { mutableStateListOf<Obstacle>() }
    val particles = remember { mutableStateListOf<Particle>() }
    val floatingTexts = remember { mutableStateListOf<FloatingText>() }

    // Ground scroll position for grid
    var groundScrollX by remember { mutableFloatStateOf(0f) }
    var cloudScrollX by remember { mutableFloatStateOf(0f) }

    // Spawning timer
    var spawnTimer by remember { mutableFloatStateOf(0f) }

    // Drawables
    val dinoPainter = rememberVectorPainter(ImageVector.vectorResource(R.drawable.arcade_dino))
    val beePainter = rememberVectorPainter(ImageVector.vectorResource(R.drawable.arcade_bee))
    val cloudPainter = rememberVectorPainter(ImageVector.vectorResource(R.drawable.arcade_pixel_cloud))

    // Reset Game Function
    fun startNewGame() {
        score = 0f
        coinsCollected = 0
        elapsedTime = 0f
        speedMultiplier = 1.0f
        lastSpeedLevel = 0
        speedUpAnnouncement = null
        speedUpAlpha = 0f
        isNewHighScore = false
        playerY = 0f
        playerVy = 0f
        isGrounded = true
        canDoubleJump = true
        jumpStretch = 1.0f
        obstacles.clear()
        particles.clear()
        floatingTexts.clear()
        spawnTimer = 0.5f
        gameState = GameState.PLAYING
    }

    // Jump Handler
    fun triggerJump() {
        if (gameState != GameState.PLAYING) return

        if (isGrounded) {
            playerVy = -1150f
            isGrounded = false
            canDoubleJump = true
            jumpStretch = 1.3f
            // Jump dust particles
            repeat(10) {
                particles.add(
                    Particle(
                        x = 130f,
                        y = playerY,
                        vx = Random.nextFloat() * -180f - 40f,
                        vy = Random.nextFloat() * -100f - 20f,
                        color = Color.White.copy(alpha = 0.8f),
                        size = Random.nextFloat() * 10f + 6f
                    )
                )
            }
        } else if (canDoubleJump) {
            playerVy = -950f
            canDoubleJump = false
            jumpStretch = 1.4f
            // Double jump burst particles
            repeat(12) {
                particles.add(
                    Particle(
                        x = 130f,
                        y = playerY + 30f,
                        vx = (Random.nextFloat() - 0.5f) * 200f,
                        vy = Random.nextFloat() * 150f + 50f,
                        color = ColorAccentCyan,
                        size = Random.nextFloat() * 12f + 6f
                    )
                )
            }
        }
    }

    // Main Smooth Frame Game Loop (60 FPS / 120 FPS capable)
    LaunchedEffect(gameState) {
        if (gameState != GameState.PLAYING) return@LaunchedEffect

        var lastFrameNanos = System.nanoTime()

        while (isActive && gameState == GameState.PLAYING) {
            withFrameNanos { currentFrameNanos ->
                val dt = ((currentFrameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.04f)
                lastFrameNanos = currentFrameNanos

                // --- 1. Speed Progression (10% increase every 5 seconds) ---
                elapsedTime += dt
                val currentLevel = (elapsedTime / 5.0f).toInt()
                speedMultiplier = 1.10f.pow(currentLevel.toFloat())

                if (currentLevel > lastSpeedLevel) {
                    lastSpeedLevel = currentLevel
                    speedUpAnnouncement = "⚡ SPEED UP! ${(speedMultiplier * 100).toInt()}% ⚡"
                    speedUpAlpha = 1.0f
                    // Speed particle burst
                    repeat(20) {
                        particles.add(
                            Particle(
                                x = Random.nextFloat() * 1000f,
                                y = Random.nextFloat() * 600f,
                                vx = -800f * speedMultiplier,
                                vy = (Random.nextFloat() - 0.5f) * 100f,
                                color = ColorAccentCyan,
                                size = Random.nextFloat() * 12f + 4f
                            )
                        )
                    }
                }

                if (speedUpAlpha > 0f) {
                    speedUpAlpha = (speedUpAlpha - dt * 0.8f).coerceAtLeast(0f)
                }

                // Increase score
                score += dt * 15f * speedMultiplier

                // --- 2. Player Physics ---
                val gravity = 3200f
                playerVy += gravity * dt
                playerY += playerVy * dt

                // Ground plane at y = 0
                if (playerY >= 0f) {
                    if (!isGrounded) {
                        // Landing impact
                        jumpStretch = 0.75f
                        repeat(8) {
                            particles.add(
                                Particle(
                                    x = 130f + (Random.nextFloat() - 0.5f) * 40f,
                                    y = 0f,
                                    vx = (Random.nextFloat() - 0.5f) * 150f,
                                    vy = -Random.nextFloat() * 100f - 20f,
                                    color = Color(0xFFD080FF),
                                    size = Random.nextFloat() * 8f + 4f
                                )
                            )
                        }
                    }
                    playerY = 0f
                    playerVy = 0f
                    isGrounded = true
                }

                // Recover stretch shape
                jumpStretch += (1.0f - jumpStretch) * dt * 10f
                playerAnimFrame += dt * 12f * speedMultiplier

                // --- 3. Background Scrolling ---
                val currentBaseSpeed = 550f * speedMultiplier
                groundScrollX = (groundScrollX + currentBaseSpeed * dt) % 80f
                cloudScrollX = (cloudScrollX + currentBaseSpeed * 0.2f * dt) % 1000f

                // --- 4. Obstacle Spawning & Movement ---
                spawnTimer -= dt
                if (spawnTimer <= 0f) {
                    val rand = Random.nextFloat()
                    val type = when {
                        rand < 0.35f -> ObstacleType.LOW_HURDLE
                        rand < 0.60f -> ObstacleType.HIGH_BARRIER
                        rand < 0.78f -> ObstacleType.DOUBLE_HURDLE
                        rand < 0.90f -> ObstacleType.FLYING_DRONE
                        else -> ObstacleType.COIN_BOX
                    }

                    val obsWidth = when (type) {
                        ObstacleType.LOW_HURDLE -> 55f
                        ObstacleType.HIGH_BARRIER -> 65f
                        ObstacleType.DOUBLE_HURDLE -> 110f
                        ObstacleType.FLYING_DRONE -> 70f
                        ObstacleType.COIN_BOX -> 50f
                    }

                    val obsHeight = when (type) {
                        ObstacleType.LOW_HURDLE -> 65f
                        ObstacleType.HIGH_BARRIER -> 115f
                        ObstacleType.DOUBLE_HURDLE -> 65f
                        ObstacleType.FLYING_DRONE -> 55f
                        ObstacleType.COIN_BOX -> 50f
                    }

                    val obsY = when (type) {
                        ObstacleType.FLYING_DRONE -> -110f - Random.nextFloat() * 70f
                        ObstacleType.COIN_BOX -> -130f - Random.nextFloat() * 90f
                        else -> 0f
                    }

                    obstacles.add(
                        Obstacle(
                            type = type,
                            x = 1200f,
                            y = obsY,
                            width = obsWidth,
                            height = obsHeight
                        )
                    )

                    // Set next spawn delay (scaled down as speed increases)
                    val baseDelay = Random.nextFloat() * 1.1f + 1.2f
                    spawnTimer = (baseDelay / speedMultiplier).coerceAtLeast(0.75f)
                }

                // Move obstacles
                val iterator = obstacles.iterator()
                val playerLeft = 110f
                val playerRight = 175f
                val playerTop = playerY - 80f
                val playerBottom = playerY

                while (iterator.hasNext()) {
                    val obs = iterator.next()
                    obs.x -= currentBaseSpeed * dt

                    val obsLeft = obs.x
                    val obsRight = obs.x + obs.width
                    val obsTop = obs.y - obs.height
                    val obsBottom = obs.y

                    // Collision Check (AABB with slight padding for fairness)
                    val isColliding = playerRight - 12f > obsLeft + 8f &&
                            playerLeft + 12f < obsRight - 8f &&
                            playerBottom - 8f > obsTop + 8f &&
                            playerTop + 8f < obsBottom - 8f

                    if (isColliding) {
                        if (obs.type == ObstacleType.COIN_BOX) {
                            if (!obs.isCollected) {
                                obs.isCollected = true
                                coinsCollected++
                                score += 50f
                                floatingTexts.add(
                                    FloatingText(
                                        text = "+50 🪙",
                                        x = obs.x,
                                        y = obs.y - 40f,
                                        color = ColorAccentYellow
                                    )
                                )
                                // Sparkle particles
                                repeat(15) {
                                    particles.add(
                                        Particle(
                                            x = obs.x + obs.width / 2,
                                            y = obs.y - obs.height / 2,
                                            vx = (Random.nextFloat() - 0.5f) * 300f,
                                            vy = (Random.nextFloat() - 0.5f) * 300f,
                                            color = ColorAccentYellow,
                                            size = Random.nextFloat() * 10f + 4f
                                        )
                                    )
                                }
                            }
                        } else {
                            // Hit Obstacle -> Game Over!
                            shakeDuration = 0.35f
                            gameState = GameState.GAME_OVER

                            // Crash explosion particles
                            repeat(35) {
                                particles.add(
                                    Particle(
                                        x = 140f,
                                        y = playerY - 40f,
                                        vx = (Random.nextFloat() - 0.5f) * 500f,
                                        vy = (Random.nextFloat() - 0.5f) * 500f,
                                        color = if (it % 2 == 0) ColorAccentPink else ColorAccentYellow,
                                        size = Random.nextFloat() * 14f + 6f
                                    )
                                )
                            }

                            val finalScore = score.toInt()
                            if (finalScore > highScore) {
                                highScore = finalScore
                                isNewHighScore = true
                                sharedPrefs.edit().putInt("HIGH_SCORE", highScore).apply()
                            }
                            break
                        }
                    }

                    // Score pass bonus
                    if (!obs.isPassed && obsRight < playerLeft) {
                        obs.isPassed = true
                        if (obs.type != ObstacleType.COIN_BOX) {
                            score += 20f
                            floatingTexts.add(
                                FloatingText(
                                    text = "+20",
                                    x = playerLeft,
                                    y = playerTop - 20f,
                                    color = ColorAccentCyan
                                )
                            )
                        }
                    }

                    // Remove offscreen obstacles
                    if (obs.x < -200f) {
                        iterator.remove()
                    }
                }

                // --- 5. Screen Shake ---
                if (shakeDuration > 0f) {
                    shakeDuration -= dt
                    screenShakeOffset = Offset(
                        x = (Random.nextFloat() - 0.5f) * 25f,
                        y = (Random.nextFloat() - 0.5f) * 25f
                    )
                } else {
                    screenShakeOffset = Offset.Zero
                }

                // --- 6. Particles & Floating Text Updates ---
                val pIter = particles.iterator()
                while (pIter.hasNext()) {
                    val p = pIter.next()
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.alpha -= dt * 1.5f
                    if (p.alpha <= 0f) {
                        pIter.remove()
                    }
                }

                val tIter = floatingTexts.iterator()
                while (tIter.hasNext()) {
                    val ft = tIter.next()
                    ft.y += ft.vy * dt
                    ft.alpha -= dt * 1.2f
                    if (ft.alpha <= 0f) {
                        tIter.remove()
                    }
                }
            }
        }
    }

    // Canvas & UI Layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorSkyTop)
            .pointerInput(gameState) {
                detectTapGestures {
                    if (gameState == GameState.PLAYING) {
                        triggerJump()
                    } else if (gameState == GameState.START) {
                        startNewGame()
                    }
                }
            }
    ) {
        // Main Game World Canvas
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val groundY = canvasHeight * 0.78f

            translate(left = screenShakeOffset.x, top = screenShakeOffset.y) {
                // Sky Gradient
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(ColorSkyTop, ColorSkyBottom, Color(0xFF531E63)),
                        startY = 0f,
                        endY = groundY
                    ),
                    size = Size(canvasWidth, groundY)
                )

                // Distant Parallax Stars
                repeat(25) { index ->
                    val starX = (index * 97f + elapsedTime * 10f) % canvasWidth
                    val starY = (index * 43f) % (groundY * 0.6f)
                    val alpha = (sin(elapsedTime * 4f + index) * 0.4f + 0.6f).coerceIn(0.2f, 1.0f)
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = if (index % 3 == 0) 3f else 1.8f,
                        center = Offset(starX, starY)
                    )
                }

                // Parallax Clouds
                repeat(4) { i ->
                    val cloudX = (i * 350f - cloudScrollX) % (canvasWidth + 300f) - 150f
                    val cloudY = 60f + i * 45f
                    with(cloudPainter) {
                        translate(left = cloudX, top = cloudY) {
                            draw(Size(120.dp.toPx(), 50.dp.toPx()), alpha = 0.45f)
                        }
                    }
                }

                // Distant Synth Skyline
                val mountainPath = Path().apply {
                    moveTo(0f, groundY)
                    var x = 0f
                    while (x <= canvasWidth + 100f) {
                        val h = 60f + sin(x * 0.008f) * 40f + sin(x * 0.02f) * 20f
                        lineTo(x, groundY - h)
                        x += 80f
                    }
                    lineTo(canvasWidth, groundY)
                    close()
                }
                drawPath(
                    path = mountainPath,
                    color = Color(0xFF280F3B)
                )

                // Neon Cyber Grid Ground
                drawRect(
                    color = ColorGroundDark,
                    topLeft = Offset(0f, groundY),
                    size = Size(canvasWidth, canvasHeight - groundY)
                )

                // Neon Horizon Line
                drawLine(
                    color = ColorAccentPink,
                    start = Offset(0f, groundY),
                    end = Offset(canvasWidth, groundY),
                    strokeWidth = 5f
                )

                // Vertical Perspective Grid Lines
                val perspectiveLines = 16
                for (i in 0..perspectiveLines) {
                    val topX = canvasWidth * (i.toFloat() / perspectiveLines)
                    val bottomX = (topX - canvasWidth / 2) * 2.2f + canvasWidth / 2
                    drawLine(
                        color = ColorGridNeon.copy(alpha = 0.25f),
                        start = Offset(topX, groundY),
                        end = Offset(bottomX, canvasHeight),
                        strokeWidth = 2f
                    )
                }

                // Horizontal Moving Grid Lines
                var gridY = groundY + groundScrollX * 0.4f
                var step = 12f
                while (gridY < canvasHeight) {
                    val progress = (gridY - groundY) / (canvasHeight - groundY)
                    val lineAlpha = (progress * 0.6f).coerceIn(0.1f, 0.5f)
                    drawLine(
                        color = ColorGridNeon.copy(alpha = lineAlpha),
                        start = Offset(0f, gridY),
                        end = Offset(canvasWidth, gridY),
                        strokeWidth = 2f
                    )
                    step *= 1.18f
                    gridY += step
                }

                // --- Draw Obstacles ---
                for (obs in obstacles) {
                    val drawX = obs.x
                    val drawY = groundY + obs.y

                    when (obs.type) {
                        ObstacleType.LOW_HURDLE -> {
                            // Neon Pink Pixel Hurdle
                            drawRoundRect(
                                color = ColorAccentPink,
                                topLeft = Offset(drawX, drawY - obs.height),
                                size = Size(obs.width, obs.height),
                                cornerRadius = CornerRadius(8f, 8f)
                            )
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.8f),
                                topLeft = Offset(drawX + 6f, drawY - obs.height + 6f),
                                size = Size(obs.width - 12f, 10f),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        }

                        ObstacleType.HIGH_BARRIER -> {
                            // Cyber Spiked Tower Barrier
                            drawRoundRect(
                                color = Color(0xFFFF3366),
                                topLeft = Offset(drawX, drawY - obs.height),
                                size = Size(obs.width, obs.height),
                                cornerRadius = CornerRadius(10f, 10f)
                            )
                            // Hazard stripes
                            for (s in 0..3) {
                                drawRect(
                                    color = ColorAccentYellow,
                                    topLeft = Offset(drawX + 8f, drawY - obs.height + 20f + s * 24f),
                                    size = Size(obs.width - 16f, 10f)
                                )
                            }
                        }

                        ObstacleType.DOUBLE_HURDLE -> {
                            // Twin Neon Hurdles
                            val hWidth = obs.width / 2f - 6f
                            drawRoundRect(
                                color = ColorAccentPurple,
                                topLeft = Offset(drawX, drawY - obs.height),
                                size = Size(hWidth, obs.height),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                            drawRoundRect(
                                color = ColorAccentPurple,
                                topLeft = Offset(drawX + hWidth + 12f, drawY - obs.height),
                                size = Size(hWidth, obs.height),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                        }

                        ObstacleType.FLYING_DRONE -> {
                            // Flying Bee Drone
                            with(beePainter) {
                                translate(left = drawX, top = drawY - obs.height) {
                                    draw(Size(obs.width, obs.height))
                                }
                            }
                        }

                        ObstacleType.COIN_BOX -> {
                            if (!obs.isCollected) {
                                // Floating Golden Star Crate
                                val bounceY = sin(elapsedTime * 6f) * 8f
                                val boxY = drawY - obs.height + bounceY
                                drawRoundRect(
                                    color = ColorAccentYellow,
                                    topLeft = Offset(drawX, boxY),
                                    size = Size(obs.width, obs.height),
                                    cornerRadius = CornerRadius(12f, 12f)
                                )
                                drawRoundRect(
                                    color = Color(0xFFFF9900),
                                    topLeft = Offset(drawX + 6f, boxY + 6f),
                                    size = Size(obs.width - 12f, obs.height - 12f),
                                    cornerRadius = CornerRadius(8f, 8f)
                                )
                                // Inner Shiny Core
                                drawCircle(
                                    color = Color.White,
                                    radius = 8f,
                                    center = Offset(drawX + obs.width / 2f, boxY + obs.height / 2f)
                                )
                            }
                        }
                    }
                }

                // --- Draw Player (Dino Character) ---
                val playerDrawX = 110f
                val playerDrawY = groundY + playerY

                if (gameState != GameState.GAME_OVER) {
                    val pWidth = 80f * (2.0f - jumpStretch)
                    val pHeight = 80f * jumpStretch

                    // Speed Trail behind Player
                    if (speedMultiplier > 1.15f) {
                        repeat(3) { t ->
                            val trailX = playerDrawX - (t + 1) * (18f * speedMultiplier)
                            with(dinoPainter) {
                                translate(left = trailX, top = playerDrawY - pHeight) {
                                    draw(
                                        size = Size(pWidth, pHeight),
                                        alpha = 0.25f / (t + 1)
                                    )
                                }
                            }
                        }
                    }

                    // Main Character Vector
                    with(dinoPainter) {
                        translate(left = playerDrawX, top = playerDrawY - pHeight) {
                            draw(size = Size(pWidth, pHeight))
                        }
                    }

                    // Running Dust / Speed Trail particles at feet
                    if (isGrounded && gameState == GameState.PLAYING) {
                        drawCircle(
                            color = ColorAccentCyan.copy(alpha = 0.6f),
                            radius = 6f + sin(playerAnimFrame) * 2f,
                            center = Offset(playerDrawX - 10f, playerDrawY - 4f)
                        )
                    }
                }

                // --- Draw Particles ---
                for (p in particles) {
                    drawCircle(
                        color = p.color.copy(alpha = p.alpha.coerceIn(0f, 1f)),
                        radius = p.size,
                        center = Offset(p.x, groundY + p.y)
                    )
                }
            }
        }

        // Floating Scores & Text Layer
        floatingTexts.forEach { ft ->
            Text(
                text = ft.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = ft.color.copy(alpha = ft.alpha.coerceIn(0f, 1f)),
                modifier = Modifier
                    .offset(x = ft.x.dp, y = ft.y.dp)
            )
        }

        // --- HUD Overlay (Top Bar) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Score Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC10051A))
                    .border(2.dp, ColorAccentPink, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column {
                    Text(
                        text = "SCORE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorAccentPink
                    )
                    Text(
                        text = "${score.toInt()}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }

            // Speed Indicator Gauge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC10051A))
                    .border(2.dp, ColorAccentCyan, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⚡ SPEED ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorAccentCyan
                    )
                    Text(
                        text = "%.1fx".format(speedMultiplier),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = ColorAccentYellow
                    )
                }
            }

            // High Score Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC10051A))
                    .border(2.dp, ColorAccentYellow, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "BEST 👑",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorAccentYellow
                    )
                    Text(
                        text = "$highScore",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }
        }

        // --- Dynamic Speed Up Announcement Banner ---
        if (speedUpAlpha > 0f && speedUpAnnouncement != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 110.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(ColorAccentPink.copy(alpha = speedUpAlpha * 0.9f))
                    .border(2.dp, ColorAccentYellow, RoundedCornerShape(20.dp))
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    text = speedUpAnnouncement!!,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }

        // --- START GAME OVERLAY ---
        if (gameState == GameState.START) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "⚡ SPEED RUSH ⚡",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        color = ColorAccentCyan,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.shadow(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "TAP ANYWHERE TO JUMP & DODGE!",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorAccentPink,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Start Card Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xF21F0D33))
                            .border(3.dp, ColorAccentCyan, RoundedCornerShape(24.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "🎮 CONTROLS & RULES",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorAccentYellow
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "• Tapscreen = Jump\n• Double Tap = Double Jump 🚀\n• Speed increases 10% every 5s!\n• Collect golden boxes for +50 pts",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                lineHeight = 20.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = { startNewGame() },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorAccentPink),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                            ) {
                                Text(
                                    text = "START SPEED RUSH 🚀",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- GAME OVER OVERLAY ---
        if (gameState == GameState.GAME_OVER) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFA1A092A))
                        .border(3.dp, ColorAccentPink, RoundedCornerShape(28.dp))
                        .padding(28.dp)
                ) {
                    Text(
                        text = "💥 GAME OVER 💥",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = ColorAccentPink,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isNewHighScore) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(ColorAccentYellow)
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "🎉 NEW HIGH SCORE! 🎉",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Black
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Score Summary Box
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF2B1042))
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "FINAL SCORE",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "${score.toInt()}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = ColorAccentCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "HIGH SCORE",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "$highScore",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = ColorAccentYellow
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "COINS COLLECTED",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "🪙 $coinsCollected",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorAccentYellow
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Action Buttons: RETRY & EXIT TO MAIN ARCADE MENU
                    Button(
                        onClick = { startNewGame() },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorAccentCyan),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = "RETRY 🔄",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { onExitGame() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B1E54)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    ) {
                        Text(
                            text = "EXIT TO MAIN ARCADE MENU 🏠",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
