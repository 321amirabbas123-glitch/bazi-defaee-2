package com.example.model

import com.squareup.moshi.JsonClass

enum class MatchState {
    MAIN_MENU,
    CREATE_JOIN,
    LOBBY,
    HOST_CONFIGURATION,
    DEPLOYMENT_PHASE,
    READY_PHASE,
    GAMEPLAY_LOOP,
    VICTORY_SCREEN,
    CONNECTION_LOST
}

enum class PlayerType {
    HOST, CLIENT
}

enum class TerrainType {
    PLAINS, HILLS, WATER, MOUNTAINS
}

enum class FogOfWarMode {
    FULL_VISIBILITY,
    FOG_OF_WAR
}

@JsonClass(generateAdapter = true)
data class HexPosState(val row: Int, val col: Int)

@JsonClass(generateAdapter = true)
data class HexTileState(
    val row: Int,
    val col: Int,
    val terrain: TerrainType,
    val hasBridge: Boolean = false,
    val hasTrench: Boolean = false,
    val trenchReductionPct: Int = 0
)

@JsonClass(generateAdapter = true)
data class UnitClassState(
    val name: String,
    val hp: Int,
    val damage: Int,
    val energy: Int,
    val visionRange: Int,
    val attackRange: Int,
    val deploymentCount: Int,
    val iconName: String
)

@JsonClass(generateAdapter = true)
data class EngineerConfigState(
    val hp: Int = 80,
    val energy: Int = 4,
    val visionRange: Int = 3,
    val attackRange: Int = 1,
    val deploymentCount: Int = 2,
    val bridgeBuildCost: Int = 2,
    val trenchBuildCost: Int = 2,
    val trenchReductionPct: Int = 35
)

@JsonClass(generateAdapter = true)
data class GameUnitState(
    val id: String,
    val className: String,
    val owner: PlayerType,
    val row: Int,
    val col: Int,
    val hp: Int,
    val maxHp: Int,
    val energy: Int,
    val maxEnergy: Int,
    val visionRange: Int,
    val attackRange: Int,
    val damage: Int,
    val isEngineer: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GameSessionState(
    val matchState: MatchState = MatchState.MAIN_MENU,
    val isHost: Boolean = true,
    val hostIp: String = "127.0.0.1",
    val rows: Int = 7,
    val cols: Int = 7,
    val tiles: List<HexTileState> = emptyList(),
    val hostDeployedUnits: List<GameUnitState> = emptyList(),
    val clientDeployedUnits: List<GameUnitState> = emptyList(),
    val unitClasses: List<UnitClassState> = emptyList(),
    val engineerConfig: EngineerConfigState = EngineerConfigState(),
    val fogOfWarMode: FogOfWarMode = FogOfWarMode.FULL_VISIBILITY,
    val movementCostPlains: Int = 1,
    val movementCostHills: Int = 2,
    val movementCostWaterWithBridge: Int = 1,
    val activeTurn: PlayerType = PlayerType.HOST,
    val hostCastleHp: Int = 100,
    val hostCastleMaxHp: Int = 100,
    val clientCastleHp: Int = 100,
    val clientCastleMaxHp: Int = 100,
    val hostCastlePos: HexPosState = HexPosState(3, 0),
    val clientCastlePos: HexPosState = HexPosState(3, 6),
    val hostReady: Boolean = false,
    val clientReady: Boolean = false,
    val winner: PlayerType? = null,
    val statusMessage: String = "Welcome! Configure your match."
)

@JsonClass(generateAdapter = true)
data class NetworkEvent(
    val type: String, // "SYNC", "CMD"
    val data: String
)

@JsonClass(generateAdapter = true)
data class ClientCommand(
    val action: String, // "PLACE", "REMOVE", "MOVE", "ATTACK", "BUILD_BRIDGE", "BUILD_TRENCH", "READY_TOGGLE", "END_TURN", "START_DEPLOYMENT"
    val unitId: String? = null,
    val className: String? = null,
    val row: Int = 0,
    val col: Int = 0,
    val targets: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PresetState(
    val name: String,
    val rows: Int,
    val cols: Int,
    val tiles: List<HexTileState>,
    val hostCastlePos: HexPosState,
    val clientCastlePos: HexPosState,
    val unitClasses: List<UnitClassState>,
    val engineerConfig: EngineerConfigState,
    val fogOfWarMode: FogOfWarMode,
    val movementCostPlains: Int,
    val movementCostHills: Int,
    val movementCostWaterWithBridge: Int
)
