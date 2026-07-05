package com.example.model

import android.content.Context
import android.util.Log
import com.example.network.GameJsonParser
import java.io.File

class PresetManager(private val context: Context) {
    private val TAG = "PresetManager"
    private val presetsDir = File(context.filesDir, "presets").apply { mkdirs() }

    fun savePreset(preset: PresetState) {
        try {
            val file = File(presetsDir, "${preset.name}.json")
            val json = GameJsonParser.presetToJson(preset)
            file.writeText(json)
            Log.d(TAG, "Saved preset: ${preset.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving preset", e)
        }
    }

    fun loadPreset(name: String): PresetState? {
        try {
            val file = File(presetsDir, "$name.json")
            if (!file.exists()) return null
            val json = file.readText()
            return GameJsonParser.presetFromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading preset $name", e)
        }
        return null
    }

    fun listPresets(): List<String> {
        val files = presetsDir.listFiles { _, name -> name.endsWith(".json") }
        val customList = files?.map { it.nameWithoutExtension }?.filter { it != "Autosave_Current_Preset" } ?: emptyList()
        return listOf("Default Small", "Default Alpine", "Default River") + customList
    }

    fun loadDefaultPreset(name: String): PresetState {
        return when (name) {
            "Default Small" -> createDefaultPresetState("Default Small", 6, 6)
            "Default Alpine" -> createDefaultPresetState("Default Alpine", 8, 8, withAlpineMountains = true)
            else -> createDefaultPresetState("Default River", 8, 10, withRiver = true)
        }
    }

    fun saveAutosave(preset: PresetState) {
        val autosavePreset = preset.copy(name = "Autosave_Current_Preset")
        savePreset(autosavePreset)
    }

    fun loadAutosave(): PresetState? {
        return loadPreset("Autosave_Current_Preset")
    }

    private fun createDefaultPresetState(
        name: String,
        rows: Int,
        cols: Int,
        withAlpineMountains: Boolean = false,
        withRiver: Boolean = false
    ): PresetState {
        val tiles = mutableListOf<HexTileState>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                var terrain = TerrainType.PLAINS
                if (withAlpineMountains) {
                    // A mountain barrier with 1 central pass, and hills flanking
                    if (c == cols / 2 && r != 2 && r != rows - 3) {
                        terrain = TerrainType.MOUNTAINS
                    } else if (r == rows / 2 && c != 1 && c != cols - 2) {
                        terrain = TerrainType.HILLS
                    }
                } else if (withRiver) {
                    // River running down the center
                    if (c == cols / 2) {
                        terrain = TerrainType.WATER
                    } else if (r == 1 || r == rows - 2) {
                        terrain = TerrainType.HILLS
                    }
                } else {
                    // Small skirmish map
                    if ((r == 1 && c == 2) || (r == rows - 2 && c == cols - 3)) {
                        terrain = TerrainType.WATER
                    } else if ((r == 2 && c == 1) || (r == rows - 3 && c == cols - 2)) {
                        terrain = TerrainType.MOUNTAINS
                    } else if (r == rows / 2 && c == cols / 2) {
                        terrain = TerrainType.HILLS
                    }
                }
                tiles.add(HexTileState(r, c, terrain))
            }
        }
        return PresetState(
            name = name,
            rows = rows,
            cols = cols,
            tiles = tiles,
            hostCastlePos = HexPosState(rows / 2, 0),
            clientCastlePos = HexPosState(rows / 2, cols - 1),
            unitClasses = listOf(
                UnitClassState("Soldier", 100, 30, 4, 3, 1, 2, "Soldier"),
                UnitClassState("Archer", 70, 20, 3, 4, 3, 2, "Archer"),
                UnitClassState("Knight", 120, 40, 5, 2, 1, 1, "Knight")
            ),
            engineerConfig = EngineerConfigState(),
            fogOfWarMode = FogOfWarMode.FULL_VISIBILITY,
            movementCostPlains = 1,
            movementCostHills = 2,
            movementCostWaterWithBridge = 1
        )
    }
}
