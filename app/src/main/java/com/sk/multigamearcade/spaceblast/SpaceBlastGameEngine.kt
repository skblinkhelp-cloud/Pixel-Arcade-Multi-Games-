package com.sk.multigamearcade.spaceblast

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Core Physics, Formation Spawner, Enemy AI, Boss Patterns, and Combo Engine for Space Blast.
 */
object SpaceBlastGameEngine {

    // --- Generate 30 Campaign Level Configurations ---
    fun getLevelConfigurations(): List<LevelConfig> {
        val list = mutableListOf<LevelConfig>()

        for (i in 1..30) {
            val world = when {
                i <= 6 -> WorldType.DEEP_SPACE
                i <= 12 -> WorldType.NEBULOUS_CORE
                i <= 18 -> WorldType.ASTEROID_FIELD
                i <= 24 -> WorldType.ALIEN_SECTOR
                else -> WorldType.DARK_VOID
            }

            val isBossLevel = (i % 5 == 0)
            val bossName = when (i) {
                5 -> "👾 VOID HUNTER"
                10 -> "👾 TITAN DESTROYER"
                15 -> "👾 NEBULA GUARDIAN"
                20 -> "👾 CRIMSON MOTHERSHIP"
                25 -> "👾 DARK STAR COMMANDER"
                30 -> "👑 THE VOID EMPEROR"
                else -> null
            }
            val bossHp = if (isBossLevel) 600 + i * 50 else 0

            val objType = when {
                isBossLevel -> ObjectiveType.DEFEAT_BOSS
                i % 3 == 0 -> ObjectiveType.SURVIVE_TIME
                i % 4 == 0 -> ObjectiveType.COLLECT_POWERUPS
                else -> ObjectiveType.DESTROY_ENEMIES
            }

            val targetAmt = when (objType) {
                ObjectiveType.DEFEAT_BOSS -> 1
                ObjectiveType.SURVIVE_TIME -> 40 + (i * 2)
                ObjectiveType.COLLECT_POWERUPS -> 3 + (i / 5)
                ObjectiveType.ACHIEVE_COMBO -> 10 + (i / 2)
                ObjectiveType.DESTROY_ENEMIES -> 15 + (i * 3)
            }

            list.add(
                LevelConfig(
                    levelIndex = i,
                    worldType = world,
                    levelName = if (isBossLevel) "Level $i: BOSS ENCOUNTER" else "Level $i: Sector $i",
                    objectiveType = objType,
                    targetAmount = targetAmt,
                    bossName = bossName,
                    bossHp = bossHp
                )
            )
        }
        return list
    }

    // --- Spawn Enemy Formations ---
    fun spawnFormation(
        formation: FormationType,
        width: Float,
        level: Int
    ): List<Enemy> {
        val list = mutableListOf<Enemy>()
        val speedMult = 1.0f + (level * 0.05f)

        when (formation) {
            FormationType.V_FORMATION -> {
                val startX = width / 2f
                val spacingX = 70f
                val spacingY = 50f
                for (i in -2..2) {
                    val x = startX + i * spacingX
                    val y = -100f - abs(i) * spacingY
                    list.add(createEnemy(EnemyType.SCOUT, x, y, speedMult))
                }
            }
            FormationType.DIAMOND_FORMATION -> {
                val startX = width / 2f
                val offsets = listOf(
                    Pair(0f, -140f),
                    Pair(-70f, -80f),
                    Pair(70f, -80f),
                    Pair(0f, -20f)
                )
                offsets.forEach { (dx, dy) ->
                    list.add(createEnemy(EnemyType.INTERCEPTOR, startX + dx, dy, speedMult))
                }
            }
            FormationType.HORIZONTAL_LINE -> {
                val count = 5
                val step = width / (count + 1)
                for (i in 1..count) {
                    list.add(createEnemy(EnemyType.DESTROYER, i * step, -100f, speedMult))
                }
            }
            FormationType.SWARM_WAVE -> {
                val startX = Random.nextFloat() * (width - 200f) + 100f
                for (i in 0..6) {
                    val x = startX + (i % 3) * 50f
                    val y = -80f - (i / 3) * 50f
                    list.add(createEnemy(EnemyType.SWARM_DRONE, x, y, speedMult))
                }
            }
            FormationType.SIDE_AMBUSH -> {
                list.add(createEnemy(EnemyType.KAMIKAZE, 80f, -80f, speedMult))
                list.add(createEnemy(EnemyType.KAMIKAZE, width - 80f, -80f, speedMult))
                list.add(createEnemy(EnemyType.ELITE_CRAFT, width / 2f, -120f, speedMult))
            }
            else -> {
                val rx = Random.nextFloat() * (width - 160f) + 80f
                val type = when (Random.nextInt(5)) {
                    0 -> EnemyType.SCOUT
                    1 -> EnemyType.INTERCEPTOR
                    2 -> EnemyType.BOMBER
                    3 -> EnemyType.SNIPER
                    else -> EnemyType.ELITE_CRAFT
                }
                list.add(createEnemy(type, rx, -90f, speedMult))
            }
        }
        return list
    }

    private fun createEnemy(type: EnemyType, x: Float, y: Float, speedMult: Float): Enemy {
        val (w, h, hp, spd, score, coins, color) = when (type) {
            EnemyType.SCOUT -> Tuple7(70f, 60f, 20f, 220f * speedMult, 10, 1, Color(0xFF39FF14))
            EnemyType.INTERCEPTOR -> Tuple7(70f, 60f, 15f, 310f * speedMult, 15, 2, Color(0xFFFF007F))
            EnemyType.DESTROYER -> Tuple7(100f, 85f, 60f, 140f * speedMult, 35, 5, Color(0xFFB5179E))
            EnemyType.BOMBER -> Tuple7(85f, 75f, 40f, 180f * speedMult, 25, 3, Color(0xFFFF9900))
            EnemyType.SNIPER -> Tuple7(75f, 65f, 30f, 160f * speedMult, 30, 4, Color(0xFF00FFCC))
            EnemyType.SWARM_DRONE -> Tuple7(55f, 50f, 10f, 280f * speedMult, 8, 1, Color(0xFFFFE500))
            EnemyType.SHIELDED_ALIEN -> Tuple7(85f, 75f, 45f, 170f * speedMult, 40, 6, Color(0xFF00E5FF))
            EnemyType.KAMIKAZE -> Tuple7(65f, 55f, 15f, 380f * speedMult, 20, 2, Color(0xFFFF0033))
            EnemyType.ELITE_CRAFT -> Tuple7(110f, 95f, 100f, 150f * speedMult, 75, 12, Color(0xFFFFD700))
        }

        return Enemy(
            type = type,
            x = x,
            y = y,
            width = w,
            height = h,
            hp = hp,
            maxHp = hp,
            speed = spd,
            scoreValue = score,
            coinValue = coins,
            color = color,
            shieldActive = (type == EnemyType.SHIELDED_ALIEN)
        )
    }

    private data class Tuple7<A, B, C, D, E, F, G>(
        val a: A, val b: B, val c: C, val d: D, val e: E, val f: F, val g: G
    )
}
