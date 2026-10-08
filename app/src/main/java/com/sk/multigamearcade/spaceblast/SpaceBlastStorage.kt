package com.sk.multigamearcade.spaceblast

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import com.sk.multigamearcade.R

/**
 * Manages persistent local storage for Space Blast using SharedPreferences.
 * Stores credits, level progress, unlocked ships, stat upgrades, and high scores.
 */
class SpaceBlastStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("SpaceBlastGamePrefs", Context.MODE_PRIVATE)

    // --- Credits / Currency ---
    fun getCredits(): Int = prefs.getInt("CREDITS", 1500) // 1500 starting bonus credits!
    fun addCredits(amount: Int) {
        val newCredits = getCredits() + amount
        prefs.edit().putInt("CREDITS", newCredits).apply()
    }
    fun spendCredits(amount: Int): Boolean {
        val current = getCredits()
        if (current >= amount) {
            prefs.edit().putInt("CREDITS", current - amount).apply()
            return true
        }
        return false
    }

    // --- Level Unlocks & High Scores ---
    fun getUnlockedLevel(): Int = prefs.getInt("UNLOCKED_LEVEL", 1)
    fun setUnlockedLevel(level: Int) {
        if (level > getUnlockedLevel()) {
            prefs.edit().putInt("UNLOCKED_LEVEL", level.coerceAtMost(30)).apply()
        }
    }

    fun getHighScore(): Int = prefs.getInt("HIGH_SCORE", 0)
    fun saveHighScore(score: Int): Boolean {
        val current = getHighScore()
        if (score > current) {
            prefs.edit().putInt("HIGH_SCORE", score).apply()
            return true
        }
        return false
    }

    // --- Ship Unlock & Selection ---
    fun getSelectedShipId(): String = prefs.getString("SELECTED_SHIP", "SHIP_ALPHA") ?: "SHIP_ALPHA"
    fun setSelectedShipId(id: String) {
        prefs.edit().putString("SELECTED_SHIP", id).apply()
    }

    fun isShipUnlocked(id: String): Boolean {
        if (id == "SHIP_ALPHA") return true
        return prefs.getBoolean("SHIP_UNLOCKED_$id", false)
    }

    fun unlockShip(id: String) {
        prefs.edit().putBoolean("SHIP_UNLOCKED_$id", true).apply()
    }

    fun getAllShips(): List<SpaceshipConfig> {
        return listOf(
            SpaceshipConfig(
                id = "SHIP_ALPHA",
                name = "Star Alpha",
                description = "Standard balanced tactical starfighter with twin plasma cannons.",
                price = 0,
                baseHp = 100f,
                baseShield = 50f,
                baseDamage = 15f,
                baseFireRate = 3.5f,
                baseSpeed = 1.0f,
                specialAbilityName = "Missile Barrage",
                primaryColor = Color(0xFF00E5FF),
                accentColor = Color(0xFF00FFCC),
                drawableResId = R.drawable.player_ship_1_balanced,
                isUnlocked = true
            ),
            SpaceshipConfig(
                id = "SHIP_PHANTOM",
                name = "Phantom Interceptor",
                description = "High-speed stealth ship with enhanced critical strike chance.",
                price = 2500,
                baseHp = 80f,
                baseShield = 40f,
                baseDamage = 22f,
                baseFireRate = 4.2f,
                baseSpeed = 1.25f,
                specialAbilityName = "Plasma Wave",
                primaryColor = Color(0xFFFF007F),
                accentColor = Color(0xFFFFE500),
                drawableResId = R.drawable.player_ship_2_speed,
                isUnlocked = isShipUnlocked("SHIP_PHANTOM")
            ),
            SpaceshipConfig(
                id = "SHIP_DREADNOUGHT",
                name = "Aegis Dreadnought",
                description = "Heavy armored cruiser with massive shield capacity and barrier forcefield.",
                price = 5000,
                baseHp = 160f,
                baseShield = 120f,
                baseDamage = 18f,
                baseFireRate = 3.0f,
                baseSpeed = 0.85f,
                specialAbilityName = "Aegis Shield",
                primaryColor = Color(0xFF39FF14),
                accentColor = Color(0xFF00E5FF),
                drawableResId = R.drawable.player_ship_3_heavy,
                isUnlocked = isShipUnlocked("SHIP_DREADNOUGHT")
            ),
            SpaceshipConfig(
                id = "SHIP_DESTROYER",
                name = "Plasma Destroyer",
                description = "High-damage assault vessel equipped with heavy beam cannons.",
                price = 8500,
                baseHp = 110f,
                baseShield = 60f,
                baseDamage = 32f,
                baseFireRate = 3.2f,
                baseSpeed = 1.0f,
                specialAbilityName = "Mega Beam",
                primaryColor = Color(0xFFFF9900),
                accentColor = Color(0xFFFF0033),
                drawableResId = R.drawable.player_ship_4_damage,
                isUnlocked = isShipUnlocked("SHIP_DESTROYER")
            ),
            SpaceshipConfig(
                id = "SHIP_OMEGACRON",
                name = "Omegacron Starfighter",
                description = "Ultimate alien-human hybrid vessel with maximum firepower and stats.",
                price = 15000,
                baseHp = 200f,
                baseShield = 150f,
                baseDamage = 45f,
                baseFireRate = 5.0f,
                baseSpeed = 1.30f,
                specialAbilityName = "EMP Blast",
                primaryColor = Color(0xFFB5179E),
                accentColor = Color(0xFFFFE500),
                drawableResId = com.sk.multigamearcade.R.drawable.player_ship_5_advanced,
                isUnlocked = isShipUnlocked("SHIP_OMEGACRON")
            )
        )
    }

    // --- Stat Upgrades ---
    fun getUpgradeLevel(type: UpgradeType): Int = prefs.getInt("UPGRADE_${type.name}", 0)

    fun getUpgradeCost(type: UpgradeType): Int {
        val currentLvl = getUpgradeLevel(type)
        return 200 + currentLvl * 250
    }

    fun upgradeStat(type: UpgradeType): Boolean {
        val currentLvl = getUpgradeLevel(type)
        if (currentLvl >= 10) return false
        val cost = getUpgradeCost(type)
        if (spendCredits(cost)) {
            prefs.edit().putInt("UPGRADE_${type.name}", currentLvl + 1).apply()
            return true
        }
        return false
    }

    // --- Missions Progress ---
    fun getMissionProgress(missionId: String): Int = prefs.getInt("MISSION_PROGRESS_$missionId", 0)
    fun addMissionProgress(missionId: String, amount: Int) {
        val current = getMissionProgress(missionId)
        prefs.edit().putInt("MISSION_PROGRESS_$missionId", current + amount).apply()
    }
    fun isMissionClaimed(missionId: String): Boolean = prefs.getBoolean("MISSION_CLAIMED_$missionId", false)
    fun claimMission(missionId: String): Boolean {
        if (!isMissionClaimed(missionId)) {
            prefs.edit().putBoolean("MISSION_CLAIMED_$missionId", true).apply()
            return true
        }
        return false
    }

    fun getMissions(): List<Mission> {
        return listOf(
            Mission(
                id = "M1",
                title = "Alien Fleet Hunter",
                description = "Destroy 100 alien spacecraft",
                rewardCoins = 500,
                currentProgress = getMissionProgress("M1"),
                targetProgress = 100,
                isClaimed = isMissionClaimed("M1")
            ),
            Mission(
                id = "M2",
                title = "Boss Slayer",
                description = "Defeat 3 Campaign Bosses",
                rewardCoins = 1000,
                currentProgress = getMissionProgress("M2"),
                targetProgress = 3,
                isClaimed = isMissionClaimed("M2")
            ),
            Mission(
                id = "M3",
                title = "Power-Up Collector",
                description = "Collect 15 Power-Up Capsules",
                rewardCoins = 600,
                currentProgress = getMissionProgress("M3"),
                targetProgress = 15,
                isClaimed = isMissionClaimed("M3")
            ),
            Mission(
                id = "M4",
                title = "Combo Master",
                description = "Achieve a 20x Kill Combo",
                rewardCoins = 800,
                currentProgress = getMissionProgress("M4"),
                targetProgress = 20,
                isClaimed = isMissionClaimed("M4")
            ),
            Mission(
                id = "M5",
                title = "Galaxy Ace",
                description = "Reach Level 10 in Campaign",
                rewardCoins = 1500,
                currentProgress = getUnlockedLevel(),
                targetProgress = 10,
                isClaimed = isMissionClaimed("M5")
            )
        )
    }

    // --- Audio Settings ---
    fun isSoundEnabled(): Boolean = prefs.getBoolean("SOUND_ENABLED", true)
    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("SOUND_ENABLED", enabled).apply()
        SpaceBlastAudio.soundEnabled = enabled
    }
}
