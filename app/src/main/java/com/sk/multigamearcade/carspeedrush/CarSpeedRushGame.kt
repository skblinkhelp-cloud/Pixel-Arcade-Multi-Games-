package com.sk.multigamearcade.carspeedrush

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// --- Colors & Aesthetics ---
private val ColorRoadDark = Color(0xFF1E1E28)
private val ColorGrassLeft = Color(0xFF120B20)
private val ColorGrassRight = Color(0xFF120B20)
private val ColorGuardrail = Color(0xFFFF007F)
private val ColorRoadLine = Color(0xFFE0E0E0)
private val ColorPlayerCar = Color(0xFF00E5FF)
private val ColorPlayerHeadlight = Color(0xFFFFFF80)

private val ColorAccentPink = Color(0xFFFF007F)
private val ColorAccentCyan = Color(0xFF00FFCC)
private val ColorAccentYellow = Color(0xFFFFE500)
private val ColorAccentPurple = Color(0xFF9D4EDD)

enum class CarGameState {
    START,
    PLAYING,
    GAME_OVER
}

enum class EnemyCarType {
    RED_SPORTS,
    YELLOW_TAXI,
    ORANGE_TRUCK,
    PURPLE_SUPERCAR,
    GOLD_COIN
}

data class EnemyCar(
    val id: String = UUID.randomUUID().toString(),
    val type: EnemyCarType,
    val lane: Int, // 0, 1, 2 (3 Lanes)
    var y: Float,
    val width: Float,
    val height: Float,
    val speedOffset: Float,
    var isPassed: Boolean = false,
    var isCollected: Boolean = false
)

data class CarParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    var size: Float,
    var alpha: Float = 1.0f
)

@Composable
fun CarSpeedRushGameScreen(
    onExitGame: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("CarSpeedRushPrefs", Context.MODE_PRIVATE) }
    var highScore by remember { mutableIntStateOf(sharedPrefs.getInt("CAR_HIGH_SCORE", 0)) }

    var gameState by remember { mutableStateOf(CarGameState.START) }
    var score by remember { mutableFloatStateOf(0f) }
    var coinsCollected by remember { mutableIntStateOf(0) }
    var isNewHighScore by remember { mutableStateOf(false) }

    // Speed progression: 10% increase every 10 seconds
    var elapsedTime by remember { mutableFloatStateOf(0f) }
    var speedMultiplier by remember { mutableFloatStateOf(1.0f) }
    var speedUpAnnouncement by remember { mutableStateOf<String?>(null) }
    var speedUpAlpha by remember { mutableFloatStateOf(0f) }
    var lastSpeedLevel by remember { mutableIntStateOf(0) }

    // Screen Shake
    var screenShakeOffset by remember { mutableStateOf(Offset.Zero) }
    var shakeDuration by remember { mutableFloatStateOf(0f) }

    // Player State (3 Lanes: 0 = Left, 1 = Center, 2 = Right)
    var playerLane by remember { mutableIntStateOf(1) }
    var playerCurrentX by remember { mutableFloatStateOf(0f) } // Lerp x position
    var playerAnimTime by remember { mutableFloatStateOf(0f) }

    // Road scroll position
    var roadScrollY by remember { mutableFloatStateOf(0f) }

    // Entities
    val enemyCars = remember { mutableStateListOf<EnemyCar>() }
    val particles = remember { mutableStateListOf<CarParticle>() }

    var spawnTimer by remember { mutableFloatStateOf(0f) }

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
        playerLane = 1
        enemyCars.clear()
        particles.clear()
        spawnTimer = 0.5f
        gameState = CarGameState.PLAYING
    }

    // Lane Change Handlers
    fun moveLaneLeft() {
        if (gameState == CarGameState.PLAYING && playerLane > 0) {
            playerLane--
        }
    }

    fun moveLaneRight() {
        if (gameState == CarGameState.PLAYING && playerLane < 2) {
            playerLane++
        }
    }

    // Main Smooth Frame Loop (60 FPS / 120 FPS)
    LaunchedEffect(gameState) {
        if (gameState != CarGameState.PLAYING) return@LaunchedEffect

        var lastFrameNanos = System.nanoTime()

        while (isActive && gameState == CarGameState.PLAYING) {
            withFrameNanos { currentFrameNanos ->
                val dt = ((currentFrameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.04f)
                lastFrameNanos = currentFrameNanos

                // --- 1. Professional Arcade Speed Progression ---
                elapsedTime += dt
                speedMultiplier = (1.0f + elapsedTime * 0.022f + (elapsedTime / 20.0f).pow(1.6f) * 0.08f).coerceAtMost(2.8f)

                val currentLevel = ((speedMultiplier - 1.0f) / 0.20f).toInt()
                if (currentLevel > lastSpeedLevel && currentLevel > 0) {
                    lastSpeedLevel = currentLevel
                    speedUpAnnouncement = "🏎️ SPEED RUSH! ${(speedMultiplier * 100).toInt()}% ⚡"
                    speedUpAlpha = 1.0f
                }

                if (speedUpAlpha > 0f) {
                    speedUpAlpha = (speedUpAlpha - dt * 0.8f).coerceAtLeast(0f)
                }

                // Increment score based on time survived
                score += dt * 25f * speedMultiplier
                playerAnimTime += dt

                // --- 2. Road Scrolling (Snappy Base Speed 650f) ---
                val currentBaseSpeed = 650f * speedMultiplier
                roadScrollY = (roadScrollY + currentBaseSpeed * dt) % 120f

                // --- 3. Obstacle Spawning & Movement ---
                spawnTimer -= dt
                if (spawnTimer <= 0f) {
                    val lane = Random.nextInt(0, 3)
                    val rand = Random.nextFloat()
                    val type = when {
                        rand < 0.35f -> EnemyCarType.RED_SPORTS
                        rand < 0.60f -> EnemyCarType.YELLOW_TAXI
                        rand < 0.80f -> EnemyCarType.PURPLE_SUPERCAR
                        rand < 0.90f -> EnemyCarType.ORANGE_TRUCK
                        else -> EnemyCarType.GOLD_COIN
                    }

                    val (cWidth, cHeight) = when (type) {
                        EnemyCarType.RED_SPORTS -> Pair(60f, 105f)
                        EnemyCarType.YELLOW_TAXI -> Pair(62f, 110f)
                        EnemyCarType.PURPLE_SUPERCAR -> Pair(60f, 115f)
                        EnemyCarType.ORANGE_TRUCK -> Pair(72f, 150f)
                        EnemyCarType.GOLD_COIN -> Pair(48f, 48f)
                    }

                    val speedOffset = if (type == EnemyCarType.GOLD_COIN) 0f else (Random.nextFloat() - 0.5f) * 90f

                    enemyCars.add(
                        EnemyCar(
                            type = type,
                            lane = lane,
                            y = -200f,
                            width = cWidth,
                            height = cHeight,
                            speedOffset = speedOffset
                        )
                    )

                    val baseDelay = Random.nextFloat() * 0.7f + 0.9f
                    spawnTimer = (baseDelay / speedMultiplier).coerceAtLeast(0.48f)
                }

                // --- 4. Update Enemy Positions & Collisions ---
                val iterator = enemyCars.iterator()
                val playerYPos = 1200f // Player y position on virtual canvas
                val pWidth = 62f
                val pHeight = 110f

                // Player current lane X lerp calculation
                val laneCenters = listOf(280f, 540f, 800f) // Virtual canvas lane X coordinates
                val targetX = laneCenters[playerLane]
                playerCurrentX += (targetX - playerCurrentX) * dt * 24f

                // Exhaust particles behind player car
                if (Random.nextFloat() < 0.6f) {
                    particles.add(
                        CarParticle(
                            x = playerCurrentX + (Random.nextFloat() - 0.5f) * 20f,
                            y = playerYPos + pHeight / 2f,
                            vx = (Random.nextFloat() - 0.5f) * 40f,
                            vy = Random.nextFloat() * 150f + 100f,
                            color = if (Random.nextBoolean()) ColorAccentCyan else Color.White,
                            size = Random.nextFloat() * 8f + 4f
                        )
                    )
                }

                while (iterator.hasNext()) {
                    val enemy = iterator.next()
                    enemy.y += (currentBaseSpeed + enemy.speedOffset) * dt

                    val enemyX = laneCenters[enemy.lane]

                    // Collision Check
                    val isColliding = abs(playerCurrentX - enemyX) < (pWidth / 2f + enemy.width / 2f - 10f) &&
                            abs(playerYPos - enemy.y) < (pHeight / 2f + enemy.height / 2f - 12f)

                    if (isColliding) {
                        if (enemy.type == EnemyCarType.GOLD_COIN) {
                            if (!enemy.isCollected) {
                                enemy.isCollected = true
                                coinsCollected++
                                score += 50f
                                repeat(12) {
                                    particles.add(
                                        CarParticle(
                                            x = enemyX,
                                            y = enemy.y,
                                            vx = (Random.nextFloat() - 0.5f) * 300f,
                                            vy = (Random.nextFloat() - 0.5f) * 300f,
                                            color = ColorAccentYellow,
                                            size = Random.nextFloat() * 10f + 4f
                                        )
                                    )
                                }
                            }
                        } else {
                            // Crash with enemy car -> Game Over!
                            shakeDuration = 0.4f
                            gameState = CarGameState.GAME_OVER

                            // Crash Explosion Particles
                            repeat(40) {
                                particles.add(
                                    CarParticle(
                                        x = playerCurrentX,
                                        y = playerYPos,
                                        vx = (Random.nextFloat() - 0.5f) * 600f,
                                        vy = (Random.nextFloat() - 0.5f) * 600f,
                                        color = if (it % 2 == 0) ColorAccentPink else ColorAccentYellow,
                                        size = Random.nextFloat() * 16f + 6f
                                    )
                                )
                            }

                            val finalScore = score.toInt()
                            if (finalScore > highScore) {
                                highScore = finalScore
                                isNewHighScore = true
                                sharedPrefs.edit().putInt("CAR_HIGH_SCORE", highScore).apply()
                            }
                            break
                        }
                    }

                    // Score pass bonus
                    if (!enemy.isPassed && enemy.y > playerYPos + 100f) {
                        enemy.isPassed = true
                        if (enemy.type != EnemyCarType.GOLD_COIN) {
                            score += 15f
                        }
                    }

                    // Remove offscreen
                    if (enemy.y > 1600f) {
                        iterator.remove()
                    }
                }

                // --- 5. Screen Shake Update ---
                if (shakeDuration > 0f) {
                    shakeDuration -= dt
                    screenShakeOffset = Offset(
                        x = (Random.nextFloat() - 0.5f) * 28f,
                        y = (Random.nextFloat() - 0.5f) * 28f
                    )
                } else {
                    screenShakeOffset = Offset.Zero
                }

                // --- 6. Particles Update ---
                val pIter = particles.iterator()
                while (pIter.hasNext()) {
                    val p = pIter.next()
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.alpha -= dt * 1.8f
                    if (p.alpha <= 0f) {
                        pIter.remove()
                    }
                }
            }
        }
    }

    // Main Layout & Touch Zones
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorGrassLeft)
    ) {
        // Main Game World Canvas
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Scale virtual coordinates (1080x1600) to actual canvas size
            val scaleX = canvasWidth / 1080f
            val scaleY = canvasHeight / 1600f

            translate(left = screenShakeOffset.x, top = screenShakeOffset.y) {
                // Highway Base Road
                val roadLeft = 140f * scaleX
                val roadWidth = 800f * scaleX
                val roadRight = roadLeft + roadWidth

                // Grass shoulders
                drawRect(
                    color = ColorGrassLeft,
                    topLeft = Offset(0f, 0f),
                    size = Size(roadLeft, canvasHeight)
                )
                drawRect(
                    color = ColorGrassRight,
                    topLeft = Offset(roadRight, 0f),
                    size = Size(canvasWidth - roadRight, canvasHeight)
                )

                // Main Asphalt Road
                drawRect(
                    color = ColorRoadDark,
                    topLeft = Offset(roadLeft, 0f),
                    size = Size(roadWidth, canvasHeight)
                )

                // Neon Guardrails
                drawLine(
                    color = ColorGuardrail,
                    start = Offset(roadLeft, 0f),
                    end = Offset(roadLeft, canvasHeight),
                    strokeWidth = 10f * scaleX
                )
                drawLine(
                    color = ColorGuardrail,
                    start = Offset(roadRight, 0f),
                    end = Offset(roadRight, canvasHeight),
                    strokeWidth = 10f * scaleX
                )

                // Scrolling Dashed White Lane Dividers
                val laneDividerX1 = 400f * scaleX
                val laneDividerX2 = 680f * scaleX

                var lineY = -120f * scaleY + roadScrollY * scaleY
                val dashHeight = 70f * scaleY
                val dashGap = 50f * scaleY

                while (lineY < canvasHeight + 100f) {
                    drawRect(
                        color = ColorRoadLine.copy(alpha = 0.85f),
                        topLeft = Offset(laneDividerX1 - 4f * scaleX, lineY),
                        size = Size(8f * scaleX, dashHeight)
                    )
                    drawRect(
                        color = ColorRoadLine.copy(alpha = 0.85f),
                        topLeft = Offset(laneDividerX2 - 4f * scaleX, lineY),
                        size = Size(8f * scaleX, dashHeight)
                    )
                    lineY += dashHeight + dashGap
                }

                // --- Draw Enemy Cars & Coins ---
                for (enemy in enemyCars) {
                    val virtualLaneCenters = listOf(280f, 540f, 800f)
                    val enemyX = virtualLaneCenters[enemy.lane] * scaleX
                    val enemyY = enemy.y * scaleY
                    val eW = enemy.width * scaleX
                    val eH = enemy.height * scaleY

                    val enemyLeft = enemyX - eW / 2f
                    val enemyTop = enemyY - eH / 2f

                    when (enemy.type) {
                        EnemyCarType.RED_SPORTS -> {
                            // Red Sports Car Body
                            drawRoundRect(
                                color = Color(0xFFFF2A55),
                                topLeft = Offset(enemyLeft, enemyTop),
                                size = Size(eW, eH),
                                cornerRadius = CornerRadius(10f * scaleX, 10f * scaleY)
                            )
                            // Roof / Windshield
                            drawRoundRect(
                                color = Color(0xFF1F0813),
                                topLeft = Offset(enemyLeft + eW * 0.15f, enemyTop + eH * 0.25f),
                                size = Size(eW * 0.7f, eH * 0.4f),
                                cornerRadius = CornerRadius(6f * scaleX, 6f * scaleY)
                            )
                            // Red Taillights
                            drawRect(
                                color = Color.Red,
                                topLeft = Offset(enemyLeft + 4f * scaleX, enemyTop + eH - 8f * scaleY),
                                size = Size(12f * scaleX, 6f * scaleY)
                            )
                            drawRect(
                                color = Color.Red,
                                topLeft = Offset(enemyLeft + eW - 16f * scaleX, enemyTop + eH - 8f * scaleY),
                                size = Size(12f * scaleX, 6f * scaleY)
                            )
                        }

                        EnemyCarType.YELLOW_TAXI -> {
                            // Yellow Taxi Body
                            drawRoundRect(
                                color = ColorAccentYellow,
                                topLeft = Offset(enemyLeft, enemyTop),
                                size = Size(eW, eH),
                                cornerRadius = CornerRadius(8f * scaleX, 8f * scaleY)
                            )
                            // Windshield
                            drawRoundRect(
                                color = Color(0xFF222222),
                                topLeft = Offset(enemyLeft + eW * 0.15f, enemyTop + eH * 0.25f),
                                size = Size(eW * 0.7f, eH * 0.38f),
                                cornerRadius = CornerRadius(4f * scaleX, 4f * scaleY)
                            )
                            // Taxi Sign
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(enemyLeft + eW * 0.3f, enemyTop + eH * 0.45f),
                                size = Size(eW * 0.4f, 8f * scaleY)
                            )
                        }

                        EnemyCarType.PURPLE_SUPERCAR -> {
                            // Purple Supercar Body
                            drawRoundRect(
                                color = ColorAccentPurple,
                                topLeft = Offset(enemyLeft, enemyTop),
                                size = Size(eW, eH),
                                cornerRadius = CornerRadius(12f * scaleX, 12f * scaleY)
                            )
                            // Windshield
                            drawRoundRect(
                                color = Color(0xFF0F001D),
                                topLeft = Offset(enemyLeft + eW * 0.15f, enemyTop + eH * 0.2f),
                                size = Size(eW * 0.7f, eH * 0.45f),
                                cornerRadius = CornerRadius(6f * scaleX, 6f * scaleY)
                            )
                        }

                        EnemyCarType.ORANGE_TRUCK -> {
                            // Orange Cargo Truck
                            drawRoundRect(
                                color = Color(0xFFFF6F00),
                                topLeft = Offset(enemyLeft, enemyTop),
                                size = Size(eW, eH),
                                cornerRadius = CornerRadius(6f * scaleX, 6f * scaleY)
                            )
                            // Cargo Roof Grill
                            drawRect(
                                color = Color(0xFFE65100),
                                topLeft = Offset(enemyLeft + 6f * scaleX, enemyTop + 12f * scaleY),
                                size = Size(eW - 12f * scaleX, eH - 30f * scaleY)
                            )
                        }

                        EnemyCarType.GOLD_COIN -> {
                            if (!enemy.isCollected) {
                                // Floating Golden Star Box
                                val coinX = enemyX
                                val coinY = enemyY
                                drawCircle(
                                    color = ColorAccentYellow,
                                    radius = 22f * scaleX,
                                    center = Offset(coinX, coinY)
                                )
                                drawCircle(
                                    color = Color(0xFFFF9900),
                                    radius = 16f * scaleX,
                                    center = Offset(coinX, coinY)
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 6f * scaleX,
                                    center = Offset(coinX, coinY)
                                )
                            }
                        }
                    }
                }

                // --- Draw Player Car ---
                if (gameState != CarGameState.GAME_OVER) {
                    val pX = playerCurrentX * scaleX
                    val pY = 1200f * scaleY
                    val pW = 62f * scaleX
                    val pH = 110f * scaleY

                    val pLeft = pX - pW / 2f
                    val pTop = pY - pH / 2f

                    // Headlight Beams forward
                    val headlightPath = Path().apply {
                        moveTo(pLeft + 8f * scaleX, pTop)
                        lineTo(pLeft - 30f * scaleX, pTop - 180f * scaleY)
                        lineTo(pLeft + pW + 30f * scaleX, pTop - 180f * scaleY)
                        lineTo(pLeft + pW - 8f * scaleX, pTop)
                        close()
                    }
                    drawPath(
                        path = headlightPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(ColorPlayerHeadlight.copy(alpha = 0.4f), Color.Transparent),
                            startY = pTop - 180f * scaleY,
                            endY = pTop
                        )
                    )

                    // Player Car Main Body (Neon Cyan)
                    drawRoundRect(
                        color = ColorPlayerCar,
                        topLeft = Offset(pLeft, pTop),
                        size = Size(pW, pH),
                        cornerRadius = CornerRadius(12f * scaleX, 12f * scaleY)
                    )

                    // Windshield & Glass
                    drawRoundRect(
                        color = Color(0xFF003847),
                        topLeft = Offset(pLeft + pW * 0.15f, pTop + pH * 0.25f),
                        size = Size(pW * 0.7f, pH * 0.42f),
                        cornerRadius = CornerRadius(6f * scaleX, 6f * scaleY)
                    )

                    // Headlight Glowing Bulbs
                    drawRect(
                        color = ColorPlayerHeadlight,
                        topLeft = Offset(pLeft + 4f * scaleX, pTop + 2f * scaleY),
                        size = Size(12f * scaleX, 8f * scaleY)
                    )
                    drawRect(
                        color = ColorPlayerHeadlight,
                        topLeft = Offset(pLeft + pW - 16f * scaleX, pTop + 2f * scaleY),
                        size = Size(12f * scaleX, 8f * scaleY)
                    )

                    // Cyan Tail Spoilers
                    drawRect(
                        color = ColorAccentPink,
                        topLeft = Offset(pLeft + 2f * scaleX, pTop + pH - 6f * scaleY),
                        size = Size(pW - 4f * scaleX, 6f * scaleY)
                    )
                }

                // --- Draw Particles ---
                for (p in particles) {
                    drawCircle(
                        color = p.color.copy(alpha = p.alpha.coerceIn(0f, 1f)),
                        radius = p.size * scaleX,
                        center = Offset(p.x * scaleX, p.y * scaleY)
                    )
                }
            }
        }

        // --- Touch Control Zones (Left half & Right half) ---
        if (gameState == CarGameState.PLAYING) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Touch Zone
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures {
                                moveLaneLeft()
                            }
                        }
                )
                // Right Touch Zone
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures {
                                moveLaneRight()
                            }
                        }
                )
            }
        }

        // --- Top HUD Overlay ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Current Score Pill
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

            // Speed Indicator
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC10051A))
                    .border(2.dp, ColorAccentCyan, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🏎️ SPEED ",
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

        // --- Speed Up Announcement Banner ---
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
        if (gameState == CarGameState.START) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "🏎️ SPEED RUSH 🏎️",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        color = ColorAccentCyan,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.shadow(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "HIGHWAY RACER",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorAccentPink,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Start Card Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xF21F0D33))
                            .border(3.dp, ColorAccentCyan, RoundedCornerShape(24.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "🕹️ HOW TO PLAY",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorAccentYellow
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "• Tap Left Side = Move Left Lane\n• Tap Right Side = Move Right Lane\n• Speed increases 10% every 10s!\n• Collect golden stars for +50 pts",
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
                                    text = "TAP TO START 🏁",
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
        if (gameState == CarGameState.GAME_OVER) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.78f)),
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
                        text = "💥 CRASH! GAME OVER 💥",
                        fontSize = 28.sp,
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

                    // Score Summary
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
                                text = "STAR PICKUPS",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "⭐ $coinsCollected",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorAccentYellow
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Buttons
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
                            text = "EXIT TO MAIN ARCADE 🏠",
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
