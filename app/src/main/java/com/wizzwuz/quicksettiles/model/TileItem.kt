package com.wizzwuz.quicksettiles.model

import org.json.JSONObject
import java.util.UUID

enum class TileType(val displayName: String) {
    APPS("Apps"),
    WEBSITE("Website"),
    SHORTCUT("Shortcut"),
    ACTIVITY("Activity"),
    INTENT("Intent"),
    SHELL("Shell"),
    FILES("Files"),
    FOLDERS("Folders"),
    COLLECTION("Collection")
}

enum class TileSortOrder(val title: String) {
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    LAST_UPDATED("Last updated"),
    TILE_NUMBER_ASC("Tile number (ascending)"),
    TILE_NUMBER_DESC("Tile number (descending)")
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
    val iconSize: Float = 32f,
    val outlineStyle: String = "OUTLINE"
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("sourceType", sourceType.name)
            put("sourceData", sourceData)
            put("outlineThickness", outlineThickness.toDouble())
            put("outlineThreshold", outlineThreshold.toDouble())
            put("iconSize", iconSize.toDouble())
            put("outlineStyle", outlineStyle)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): IconConfig {
            return IconConfig(
                sourceType = try { IconSourceType.valueOf(json.optString("sourceType", "PRESET")) } catch (e: Exception) { IconSourceType.PRESET },
                sourceData = json.optString("sourceData", "tile_default"),
                outlineThickness = json.optDouble("outlineThickness", 0.0).toFloat(),
                outlineThreshold = json.optDouble("outlineThreshold", 0.5).toFloat(),
                iconSize = json.optDouble("iconSize", 32.0).toFloat(),
                outlineStyle = json.optString("outlineStyle", "OUTLINE")
            )
        }
    }
}

enum class TileVisualState(val label: String) {
    ENABLED("Always Enabled"),
    DISABLED("Always Disabled"),
    TOGGLE("Toggle (Both)")
}

data class ShellStep(
    val name: String = "",
    val command: String = "",
    val visualState: TileVisualState = TileVisualState.ENABLED,
    val iconConfig: IconConfig = IconConfig(sourceType = IconSourceType.PRESET, sourceData = "terminal"),
    val executionMode: String = "AUTO",
    val collapsePanel: Boolean = true
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("command", command)
            put("visualState", visualState.name)
            put("iconConfig", iconConfig.toJson())
            put("executionMode", executionMode)
            put("collapsePanel", collapsePanel)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ShellStep {
            return ShellStep(
                name = json.optString("name", ""),
                command = json.optString("command", ""),
                visualState = try { TileVisualState.valueOf(json.optString("visualState", "ENABLED")) } catch (e: Exception) { TileVisualState.ENABLED },
                iconConfig = if (json.has("iconConfig")) IconConfig.fromJson(json.getJSONObject("iconConfig")) else IconConfig(sourceType = IconSourceType.PRESET, sourceData = "terminal"),
                executionMode = json.optString("executionMode", "AUTO"),
                collapsePanel = json.optBoolean("collapsePanel", true)
            )
        }
    }
}

data class TileItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: TileType,
    val targetData: String,
    val iconConfig: IconConfig = IconConfig(),
    val isActive: Boolean = true,
    val isAddedToQs: Boolean = false,
    val slotNumber: Int = -1,
    val createdAt: Long = System.currentTimeMillis(),
    val visualState: TileVisualState = TileVisualState.ENABLED,
    val onCommand: String = "",
    val offCommand: String = "",
    val currentToggleOn: Boolean = true,
    val collapseOnTap: Boolean = true,
    val onTileName: String = "",
    val offTileName: String = "",
    val onIconConfig: IconConfig? = null,
    val offIconConfig: IconConfig? = null,
    val collectionItemIds: List<String> = emptyList(),
    val collapseCollectionOnClick: Boolean = false,
    val tilesPerRow: Int = 3,
    val showPopups: Boolean = false,
    val multiShellSteps: List<ShellStep> = emptyList(),
    val currentMultiShellIndex: Int = 0
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("type", type.name)
            put("targetData", targetData)
            put("iconConfig", iconConfig.toJson())
            put("isActive", isActive)
            put("isAddedToQs", isAddedToQs)
            put("slotNumber", slotNumber)
            put("createdAt", createdAt)
            put("visualState", visualState.name)
            put("onCommand", onCommand)
            put("offCommand", offCommand)
            put("currentToggleOn", currentToggleOn)
            put("collapseOnTap", collapseOnTap)
            put("onTileName", onTileName)
            put("offTileName", offTileName)
            if (onIconConfig != null) put("onIconConfig", onIconConfig.toJson())
            if (offIconConfig != null) put("offIconConfig", offIconConfig.toJson())
            val collectionArray = org.json.JSONArray()
            collectionItemIds.forEach { collectionArray.put(it) }
            put("collectionItemIds", collectionArray)
            put("collapseCollectionOnClick", collapseCollectionOnClick)
            put("tilesPerRow", tilesPerRow)
            put("showPopups", showPopups)
            val stepsArray = org.json.JSONArray()
            multiShellSteps.forEach { stepsArray.put(it.toJson()) }
            put("multiShellSteps", stepsArray)
            put("currentMultiShellIndex", currentMultiShellIndex)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): TileItem {
            val collectionList = mutableListOf<String>()
            if (json.has("collectionItemIds")) {
                val arr = json.getJSONArray("collectionItemIds")
                for (i in 0 until arr.length()) {
                    collectionList.add(arr.optString(i))
                }
            }
            val stepsList = mutableListOf<ShellStep>()
            if (json.has("multiShellSteps")) {
                val sArr = json.getJSONArray("multiShellSteps")
                for (i in 0 until sArr.length()) {
                    stepsList.add(ShellStep.fromJson(sArr.getJSONObject(i)))
                }
            }
            return TileItem(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", "Tile"),
                type = try { TileType.valueOf(json.optString("type", "APPS")) } catch (e: Exception) { TileType.APPS },
                targetData = json.optString("targetData", ""),
                iconConfig = if (json.has("iconConfig")) IconConfig.fromJson(json.getJSONObject("iconConfig")) else IconConfig(),
                isActive = json.optBoolean("isActive", true),
                isAddedToQs = json.optBoolean("isAddedToQs", false),
                slotNumber = json.optInt("slotNumber", -1),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                visualState = try { TileVisualState.valueOf(json.optString("visualState", "ENABLED")) } catch (e: Exception) { TileVisualState.ENABLED },
                onCommand = json.optString("onCommand", ""),
                offCommand = json.optString("offCommand", ""),
                currentToggleOn = json.optBoolean("currentToggleOn", true),
                collapseOnTap = json.optBoolean("collapseOnTap", true),
                onTileName = json.optString("onTileName", ""),
                offTileName = json.optString("offTileName", ""),
                onIconConfig = if (json.has("onIconConfig")) IconConfig.fromJson(json.getJSONObject("onIconConfig")) else null,
                offIconConfig = if (json.has("offIconConfig")) IconConfig.fromJson(json.getJSONObject("offIconConfig")) else null,
                collectionItemIds = collectionList,
                collapseCollectionOnClick = json.optBoolean("collapseCollectionOnClick", false),
                tilesPerRow = json.optInt("tilesPerRow", 3),
                showPopups = json.optBoolean("showPopups", false),
                multiShellSteps = stepsList,
                currentMultiShellIndex = json.optInt("currentMultiShellIndex", 0)
            )
        }
    }
}
