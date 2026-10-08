package com.sk.multigamearcade.spaceblast

import androidx.compose.ui.graphics.Color
import com.sk.multigamearcade.R
import java.util.UUID

// --- Navigation & UI Screen States ---
enum class SpaceBlastScreenState {
    MAIN_MENU,
    LEVEL_SELECT,
    HANGAR,
    UPGRADES,
    MISSIONS,
    PLAYING,
    PAUSED,
    GAME_OVER,
    VICTORY
}

// --- Worlds & Environments ---
enum class WorldType(val displayName: String, val bgTop: Color, val bgBottom: Color) {
    DEEP_SPACE("World 1: Deep Space Sector", Color(0xFF070012), Color(0xFF14002A)),
    NEBULOUS_CORE("World 2: Neon Nebula", Color(0xFF0F001A), Color(0xFF2A003B)),
    ASTEROID_FIELD("World 3: Asteroid Belt", Color(0xFF140D00), Color(0xFF2E1900)),
    ALIEN_SECTOR("World 4: Alien Domain", Color(0xFF00121A), Color(0xFF002B2E)),
    DARK_VOID("World 5: Dark Void Citadel", Color(0xFF120000), Color(0xFF2B0000))
}

// --- Level Objectives ---
enum class ObjectiveType(val label: String) {
    DESTROY_ENEMIES("Destroy Alien Fleet"),
    SURVIVE_TIME("Survive Invasion"),
    DEFEAT_BOSS("Defeat Mega Boss"),
    COLLECT_POWERUPS("Collect Power Capsules"),
    ACHIEVE_COMBO("Reach Combo Streak")
}

data class LevelConfig(
    val levelIndex: Int, // 1 to 30
    val worldType: WorldType,
    val levelName: String,
    val objectiveType: ObjectiveType,
    val targetAmount: Int, // E.g., 20 enemies, 60 seconds, etc.
    val bossName: String? = null,
    val bossHp: Int = 0,
    val isUnlocked: Boolean = false,
    val starRating: Int = 0
)

// --- Playable Ships Configuration ---
data class SpaceshipConfig(
    val id: String,
    val name: String,
    val description: String,
    val price: Int,
    val baseHp: Float,
    val baseShield: Float,
    val baseDamage: Float,
    val baseFireRate: Float, // Shots per second
    val baseSpeed: Float,
    val specialAbilityName: String,
    val primaryColor: Color,
    val accentColor: Color,
    val drawableResId: Int,
    val isUnlocked: Boolean = false
)

// --- Upgrades ---
enum class UpgradeType(val title: String, val description: String) {
    DAMAGE("Weapon Damage", "Increases laser damage per shot"),
    FIRE_RATE("Fire Rate", "Increases autofire shooting speed"),
    MAX_HEALTH("Hull Armor", "Increases maximum hull integrity"),
    MAX_SHIELD("Shield Capacity", "Increases energy shield capacity"),
    SPEED("Engine Thrusters", "Increases maneuverability speed"),
    SPECIAL_CD("Special Cooldown", "Reduces special ability recharge time"),
    CRIT_CHANCE("Critical Hit", "Increases critical hit chance")
}

// --- Missions ---
data class Mission(
    val id: String,
    val title: String,
    val description: String,
    val rewardCoins: Int,
    val currentProgress: Int,
    val targetProgress: Int,
    val isClaimed: Boolean = false
)

// --- Collectible Power-Ups ---
enum class PowerUpType(val displayName: String, val color: Color) {
    WEAPON_UPGRADE("WEAPON UPGRADE", Color(0xFFFFE500)),
    RAPID_FIRE("RAPID FIRE", Color(0xFF00FFCC)),
    SHIELD_REPAIR("SHIELD REPAIR", Color(0xFF00E5FF)),
    HEALTH_RESTORE("HEALTH RESTORE", Color(0xFF39FF14)),
    MISSILE_BARRAGE("MISSILE BARRAGE", Color(0xFFFF007F)),
    MULTI_SHOT("MULTI-SHOT VOLLEY", Color(0xFFFF9900)),
    INVINCIBILITY("INVINCIBILITY SHIELD", Color(0xFFB5179E)),
    MAGNET("MAGNET FIELD", Color(0xFF00FFCC)),
    SCORE_MULTIPLIER("SCORE x2 MULTIPLIER", Color(0xFFFFD700)),
    SLOW_MOTION("TIME DILATION", Color(0xFF9D4EDD))
}

// --- Enemy Types & Behaviors ---
enum class EnemyType {
    SCOUT,              // Low HP, direct rush
    INTERCEPTOR,        // Fast, dodges horizontally
    DESTROYER,          // Slow, high HP, heavy shots
    BOMBER,             // Moves into position, drops bombs
    SNIPER,             // Locks on from afar, fast laser
    SWARM_DRONE,        // Small, spawns in large formations
    SHIELDED_ALIEN,     // Temporary invincible shield
    KAMIKAZE,           // High speed kamikaze dive
    ELITE_CRAFT         // High HP, multi-pattern shooter
}

// --- Tactical Formations ---
enum class FormationType {
    RANDOM_SPAWN,
    V_FORMATION,
    DIAMOND_FORMATION,
    HORIZONTAL_LINE,
    SWARM_WAVE,
    SPIRAL_ENTRY,
    SIDE_AMBUSH
}

// --- Game Engine Entities ---
data class Star(
    var x: Float,
    var y: Float,
    val size: Float,
    val speed: Float,
    val alpha: Float,
    val color: Color
)

data class Planet(
    var x: Float,
    var y: Float,
    val radius: Float,
    val color: Color,
    val ringColor: Color? = null,
    val speed: Float = 15f
)

data class CosmicNebula(
    var x: Float,
    var y: Float,
    val radius: Float,
    val color: Color
)

data class Asteroid(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    val radius: Float,
    val speed: Float,
    var rotation: Float = 0f,
    val rotSpeed: Float = 30f
)

data class Laser(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = -1300f,
    val damage: Float = 10f,
    val isCritical: Boolean = false,
    val width: Float = 12f,
    val height: Float = 38f,
    val color: Color = Color(0xFF00FFCC)
)

data class Missile(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = -800f,
    val damage: Float = 40f
)

data class Enemy(
    val id: String = UUID.randomUUID().toString(),
    val type: EnemyType,
    var x: Float,
    var y: Float,
    val width: Float,
    val height: Float,
    var hp: Float,
    val maxHp: Float,
    val speed: Float,
    val scoreValue: Int,
    val coinValue: Int,
    val color: Color,
    var animPhase: Float = 0f,
    var shootTimer: Float = 1.5f,
    var shieldActive: Boolean = false,
    var shieldTimer: Float = 0f
)

data class EnemyBullet(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val damage: Float = 10f,
    val color: Color = Color(0xFFFF0033),
    val isBomb: Boolean = false
)

data class PowerUpCapsule(
    val id: String = UUID.randomUUID().toString(),
    val type: PowerUpType,
    var x: Float,
    var y: Float,
    val speed: Float = 150f,
    var animPhase: Float = 0f
)

data class CoinEntity(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    val value: Int = 1,
    var vx: Float = 0f,
    var vy: Float = 180f
)

data class BossEntity(
    val name: String,
    var x: Float,
    var y: Float,
    var vx: Float,
    var hp: Float,
    val maxHp: Float,
    var phase: Int = 1, // Phase 1, 2, 3
    var shootTimer: Float = 0f,
    var specialAttackTimer: Float = 0f,
    val width: Float = 260f,
    val height: Float = 180f
)

data class BlastParticle(
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
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    var x: Float,
    var y: Float,
    val color: Color,
    var alpha: Float = 1.0f,
    var vy: Float = -80f
)

fun EnemyType.getDrawableResId(): Int {
    return when (this) {
        EnemyType.SCOUT -> R.drawable.enemy_scout
        EnemyType.INTERCEPTOR -> R.drawable.enemy_interceptor
        EnemyType.DESTROYER -> R.drawable.enemy_destroyer
        EnemyType.BOMBER -> R.drawable.enemy_bomber
        EnemyType.SNIPER -> R.drawable.enemy_sniper
        EnemyType.SWARM_DRONE -> R.drawable.enemy_swarm_drone
        EnemyType.SHIELDED_ALIEN -> R.drawable.enemy_shielded
        EnemyType.KAMIKAZE -> R.drawable.enemy_kamikaze
        EnemyType.ELITE_CRAFT -> R.drawable.enemy_elite
    }
}

fun getBossDrawableResId(levelIndex: Int): Int {
    return when (levelIndex) {
        5 -> R.drawable.boss_1_void_hunter
        10 -> R.drawable.boss_2_titan_destroyer
        15 -> R.drawable.boss_3_nebula_guardian
        20 -> R.drawable.boss_4_crimson_mothership
        25 -> R.drawable.boss_5_dark_star_commander
        30 -> R.drawable.boss_final_void_emperor
        else -> R.drawable.boss_1_void_hunter
    }
}
