package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.*
import com.example.network.GameJsonParser
import com.example.network.GameNetworkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "GameViewModel"
    private val context = application.applicationContext
    private val presetManager = PresetManager(context)

    private val _uiState = MutableStateFlow(GameSessionState())
    val uiState: StateFlow<GameSessionState> = _uiState.asStateFlow()

    private var networkManager: GameNetworkManager? = null

    // For local interactive state during gameplay
    private val _selectedUnitId = MutableStateFlow<String?>(null)
    val selectedUnitId: StateFlow<String?> = _selectedUnitId.asStateFlow()

    private val _targetSelectionMode = MutableStateFlow(false)
    val targetSelectionMode: StateFlow<Boolean> = _targetSelectionMode.asStateFlow()

    private val _selectedTargetIds = MutableStateFlow<List<String>>(emptyList())
    val selectedTargetIds: StateFlow<List<String>> = _selectedTargetIds.asStateFlow()

    init {
        // Try to load autosave initially to prevent loss of progress
        loadAutosavePreset()
    }

    private fun loadAutosavePreset() {
        viewModelScope.launch {
            val autosave = presetManager.loadAutosave()
            if (autosave != null) {
                applyPresetToState(autosave)
            } else {
                // Apply default preset
                val defaultPreset = presetManager.loadDefaultPreset("Default Small")
                applyPresetToState(defaultPreset)
            }
        }
    }

    private fun applyPresetToState(preset: PresetState) {
        _uiState.update { current ->
            current.copy(
                rows = preset.rows,
                cols = preset.cols,
                tiles = preset.tiles,
                unitClasses = preset.unitClasses,
                engineerConfig = preset.engineerConfig,
                fogOfWarMode = preset.fogOfWarMode,
                movementCostPlains = preset.movementCostPlains,
                movementCostHills = preset.movementCostHills,
                movementCostWaterWithBridge = preset.movementCostWaterWithBridge,
                hostCastlePos = preset.hostCastlePos,
                clientCastlePos = preset.clientCastlePos,
                hostCastleHp = 100,
                clientCastleHp = 100
            )
        }
    }

    // --- NETWORKING INTERACTION ---

    fun hostGame() {
        val localIp = GameNetworkManager.getLocalIpAddress()
        _uiState.update { it.copy(isHost = true, hostIp = localIp, matchState = MatchState.CREATE_JOIN) }
        
        networkManager = GameNetworkManager(
            onMessageReceived = { json -> handleIncomingNetworkMessage(json) },
            onConnectionStateChanged = { status -> handleConnectionStatusChange(status) }
        )
        networkManager?.startServer()
    }

    fun joinGame(hostIp: String) {
        _uiState.update { it.copy(isHost = false, hostIp = hostIp, matchState = MatchState.CREATE_JOIN) }
        
        networkManager = GameNetworkManager(
            onMessageReceived = { json -> handleIncomingNetworkMessage(json) },
            onConnectionStateChanged = { status -> handleConnectionStatusChange(status) }
        )
        networkManager?.connectToHost(hostIp)
    }

    fun disconnect() {
        networkManager?.disconnect()
        _uiState.update { GameSessionState() }
        _selectedUnitId.value = null
        _targetSelectionMode.value = false
        _selectedTargetIds.value = emptyList()
        loadAutosavePreset()
    }

    private fun handleConnectionStatusChange(status: GameNetworkManager.ConnectionStatus) {
        viewModelScope.launch {
            when (status) {
                GameNetworkManager.ConnectionStatus.HOST_LISTENING -> {
                    _uiState.update { it.copy(statusMessage = "Waiting for client connection...") }
                }
                GameNetworkManager.ConnectionStatus.CONNECTED -> {
                    _uiState.update { current ->
                        val newState = current.copy(
                            matchState = MatchState.LOBBY,
                            statusMessage = "Players connected! Ready to configure."
                        )
                        if (current.isHost) {
                            syncStateToClient(newState)
                        }
                        newState
                    }
                }
                GameNetworkManager.ConnectionStatus.DISCONNECTED -> {
                    val current = _uiState.value
                    if (current.matchState != MatchState.MAIN_MENU && current.matchState != MatchState.VICTORY_SCREEN) {
                        _uiState.update { it.copy(matchState = MatchState.CONNECTION_LOST, statusMessage = "Connection lost!") }
                    }
                }
            }
        }
    }

    private fun handleIncomingNetworkMessage(json: String) {
        viewModelScope.launch {
            try {
                val event = GameJsonParser.eventFromJson(json) ?: return@launch
                if (_uiState.value.isHost) {
                    // Host handles incoming Client commands
                    if (event.type == "CMD") {
                        val cmd = GameJsonParser.commandFromJson(event.data) ?: return@launch
                        executeClientCommandOnHost(cmd)
                    }
                } else {
                    // Client handles incoming Host state syncs
                    if (event.type == "SYNC") {
                        val syncedState = GameJsonParser.fromJson(event.data) ?: return@launch
                        _uiState.update { current ->
                            // Keep Client-side specific attributes
                            syncedState.copy(isHost = false)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing incoming network message", e)
            }
        }
    }

    private fun syncStateToClient(state: GameSessionState) {
        if (!state.isHost) return
        val jsonState = GameJsonParser.toJson(state)
        val event = NetworkEvent(type = "SYNC", data = jsonState)
        networkManager?.sendMessage(GameJsonParser.eventToJson(event))
    }

    private fun sendCommandToHost(cmd: ClientCommand) {
        val jsonCmd = GameJsonParser.commandToJson(cmd)
        val event = NetworkEvent(type = "CMD", data = jsonCmd)
        networkManager?.sendMessage(GameJsonParser.eventToJson(event))
    }

    // --- CONFIGURATION MANAGEMENT (HOST ONLY) ---

    fun selectPreset(presetName: String) {
        if (!_uiState.value.isHost) return
        val preset = if (presetName.startsWith("Default ")) {
            presetManager.loadDefaultPreset(presetName)
        } else {
            presetManager.loadPreset(presetName)
        }
        if (preset != null) {
            applyPresetToState(preset)
            _uiState.update { it.copy(statusMessage = "Preset '$presetName' loaded.") }
            autoSaveAndSync()
        }
    }

    fun saveCustomPreset(presetName: String) {
        if (!_uiState.value.isHost) return
        val current = _uiState.value
        val preset = PresetState(
            name = presetName,
            rows = current.rows,
            cols = current.cols,
            tiles = current.tiles,
            hostCastlePos = current.hostCastlePos,
            clientCastlePos = current.clientCastlePos,
            unitClasses = current.unitClasses,
            engineerConfig = current.engineerConfig,
            fogOfWarMode = current.fogOfWarMode,
            movementCostPlains = current.movementCostPlains,
            movementCostHills = current.movementCostHills,
            movementCostWaterWithBridge = current.movementCostWaterWithBridge
        )
        presetManager.savePreset(preset)
        _uiState.update { it.copy(statusMessage = "Preset '$presetName' saved.") }
    }

    fun listSavedPresets(): List<String> {
        return presetManager.listPresets()
    }

    fun updateGridSize(rows: Int, cols: Int) {
        if (!_uiState.value.isHost) return
        if (rows < 2 || cols < 2) return // reject invalid values
        
        _uiState.update { current ->
            // Re-generate tiles to match size
            val newTiles = mutableListOf<HexTileState>()
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    // Retain old terrain if still within bounds
                    val existing = current.tiles.firstOrNull { it.row == r && it.col == c }
                    if (existing != null) {
                        newTiles.add(existing)
                    } else {
                        newTiles.add(HexTileState(r, c, TerrainType.PLAINS))
                    }
                }
            }
            
            // Re-adjust castle locations if out of bounds
            val hCastle = if (current.hostCastlePos.row < rows && current.hostCastlePos.col < cols) current.hostCastlePos else HexPosState(rows / 2, 0)
            val cCastle = if (current.clientCastlePos.row < rows && current.clientCastlePos.col < cols) current.clientCastlePos else HexPosState(rows / 2, cols - 1)

            current.copy(
                rows = rows,
                cols = cols,
                tiles = newTiles,
                hostCastlePos = hCastle,
                clientCastlePos = cCastle
            )
        }
        autoSaveAndSync()
    }

    fun paintTileTerrain(row: Int, col: Int, terrain: TerrainType) {
        if (!_uiState.value.isHost) return
        _uiState.update { current ->
            val updatedTiles = current.tiles.map {
                if (it.row == row && it.col == col) it.copy(terrain = terrain) else it
            }
            current.copy(tiles = updatedTiles)
        }
        autoSaveAndSync()
    }

    fun setCastlePosition(player: PlayerType, row: Int, col: Int) {
        if (!_uiState.value.isHost) return
        _uiState.update { current ->
            if (player == PlayerType.HOST) {
                current.copy(hostCastlePos = HexPosState(row, col))
            } else {
                current.copy(clientCastlePos = HexPosState(row, col))
            }
        }
        autoSaveAndSync()
    }

    fun updateUnitClassConfig(index: Int, hp: Int, damage: Int, energy: Int, vision: Int, range: Int, count: Int) {
        if (!_uiState.value.isHost) return
        if (hp <= 0 || damage < 0 || energy <= 0 || vision < 0 || range <= 0 || count < 0) return // reject invalid values
        
        _uiState.update { current ->
            val updatedClasses = current.unitClasses.toMutableList()
            if (index in updatedClasses.indices) {
                updatedClasses[index] = updatedClasses[index].copy(
                    hp = hp,
                    damage = damage,
                    energy = energy,
                    visionRange = vision,
                    attackRange = range,
                    deploymentCount = count
                )
            }
            current.copy(unitClasses = updatedClasses)
        }
        autoSaveAndSync()
    }

    fun updateEngineerConfig(hp: Int, energy: Int, vision: Int, range: Int, count: Int, bridgeCost: Int, trenchCost: Int, trenchRed: Int) {
        if (!_uiState.value.isHost) return
        if (hp <= 0 || energy <= 0 || vision < 0 || range <= 0 || count < 0 || bridgeCost < 0 || trenchCost < 0 || trenchRed !in 0..100) return
        
        _uiState.update { current ->
            current.copy(
                engineerConfig = current.engineerConfig.copy(
                    hp = hp,
                    energy = energy,
                    visionRange = vision,
                    attackRange = range,
                    deploymentCount = count,
                    bridgeBuildCost = bridgeCost,
                    trenchBuildCost = trenchCost,
                    trenchReductionPct = trenchRed
                )
            )
        }
        autoSaveAndSync()
    }

    fun updateRulesConfig(fowMode: FogOfWarMode, pCost: Int, hCost: Int, wCost: Int) {
        if (!_uiState.value.isHost) return
        if (pCost < 0 || hCost < 0 || wCost < 0) return
        
        _uiState.update { current ->
            current.copy(
                fogOfWarMode = fowMode,
                movementCostPlains = pCost,
                movementCostHills = hCost,
                movementCostWaterWithBridge = wCost
            )
        }
        autoSaveAndSync()
    }

    private fun autoSaveAndSync() {
        val current = _uiState.value
        if (!current.isHost) return
        
        // Auto save to local directory
        val preset = PresetState(
            name = "Autosave_Current_Preset",
            rows = current.rows,
            cols = current.cols,
            tiles = current.tiles,
            hostCastlePos = current.hostCastlePos,
            clientCastlePos = current.clientCastlePos,
            unitClasses = current.unitClasses,
            engineerConfig = current.engineerConfig,
            fogOfWarMode = current.fogOfWarMode,
            movementCostPlains = current.movementCostPlains,
            movementCostHills = current.movementCostHills,
            movementCostWaterWithBridge = current.movementCostWaterWithBridge
        )
        presetManager.saveAutosave(preset)
        
        // Synchronize with Client in real time
        syncStateToClient(current)
    }

    // --- MATCH FLOW TRANSITIONS ---

    fun advanceToHostConfiguration() {
        if (!_uiState.value.isHost) return
        _uiState.update { it.copy(matchState = MatchState.HOST_CONFIGURATION, statusMessage = "Host is configuring the match...") }
        autoSaveAndSync()
    }

    fun lockConfigurationAndStartDeployment() {
        if (!_uiState.value.isHost) return
        _uiState.update { current ->
            val newState = current.copy(
                matchState = MatchState.DEPLOYMENT_PHASE,
                hostReady = false,
                clientReady = false,
                hostDeployedUnits = emptyList(),
                clientDeployedUnits = emptyList(),
                statusMessage = "Configuration Locked! Deployment Phase started."
            )
            syncStateToClient(newState)
            newState
        }
    }

    // --- GAMEPLAY COMMANDS (HOST-AUTHORITATIVE & LIVE-SYNCED) ---

    fun placeUnit(className: String, row: Int, col: Int) {
        val current = _uiState.value
        val player = if (current.isHost) PlayerType.HOST else PlayerType.CLIENT
        
        if (current.isHost) {
            executePlaceUnitOnHost(player, className, row, col)
        } else {
            sendCommandToHost(ClientCommand("PLACE", className = className, row = row, col = col))
        }
    }

    fun removeUnit(row: Int, col: Int) {
        val current = _uiState.value
        if (current.isHost) {
            executeRemoveUnitOnHost(row, col)
        } else {
            sendCommandToHost(ClientCommand("REMOVE", row = row, col = col))
        }
    }

    fun toggleReady() {
        val current = _uiState.value
        if (current.isHost) {
            executeToggleReadyOnHost(PlayerType.HOST)
        } else {
            sendCommandToHost(ClientCommand("READY_TOGGLE"))
        }
    }

    fun attemptStepMove(unitId: String, targetRow: Int, targetCol: Int) {
        val current = _uiState.value
        if (current.isHost) {
            executeMoveOnHost(unitId, targetRow, targetCol)
        } else {
            sendCommandToHost(ClientCommand("MOVE", unitId = unitId, row = targetRow, col = targetCol))
        }
    }

    fun attemptAttack(unitId: String, targetIds: List<String>) {
        val current = _uiState.value
        if (current.isHost) {
            executeAttackOnHost(unitId, targetIds)
        } else {
            sendCommandToHost(ClientCommand("ATTACK", unitId = unitId, targets = targetIds))
        }
    }

    fun buildBridge(unitId: String, row: Int, col: Int) {
        val current = _uiState.value
        if (current.isHost) {
            executeBuildBridgeOnHost(unitId, row, col)
        } else {
            sendCommandToHost(ClientCommand("BUILD_BRIDGE", unitId = unitId, row = row, col = col))
        }
    }

    fun buildTrench(unitId: String, row: Int, col: Int) {
        val current = _uiState.value
        if (current.isHost) {
            executeBuildTrenchOnHost(unitId, row, col)
        } else {
            sendCommandToHost(ClientCommand("BUILD_TRENCH", unitId = unitId, row = row, col = col))
        }
    }

    fun endTurn() {
        val current = _uiState.value
        if (current.isHost) {
            executeEndTurnOnHost()
        } else {
            sendCommandToHost(ClientCommand("END_TURN"))
        }
    }

    // --- HOST SIMULATION ENGINE (THE SOURCE OF TRUTH) ---

    private fun executeClientCommandOnHost(cmd: ClientCommand) {
        when (cmd.action) {
            "PLACE" -> executePlaceUnitOnHost(PlayerType.CLIENT, cmd.className ?: "", cmd.row, cmd.col)
            "REMOVE" -> executeRemoveUnitOnHost(cmd.row, cmd.col)
            "READY_TOGGLE" -> executeToggleReadyOnHost(PlayerType.CLIENT)
            "MOVE" -> executeMoveOnHost(cmd.unitId ?: "", cmd.row, cmd.col)
            "ATTACK" -> executeAttackOnHost(cmd.unitId ?: "", cmd.targets)
            "BUILD_BRIDGE" -> executeBuildBridgeOnHost(cmd.unitId ?: "", cmd.row, cmd.col)
            "BUILD_TRENCH" -> executeBuildTrenchOnHost(cmd.unitId ?: "", cmd.row, cmd.col)
            "END_TURN" -> executeEndTurnOnHost()
        }
    }

    private fun executePlaceUnitOnHost(player: PlayerType, className: String, row: Int, col: Int) {
        _uiState.update { current ->
            // 1. Verify Deployment Zone
            if (!isInDeploymentZone(player, col, current.cols)) {
                return@update current.copy(statusMessage = "Placement failed: Out of deployment zone!")
            }

            // 2. Validate position collisions (Host authoritative)
            val existingAtTile = (current.hostDeployedUnits + current.clientDeployedUnits).filter { it.row == row && it.col == col }
            val isEngineer = className == "Engineer"
            
            val canPlace = if (existingAtTile.isEmpty()) {
                true
            } else if (existingAtTile.size == 1 && (isEngineer || existingAtTile[0].isEngineer) && existingAtTile[0].owner == player) {
                // Stacking allowed: only if one of them is Engineer, and they share the owner
                true
            } else {
                false
            }

            if (!canPlace) {
                return@update current.copy(statusMessage = "Placement failed: Slot occupied.")
            }

            // 3. Check unit class counts pool
            val countLimit = if (isEngineer) {
                current.engineerConfig.deploymentCount
            } else {
                current.unitClasses.firstOrNull { it.name == className }?.deploymentCount ?: 0
            }

            val currentDeployedCount = (if (player == PlayerType.HOST) current.hostDeployedUnits else current.clientDeployedUnits)
                .count { it.className == className }

            if (currentDeployedCount >= countLimit) {
                return@update current.copy(statusMessage = "Placement failed: All $className units already placed!")
            }

            // Create new unit
            val unitClass = current.unitClasses.firstOrNull { it.name == className }
            val hp = if (isEngineer) current.engineerConfig.hp else (unitClass?.hp ?: 100)
            val energy = if (isEngineer) current.engineerConfig.energy else (unitClass?.energy ?: 4)
            val vision = if (isEngineer) current.engineerConfig.visionRange else (unitClass?.visionRange ?: 3)
            val range = if (isEngineer) current.engineerConfig.attackRange else (unitClass?.attackRange ?: 1)
            val dmg = if (isEngineer) 0 else (unitClass?.damage ?: 20)

            val newUnit = GameUnitState(
                id = UUID.randomUUID().toString(),
                className = className,
                owner = player,
                row = row,
                col = col,
                hp = hp,
                maxHp = hp,
                energy = energy,
                maxEnergy = energy,
                visionRange = vision,
                attackRange = range,
                damage = dmg,
                isEngineer = isEngineer
            )

            val updatedList = if (player == PlayerType.HOST) {
                current.hostDeployedUnits + newUnit
            } else {
                current.clientDeployedUnits + newUnit
            }

            val newState = if (player == PlayerType.HOST) {
                current.copy(hostDeployedUnits = updatedList, statusMessage = "Placed Host $className at ($row, $col)")
            } else {
                current.copy(clientDeployedUnits = updatedList, statusMessage = "Placed Client $className at ($row, $col)")
            }
            syncStateToClient(newState)
            newState
        }
    }

    private fun executeRemoveUnitOnHost(row: Int, col: Int) {
        _uiState.update { current ->
            // Remove any unit on (row, col)
            val filteredHost = current.hostDeployedUnits.filterNot { it.row == row && it.col == col }
            val filteredClient = current.clientDeployedUnits.filterNot { it.row == row && it.col == col }
            
            val newState = current.copy(
                hostDeployedUnits = filteredHost,
                clientDeployedUnits = filteredClient,
                statusMessage = "Unit removed at ($row, $col)"
            )
            syncStateToClient(newState)
            newState
        }
    }

    private fun executeToggleReadyOnHost(player: PlayerType) {
        _uiState.update { current ->
            // Check if ALL allocated units are placed before allowing Ready
            val expectedHostCount = current.unitClasses.sumOf { it.deploymentCount } + current.engineerConfig.deploymentCount
            val expectedClientCount = expectedHostCount // symmetric pool sizes

            if (player == PlayerType.HOST && current.hostDeployedUnits.size < expectedHostCount) {
                return@update current.copy(statusMessage = "Host cannot ready up: Place all units first!")
            }
            if (player == PlayerType.CLIENT && current.clientDeployedUnits.size < expectedClientCount) {
                return@update current.copy(statusMessage = "Client cannot ready up: Place all units first!")
            }

            val nextHostReady = if (player == PlayerType.HOST) !current.hostReady else current.hostReady
            val nextClientReady = if (player == PlayerType.CLIENT) !current.clientReady else current.clientReady

            var nextMatchState = current.matchState
            var statusStr = ""

            if (nextHostReady && nextClientReady) {
                nextMatchState = MatchState.GAMEPLAY_LOOP
                statusStr = "Match started! It is Host's turn."
                // Fully restore energy for Host's turn start
                val restoredHost = current.hostDeployedUnits.map { it.copy(energy = it.maxEnergy) }
                val newState = current.copy(
                    matchState = nextMatchState,
                    hostReady = true,
                    clientReady = true,
                    hostDeployedUnits = restoredHost,
                    activeTurn = PlayerType.HOST,
                    statusMessage = statusStr
                )
                syncStateToClient(newState)
                return@update newState
            } else {
                statusStr = if (player == PlayerType.HOST) {
                    if (nextHostReady) "Host is READY" else "Host is NOT READY"
                } else {
                    if (nextClientReady) "Client is READY" else "Client is NOT READY"
                }
            }

            val newState = current.copy(
                hostReady = nextHostReady,
                clientReady = nextClientReady,
                statusMessage = statusStr
            )
            syncStateToClient(newState)
            newState
        }
    }

    private fun executeMoveOnHost(unitId: String, targetRow: Int, targetCol: Int) {
        _uiState.update { current ->
            // Find the unit
            val isHostUnit = current.hostDeployedUnits.any { it.id == unitId }
            val listToSearch = if (isHostUnit) current.hostDeployedUnits else current.clientDeployedUnits
            val unit = listToSearch.find { it.id == unitId }
                ?: return@update current.copy(statusMessage = "Move rejected: Unit not found!")

            // Verify active turn matches unit owner
            if (current.activeTurn != unit.owner) {
                return@update current.copy(statusMessage = "Move rejected: Not your turn!")
            }

            // Verify adjacency (continuous step-by-step movement)
            val dist = hexDistance(unit.row, unit.col, targetRow, targetCol)
            if (dist != 1) {
                return@update current.copy(statusMessage = "Move rejected: Continuous movement is step-by-step (distance 1 only)!")
            }

            // Verify terrain passable and get energy cost
            val tile = current.tiles.find { it.row == targetRow && it.col == targetCol }
                ?: return@update current.copy(statusMessage = "Move rejected: Out of bounds!")

            val movementCost = when (tile.terrain) {
                TerrainType.PLAINS -> current.movementCostPlains
                TerrainType.HILLS -> current.movementCostHills
                TerrainType.WATER -> {
                    if (tile.hasBridge) current.movementCostWaterWithBridge
                    else return@update current.copy(statusMessage = "Move rejected: Cannot cross water without a bridge!")
                }
                TerrainType.MOUNTAINS -> return@update current.copy(statusMessage = "Move rejected: Mountains are permanently blocked!")
            }

            // Check Castle collision (permanent block)
            if ((targetRow == current.hostCastlePos.row && targetCol == current.hostCastlePos.col) ||
                (targetRow == current.clientCastlePos.row && targetCol == current.clientCastlePos.col)) {
                return@update current.copy(statusMessage = "Move rejected: Castles are permanently impassable!")
            }

            if (unit.energy < movementCost) {
                return@update current.copy(statusMessage = "Move rejected: Insufficient energy (requires $movementCost, has ${unit.energy})!")
            }

            // Collision check with other units
            val unitsAtTarget = (current.hostDeployedUnits + current.clientDeployedUnits).filter { it.row == targetRow && it.col == targetCol }
            val canEnter = if (unitsAtTarget.isEmpty()) {
                true
            } else if (unitsAtTarget.size == 1 && unitsAtTarget[0].owner == unit.owner) {
                // Only allowed if one is Engineer, to support stacking rules
                unit.isEngineer || unitsAtTarget[0].isEngineer
            } else {
                false
            }

            if (!canEnter) {
                return@update current.copy(statusMessage = "Move rejected: Blocked by unit collision!")
            }

            // Execute the move
            val updatedUnit = unit.copy(row = targetRow, col = targetCol, energy = unit.energy - movementCost)

            val updatedHostList = if (isHostUnit) {
                current.hostDeployedUnits.map { if (it.id == unitId) updatedUnit else it }
            } else {
                current.hostDeployedUnits
            }

            val updatedClientList = if (!isHostUnit) {
                current.clientDeployedUnits.map { if (it.id == unitId) updatedUnit else it }
            } else {
                current.clientDeployedUnits
            }

            val newState = current.copy(
                hostDeployedUnits = updatedHostList,
                clientDeployedUnits = updatedClientList,
                statusMessage = "Moved ${unit.className} to ($targetRow, $targetCol)"
            )
            syncStateToClient(newState)
            newState
        }
    }

    private fun executeAttackOnHost(unitId: String, targetIds: List<String>) {
        if (targetIds.isEmpty()) return
        
        _uiState.update { current ->
            // Find attacker
            val isHostUnit = current.hostDeployedUnits.any { it.id == unitId }
            val listToSearch = if (isHostUnit) current.hostDeployedUnits else current.clientDeployedUnits
            val attacker = listToSearch.find { it.id == unitId }
                ?: return@update current.copy(statusMessage = "Attack rejected: Attacker not found!")

            // Verify active turn matches unit owner
            if (current.activeTurn != attacker.owner) {
                return@update current.copy(statusMessage = "Attack rejected: Not your turn!")
            }

            if (attacker.energy <= 0) {
                return@update current.copy(statusMessage = "Attack rejected: Attacker has no energy remaining!")
            }

            val allEnemyUnits = if (isHostUnit) current.clientDeployedUnits else current.hostDeployedUnits
            val enemyCastleHp = if (isHostUnit) current.clientCastleHp else current.hostCastleHp
            val enemyCastlePos = if (isHostUnit) current.clientCastlePos else current.hostCastlePos
            val enemyCastleId = "CASTLE"

            // Validate targets distance, Fog of War, and line of sight block
            for (tid in targetIds) {
                val (tr, tc) = if (tid == enemyCastleId) {
                    enemyCastlePos.row to enemyCastlePos.col
                } else {
                    val tar = allEnemyUnits.find { it.id == tid }
                        ?: return@update current.copy(statusMessage = "Attack rejected: Target unit '$tid' not found!")
                    tar.row to tar.col
                }

                // Check Hex range
                val hexDist = hexDistance(attacker.row, attacker.col, tr, tc)
                if (hexDist > attacker.attackRange) {
                    return@update current.copy(statusMessage = "Attack rejected: Target at ($tr, $tc) is out of range (${hexDist}/${attacker.attackRange})!")
                }

                // Check Line of Sight (Mountains block attacks)
                if (isLineOfSightBlocked(attacker.row, attacker.col, tr, tc, current.tiles)) {
                    return@update current.copy(statusMessage = "Attack rejected: Line of sight to ($tr, $tc) is blocked by mountains!")
                }
            }

            // Calculate damage details
            val targetsCount = targetIds.size
            val hpPercentage = attacker.hp.toFloat() / attacker.maxHp
            val energyPercentage = attacker.energy.toFloat() / attacker.maxEnergy
            
            val rawDamage = (attacker.damage * hpPercentage * energyPercentage / targetsCount)

            var updatedHostDeployed = current.hostDeployedUnits.toMutableList()
            var updatedClientDeployed = current.clientDeployedUnits.toMutableList()
            var nextHostCastleHp = current.hostCastleHp
            var nextClientCastleHp = current.clientCastleHp

            var combatLogs = "Combat Results:\n"

            for (tid in targetIds) {
                if (tid == enemyCastleId) {
                    val roundedDmg = rawDamage.roundToInt()
                    if (isHostUnit) {
                        nextClientCastleHp = (nextClientCastleHp - roundedDmg).coerceAtLeast(0)
                        combatLogs += "- Enemy Castle hit for $roundedDmg damage! (HP: $nextClientCastleHp)\n"
                    } else {
                        nextHostCastleHp = (nextHostCastleHp - roundedDmg).coerceAtLeast(0)
                        combatLogs += "- Host Castle hit for $roundedDmg damage! (HP: $nextHostCastleHp)\n"
                    }
                } else {
                    val targetUnit = allEnemyUnits.find { it.id == tid } ?: continue
                    val targetTile = current.tiles.find { it.row == targetUnit.row && it.col == targetUnit.col }
                    
                    val trenchReduction = if (targetTile?.hasTrench == true) {
                        current.engineerConfig.trenchReductionPct
                    } else 0
                    
                    val reducedDamage = rawDamage * (1.0f - trenchReduction / 100.0f)
                    val roundedDmg = reducedDamage.roundToInt()
                    val nextHp = (targetUnit.hp - roundedDmg).coerceAtLeast(0)

                    combatLogs += "- ${targetUnit.className} at (${targetUnit.row}, ${targetUnit.col}) hit for $roundedDmg damage! (HP: $nextHp)\n"

                    if (isHostUnit) {
                        if (nextHp <= 0) {
                            updatedClientDeployed.removeAll { it.id == tid }
                            combatLogs += "  -> Destroyed!\n"
                        } else {
                            updatedClientDeployed = updatedClientDeployed.map {
                                if (it.id == tid) it.copy(hp = nextHp) else it
                            }.toMutableList()
                        }
                    } else {
                        if (nextHp <= 0) {
                            updatedHostDeployed.removeAll { it.id == tid }
                            combatLogs += "  -> Destroyed!\n"
                        } else {
                            updatedHostDeployed = updatedHostDeployed.map {
                                if (it.id == tid) it.copy(hp = nextHp) else it
                            }.toMutableList()
                        }
                    }
                }
            }

            // Deplete ALL attacker energy upon attacking
            val depletedAttacker = attacker.copy(energy = 0)
            if (isHostUnit) {
                updatedHostDeployed = updatedHostDeployed.map {
                    if (it.id == unitId) depletedAttacker else it
                }.toMutableList()
            } else {
                updatedClientDeployed = updatedClientDeployed.map {
                    if (it.id == unitId) depletedAttacker else it
                }.toMutableList()
            }

            // Check victory conditions
            var nextWinner: PlayerType? = null
            var nextMatchState = current.matchState
            if (nextHostCastleHp <= 0) {
                nextWinner = PlayerType.CLIENT
                nextMatchState = MatchState.VICTORY_SCREEN
                combatLogs += "\n*** Client wins! Host Castle has been destroyed. ***"
            } else if (nextClientCastleHp <= 0) {
                nextWinner = PlayerType.HOST
                nextMatchState = MatchState.VICTORY_SCREEN
                combatLogs += "\n*** Host wins! Client Castle has been destroyed. ***"
            }

            val newState = current.copy(
                hostDeployedUnits = updatedHostDeployed,
                clientDeployedUnits = updatedClientDeployed,
                hostCastleHp = nextHostCastleHp,
                clientCastleHp = nextClientCastleHp,
                winner = nextWinner,
                matchState = nextMatchState,
                statusMessage = combatLogs
            )
            syncStateToClient(newState)
            newState
        }
    }

    private fun executeBuildBridgeOnHost(unitId: String, row: Int, col: Int) {
        _uiState.update { current ->
            val isHostUnit = current.hostDeployedUnits.any { it.id == unitId }
            val listToSearch = if (isHostUnit) current.hostDeployedUnits else current.clientDeployedUnits
            val engineer = listToSearch.find { it.id == unitId }
                ?: return@update current.copy(statusMessage = "Build bridge failed: Engineer not found!")

            if (!engineer.isEngineer) {
                return@update current.copy(statusMessage = "Build bridge failed: Only Engineers can build bridges!")
            }

            if (current.activeTurn != engineer.owner) {
                return@update current.copy(statusMessage = "Build bridge failed: Not your turn!")
            }

            val cost = current.engineerConfig.bridgeBuildCost
            if (engineer.energy < cost) {
                return@update current.copy(statusMessage = "Build bridge failed: Insufficient energy (requires $cost, has ${engineer.energy})!")
            }

            // Verify target is water and adjacent
            val dist = hexDistance(engineer.row, engineer.col, row, col)
            if (dist != 1) {
                return@update current.copy(statusMessage = "Build bridge failed: Must build adjacent to Engineer!")
            }

            val tile = current.tiles.find { it.row == row && it.col == col }
            if (tile == null || tile.terrain != TerrainType.WATER) {
                return@update current.copy(statusMessage = "Build bridge failed: Can only build bridges on Water tiles!")
            }

            if (tile.hasBridge) {
                return@update current.copy(statusMessage = "Build bridge failed: Bridge already exists!")
            }

            // Build bridge, deduct energy
            val updatedTiles = current.tiles.map {
                if (it.row == row && it.col == col) it.copy(hasBridge = true) else it
            }

            val updatedEngineer = engineer.copy(energy = engineer.energy - cost)

            val updatedHostList = if (isHostUnit) {
                current.hostDeployedUnits.map { if (it.id == unitId) updatedEngineer else it }
            } else {
                current.hostDeployedUnits
            }

            val updatedClientList = if (!isHostUnit) {
                current.clientDeployedUnits.map { if (it.id == unitId) updatedEngineer else it }
            } else {
                current.clientDeployedUnits
            }

            val newState = current.copy(
                tiles = updatedTiles,
                hostDeployedUnits = updatedHostList,
                clientDeployedUnits = updatedClientList,
                statusMessage = "Built a bridge at ($row, $col)"
            )
            syncStateToClient(newState)
            newState
        }
    }

    private fun executeBuildTrenchOnHost(unitId: String, row: Int, col: Int) {
        _uiState.update { current ->
            val isHostUnit = current.hostDeployedUnits.any { it.id == unitId }
            val listToSearch = if (isHostUnit) current.hostDeployedUnits else current.clientDeployedUnits
            val engineer = listToSearch.find { it.id == unitId }
                ?: return@update current.copy(statusMessage = "Build trench failed: Engineer not found!")

            if (!engineer.isEngineer) {
                return@update current.copy(statusMessage = "Build trench failed: Only Engineers can build trenches!")
            }

            if (current.activeTurn != engineer.owner) {
                return@update current.copy(statusMessage = "Build trench failed: Not your turn!")
            }

            val cost = current.engineerConfig.trenchBuildCost
            if (engineer.energy < cost) {
                return@update current.copy(statusMessage = "Build trench failed: Insufficient energy (requires $cost, has ${engineer.energy})!")
            }

            // Build on current tile
            if (engineer.row != row || engineer.col != col) {
                return@update current.copy(statusMessage = "Build trench failed: Engineer can only build trenches directly on their current tile!")
            }

            val tile = current.tiles.find { it.row == row && it.col == col }
            if (tile == null || (tile.terrain != TerrainType.PLAINS && tile.terrain != TerrainType.HILLS)) {
                return@update current.copy(statusMessage = "Build trench failed: Can only build trenches on Plains or Hills!")
            }

            if (tile.hasTrench) {
                return@update current.copy(statusMessage = "Build trench failed: Trench already exists here!")
            }

            // Build trench, deduct energy
            val updatedTiles = current.tiles.map {
                if (it.row == row && it.col == col) it.copy(hasTrench = true, trenchReductionPct = current.engineerConfig.trenchReductionPct) else it
            }

            val updatedEngineer = engineer.copy(energy = engineer.energy - cost)

            val updatedHostList = if (isHostUnit) {
                current.hostDeployedUnits.map { if (it.id == unitId) updatedEngineer else it }
            } else {
                current.hostDeployedUnits
            }

            val updatedClientList = if (!isHostUnit) {
                current.clientDeployedUnits.map { if (it.id == unitId) updatedEngineer else it }
            } else {
                current.clientDeployedUnits
            }

            val newState = current.copy(
                tiles = updatedTiles,
                hostDeployedUnits = updatedHostList,
                clientDeployedUnits = updatedClientList,
                statusMessage = "Built a trench at ($row, $col) providing ${current.engineerConfig.trenchReductionPct}% protection!"
            )
            syncStateToClient(newState)
            newState
        }
    }

    private fun executeEndTurnOnHost() {
        _uiState.update { current ->
            val nextTurn = if (current.activeTurn == PlayerType.HOST) PlayerType.CLIENT else PlayerType.HOST
            
            // Fully restore energy for the player whose turn is starting, discard current player's remaining energy
            val updatedHost = current.hostDeployedUnits.map {
                if (nextTurn == PlayerType.HOST) it.copy(energy = it.maxEnergy) else it
            }
            val updatedClient = current.clientDeployedUnits.map {
                if (nextTurn == PlayerType.CLIENT) it.copy(energy = it.maxEnergy) else it
            }

            val statusStr = if (nextTurn == PlayerType.HOST) "It is Host's turn! Energy restored." else "It is Client's turn! Energy restored."

            val newState = current.copy(
                activeTurn = nextTurn,
                hostDeployedUnits = updatedHost,
                clientDeployedUnits = updatedClient,
                statusMessage = statusStr
            )
            syncStateToClient(newState)
            newState
        }
    }

    // --- GAMEPLAY HELPERS (DETERMINISTIC PHYSICS & MATH) ---

    fun isInDeploymentZone(player: PlayerType, col: Int, totalCols: Int): Boolean {
        // Defined by Host as fixed region per player: Host is left side, Client is right side.
        val zoneWidth = (totalCols / 3).coerceAtLeast(1)
        return if (player == PlayerType.HOST) {
            col < zoneWidth
        } else {
            col >= totalCols - zoneWidth
        }
    }

    // Hex distance offset to axial convertor
    fun offsetToCube(row: Int, col: Int): Triple<Int, Int, Int> {
        val q = col - (row - (row and 1)) / 2
        val r = row
        val s = -q - r
        return Triple(q, r, s)
    }

    fun hexDistance(r1: Int, c1: Int, r2: Int, c2: Int): Int {
        val (q1, r1Ax, s1) = offsetToCube(r1, c1)
        val (q2, r2Ax, s2) = offsetToCube(r2, c2)
        return (abs(q1 - q2) + abs(r1Ax - r2Ax) + abs(s1 - s2)) / 2
    }

    // Linear interpolation on hex coordinates
    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun cubeLerp(q1: Float, r1: Float, s1: Float, q2: Float, r2: Float, s2: Float, t: Float): Triple<Float, Float, Float> {
        return Triple(lerp(q1, q2, t), lerp(r1, r2, t), lerp(s1, s2, t))
    }

    private fun cubeRound(q: Float, r: Float, s: Float): Triple<Int, Int, Int> {
        var qi = q.roundToInt()
        var ri = r.roundToInt()
        var si = s.roundToInt()
        val qDiff = abs(qi - q)
        val rDiff = abs(ri - r)
        val sDiff = abs(si - s)
        if (qDiff > rDiff && qDiff > sDiff) {
            qi = -ri - si
        } else if (rDiff > sDiff) {
            ri = -qi - si
        } else {
            si = -qi - ri
        }
        return Triple(qi, ri, si)
    }

    private fun cubeToOffset(q: Int, r: Int): HexPosState {
        val col = q + (r - (r and 1)) / 2
        return HexPosState(r, col)
    }

    fun getHexLine(r1: Int, c1: Int, r2: Int, c2: Int): List<HexPosState> {
        val (q1, r1Ax, s1) = offsetToCube(r1, c1)
        val (q2, r2Ax, s2) = offsetToCube(r2, c2)
        val dist = hexDistance(r1, c1, r2, c2)
        if (dist == 0) return listOf(HexPosState(r1, c1))
        
        val results = mutableListOf<HexPosState>()
        for (i in 0..dist) {
            val t = i.toFloat() / dist.toFloat()
            val (lq, lr, ls) = cubeLerp(q1.toFloat(), r1Ax.toFloat(), s1.toFloat(), q2.toFloat(), r2Ax.toFloat(), s2.toFloat(), t)
            val (rq, rr, rs) = cubeRound(lq, lr, ls)
            results.add(cubeToOffset(rq, rr))
        }
        return results
    }

    fun isLineOfSightBlocked(r1: Int, c1: Int, r2: Int, c2: Int, tiles: List<HexTileState>): Boolean {
        val line = getHexLine(r1, c1, r2, c2)
        if (line.size <= 2) return false // immediate neighbors always have clear sight
        
        // Check if any intermediate tile is a mountain
        for (i in 1 until line.size - 1) {
            val h = line[i]
            val tile = tiles.find { it.row == h.row && it.col == h.col }
            if (tile?.terrain == TerrainType.MOUNTAINS) {
                return true
            }
        }
        return false
    }

    // Dynamic vision checking
    fun isTileVisible(player: PlayerType, row: Int, col: Int, state: GameSessionState): Boolean {
        if (state.fogOfWarMode == FogOfWarMode.FULL_VISIBILITY) return true
        
        // Own Castle is always visible
        val castlePos = if (player == PlayerType.HOST) state.hostCastlePos else state.clientCastlePos
        if (row == castlePos.row && col == castlePos.col) return true
        
        val friendlyUnits = if (player == PlayerType.HOST) state.hostDeployedUnits else state.clientDeployedUnits
        for (unit in friendlyUnits) {
            val hexDist = hexDistance(unit.row, unit.col, row, col)
            if (hexDist <= unit.visionRange) {
                // Check if line of sight is clear
                if (!isLineOfSightBlocked(unit.row, unit.col, row, col, state.tiles)) {
                    return true
                }
            }
        }
        return false
    }

    // --- INTERACTIVE UI CONTEXT MUTATORS ---

    fun selectUnitForAction(unitId: String?) {
        _selectedUnitId.value = unitId
        _targetSelectionMode.value = false
        _selectedTargetIds.value = emptyList()
    }

    fun enterTargetSelectionMode() {
        if (_selectedUnitId.value != null) {
            _targetSelectionMode.value = true
            _selectedTargetIds.value = emptyList()
        }
    }

    fun exitTargetSelectionMode() {
        _targetSelectionMode.value = false
        _selectedTargetIds.value = emptyList()
    }

    fun toggleAttackTargetSelection(targetId: String) {
        val currentTargets = _selectedTargetIds.value.toMutableList()
        if (currentTargets.contains(targetId)) {
            currentTargets.remove(targetId)
        } else {
            currentTargets.add(targetId)
        }
        _selectedTargetIds.value = currentTargets
    }

    override fun onCleared() {
        networkManager?.disconnect()
        super.onCleared()
    }
}
