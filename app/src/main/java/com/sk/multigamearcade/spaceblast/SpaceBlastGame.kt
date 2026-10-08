package com.sk.multigamearcade.spaceblast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sk.multigamearcade.R
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// --- Color Palette for Pixel Arcade Space Blast ---
private val ColorSpaceBackgroundTop = Color(0xFF070012)
private val ColorSpaceBackgroundBottom = Color(0xFF14002A)
private val ColorNeonBlue = Color(0xFF00E5FF)
private val ColorNeonCyan = Color(0xFF00FFCC)
private val ColorNeonPink = Color(0xFFFF007F)
private val ColorNeonGreen = Color(0xFF39FF14)
private val ColorNeonYellow = Color(0xFFFFE500)
private val ColorNeonPurple = Color(0xFFB5179E)
private val ColorBossRed = Color(0xFFFF0033)

/**
 * Primary entry point composable for Space Blast.
 */
@Composable
fun SpaceBlastScreen(
    onExitGame: () -> Unit = {}
) {
    SpaceBlastGameApp(onExitGame = onExitGame)
}

/**
 * Full Space Blast Retro Pixel-Arcade Game.
 * Integrated with original supplied PNG pixel-art assets and nearest-neighbor scaling.
 */
@Composable
fun SpaceBlastGameApp(
    onExitGame: () -> Unit = {}
) {
    val context = LocalContext.current
    val storage = remember { SpaceBlastStorage(context) }

    // Screen Navigation State
    var currentScreen by remember { mutableStateOf(SpaceBlastScreenState.MAIN_MENU) }
    var selectedLevelIndex by remember { mutableIntStateOf(storage.getUnlockedLevel()) }

    // Currency & Persistence States
    var credits by remember { mutableIntStateOf(storage.getCredits()) }
    var highScore by remember { mutableIntStateOf(storage.getHighScore()) }
    var unlockedLevel by remember { mutableIntStateOf(storage.getUnlockedLevel()) }
    var selectedShipId by remember { mutableStateOf(storage.getSelectedShipId()) }

    // Current Equipped Ship Data
    val currentShipConfig = remember(selectedShipId) {
        storage.getAllShips().find { it.id == selectedShipId } ?: storage.getAllShips()[0]
    }

    // Main UI Router
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ColorSpaceBackgroundTop, ColorSpaceBackgroundBottom)
                )
            )
    ) {
        when (currentScreen) {
            SpaceBlastScreenState.MAIN_MENU -> {
                MainMenuScreen(
                    credits = credits,
                    highScore = highScore,
                    selectedShip = currentShipConfig,
                    onPlayClick = { currentScreen = SpaceBlastScreenState.PLAYING },
                    onLevelSelectClick = { currentScreen = SpaceBlastScreenState.LEVEL_SELECT },
                    onHangarClick = { currentScreen = SpaceBlastScreenState.HANGAR },
                    onUpgradesClick = { currentScreen = SpaceBlastScreenState.UPGRADES },
                    onMissionsClick = { currentScreen = SpaceBlastScreenState.MISSIONS },
                    onExitClick = onExitGame
                )
            }

            SpaceBlastScreenState.LEVEL_SELECT -> {
                LevelSelectScreen(
                    unlockedLevel = unlockedLevel,
                    onSelectLevel = { lvl ->
                        selectedLevelIndex = lvl
                        currentScreen = SpaceBlastScreenState.PLAYING
                    },
                    onBack = { currentScreen = SpaceBlastScreenState.MAIN_MENU }
                )
            }

            SpaceBlastScreenState.HANGAR -> {
                ShipHangarScreen(
                    credits = credits,
                    selectedShipId = selectedShipId,
                    storage = storage,
                    onShipEquipped = { shipId ->
                        selectedShipId = shipId
                        credits = storage.getCredits()
                    },
                    onBack = { currentScreen = SpaceBlastScreenState.MAIN_MENU }
                )
            }

            SpaceBlastScreenState.UPGRADES -> {
                UpgradesScreen(
                    credits = credits,
                    storage = storage,
                    onUpgradeBought = {
                        credits = storage.getCredits()
                    },
                    onBack = { currentScreen = SpaceBlastScreenState.MAIN_MENU }
                )
            }

            SpaceBlastScreenState.MISSIONS -> {
                MissionsScreen(
                    credits = credits,
                    storage = storage,
                    onClaimReward = {
                        credits = storage.getCredits()
                    },
                    onBack = { currentScreen = SpaceBlastScreenState.MAIN_MENU }
                )
            }

            SpaceBlastScreenState.PLAYING -> {
                SpaceBlastGameLoopScreen(
                    levelIndex = selectedLevelIndex,
                    shipConfig = currentShipConfig,
                    storage = storage,
                    onLevelCompleted = { score, coinsEarned ->
                        storage.addCredits(coinsEarned)
                        storage.saveHighScore(score)
                        storage.setUnlockedLevel(selectedLevelIndex + 1)

                        credits = storage.getCredits()
                        highScore = storage.getHighScore()
                        unlockedLevel = storage.getUnlockedLevel()

                        currentScreen = SpaceBlastScreenState.VICTORY
                    },
                    onGameOver = { score, coinsEarned ->
                        storage.addCredits(coinsEarned)
                        storage.saveHighScore(score)

                        credits = storage.getCredits()
                        highScore = storage.getHighScore()

                        currentScreen = SpaceBlastScreenState.GAME_OVER
                    },
                    onPause = { currentScreen = SpaceBlastScreenState.PAUSED }
                )
            }

            SpaceBlastScreenState.PAUSED -> {
                PauseOverlayScreen(
                    onResume = { currentScreen = SpaceBlastScreenState.PLAYING },
                    onRestart = { currentScreen = SpaceBlastScreenState.PLAYING },
                    onMainMenu = { currentScreen = SpaceBlastScreenState.MAIN_MENU }
                )
            }

            SpaceBlastScreenState.GAME_OVER -> {
                GameOverScreen(
                    score = highScore,
                    highScore = highScore,
                    onRetry = { currentScreen = SpaceBlastScreenState.PLAYING },
                    onMainMenu = { currentScreen = SpaceBlastScreenState.MAIN_MENU }
                )
            }

            SpaceBlastScreenState.VICTORY -> {
                LevelVictoryScreen(
                    levelIndex = selectedLevelIndex,
                    score = highScore,
                    coinsEarned = 350,
                    onNextLevel = {
                        if (selectedLevelIndex < 30) {
                            selectedLevelIndex += 1
                            currentScreen = SpaceBlastScreenState.PLAYING
                        } else {
                            currentScreen = SpaceBlastScreenState.MAIN_MENU
                        }
                    },
                    onMainMenu = { currentScreen = SpaceBlastScreenState.MAIN_MENU }
                )
            }
        }
    }
}

// ==========================================
// --- 1. MAIN MENU SCREEN ---
// ==========================================
@Composable
private fun MainMenuScreen(
    credits: Int,
    highScore: Int,
    selectedShip: SpaceshipConfig,
    onPlayClick: () -> Unit,
    onLevelSelectClick: () -> Unit,
    onHangarClick: () -> Unit,
    onUpgradesClick: () -> Unit,
    onMissionsClick: () -> Unit,
    onExitClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(20.dp)
    ) {
        // Top Header Info Cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xD9100222))
                    .border(2.dp, ColorNeonYellow, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🪙 ", fontSize = 15.sp)
                    Text(
                        text = "$credits CREDITS",
                        color = ColorNeonYellow,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xD9100222))
                    .border(2.dp, ColorNeonCyan, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🏆 ", fontSize = 15.sp)
                    Text(
                        text = "BEST: $highScore",
                        color = ColorNeonCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Center Pixel Arcade Banner & Ship Preview
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🚀 SPACE BLAST",
                color = ColorNeonCyan,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Text(
                text = "NEON PIXEL ARCADE SHOOTER",
                color = ColorNeonPink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
            )

            // Ship Showcase Frame
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xF0180236))
                    .border(2.5.dp, selectedShip.primaryColor, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = selectedShip.drawableResId),
                        contentDescription = selectedShip.name,
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = selectedShip.name.uppercase(),
                        color = selectedShip.primaryColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "SPECIAL: ${selectedShip.specialAbilityName}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        // Bottom Menu Buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onPlayClick,
                colors = ButtonDefaults.buttonColors(containerColor = ColorNeonCyan),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp))
            ) {
                Text("▶ PLAY CAMPAIGN", color = Color(0xFF070012), fontSize = 18.sp, fontWeight = FontWeight.Black)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onLevelSelectClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF220842)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(46.dp).border(1.5.dp, ColorNeonPurple, RoundedCornerShape(14.dp))
                ) {
                    Text("🗺️ LEVELS", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onHangarClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF220842)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(46.dp).border(1.5.dp, ColorNeonPink, RoundedCornerShape(14.dp))
                ) {
                    Text("🛸 SHIPS", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onUpgradesClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF220842)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(46.dp).border(1.5.dp, ColorNeonYellow, RoundedCornerShape(14.dp))
                ) {
                    Text("⚡ UPGRADES", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onMissionsClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF220842)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(46.dp).border(1.5.dp, ColorNeonGreen, RoundedCornerShape(14.dp))
                ) {
                    Text("📜 MISSIONS", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = onExitClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(42.dp).border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            ) {
                Text("EXIT TO ARCADE", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ==========================================
// --- 2. LEVEL SELECT SCREEN ---
// ==========================================
@Composable
private fun LevelSelectScreen(
    unlockedLevel: Int,
    onSelectLevel: (Int) -> Unit,
    onBack: () -> Unit
) {
    val levels = remember { SpaceBlastGameEngine.getLevelConfigurations() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF220842)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("◄ BACK", color = ColorNeonCyan, fontWeight = FontWeight.Bold)
            }

            Text("30 CAMPAIGN LEVELS", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.width(40.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(levels) { levelConfig ->
                val isUnlocked = levelConfig.levelIndex <= unlockedLevel
                val isBoss = levelConfig.bossName != null

                Box(
                    modifier = Modifier
                        .height(95.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            when {
                                !isUnlocked -> Color(0xFF10051A)
                                isBoss -> Color(0xFF3B0014)
                                else -> Color(0xFF1B0038)
                            }
                        )
                        .border(
                            width = 2.dp,
                            color = when {
                                !isUnlocked -> Color.Gray.copy(alpha = 0.3f)
                                isBoss -> ColorBossRed
                                else -> ColorNeonCyan
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable(enabled = isUnlocked) {
                            onSelectLevel(levelConfig.levelIndex)
                        }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isBoss) "👾 BOSS" else "LEVEL",
                            color = if (isBoss) ColorBossRed else Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${levelConfig.levelIndex}",
                            color = if (isUnlocked) ColorNeonYellow else Color.Gray,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = if (isUnlocked) "UNLOCKED" else "🔒 LOCKED",
                            color = if (isUnlocked) ColorNeonGreen else Color.Gray,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// --- 3. SHIP HANGAR SCREEN ---
// ==========================================
@Composable
private fun ShipHangarScreen(
    credits: Int,
    selectedShipId: String,
    storage: SpaceBlastStorage,
    onShipEquipped: (String) -> Unit,
    onBack: () -> Unit
) {
    val ships = remember(credits) { storage.getAllShips() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF220842)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("◄ BACK", color = ColorNeonCyan, fontWeight = FontWeight.Bold)
            }

            Text("SHIP HANGAR", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)

            Text("🪙 $credits", color = ColorNeonYellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(ships) { ship ->
                val isSelected = ship.id == selectedShipId

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xF0180236))
                        .border(
                            width = if (isSelected) 3.dp else 1.5.dp,
                            color = if (isSelected) ColorNeonYellow else ship.primaryColor,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = ship.drawableResId),
                                    contentDescription = ship.name,
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(ship.name, color = ship.primaryColor, fontSize = 17.sp, fontWeight = FontWeight.Black)
                                    Text("ABILITY: ${ship.specialAbilityName}", color = ColorNeonYellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (isSelected) {
                                Text("EQUIPPED ✓", color = ColorNeonYellow, fontSize = 12.sp, fontWeight = FontWeight.Black)
                            } else if (ship.isUnlocked) {
                                Button(
                                    onClick = { onShipEquipped(ship.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorNeonCyan),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("EQUIP", color = Color(0xFF070012), fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (storage.spendCredits(ship.price)) {
                                            storage.unlockShip(ship.id)
                                            storage.setSelectedShipId(ship.id)
                                            onShipEquipped(ship.id)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorNeonYellow),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("BUY: 🪙 ${ship.price}", color = Color(0xFF070012), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Text(ship.description, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))

                        // Stats Grid
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("HP: ${ship.baseHp.toInt()}", color = ColorNeonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Shield: ${ship.baseShield.toInt()}", color = ColorNeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Damage: ${ship.baseDamage.toInt()}", color = ColorNeonPink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Speed: ${ship.baseSpeed}x", color = ColorNeonYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// --- 4. UPGRADES SCREEN ---
// ==========================================
@Composable
private fun UpgradesScreen(
    credits: Int,
    storage: SpaceBlastStorage,
    onUpgradeBought: () -> Unit,
    onBack: () -> Unit
) {
    val types = UpgradeType.values()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF220842)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("◄ BACK", color = ColorNeonCyan, fontWeight = FontWeight.Bold)
            }

            Text("STAT UPGRADES", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)

            Text("🪙 $credits", color = ColorNeonYellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(types) { type ->
                val level = storage.getUpgradeLevel(type)
                val cost = storage.getUpgradeCost(type)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xF0180236))
                        .border(1.5.dp, ColorNeonPurple, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(type.title, color = ColorNeonYellow, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(type.description, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                            Text("LEVEL $level / 10", color = ColorNeonCyan, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
                        }

                        Button(
                            onClick = {
                                if (storage.upgradeStat(type)) {
                                    onUpgradeBought()
                                }
                            },
                            enabled = (level < 10) && (credits >= cost),
                            colors = ButtonDefaults.buttonColors(containerColor = ColorNeonCyan),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (level >= 10) "MAXED" else "UPGRADE\n🪙 $cost", fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// --- 5. MISSIONS SCREEN ---
// ==========================================
@Composable
private fun MissionsScreen(
    credits: Int,
    storage: SpaceBlastStorage,
    onClaimReward: () -> Unit,
    onBack: () -> Unit
) {
    val missions = remember(credits) { storage.getMissions() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF220842)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("◄ BACK", color = ColorNeonCyan, fontWeight = FontWeight.Bold)
            }

            Text("ARCADE MISSIONS", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)

            Text("🪙 $credits", color = ColorNeonYellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(missions) { mission ->
                val isComplete = mission.currentProgress >= mission.targetProgress

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xF0180236))
                        .border(1.5.dp, ColorNeonGreen, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mission.title, color = ColorNeonGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(mission.description, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                            Text("PROGRESS: ${min(mission.currentProgress, mission.targetProgress)} / ${mission.targetProgress}", color = ColorNeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                        }

                        if (mission.isClaimed) {
                            Text("CLAIMED ✓", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Button(
                                onClick = {
                                    if (storage.claimMission(mission.id)) {
                                        storage.addCredits(mission.rewardCoins)
                                        onClaimReward()
                                    }
                                },
                                enabled = isComplete,
                                colors = ButtonDefaults.buttonColors(containerColor = ColorNeonYellow),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("CLAIM\n🪙 ${mission.rewardCoins}", color = Color(0xFF070012), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// --- 6. 60FPS GAME LOOP WITH PNG DRAWABLES ---
// ==========================================
@Composable
private fun SpaceBlastGameLoopScreen(
    levelIndex: Int,
    shipConfig: SpaceshipConfig,
    storage: SpaceBlastStorage,
    onLevelCompleted: (score: Int, coinsEarned: Int) -> Unit,
    onGameOver: (score: Int, coinsEarned: Int) -> Unit,
    onPause: () -> Unit
) {
    val levelConfig = remember(levelIndex) {
        SpaceBlastGameEngine.getLevelConfigurations().find { it.levelIndex == levelIndex }
            ?: SpaceBlastGameEngine.getLevelConfigurations()[0]
    }

    // Preload PNG ImageBitmaps for Player Ships, Enemies, and Bosses
    val shipBitmap = ImageBitmap.imageResource(id = shipConfig.drawableResId)

    val scoutImage = ImageBitmap.imageResource(id = R.drawable.enemy_scout)
    val interceptorImage = ImageBitmap.imageResource(id = R.drawable.enemy_interceptor)
    val destroyerImage = ImageBitmap.imageResource(id = R.drawable.enemy_destroyer)
    val bomberImage = ImageBitmap.imageResource(id = R.drawable.enemy_bomber)
    val sniperImage = ImageBitmap.imageResource(id = R.drawable.enemy_sniper)
    val swarmImage = ImageBitmap.imageResource(id = R.drawable.enemy_swarm_drone)
    val shieldedImage = ImageBitmap.imageResource(id = R.drawable.enemy_shielded)
    val kamikazeImage = ImageBitmap.imageResource(id = R.drawable.enemy_kamikaze)
    val eliteImage = ImageBitmap.imageResource(id = R.drawable.enemy_elite)

    val boss1Image = ImageBitmap.imageResource(id = R.drawable.boss_1_void_hunter)
    val boss2Image = ImageBitmap.imageResource(id = R.drawable.boss_2_titan_destroyer)
    val boss3Image = ImageBitmap.imageResource(id = R.drawable.boss_3_nebula_guardian)
    val boss4Image = ImageBitmap.imageResource(id = R.drawable.boss_4_crimson_mothership)
    val boss5Image = ImageBitmap.imageResource(id = R.drawable.boss_5_dark_star_commander)
    val bossFinalImage = ImageBitmap.imageResource(id = R.drawable.boss_final_void_emperor)

    // Game Engine State
    var score by remember { mutableIntStateOf(0) }
    var coinsEarned by remember { mutableIntStateOf(0) }
    var comboCount by remember { mutableIntStateOf(0) }
    var specialEnergy by remember { mutableFloatStateOf(0f) }

    var playerHp by remember { mutableFloatStateOf(shipConfig.baseHp + storage.getUpgradeLevel(UpgradeType.MAX_HEALTH) * 15f) }
    var playerShield by remember { mutableFloatStateOf(shipConfig.baseShield + storage.getUpgradeLevel(UpgradeType.MAX_SHIELD) * 15f) }
    val maxHp = remember { playerHp }
    val maxShield = remember { playerShield }

    var playerX by remember { mutableFloatStateOf(0.5f) }
    var playerY by remember { mutableFloatStateOf(0.82f) }

    var activePowerUp by remember { mutableStateOf<PowerUpType?>(null) }
    var powerUpTimer by remember { mutableFloatStateOf(0f) }
    var levelTimer by remember { mutableFloatStateOf(0f) }
    val targetLevelTime = remember(levelIndex) { (35f + levelIndex * 0.5f).coerceAtMost(50f) }

    // Canvas size
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var targetPlayerX by remember { mutableFloatStateOf(0.5f) }
    var targetPlayerY by remember { mutableFloatStateOf(0.82f) }

    // Entities
    val stars = remember { mutableStateListOf<Star>() }
    val lasers = remember { mutableStateListOf<Laser>() }
    val missiles = remember { mutableStateListOf<Missile>() }
    val enemies = remember { mutableStateListOf<Enemy>() }
    val enemyBullets = remember { mutableStateListOf<EnemyBullet>() }
    val powerUps = remember { mutableStateListOf<PowerUpCapsule>() }
    val particles = remember { mutableStateListOf<BlastParticle>() }
    val floatingTexts = remember { mutableStateListOf<FloatingText>() }

    var bossEntity by remember { mutableStateOf<BossEntity?>(null) }
    var spawnTimer by remember { mutableFloatStateOf(0f) }
    var laserTimer by remember { mutableFloatStateOf(0f) }

    // Floating Announcement Banner
    var announcementText by remember { mutableStateOf<String?>(null) }
    var announcementAlpha by remember { mutableFloatStateOf(0f) }

    // Initialize Starfield
    LaunchedEffect(canvasSize) {
        if (canvasSize.width > 0f && stars.isEmpty()) {
            repeat(75) {
                stars.add(
                    Star(
                        x = Random.nextFloat() * canvasSize.width,
                        y = Random.nextFloat() * canvasSize.height,
                        size = Random.nextFloat() * 3.5f + 1.5f,
                        speed = Random.nextFloat() * 120f + 40f,
                        alpha = Random.nextFloat() * 0.6f + 0.4f,
                        color = when (Random.nextInt(5)) {
                            0 -> ColorNeonCyan
                            1 -> ColorNeonPink
                            else -> Color.White
                        }
                    )
                )
            }
        }
    }

    // 60FPS Game Loop
    LaunchedEffect(Unit) {
        var lastNanos = System.nanoTime()

        while (isActive) {
            withFrameNanos { nowNanos ->
                val dt = ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.04f)
                lastNanos = nowNanos

                val w = canvasSize.width
                val h = canvasSize.height
                if (w <= 0f || h <= 0f) return@withFrameNanos

                // Powerup Timer
                if (powerUpTimer > 0f) {
                    powerUpTimer = (powerUpTimer - dt).coerceAtLeast(0f)
                    if (powerUpTimer <= 0f) activePowerUp = null
                }

                // Fade announcements
                if (announcementAlpha > 0f) {
                    announcementAlpha = (announcementAlpha - dt * 0.8f).coerceAtLeast(0f)
                }

                // 2D Movement Lerp
                playerX += (targetPlayerX - playerX) * min(1f, dt * 22f)
                playerY += (targetPlayerY - playerY) * min(1f, dt * 22f)

                val shipPx = playerX * w
                val shipPy = playerY * h

                // Move Stars
                stars.forEach { star ->
                    star.y += star.speed * dt
                    if (star.y > h) {
                        star.y = -10f
                        star.x = Random.nextFloat() * w
                    }
                }

                // Autofire Lasers
                laserTimer -= dt
                val baseFireRate = shipConfig.baseFireRate + storage.getUpgradeLevel(UpgradeType.FIRE_RATE) * 0.4f
                val fireInterval = if (activePowerUp == PowerUpType.RAPID_FIRE) (1f / (baseFireRate * 1.8f)) else (1f / baseFireRate)

                if (laserTimer <= 0f) {
                    laserTimer = fireInterval
                    SpaceBlastAudio.playLaserSound()

                    val dmg = shipConfig.baseDamage + storage.getUpgradeLevel(UpgradeType.DAMAGE) * 4f
                    lasers.add(Laser(x = shipPx - 26f, y = shipPy - 20f, damage = dmg))
                    lasers.add(Laser(x = shipPx + 26f, y = shipPy - 20f, damage = dmg))
                }

                // Update Lasers
                val laserIter = lasers.iterator()
                while (laserIter.hasNext()) {
                    val l = laserIter.next()
                    l.x += l.vx * dt
                    l.y += l.vy * dt
                    if (l.y < -50f) laserIter.remove()
                }

                // Update Missiles
                val mIter = missiles.iterator()
                while (mIter.hasNext()) {
                    val m = mIter.next()
                    m.y += m.vy * dt
                    if (m.y < -50f) mIter.remove()
                }

                // Update Level Timer
                levelTimer += dt

                // Boss Spawner for Boss Levels
                if (levelConfig.bossName != null && bossEntity == null && (levelTimer >= 15f || score >= 120)) {
                    val bHp = 600f + levelIndex * 60f
                    bossEntity = BossEntity(
                        name = levelConfig.bossName,
                        x = w / 2f,
                        y = 140f,
                        vx = 200f + levelIndex * 8f,
                        hp = bHp,
                        maxHp = bHp,
                        phase = 1
                    )
                    SpaceBlastAudio.playBossWarningSound()
                    announcementText = "⚠️ WARNING! ${levelConfig.bossName} HAS ENTERED THE SECTOR! ⚠️"
                    announcementAlpha = 1.0f
                }

                // Boss AI with 3 Distinct Phases & Attack Patterns
                bossEntity?.let { b ->
                    val hpRatio = (b.hp / b.maxHp).coerceIn(0f, 1f)

                    // Phase 1 -> Phase 2 -> Phase 3 Transitions
                    if (hpRatio > 0.66f) {
                        b.phase = 1
                    } else if (hpRatio in 0.33f..0.66f) {
                        if (b.phase == 1) {
                            b.phase = 2
                            announcementText = "⚠️ ${b.name} — PHASE 2: ATTACK OVERDRIVE! ⚠️"
                            announcementAlpha = 1.0f
                            SpaceBlastAudio.playBossWarningSound()
                        }
                    } else { // hpRatio < 0.33f
                        if (b.phase < 3) {
                            b.phase = 3
                            announcementText = "🔥 ${b.name} — FINAL PHASE 3: CRITICAL OVERDRIVE! 🔥"
                            announcementAlpha = 1.0f
                            SpaceBlastAudio.playBossWarningSound()
                        }
                    }

                    val speedMult = when (b.phase) {
                        1 -> 1.0f
                        2 -> 1.5f
                        else -> 2.0f
                    }
                    b.x += b.vx * speedMult * dt
                    if (b.x > w - 140f) { b.x = w - 140f; b.vx = -abs(b.vx) }
                    if (b.x < 140f) { b.x = 140f; b.vx = abs(b.vx) }

                    b.shootTimer -= dt
                    val shootInterval = when (b.phase) {
                        1 -> 0.80f
                        2 -> 0.55f
                        else -> 0.38f
                    }

                    if (b.shootTimer <= 0f) {
                        b.shootTimer = shootInterval
                        SpaceBlastAudio.playHitSound()

                        when (b.phase) {
                            1 -> { // Phase 1: 2-Way Plasma
                                enemyBullets.add(EnemyBullet(x = b.x - 60f, y = b.y + 40f, vx = -90f, vy = 480f))
                                enemyBullets.add(EnemyBullet(x = b.x + 60f, y = b.y + 40f, vx = 90f, vy = 480f))
                            }
                            2 -> { // Phase 2: 4-Way Plasma + Bombs
                                enemyBullets.add(EnemyBullet(x = b.x - 70f, y = b.y + 40f, vx = -180f, vy = 500f))
                                enemyBullets.add(EnemyBullet(x = b.x - 20f, y = b.y + 50f, vx = -60f, vy = 540f))
                                enemyBullets.add(EnemyBullet(x = b.x + 20f, y = b.y + 50f, vx = 60f, vy = 540f))
                                enemyBullets.add(EnemyBullet(x = b.x + 70f, y = b.y + 40f, vx = 180f, vy = 500f))
                            }
                            else -> { // Phase 3 (FINAL OVERDRIVE): 6-Way Rapid Fire Barrage!
                                enemyBullets.add(EnemyBullet(x = b.x - 90f, y = b.y + 30f, vx = -280f, vy = 520f, color = ColorBossRed))
                                enemyBullets.add(EnemyBullet(x = b.x - 45f, y = b.y + 45f, vx = -140f, vy = 550f, color = ColorBossRed))
                                enemyBullets.add(EnemyBullet(x = b.x - 15f, y = b.y + 55f, vx = -50f, vy = 580f, color = ColorNeonYellow))
                                enemyBullets.add(EnemyBullet(x = b.x + 15f, y = b.y + 55f, vx = 50f, vy = 580f, color = ColorNeonYellow))
                                enemyBullets.add(EnemyBullet(x = b.x + 45f, y = b.y + 45f, vx = 140f, vy = 550f, color = ColorBossRed))
                                enemyBullets.add(EnemyBullet(x = b.x + 90f, y = b.y + 30f, vx = 280f, vy = 520f, color = ColorBossRed))
                            }
                        }
                    }
                }

                // Spawn Enemy Formations
                if (bossEntity == null) {
                    spawnTimer -= dt
                    val interval = (1.8f - levelIndex * 0.03f).coerceAtLeast(0.6f)
                    if (spawnTimer <= 0f) {
                        spawnTimer = interval
                        val newEnemies = SpaceBlastGameEngine.spawnFormation(
                            formation = FormationType.values()[Random.nextInt(FormationType.values().size)],
                            width = w,
                            level = levelIndex
                        )
                        enemies.addAll(newEnemies)
                    }
                }

                // Update Enemies & Active Enemy Shooting Attacks
                val enemyIter = enemies.iterator()
                while (enemyIter.hasNext()) {
                    val e = enemyIter.next()
                    e.y += e.speed * dt

                    // Active Enemy Fire for ALL Enemy Types!
                    e.shootTimer -= dt
                    if (e.shootTimer <= 0f) {
                        e.shootTimer = (1.8f - levelIndex * 0.03f).coerceAtLeast(0.8f) + Random.nextFloat() * 0.8f

                        when (e.type) {
                            EnemyType.SCOUT, EnemyType.SWARM_DRONE -> {
                                enemyBullets.add(EnemyBullet(x = e.x, y = e.y + e.height / 2f, vx = 0f, vy = 420f, color = ColorNeonGreen))
                            }
                            EnemyType.INTERCEPTOR -> {
                                enemyBullets.add(EnemyBullet(x = e.x - 15f, y = e.y + e.height / 2f, vx = -120f, vy = 500f, color = ColorNeonPink))
                                enemyBullets.add(EnemyBullet(x = e.x + 15f, y = e.y + e.height / 2f, vx = 120f, vy = 500f, color = ColorNeonPink))
                            }
                            EnemyType.DESTROYER -> {
                                enemyBullets.add(EnemyBullet(x = e.x, y = e.y + e.height / 2f, vx = 0f, vy = 360f, damage = 22f, color = ColorNeonPurple))
                            }
                            EnemyType.BOMBER -> {
                                enemyBullets.add(EnemyBullet(x = e.x, y = e.y + e.height / 2f, vx = 0f, vy = 380f, damage = 18f, color = ColorNeonYellow, isBomb = true))
                            }
                            EnemyType.SNIPER -> {
                                enemyBullets.add(EnemyBullet(x = e.x, y = e.y + e.height / 2f, vx = (shipPx - e.x) * 0.8f, vy = 680f, damage = 20f, color = ColorNeonCyan))
                            }
                            EnemyType.SHIELDED_ALIEN -> {
                                enemyBullets.add(EnemyBullet(x = e.x, y = e.y + e.height / 2f, vx = 0f, vy = 440f, color = ColorNeonCyan))
                            }
                            EnemyType.KAMIKAZE -> {
                                e.y += 180f * dt
                            }
                            EnemyType.ELITE_CRAFT -> {
                                enemyBullets.add(EnemyBullet(x = e.x - 25f, y = e.y + e.height / 2f, vx = -150f, vy = 480f, color = ColorNeonYellow))
                                enemyBullets.add(EnemyBullet(x = e.x, y = e.y + e.height / 2f, vx = 0f, vy = 520f, color = ColorNeonYellow))
                                enemyBullets.add(EnemyBullet(x = e.x + 25f, y = e.y + e.height / 2f, vx = 150f, vy = 480f, color = ColorNeonYellow))
                            }
                        }
                    }

                    if (e.y > h + 60f) enemyIter.remove()
                }

                // Update Enemy Bullets
                val ebIter = enemyBullets.iterator()
                while (ebIter.hasNext()) {
                    val eb = ebIter.next()
                    eb.x += eb.vx * dt
                    eb.y += eb.vy * dt
                    if (eb.y > h + 50f) ebIter.remove()
                }

                // Laser vs Enemy Collision
                val deadEnemies = mutableSetOf<String>()
                val deadLasers = mutableSetOf<String>()

                for (l in lasers) {
                    for (e in enemies) {
                        if (deadEnemies.contains(e.id)) continue
                        if (abs(l.x - e.x) < e.width / 2f && abs(l.y - e.y) < e.height / 2f) {
                            deadLasers.add(l.id)
                            e.hp -= l.damage
                            SpaceBlastAudio.playHitSound()

                            if (e.hp <= 0f) {
                                deadEnemies.add(e.id)
                                score += e.scoreValue
                                coinsEarned += e.coinValue
                                comboCount += 1
                                specialEnergy = (specialEnergy + 0.05f).coerceAtMost(1f)
                                SpaceBlastAudio.playExplosionSound()

                                if (Random.nextFloat() < 0.20f) {
                                    powerUps.add(PowerUpCapsule(type = PowerUpType.values()[Random.nextInt(PowerUpType.values().size)], x = e.x, y = e.y))
                                }
                            }
                            break
                        }
                    }
                }

                // Laser vs Boss Collision
                bossEntity?.let { b ->
                    for (l in lasers) {
                        if (deadLasers.contains(l.id)) continue
                        if (abs(l.x - b.x) < b.width / 2f && abs(l.y - b.y) < b.height / 2f) {
                            deadLasers.add(l.id)
                            b.hp -= l.damage
                            SpaceBlastAudio.playHitSound()

                            if (b.hp <= 0f) {
                                score += 500
                                coinsEarned += 250
                                bossEntity = null
                                SpaceBlastAudio.playVictorySound()
                                onLevelCompleted(score, coinsEarned)
                                return@withFrameNanos
                            }
                        }
                    }
                }

                lasers.removeAll { deadLasers.contains(it.id) }
                enemies.removeAll { deadEnemies.contains(it.id) }

                // Enemy Bullet vs Player Collision
                if (activePowerUp != PowerUpType.INVINCIBILITY) {
                    for (eb in enemyBullets) {
                        if (abs(eb.x - shipPx) < 35f && abs(eb.y - shipPy) < 35f) {
                            SpaceBlastAudio.playDamageSound()
                            if (playerShield > 0f) {
                                playerShield = (playerShield - eb.damage).coerceAtLeast(0f)
                            } else {
                                playerHp = (playerHp - eb.damage).coerceAtLeast(0f)
                                comboCount = 0
                                if (playerHp <= 0f) {
                                    SpaceBlastAudio.playExplosionSound()
                                    onGameOver(score, coinsEarned)
                                    return@withFrameNanos
                                }
                            }
                        }
                    }
                }

                // Check Level Objective Win Condition (Requires Target Level Time 35s..50s!)
                if (levelConfig.bossName == null && levelTimer >= targetLevelTime && score >= levelConfig.targetAmount * 10) {
                    SpaceBlastAudio.playVictorySound()
                    onLevelCompleted(score, coinsEarned)
                    return@withFrameNanos
                }
            }
        }
    }

    // UI Layout Box
    Box(modifier = Modifier.fillMaxSize()) {
        // Canvas Game Renderer with Nearest-Neighbor Pixel Art Filtering!
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        if (size.width > 0 && size.height > 0) {
                            targetPlayerX = (change.position.x / size.width).coerceIn(0.08f, 0.92f)
                            targetPlayerY = (change.position.y / size.height).coerceIn(0.10f, 0.88f)
                        }
                    }
                }
        ) {
            canvasSize = size
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            // Stars
            stars.forEach { s ->
                drawCircle(s.color, s.size, Offset(s.x, s.y), s.alpha)
            }

            // Lasers
            lasers.forEach { l ->
                drawRoundRect(ColorNeonCyan, Offset(l.x - l.width / 2f, l.y - l.height / 2f), Size(l.width, l.height), CornerRadius(4f, 4f))
            }

            // Enemy Bullets
            enemyBullets.forEach { eb ->
                drawCircle(eb.color, 8f, Offset(eb.x, eb.y))
            }

            // Enemies (Rendered with Nearest-Neighbor Pixel Art PNG Assets!)
            enemies.forEach { e ->
                val img = when (e.type) {
                    EnemyType.SCOUT -> scoutImage
                    EnemyType.INTERCEPTOR -> interceptorImage
                    EnemyType.DESTROYER -> destroyerImage
                    EnemyType.BOMBER -> bomberImage
                    EnemyType.SNIPER -> sniperImage
                    EnemyType.SWARM_DRONE -> swarmImage
                    EnemyType.SHIELDED_ALIEN -> shieldedImage
                    EnemyType.KAMIKAZE -> kamikazeImage
                    EnemyType.ELITE_CRAFT -> eliteImage
                }
                val renderW = if (e.type == EnemyType.ELITE_CRAFT || e.type == EnemyType.DESTROYER) 115f else 92f
                val renderH = if (e.type == EnemyType.ELITE_CRAFT || e.type == EnemyType.DESTROYER) 115f else 92f

                drawImage(
                    image = img,
                    dstOffset = IntOffset((e.x - renderW / 2f).toInt(), (e.y - renderH / 2f).toInt()),
                    dstSize = IntSize(renderW.toInt(), renderH.toInt()),
                    filterQuality = FilterQuality.None
                )
                if (e.shieldActive) {
                    drawCircle(ColorNeonCyan.copy(alpha = 0.45f), renderW / 1.3f, Offset(e.x, e.y), style = Stroke(width = 3.5f))
                }
            }

            // Boss Entity
            bossEntity?.let { b ->
                val bImg = when (levelIndex) {
                    5 -> boss1Image
                    10 -> boss2Image
                    15 -> boss3Image
                    20 -> boss4Image
                    25 -> boss5Image
                    30 -> bossFinalImage
                    else -> boss1Image
                }
                val bWidth = 320f
                val bHeight = 230f

                if (b.phase == 3) {
                    drawCircle(
                        color = ColorBossRed.copy(alpha = 0.55f),
                        radius = bWidth / 1.3f,
                        center = Offset(b.x, b.y)
                    )
                }
                drawImage(
                    image = bImg,
                    dstOffset = IntOffset((b.x - bWidth / 2f).toInt(), (b.y - bHeight / 2f).toInt()),
                    dstSize = IntSize(bWidth.toInt(), bHeight.toInt()),
                    filterQuality = FilterQuality.None
                )
            }

            // Player Spaceship (Rendered with Crisp Nearest-Neighbor Pixel Art PNG Asset!)
            val shipW = 96f
            val shipH = 96f
            drawImage(
                image = shipBitmap,
                dstOffset = IntOffset((playerX * w - shipW / 2f).toInt(), (playerY * h - shipH / 2f).toInt()),
                dstSize = IntSize(shipW.toInt(), shipH.toInt()),
                filterQuality = FilterQuality.None
            )

            // Invincibility Shield Aura
            if (activePowerUp == PowerUpType.INVINCIBILITY) {
                drawCircle(ColorNeonGreen.copy(alpha = 0.55f), 58f, Offset(playerX * w, playerY * h), style = Stroke(width = 4f))
            }
        }

        // HUD Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("SCORE: $score", color = ColorNeonYellow, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    Text("COMBO x$comboCount", color = ColorNeonPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LEVEL $levelIndex", color = ColorNeonCyan, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text("⏳ TIME: ${(targetLevelTime - levelTimer).coerceAtLeast(0f).toInt()}s", color = ColorNeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(onClick = onPause, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF220842)), shape = RoundedCornerShape(10.dp)) {
                    Text("⏸ PAUSE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Health & Shield Bars
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(0.6f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Health Bar
                Box(modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF330000))) {
                    Box(modifier = Modifier.fillMaxWidth((playerHp / maxHp).coerceIn(0f, 1f)).fillMaxHeight().background(ColorNeonGreen))
                }
                // Shield Bar
                Box(modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF002233))) {
                    Box(modifier = Modifier.fillMaxWidth((playerShield / maxShield).coerceIn(0f, 1f)).fillMaxHeight().background(ColorNeonCyan))
                }
            }

            // Boss Health Bar with Phase Indicator
            bossEntity?.let { b ->
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xE62B000C))
                        .border(1.5.dp, if (b.phase == 3) ColorNeonYellow else ColorBossRed, RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "${b.name} — PHASE ${b.phase}/3 ${if (b.phase == 3) "🔥 OVERDRIVE" else ""}",
                            color = if (b.phase == 3) ColorNeonYellow else ColorBossRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text("${b.hp.toInt()} / ${b.maxHp.toInt()}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val barColors = when (b.phase) {
                        1 -> listOf(ColorNeonCyan, ColorNeonBlue)
                        2 -> listOf(ColorNeonPink, ColorNeonYellow)
                        else -> listOf(ColorBossRed, ColorNeonYellow)
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF4A0014))) {
                        Box(modifier = Modifier.fillMaxWidth((b.hp / b.maxHp).coerceIn(0f, 1f)).fillMaxHeight().background(Brush.horizontalGradient(barColors)))
                    }
                }
            }
        }

        // On-Screen Announcement Banner
        if (announcementAlpha > 0f && announcementText != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 110.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xE62A004D))
                        .border(2.dp, ColorNeonYellow, RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = announcementText!!,
                        color = ColorNeonYellow,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Bottom Special Ability Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Button(
                onClick = {
                    if (specialEnergy >= 1.0f) {
                        specialEnergy = 0f
                        SpaceBlastAudio.playSpecialAbilitySound()

                        // Release Missile Barrage!
                        for (angleDeg in 0..360 step 30) {
                            val rad = Math.toRadians(angleDeg.toDouble())
                            missiles.add(
                                Missile(
                                    x = playerX * canvasSize.width,
                                    y = playerY * canvasSize.height,
                                    vx = cos(rad).toFloat() * 600f,
                                    vy = sin(rad).toFloat() * 600f
                                )
                            )
                        }
                    }
                },
                enabled = specialEnergy >= 1.0f,
                colors = ButtonDefaults.buttonColors(containerColor = if (specialEnergy >= 1f) ColorNeonYellow else Color.Gray),
                shape = CircleShape,
                modifier = Modifier
                    .size(68.dp)
                    .shadow(8.dp, CircleShape)
            ) {
                Text(
                    text = if (specialEnergy >= 1f) "⚡\nREADY" else "${(specialEnergy * 100).toInt()}%",
                    color = Color(0xFF070012),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ==========================================
// --- 7. OVERLAY & MENUS ---
// ==========================================
@Composable
private fun PauseOverlayScreen(
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onMainMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF0180232))
                .border(2.dp, ColorNeonCyan, RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("GAME PAUSED", color = ColorNeonCyan, fontSize = 28.sp, fontWeight = FontWeight.Black)

            Button(onClick = onResume, colors = ButtonDefaults.buttonColors(containerColor = ColorNeonCyan), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("RESUME", color = Color(0xFF070012), fontWeight = FontWeight.Bold)
            }
            Button(onClick = onRestart, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF220842)), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("RESTART LEVEL", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Button(onClick = onMainMenu, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), modifier = Modifier.fillMaxWidth().height(44.dp)) {
                Text("MAIN MENU", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GameOverScreen(
    score: Int,
    highScore: Int,
    onRetry: () -> Unit,
    onMainMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.80f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF0180232))
                .border(2.dp, ColorNeonPink, RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("GAME OVER", color = ColorNeonPink, fontSize = 32.sp, fontWeight = FontWeight.Black)
            Text("SCORE: $score", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("BEST SCORE: $highScore", color = ColorNeonCyan, fontSize = 14.sp)

            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = ColorNeonPink), modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text("RETRY LEVEL", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Button(onClick = onMainMenu, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), modifier = Modifier.fillMaxWidth().height(44.dp)) {
                Text("MAIN MENU", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LevelVictoryScreen(
    levelIndex: Int,
    score: Int,
    coinsEarned: Int,
    onNextLevel: () -> Unit,
    onMainMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.80f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF0180232))
                .border(2.dp, ColorNeonGreen, RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("🎉 LEVEL $levelIndex COMPLETE!", color = ColorNeonGreen, fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text("SCORE: $score", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("REWARD: 🪙 +$coinsEarned CREDITS", color = ColorNeonYellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)

            Button(onClick = onNextLevel, colors = ButtonDefaults.buttonColors(containerColor = ColorNeonGreen), modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text("NEXT LEVEL ►", color = Color(0xFF070012), fontWeight = FontWeight.Bold)
            }
            Button(onClick = onMainMenu, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), modifier = Modifier.fillMaxWidth().height(44.dp)) {
                Text("MAIN MENU", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
            }
        }
    }
}
