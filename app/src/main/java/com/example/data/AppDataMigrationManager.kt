package com.example.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appMigrationDataStore by preferencesDataStore(name = "narendra_app_update_meta")

data class AppUpdateMeta(
    val previousVersionCode: Int = 0,
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val upgradedSafelyAtMillis: Long = 0L,
    val upgradeCount: Int = 0
)

/**
 * Non-destructive forward-only app upgrade & schema migration tracker.
 *
 * Strictly preserves all existing PrinterPreferences DataStore keys, Firebase Auth sessions,
 * Cloud Firestore user records (shops, Mohar, signature, customers, stock, invoices),
 * and offline caches across in-place APK upgrades.
 */
object AppDataMigrationManager {

    private const val TAG = "AppMigration"
    private const val CURRENT_DATA_SCHEMA_VERSION = 4

    private val keyLastVersionCode = intPreferencesKey("last_version_code")
    private val keyPreviousVersionCode = intPreferencesKey("previous_version_code")
    private val keyLastVersionName = stringPreferencesKey("last_version_name")
    private val keySchemaVersion = intPreferencesKey("data_schema_version")
    private val keyUpgradedAtMillis = longPreferencesKey("upgraded_at_millis")
    private val keyUpgradeCount = intPreferencesKey("upgrade_count")

    fun observeMeta(context: Context): Flow<AppUpdateMeta> =
        context.applicationContext.appMigrationDataStore.data.map { prefs ->
            AppUpdateMeta(
                previousVersionCode = prefs[keyPreviousVersionCode] ?: 0,
                currentVersionCode = prefs[keyLastVersionCode] ?: BuildConfig.VERSION_CODE,
                currentVersionName = prefs[keyLastVersionName] ?: BuildConfig.VERSION_NAME,
                upgradedSafelyAtMillis = prefs[keyUpgradedAtMillis] ?: 0L,
                upgradeCount = prefs[keyUpgradeCount] ?: 0
            )
        }

    /**
     * Records a forward-only version transition without clearing or resetting any user data.
     */
    suspend fun recordSafeLaunch(
        context: Context,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        currentVersionName: String = BuildConfig.VERSION_NAME
    ): AppUpdateMeta {
        var result = AppUpdateMeta(
            currentVersionCode = currentVersionCode,
            currentVersionName = currentVersionName
        )
        try {
            context.applicationContext.appMigrationDataStore.edit { prefs ->
                val storedCode = prefs[keyLastVersionCode] ?: 0
                val storedSchema = prefs[keySchemaVersion] ?: 1
                val storedUpgrades = prefs[keyUpgradeCount] ?: 0
                val now = System.currentTimeMillis()

                // Forward-only schema steps (strictly additive, never destructive)
                if (storedSchema < CURRENT_DATA_SCHEMA_VERSION) {
                    Log.i(TAG, "Safely migrating local metadata schema $storedSchema -> $CURRENT_DATA_SCHEMA_VERSION (preserving all data)")
                    prefs[keySchemaVersion] = CURRENT_DATA_SCHEMA_VERSION
                }

                if (storedCode in 1 until currentVersionCode) {
                    prefs[keyPreviousVersionCode] = storedCode
                    prefs[keyUpgradedAtMillis] = now
                    prefs[keyUpgradeCount] = storedUpgrades + 1
                    Log.i(TAG, "Safe in-place APK upgrade detected: vCode $storedCode -> $currentVersionCode ($currentVersionName)")
                } else if (storedCode == 0) {
                    // First launch with migration tracker (could be fresh install OR upgrade from legacy v1.0)
                    prefs[keyPreviousVersionCode] = 1
                    prefs[keyUpgradedAtMillis] = now
                }

                prefs[keyLastVersionCode] = currentVersionCode
                prefs[keyLastVersionName] = currentVersionName

                result = AppUpdateMeta(
                    previousVersionCode = prefs[keyPreviousVersionCode] ?: 0,
                    currentVersionCode = currentVersionCode,
                    currentVersionName = currentVersionName,
                    upgradedSafelyAtMillis = prefs[keyUpgradedAtMillis] ?: now,
                    upgradeCount = prefs[keyUpgradeCount] ?: 0
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Non-fatal migration metadata check skipped: ${e.message}")
        }
        return result
    }

    /**
     * Resolves a monotonic versionCode floor so upgrades never downgrade below baseVersionCode.
     */
    fun resolveMonotonicVersionCode(baseVersionCode: Int, ciRunCode: Int?): Int {
        val safeBase = baseVersionCode.coerceAtLeast(1)
        val safeCi = ciRunCode?.coerceAtLeast(1) ?: safeBase
        return maxOf(safeBase, safeCi)
    }
}
