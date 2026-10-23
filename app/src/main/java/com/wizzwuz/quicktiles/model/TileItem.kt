package com.wizzwuz.quicktiles.model

import org.json.JSONObject
import java.util.UUID

enum class TileType {
    APPS,
    WEBSITE,
    SHORTCUT,
    ACTIVITY,
    INTENT,
    SHELL,
    FILES,
    FOLDERS
}

enum class IconSourceType {
    APP_ICON,
    PRESET,
    CUSTOM_FILE
}

data class IconConfig(
    val sourceType: IconSourceType = IconSourceType.PRESET,
    val sourceData: String = "tile_default",
    val outlineThickness: Float = 0f,
    val outlineThreshold: Float = 0.5f,
    val iconSize: Float = 32f
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("sourceType", sourceType.name)
            put("sourceData", sourceData)
            put("outlineThickness", outlineThickness.toDouble())
            put("outlineThreshold", outlineThreshold.toDouble())
            put("iconSize", iconSize.toDouble())
        }
    }

    companion object {
        fun fromJson(json: JSONObject): IconConfig {
            return IconConfig(
                sourceType = try { IconSourceType.valueOf(json.optString("sourceType", "PRESET")) } catch (e: Exception) { IconSourceType.PRESET },
                sourceData = json.optString("sourceData", "tile_default"),
                outlineThickness = json.optDouble("outlineThickness", 0.0).toFloat(),
                outlineThreshold = json.optDouble("outlineThreshold", 0.5).toFloat(),
                iconSize = json.optDouble("iconSize", 32.0).toFloat()
            )
        }
    }
}

enum class TileVisualState(val label: String) {
    ENABLED("Always Enabled"),
    DISABLED("Always Disabled"),
    TOGGLE("Toggle (Both)")
}

data class TileItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: TileType,
    val targetData: String,
    val iconConfig: IconConfig = IconConfig(),
    val isActive: Boolean = true,
    val slotNumber: Int = -1,
    val createdAt: Long = System.currentTimeMillis(),
    val visualState: TileVisualState = TileVisualState.ENABLED,
    val onCommand: String = "",
    val offCommand: String = "",
    val currentToggleOn: Boolean = true,
    val collapseOnTap: Boolean = true
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("type", type.name)
            put("targetData", targetData)
            put("iconConfig", iconConfig.toJson())
            put("isActive", isActive)
            put("slotNumber", slotNumber)
            put("createdAt", createdAt)
            put("visualState", visualState.name)
            put("onCommand", onCommand)
            put("offCommand", offCommand)
            put("currentToggleOn", currentToggleOn)
            put("collapseOnTap", collapseOnTap)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): TileItem {
            return TileItem(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", "Tile"),
                type = try { TileType.valueOf(json.optString("type", "APPS")) } catch (e: Exception) { TileType.APPS },
                targetData = json.optString("targetData", ""),
                iconConfig = if (json.has("iconConfig")) IconConfig.fromJson(json.getJSONObject("iconConfig")) else IconConfig(),
                isActive = json.optBoolean("isActive", true),
                slotNumber = json.optInt("slotNumber", -1),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                visualState = try { TileVisualState.valueOf(json.optString("visualState", "ENABLED")) } catch (e: Exception) { TileVisualState.ENABLED },
                onCommand = json.optString("onCommand", ""),
                offCommand = json.optString("offCommand", ""),
                currentToggleOn = json.optBoolean("currentToggleOn", true),
                collapseOnTap = json.optBoolean("collapseOnTap", true)
            )
        }
    }
}
