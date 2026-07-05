package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.GameViewModel
import kotlin.math.sqrt
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF0F172A), // Slate 900
                                        Color(0xFF1E293B), // Slate 800
                                        Color(0xFF020617)  // Slate 950
                                    )
                                )
                            )
                    ) {
                        GameNavigation()
                    }
                }
            }
        }
    }
}

@Composable
fun GameNavigation() {
    val viewModel: GameViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    val selectedUnitId by viewModel.selectedUnitId.collectAsState()
    val targetSelectionMode by viewModel.targetSelectionMode.collectAsState()
    val selectedTargetIds by viewModel.selectedTargetIds.collectAsState()

    Crossfade(targetState = state.matchState, label = "ScreenTransition") { matchState ->
        when (matchState) {
            MatchState.MAIN_MENU -> MainMenuScreen(viewModel)
            MatchState.CREATE_JOIN -> CreateJoinScreen(viewModel, state)
            MatchState.LOBBY -> LobbyScreen(viewModel, state)
            MatchState.HOST_CONFIGURATION -> HostConfigurationScreen(viewModel, state)
            MatchState.DEPLOYMENT_PHASE -> DeploymentPhaseScreen(viewModel, state, selectedUnitId)
            MatchState.READY_PHASE -> ReadyPhaseScreen(viewModel, state)
            MatchState.GAMEPLAY_LOOP -> GameplayScreen(
                viewModel,
                state,
                selectedUnitId,
                targetSelectionMode,
                selectedTargetIds
            )
            MatchState.VICTORY_SCREEN -> VictoryScreen(viewModel, state)
            MatchState.CONNECTION_LOST -> ConnectionLostScreen(viewModel, state)
        }
    }
}

// ==========================================
// 1. MAIN MENU SCREEN
// ==========================================
@Composable
fun MainMenuScreen(viewModel: GameViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Aesthetic Space Header
        Text(
            text = "HEX LAN",
            fontSize = 42.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF38BDF8), // Sky Blue
            fontFamily = FontFamily.Monospace,
            letterSpacing = 4.sp
        )
        Text(
            text = "TACTICS",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF43F5E), // Rose 500
            fontFamily = FontFamily.Monospace,
            letterSpacing = 8.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Game Card Info
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0x7F1E293B)),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(bottom = 40.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Tactical Hex Strategy Engine",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "A fully deterministic 1v1 LAN-only battle simulator. No randomness. No AI. No delay. Complete Host authority and custompreset capabilities.",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // Action Buttons
        Button(
            onClick = { viewModel.hostGame() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .width(280.dp)
                .height(56.dp)
                .testTag("host_game_button")
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = "Host Match",
                modifier = Modifier.padding(end = 8.dp)
            )
            Text("HOST MATCH", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { viewModel.joinGame("") },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
            border = BorderStroke(1.5.dp, Color(0xFF0EA5E9)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .width(280.dp)
                .height(56.dp)
                .testTag("join_game_button")
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Join Match",
                modifier = Modifier.padding(end = 8.dp)
            )
            Text("JOIN MATCH", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

// ==========================================
// 2. CREATE / JOIN MATCH SCREEN
// ==========================================
@Composable
fun CreateJoinScreen(viewModel: GameViewModel, state: GameSessionState) {
    var ipInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = { viewModel.disconnect() },
            modifier = Modifier.align(Alignment.Start)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back to Menu",
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (state.isHost) {
            // Host Mode waiting screen
            Text(
                text = "LAN SERVER ONLINE",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.5.dp, Color(0xFF0EA5E9)),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Your Local IP Address:",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )
                    Text(
                        text = state.hostIp,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF38BDF8),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    Text(
                        text = "Tell the Client to enter this IP on their device to connect over the same Wi-Fi network.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            CircularProgressIndicator(color = Color(0xFFF43F5E))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = state.statusMessage, color = Color(0xFF94A3B8), fontSize = 14.sp)

        } else {
            // Client Mode connect screen
            Text(
                text = "CONNECT TO HOST",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Host LAN IP Address",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = ipInput,
                        onValueChange = { ipInput = it },
                        placeholder = { Text("e.g. 192.168.1.15", color = Color(0xFF64748B)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("host_ip_input")
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { viewModel.joinGame(ipInput.trim()) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                        shape = RoundedCornerShape(8.dp),
                        enabled = ipInput.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("connect_button")
                    ) {
                        Text("CONNECT MATCH", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(text = state.statusMessage, color = Color(0xFFE2E8F0), fontSize = 14.sp)
        }
    }
}

// ==========================================
// 3. LOBBY SCREEN
// ==========================================
@Composable
fun LobbyScreen(viewModel: GameViewModel, state: GameSessionState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "MATCH LOBBY",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color(0xFF475569)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(bottom = 40.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Connected Players:",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Host row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color(0xFF10B981), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "PLAYER 1 (HOST)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Client row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color(0xFF10B981), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "PLAYER 2 (CLIENT)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (state.isHost) {
            Button(
                onClick = { viewModel.advanceToHostConfiguration() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .width(280.dp)
                    .height(56.dp)
                    .testTag("configure_match_button")
            ) {
                Text("CONFIGURE MATCH", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        } else {
            CircularProgressIndicator(color = Color(0xFF38BDF8))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Waiting for Host to configure the rules...",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp
            )
        }
    }
}

// ==========================================
// 4. HOST CONFIGURATION SCREEN
// ==========================================
@Composable
fun HostConfigurationScreen(viewModel: GameViewModel, state: GameSessionState) {
    var customPresetName by remember { mutableStateOf("") }
    var showPresetDialog by remember { mutableStateOf(false) }
    var selectedBrush by remember { mutableStateOf(TerrainType.PLAINS) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "MATCH CONFIGURATION",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            if (state.isHost) {
                Text(
                    text = "AUTHORITATIVE HOST",
                    fontSize = 11.sp,
                    color = Color(0xFFF43F5E),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color(0x3FF43F5E), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            } else {
                Text(
                    text = "CLIENT VIEW ONLY",
                    fontSize = 11.sp,
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color(0x3F38BDF8), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Preset selector panel
        if (state.isHost) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Snapshots & Presets",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        viewModel.listSavedPresets().forEach { name ->
                            Button(
                                onClick = { viewModel.selectPreset(name) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(name, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = customPresetName,
                            onValueChange = { customPresetName = it },
                            placeholder = { Text("Custom Preset Name", color = Color(0xFF64748B)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (customPresetName.isNotEmpty()) {
                                    viewModel.saveCustomPreset(customPresetName.trim())
                                    customPresetName = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                            shape = RoundedCornerShape(8.dp),
                            enabled = customPresetName.isNotEmpty(),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text("Save Preset", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Grid size configurator
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Map Dimensions",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Rows
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rows: ", color = Color(0xFF94A3B8))
                        IconButton(
                            onClick = { viewModel.updateGridSize(state.rows - 1, state.cols) },
                            enabled = state.isHost && state.rows > 4
                        ) {
                            Icon(imageVector = Icons.Default.Remove, contentDescription = "Reduce Rows", tint = Color.White)
                        }
                        Text("${state.rows}", color = Color.White, fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = { viewModel.updateGridSize(state.rows + 1, state.cols) },
                            enabled = state.isHost && state.rows < 12
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Increase Rows", tint = Color.White)
                        }
                    }

                    // Cols
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Cols: ", color = Color(0xFF94A3B8))
                        IconButton(
                            onClick = { viewModel.updateGridSize(state.rows, state.cols - 1) },
                            enabled = state.isHost && state.cols > 4
                        ) {
                            Icon(imageVector = Icons.Default.Remove, contentDescription = "Reduce Cols", tint = Color.White)
                        }
                        Text("${state.cols}", color = Color.White, fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = { viewModel.updateGridSize(state.rows, state.cols + 1) },
                            enabled = state.isHost && state.cols < 12
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Increase Cols", tint = Color.White)
                        }
                    }
                }
            }
        }

        // Live grid layout with painting brushes
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Terrain Paint Brush",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (state.isHost) {
                    // Brush selector
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        TerrainType.values().forEach { terrain ->
                            Button(
                                onClick = { selectedBrush = terrain },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedBrush == terrain) Color(0xFF38BDF8) else Color(0xFF334155)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text(
                                    terrain.name.take(5),
                                    fontSize = 11.sp,
                                    color = if (selectedBrush == terrain) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }

                // Grid layout preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Let's draw our preview Hex Grid!
                    val hexRadius = when {
                        state.cols <= 6 -> 24.dp
                        state.cols <= 8 -> 18.dp
                        else -> 14.dp
                    }
                    HexGridPreviewLayout(
                        state = state,
                        hexRadius = hexRadius,
                        onTileTap = { r, c ->
                            if (state.isHost) {
                                viewModel.paintTileTerrain(r, c, selectedBrush)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (state.isHost) "Tap tiles on the preview grid above to paint the selected terrain type instantly." else "View live configuration updates drawn by Player 1 above.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Unit Classes Config Panel
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Unit Classes Attributes",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                state.unitClasses.forEachIndexed { idx, uc ->
                    Text(
                        text = uc.name.uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ConfigIntPicker("HP", uc.hp, state.isHost) { v ->
                            viewModel.updateUnitClassConfig(idx, hp = v, damage = uc.damage, energy = uc.energy, vision = uc.visionRange, range = uc.attackRange, count = uc.deploymentCount)
                        }
                        ConfigIntPicker("DMG", uc.damage, state.isHost) { v ->
                            viewModel.updateUnitClassConfig(idx, hp = uc.hp, damage = v, energy = uc.energy, vision = uc.visionRange, range = uc.attackRange, count = uc.deploymentCount)
                        }
                        ConfigIntPicker("ENG", uc.energy, state.isHost) { v ->
                            viewModel.updateUnitClassConfig(idx, hp = uc.hp, damage = uc.damage, energy = v, vision = uc.visionRange, range = uc.attackRange, count = uc.deploymentCount)
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        ConfigIntPicker("VIS", uc.visionRange, state.isHost) { v ->
                            viewModel.updateUnitClassConfig(idx, hp = uc.hp, damage = uc.damage, energy = uc.energy, vision = v, range = uc.attackRange, count = uc.deploymentCount)
                        }
                        ConfigIntPicker("RNG", uc.attackRange, state.isHost) { v ->
                            viewModel.updateUnitClassConfig(idx, hp = uc.hp, damage = uc.damage, energy = uc.energy, vision = uc.visionRange, range = v, count = uc.deploymentCount)
                        }
                        ConfigIntPicker("QTY", uc.deploymentCount, state.isHost) { v ->
                            viewModel.updateUnitClassConfig(idx, hp = uc.hp, damage = uc.damage, energy = uc.energy, vision = uc.visionRange, range = uc.attackRange, count = v)
                        }
                    }
                }
            }
        }

        // Engineer special configuration
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Special Unit: Engineer Config",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ConfigIntPicker("HP", state.engineerConfig.hp, state.isHost) { v ->
                        viewModel.updateEngineerConfig(hp = v, energy = state.engineerConfig.energy, vision = state.engineerConfig.visionRange, range = state.engineerConfig.attackRange, count = state.engineerConfig.deploymentCount, bridgeCost = state.engineerConfig.bridgeBuildCost, trenchCost = state.engineerConfig.trenchBuildCost, trenchRed = state.engineerConfig.trenchReductionPct)
                    }
                    ConfigIntPicker("ENG", state.engineerConfig.energy, state.isHost) { v ->
                        viewModel.updateEngineerConfig(hp = state.engineerConfig.hp, energy = v, vision = state.engineerConfig.visionRange, range = state.engineerConfig.attackRange, count = state.engineerConfig.deploymentCount, bridgeCost = state.engineerConfig.bridgeBuildCost, trenchCost = state.engineerConfig.trenchBuildCost, trenchRed = state.engineerConfig.trenchReductionPct)
                    }
                    ConfigIntPicker("QTY", state.engineerConfig.deploymentCount, state.isHost) { v ->
                        viewModel.updateEngineerConfig(hp = state.engineerConfig.hp, energy = state.engineerConfig.energy, vision = state.engineerConfig.visionRange, range = state.engineerConfig.attackRange, count = v, bridgeCost = state.engineerConfig.bridgeBuildCost, trenchCost = state.engineerConfig.trenchBuildCost, trenchRed = state.engineerConfig.trenchReductionPct)
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ConfigIntPicker("Bridge Cost", state.engineerConfig.bridgeBuildCost, state.isHost) { v ->
                        viewModel.updateEngineerConfig(hp = state.engineerConfig.hp, energy = state.engineerConfig.energy, vision = state.engineerConfig.visionRange, range = state.engineerConfig.attackRange, count = state.engineerConfig.deploymentCount, bridgeCost = v, trenchCost = state.engineerConfig.trenchBuildCost, trenchRed = state.engineerConfig.trenchReductionPct)
                    }
                    ConfigIntPicker("Trench Cost", state.engineerConfig.trenchBuildCost, state.isHost) { v ->
                        viewModel.updateEngineerConfig(hp = state.engineerConfig.hp, energy = state.engineerConfig.energy, vision = state.engineerConfig.visionRange, range = state.engineerConfig.attackRange, count = state.engineerConfig.deploymentCount, bridgeCost = state.engineerConfig.bridgeBuildCost, trenchCost = v, trenchRed = state.engineerConfig.trenchReductionPct)
                    }
                    ConfigIntPicker("Trench Red. %", state.engineerConfig.trenchReductionPct, state.isHost) { v ->
                        viewModel.updateEngineerConfig(hp = state.engineerConfig.hp, energy = state.engineerConfig.energy, vision = state.engineerConfig.visionRange, range = state.engineerConfig.attackRange, count = state.engineerConfig.deploymentCount, bridgeCost = state.engineerConfig.bridgeBuildCost, trenchCost = state.engineerConfig.trenchBuildCost, trenchRed = v)
                    }
                }
            }
        }

        // Action controls
        if (state.isHost) {
            Button(
                onClick = { viewModel.lockConfigurationAndStartDeployment() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("lock_and_deploy_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock",
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("LOCK CONFIG & DEPLOY", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x3F38BDF8)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "LOCKED VIEWER MODE: Player 1 is currently editing match variables. Everything is synchronized dynamically below.",
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun ConfigIntPicker(label: String, valNow: Int, enabled: Boolean, onValChange: (Int) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        Text(label, color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.width(96.dp)
        ) {
            IconButton(
                onClick = { if (valNow > 0) onValChange(valNow - 1) },
                enabled = enabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(imageVector = Icons.Default.Remove, contentDescription = "Sub", tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Text("$valNow", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            IconButton(
                onClick = { onValChange(valNow + 1) },
                enabled = enabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ==========================================
// 5. DEPLOYMENT PHASE SCREEN
// ==========================================
@Composable
fun DeploymentPhaseScreen(viewModel: GameViewModel, state: GameSessionState, selectedUnitId: String?) {
    var selectedClassForPlacement by remember { mutableStateOf<String?>(null) }
    val myPlayer = if (state.isHost) PlayerType.HOST else PlayerType.CLIENT
    val isReady = if (state.isHost) state.hostReady else state.clientReady

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Status header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DEPLOYMENT STAGE",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = state.statusMessage,
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = { viewModel.toggleReady() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isReady) Color(0xFF10B981) else Color(0xFFF43F5E)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isReady) "READY!" else "READY UP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // Hex grid placement map
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            val hexRadius = when {
                state.cols <= 6 -> 28.dp
                state.cols <= 8 -> 22.dp
                else -> 18.dp
            }

            HexGridDeploymentLayout(
                state = state,
                myPlayer = myPlayer,
                hexRadius = hexRadius,
                selectedClass = selectedClassForPlacement,
                onTileTap = { r, c ->
                    if (isReady) return@HexGridDeploymentLayout
                    
                    if (selectedClassForPlacement != null) {
                        viewModel.placeUnit(selectedClassForPlacement!!, r, c)
                        selectedClassForPlacement = null // Reset selection
                    } else {
                        // Tapping on friendly unit highlights it, allowing deletion
                        val hostMatch = state.hostDeployedUnits.any { it.row == r && it.col == c }
                        val clientMatch = state.clientDeployedUnits.any { it.row == r && it.col == c }
                        if (hostMatch && state.isHost) {
                            viewModel.removeUnit(r, c)
                        } else if (clientMatch && !state.isHost) {
                            viewModel.removeUnit(r, c)
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Placement Selection pool bottom bar
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Select a unit class from your pool below, then tap inside your highlighted Deployment Zone to place it:",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val myUnitsList = if (state.isHost) state.hostDeployedUnits else state.clientDeployedUnits

                    // Standard Classes
                    state.unitClasses.forEach { uc ->
                        val currentCount = myUnitsList.count { it.className == uc.name }
                        val remaining = uc.deploymentCount - currentCount
                        val isSel = selectedClassForPlacement == uc.name

                        Button(
                            onClick = { selectedClassForPlacement = if (isSel) null else uc.name },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSel) Color(0xFF38BDF8) else Color(0xFF334155),
                                disabledContainerColor = Color(0x3F334155)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            enabled = remaining > 0 && !isReady,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(uc.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.Black else Color.White)
                                Text("$remaining left", fontSize = 10.sp, color = if (isSel) Color(0xFF1E293B) else Color(0xFF94A3B8))
                            }
                        }
                    }

                    // Special Engineer Class
                    val engCount = myUnitsList.count { it.className == "Engineer" }
                    val engRemaining = state.engineerConfig.deploymentCount - engCount
                    val isEngSel = selectedClassForPlacement == "Engineer"

                    Button(
                        onClick = { selectedClassForPlacement = if (isEngSel) null else "Engineer" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isEngSel) Color(0xFFF43F5E) else Color(0xFF334155),
                            disabledContainerColor = Color(0x3F334155)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        enabled = engRemaining > 0 && !isReady,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Engineer", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("$engRemaining left", fontSize = 10.sp, color = Color(0xFFFDA4AF))
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 6. READY PHASE SCREEN
// ==========================================
@Composable
fun ReadyPhaseScreen(viewModel: GameViewModel, state: GameSessionState) {
    LobbyScreen(viewModel, state)
}

// ==========================================
// 7. GAMEPLAY LOOP SCREEN
// ==========================================
@Composable
fun GameplayScreen(
    viewModel: GameViewModel,
    state: GameSessionState,
    selectedUnitId: String?,
    targetSelectionMode: Boolean,
    selectedTargetIds: List<String>
) {
    val myPlayer = if (state.isHost) PlayerType.HOST else PlayerType.CLIENT
    val activeUnit = (state.hostDeployedUnits + state.clientDeployedUnits).find { it.id == selectedUnitId }
    val isMyTurn = state.activeTurn == myPlayer

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Active Turn header and status message
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    if (state.activeTurn == PlayerType.HOST) Color(0xFF38BDF8) else Color(0xFFF43F5E),
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.activeTurn == PlayerType.HOST) "P1 TURN (HOST)" else "P2 TURN (CLIENT)",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        if (isMyTurn) {
                            Text(
                                text = "YOUR ACTION",
                                fontSize = 10.sp,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .background(Color(0x3F10B981), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = state.statusMessage,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Castle HPs details
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Castles HP", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(text = "P1: ${state.hostCastleHp}%", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(text = "P2: ${state.clientCastleHp}%", color = Color(0xFFF43F5E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // The Hex Board layout (center of view)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val hexRadius = when {
                state.cols <= 6 -> 26.dp
                state.cols <= 8 -> 20.dp
                else -> 16.dp
            }

            HexGridGameplayLayout(
                state = state,
                myPlayer = myPlayer,
                hexRadius = hexRadius,
                selectedUnitId = selectedUnitId,
                targetSelectionMode = targetSelectionMode,
                selectedTargetIds = selectedTargetIds,
                onTileTap = { r, c ->
                    if (!isMyTurn) return@HexGridGameplayLayout
                    
                    val allUnits = state.hostDeployedUnits + state.clientDeployedUnits
                    val clickedUnit = allUnits.find { it.row == r && it.col == c }

                    if (targetSelectionMode) {
                        // Select target for multi-target combat
                        val isEnemyCastle = (r == state.clientCastlePos.row && c == state.clientCastlePos.col && myPlayer == PlayerType.HOST) ||
                                            (r == state.hostCastlePos.row && c == state.hostCastlePos.col && myPlayer == PlayerType.CLIENT)
                        
                        if (clickedUnit != null && clickedUnit.owner != myPlayer) {
                            viewModel.toggleAttackTargetSelection(clickedUnit.id)
                        } else if (isEnemyCastle) {
                            viewModel.toggleAttackTargetSelection("CASTLE")
                        }
                    } else {
                        // Standard interactive tap
                        if (clickedUnit != null && clickedUnit.owner == myPlayer) {
                            // Select friendly unit
                            viewModel.selectUnitForAction(clickedUnit.id)
                        } else if (activeUnit != null) {
                            // Selected friendly unit is trying to perform an adjacent step-movement
                            val isNeighbor = viewModel.hexDistance(activeUnit.row, activeUnit.col, r, c) == 1
                            if (isNeighbor) {
                                viewModel.attemptStepMove(activeUnit.id, r, c)
                            }
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Selected Unit controls panel (bottom of gameplay)
        if (activeUnit != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Title and attributes row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (activeUnit.isEngineer) Icons.Default.Build else Icons.Default.DirectionsRun,
                            contentDescription = "Unit Type",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${activeUnit.className} (P${if (activeUnit.owner == PlayerType.HOST) 1 else 2})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "HP: ${activeUnit.hp}/${activeUnit.maxHp}  |  ENG: ${activeUnit.energy}/${activeUnit.maxEnergy}",
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isMyTurn) {
                            if (targetSelectionMode) {
                                // CONFIRM MULTI-TARGET ATTACK
                                Button(
                                    onClick = {
                                        viewModel.attemptAttack(activeUnit.id, selectedTargetIds)
                                        viewModel.selectUnitForAction(null)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(8.dp),
                                    enabled = selectedTargetIds.isNotEmpty(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("CONFIRM SPLIT ATTACK (${selectedTargetIds.size})", fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = { viewModel.exitTargetSelectionMode() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = BorderStroke(1.dp, Color(0xFF475569)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(0.5f)
                                ) {
                                    Text("CANCEL", fontSize = 11.sp)
                                }
                            } else {
                                // Enter Target mode
                                Button(
                                    onClick = { viewModel.enterTargetSelectionMode() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E)),
                                    shape = RoundedCornerShape(8.dp),
                                    enabled = activeUnit.energy > 0,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("ATTACK MODE (RNG: ${activeUnit.attackRange})", fontSize = 11.sp)
                                }

                                // Special Engineer controls
                                if (activeUnit.isEngineer && activeUnit.energy > 0) {
                                    Button(
                                        onClick = {
                                            // Build Trench directly on current tile
                                            viewModel.buildTrench(activeUnit.id, activeUnit.row, activeUnit.col)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("BUILD TRENCH (-${state.engineerConfig.trenchBuildCost}E)", fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = {
                                            // Search for adjacent water tile to build bridge
                                            val neighbors = getNeighbors(activeUnit.row, activeUnit.col, state.rows, state.cols)
                                            val waterTile = state.tiles.firstOrNull { t ->
                                                neighbors.any { n -> n.row == t.row && n.col == t.col } && t.terrain == TerrainType.WATER && !t.hasBridge
                                            }
                                            if (waterTile != null) {
                                                viewModel.buildBridge(activeUnit.id, waterTile.row, waterTile.col)
                                            } else {
                                                // Alert via viewmodel status message
                                                viewModel.buildBridge(activeUnit.id, -1, -1)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1.5f)
                                    ) {
                                        Text("BUILD ADJ. BRIDGE (-${state.engineerConfig.bridgeBuildCost}E)", fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        // DESELECT Button
                        if (!targetSelectionMode) {
                            OutlinedButton(
                                onClick = { viewModel.selectUnitForAction(null) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFF475569)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(0.7f)
                            ) {
                                Text("DESELECT", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Global Bottom Row action panel (End Turn)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = { viewModel.disconnect() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFDA4AF)),
                border = BorderStroke(1.dp, Color(0xFFE11D48)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(0.8f)
                    .height(48.dp)
            ) {
                Text("SURRENDER", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = { viewModel.endTurn() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMyTurn) Color(0xFF38BDF8) else Color(0xFF334155)
                ),
                shape = RoundedCornerShape(8.dp),
                enabled = isMyTurn,
                modifier = Modifier
                    .weight(1.2f)
                    .height(48.dp)
                    .testTag("end_turn_button")
            ) {
                Text("END ACTION TURN", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (isMyTurn) Color.Black else Color(0xFF94A3B8))
            }
        }
    }
}

// ==========================================
// 8. VICTORY SCREEN
// ==========================================
@Composable
fun VictoryScreen(viewModel: GameViewModel, state: GameSessionState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Fort,
            contentDescription = "Castle Destroyed",
            tint = Color(0xFFF59E0B),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "MATCH COMPLETED!",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.5.dp, Color(0xFFF59E0B)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(bottom = 32.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val isHostWin = state.winner == PlayerType.HOST
                Text(
                    text = if (isHostWin) "VICTORY FOR PLAYER 1" else "VICTORY FOR PLAYER 2",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isHostWin) Color(0xFF38BDF8) else Color(0xFFF43F5E),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = state.statusMessage,
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Button(
            onClick = { viewModel.disconnect() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .width(240.dp)
                .height(56.dp)
                .testTag("victory_menu_button")
        ) {
            Text("RETURN TO MAIN MENU", fontWeight = FontWeight.Bold)
        }
    }
}

// ==========================================
// 9. CONNECTION LOST SCREEN
// ==========================================
@Composable
fun ConnectionLostScreen(viewModel: GameViewModel, state: GameSessionState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Disconnect Error",
            tint = Color(0xFFEF4444),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "LAN DISCONNECTED",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0x3FEF4444)),
            border = BorderStroke(1.dp, Color(0xFFEF4444)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "The connection between the Host and the Client was lost. As per authoritative LAN tournament rules, the match has been aborted immediately. No reconnects are allowed.",
                color = Color(0xFFFCA5A5),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(16.dp)
            )
        }

        Button(
            onClick = { viewModel.disconnect() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .width(220.dp)
                .height(48.dp)
        ) {
            Text("MAIN MENU", fontWeight = FontWeight.Bold)
        }
    }
}

// ==========================================
// HEX GRID DRAWING AND LAYOUT RENDERING
// ==========================================

@Composable
fun HexGridPreviewLayout(
    state: GameSessionState,
    hexRadius: Dp,
    onTileTap: (Int, Int) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val radiusPx = hexRadius.value
        val widthPx = radiusPx * 1.732f
        val heightPx = radiusPx * 2f
        val horizontalSpacing = widthPx
        val verticalSpacing = heightPx * 0.75f

        val totalGridWidth = (state.cols - 1) * horizontalSpacing + (widthPx / 2f)
        val totalGridHeight = (state.rows - 1) * verticalSpacing + heightPx

        Box(
            modifier = Modifier
                .size(totalGridWidth.dp, totalGridHeight.dp)
        ) {
            state.tiles.forEach { tile ->
                val x = tile.col * horizontalSpacing + (if (tile.row % 2 != 0) horizontalSpacing / 2f else 0f)
                val y = tile.row * verticalSpacing

                val terrainColor = when (tile.terrain) {
                    TerrainType.PLAINS -> Color(0xFF15803D) // Emerald 700
                    TerrainType.HILLS -> Color(0xFFEAB308) // Yellow 500
                    TerrainType.WATER -> Color(0xFF1D4ED8) // Blue 700
                    TerrainType.MOUNTAINS -> Color(0xFF78350F) // Amber 900
                }

                Box(
                    modifier = Modifier
                        .size(widthPx.dp, heightPx.dp)
                        .offset(x.dp, y.dp)
                        .clip(HexagonShape())
                        .background(terrainColor)
                        .clickable { onTileTap(tile.row, tile.col) }
                ) {
                    // Check if Castle
                    if (tile.row == state.hostCastlePos.row && tile.col == state.hostCastlePos.col) {
                        Icon(
                            imageVector = Icons.Default.Fort,
                            contentDescription = "P1 Castle",
                            tint = Color.White,
                            modifier = Modifier
                                .size((hexRadius.value * 1.2f).dp)
                                .align(Alignment.Center)
                        )
                    } else if (tile.row == state.clientCastlePos.row && tile.col == state.clientCastlePos.col) {
                        Icon(
                            imageVector = Icons.Default.Fort,
                            contentDescription = "P2 Castle",
                            tint = Color.Magenta,
                            modifier = Modifier
                                .size((hexRadius.value * 1.2f).dp)
                                .align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HexGridDeploymentLayout(
    state: GameSessionState,
    myPlayer: PlayerType,
    hexRadius: Dp,
    selectedClass: String?,
    onTileTap: (Int, Int) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val radiusPx = hexRadius.value
        val widthPx = radiusPx * 1.732f
        val heightPx = radiusPx * 2f
        val horizontalSpacing = widthPx
        val verticalSpacing = heightPx * 0.75f

        val totalGridWidth = (state.cols - 1) * horizontalSpacing + (widthPx / 2f)
        val totalGridHeight = (state.rows - 1) * verticalSpacing + heightPx

        Box(modifier = Modifier.size(totalGridWidth.dp, totalGridHeight.dp)) {
            state.tiles.forEach { tile ->
                val x = tile.col * horizontalSpacing + (if (tile.row % 2 != 0) horizontalSpacing / 2f else 0f)
                val y = tile.row * verticalSpacing

                val terrainColor = when (tile.terrain) {
                    TerrainType.PLAINS -> Color(0xFF166534)
                    TerrainType.HILLS -> Color(0xFFCA8A04)
                    TerrainType.WATER -> Color(0xFF1E40AF)
                    TerrainType.MOUNTAINS -> Color(0xFF78350F)
                }

                val inMyDeployZone = (myPlayer == PlayerType.HOST && tile.col < (state.cols / 3).coerceAtLeast(1)) ||
                                     (myPlayer == PlayerType.CLIENT && tile.col >= state.cols - (state.cols / 3).coerceAtLeast(1))

                val borderStrokeColor = when {
                    inMyDeployZone -> Color(0xFF10B981) // Green border for deploy zone
                    else -> Color(0xFF475569) // Dark Slate otherwise
                }

                Box(
                    modifier = Modifier
                        .size(widthPx.dp, heightPx.dp)
                        .offset(x.dp, y.dp)
                        .drawBehind {
                            val w = size.width
                            val h = size.height
                            val path = Path().apply {
                                moveTo(w / 2f, 0f)
                                writeHexLine(w, h)
                            }
                            drawPath(path, terrainColor)
                            drawPath(path, borderStrokeColor, style = Stroke(width = if (inMyDeployZone) 2.5.dp.toPx() else 1.dp.toPx()))
                        }
                        .clickable { onTileTap(tile.row, tile.col) }
                ) {
                    // Highlight deployment zone tinted overlay
                    if (inMyDeployZone) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x1F10B981))
                        )
                    }

                    // Render Castles
                    if (tile.row == state.hostCastlePos.row && tile.col == state.hostCastlePos.col) {
                        Icon(
                            imageVector = Icons.Default.Fort,
                            contentDescription = "P1 Castle",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier
                                .size((hexRadius.value * 1.3f).dp)
                                .align(Alignment.Center)
                        )
                    } else if (tile.row == state.clientCastlePos.row && tile.col == state.clientCastlePos.col) {
                        Icon(
                            imageVector = Icons.Default.Fort,
                            contentDescription = "P2 Castle",
                            tint = Color(0xFFF43F5E),
                            modifier = Modifier
                                .size((hexRadius.value * 1.3f).dp)
                                .align(Alignment.Center)
                        )
                    }

                    // Render any deployed unit on this tile
                    val hostUnit = state.hostDeployedUnits.find { it.row == tile.row && it.col == tile.col }
                    val clientUnit = state.clientDeployedUnits.find { it.row == tile.row && it.col == tile.col }
                    val topUnit = hostUnit ?: clientUnit

                    if (topUnit != null) {
                        val badgeColor = if (topUnit.owner == PlayerType.HOST) Color(0xFF38BDF8) else Color(0xFFF43F5E)
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (topUnit.isEngineer) Icons.Default.Build else Icons.Default.DirectionsRun,
                                contentDescription = topUnit.className,
                                tint = badgeColor,
                                modifier = Modifier.size((hexRadius.value * 1.2f).dp)
                            )
                            Text(
                                text = topUnit.className.take(3).uppercase(),
                                color = Color.White,
                                fontSize = (hexRadius.value * 0.5f).sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Stacking Indicator Badge (+ENG badge)
                        val hasStackedEng = (state.hostDeployedUnits + state.clientDeployedUnits)
                            .count { it.row == tile.row && it.col == tile.col } > 1

                        if (hasStackedEng) {
                            Box(
                                modifier = Modifier
                                    .size((hexRadius.value * 0.7f).dp)
                                    .align(Alignment.TopEnd)
                                    .background(Color(0xFF8B5CF6), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", color = Color.White, fontSize = (hexRadius.value * 0.45f).sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HexGridGameplayLayout(
    state: GameSessionState,
    myPlayer: PlayerType,
    hexRadius: Dp,
    selectedUnitId: String?,
    targetSelectionMode: Boolean,
    selectedTargetIds: List<String>,
    onTileTap: (Int, Int) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val radiusPx = hexRadius.value
        val widthPx = radiusPx * 1.732f
        val heightPx = radiusPx * 2f
        val horizontalSpacing = widthPx
        val verticalSpacing = heightPx * 0.75f

        val totalGridWidth = (state.cols - 1) * horizontalSpacing + (widthPx / 2f)
        val totalGridHeight = (state.rows - 1) * verticalSpacing + heightPx

        Box(modifier = Modifier.size(totalGridWidth.dp, totalGridHeight.dp)) {
            state.tiles.forEach { tile ->
                val x = tile.col * horizontalSpacing + (if (tile.row % 2 != 0) horizontalSpacing / 2f else 0f)
                val y = tile.row * verticalSpacing

                // FOG OF WAR Dynamic visibility
                val isVisible = !state.isHost && state.fogOfWarMode == FogOfWarMode.FOG_OF_WAR &&
                                !state.tiles.any { isTileVisibleFromPlayer(myPlayer, tile.row, tile.col, state) }
                
                // Let's check visibility from Host/Client perspectives correctly
                val actualVisible = if (state.fogOfWarMode == FogOfWarMode.FULL_VISIBILITY) {
                    true
                } else {
                    isTileVisibleFromPlayer(myPlayer, tile.row, tile.col, state)
                }

                val terrainColor = if (actualVisible) {
                    when (tile.terrain) {
                        TerrainType.PLAINS -> Color(0xFF15803D)
                        TerrainType.HILLS -> Color(0xFFEAB308)
                        TerrainType.WATER -> Color(0xFF1D4ED8)
                        TerrainType.MOUNTAINS -> Color(0xFF78350F)
                    }
                } else {
                    Color(0xFF1E293B).copy(alpha = 0.5f) // fog dimmed terrain
                }

                // Interactive ranges highlights
                val selectedUnit = (state.hostDeployedUnits + state.clientDeployedUnits).find { it.id == selectedUnitId }
                
                val isMoveRange = selectedUnit != null && selectedUnit.owner == myPlayer &&
                                  hexDist(selectedUnit.row, selectedUnit.col, tile.row, tile.col) == 1 &&
                                  tile.terrain != TerrainType.MOUNTAINS &&
                                  !(tile.terrain == TerrainType.WATER && !tile.hasBridge)

                val isAttackRange = selectedUnit != null && selectedUnit.owner == myPlayer &&
                                    hexDist(selectedUnit.row, selectedUnit.col, tile.row, tile.col) <= selectedUnit.attackRange &&
                                    !isLOSBlocked(selectedUnit.row, selectedUnit.col, tile.row, tile.col, state.tiles)

                val isSelectedTarget = targetSelectionMode && (
                        selectedTargetIds.contains("CASTLE") && (tile.row == state.hostCastlePos.row && tile.col == state.hostCastlePos.col || tile.row == state.clientCastlePos.row && tile.col == state.clientCastlePos.col) ||
                        state.hostDeployedUnits.any { it.row == tile.row && it.col == tile.col && selectedTargetIds.contains(it.id) } ||
                        state.clientDeployedUnits.any { it.row == tile.row && it.col == tile.col && selectedTargetIds.contains(it.id) }
                )

                val borderStrokeColor = when {
                    isSelectedTarget -> Color(0xFFEF4444) // Targeting glowing Red
                    selectedUnit != null && selectedUnit.row == tile.row && selectedUnit.col == tile.col -> Color(0xFFF59E0B) // Active unit glowing yellow
                    isMoveRange && isMyTurn(state, myPlayer) -> Color(0xFF10B981) // Movable green
                    isAttackRange && targetSelectionMode && isMyTurn(state, myPlayer) -> Color(0xFFEF4444) // Attacking red
                    else -> Color(0xFF475569) // Standard dark border
                }

                Box(
                    modifier = Modifier
                        .size(widthPx.dp, heightPx.dp)
                        .offset(x.dp, y.dp)
                        .drawBehind {
                            val w = size.width
                            val h = size.height
                            val path = Path().apply {
                                moveTo(w / 2f, 0f)
                                writeHexLine(w, h)
                            }
                            drawPath(path, terrainColor)
                            drawPath(path, borderStrokeColor, style = Stroke(width = if (isSelectedTarget || isMoveRange) 2.5.dp.toPx() else 1.dp.toPx()))

                            // Draw Trench icon overlay
                            if (tile.hasTrench && actualVisible) {
                                drawCircle(
                                    color = Color(0x3F000000),
                                    radius = w * 0.35f
                                )
                                val tPath = Path().apply {
                                    moveTo(w * 0.25f, h * 0.5f)
                                    lineTo(w * 0.75f, h * 0.5f)
                                }
                                drawPath(tPath, Color(0xFFE2E8F0), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                            }

                            // Draw Bridge icon overlay
                            if (tile.hasBridge && actualVisible) {
                                val bPath = Path().apply {
                                    moveTo(w * 0.2f, h * 0.35f)
                                    lineTo(w * 0.8f, h * 0.35f)
                                    moveTo(w * 0.2f, h * 0.65f)
                                    lineTo(w * 0.8f, h * 0.65f)
                                    moveTo(w * 0.4f, h * 0.35f)
                                    lineTo(w * 0.4f, h * 0.65f)
                                    moveTo(w * 0.6f, h * 0.35f)
                                    lineTo(w * 0.6f, h * 0.65f)
                                }
                                drawPath(bPath, Color(0xFFCA8A04), style = Stroke(width = 2.dp.toPx()))
                            }
                        }
                        .clickable { onTileTap(tile.row, tile.col) }
                ) {
                    // Fog darker cover
                    if (!actualVisible) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x99020617))
                        )
                    } else {
                        // Render Castles if visible
                        if (tile.row == state.hostCastlePos.row && tile.col == state.hostCastlePos.col) {
                            Icon(
                                imageVector = Icons.Default.Fort,
                                contentDescription = "P1 Castle",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier
                                    .size((hexRadius.value * 1.35f).dp)
                                    .align(Alignment.Center)
                            )
                        } else if (tile.row == state.clientCastlePos.row && tile.col == state.clientCastlePos.col) {
                            Icon(
                                imageVector = Icons.Default.Fort,
                                contentDescription = "P2 Castle",
                                tint = Color(0xFFF43F5E),
                                modifier = Modifier
                                    .size((hexRadius.value * 1.35f).dp)
                                    .align(Alignment.Center)
                            )
                        }

                        // Render Unit if visible
                        val hostUnit = state.hostDeployedUnits.find { it.row == tile.row && it.col == tile.col }
                        val clientUnit = state.clientDeployedUnits.find { it.row == tile.row && it.col == tile.col }
                        val topUnit = hostUnit ?: clientUnit

                        if (topUnit != null) {
                            val badgeColor = if (topUnit.owner == PlayerType.HOST) Color(0xFF38BDF8) else Color(0xFFF43F5E)
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = if (topUnit.isEngineer) Icons.Default.Build else Icons.Default.DirectionsRun,
                                    contentDescription = topUnit.className,
                                    tint = badgeColor,
                                    modifier = Modifier.size((hexRadius.value * 1.2f).dp)
                                )
                                Text(
                                    text = "${topUnit.hp}",
                                    color = Color.White,
                                    fontSize = (hexRadius.value * 0.55f).sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier
                                        .background(Color(0x99000000), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }

                            // Stacked Unit overlay indicator
                            val unitCount = (state.hostDeployedUnits + state.clientDeployedUnits)
                                .count { it.row == tile.row && it.col == tile.col }
                            if (unitCount > 1) {
                                Box(
                                    modifier = Modifier
                                        .size((hexRadius.value * 0.7f).dp)
                                        .align(Alignment.TopEnd)
                                        .background(Color(0xFF8B5CF6), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("+", color = Color.White, fontSize = (hexRadius.value * 0.45f).sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Write Points for a pointy-topped Hexagon
fun Path.writeHexLine(w: Float, h: Float) {
    lineTo(w, h / 4f)
    lineTo(w, h * 3f / 4f)
    lineTo(w / 2f, h)
    lineTo(0f, h * 3f / 4f)
    lineTo(0f, h / 4f)
    close()
}

// Symmetrical Shape for Compose Hex clip
class HexagonShape : Shape {
    override fun createOutline(size: Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): Outline {
        val path = Path().apply {
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height / 4f)
            lineTo(size.width, size.height * 3f / 4f)
            lineTo(size.width / 2f, size.height)
            lineTo(0f, size.height * 3f / 4f)
            lineTo(0f, size.height / 4f)
            close()
        }
        return Outline.Generic(path)
    }
}

// Helpers for Compose Layout inline physics checks
fun hexDist(r1: Int, c1: Int, r2: Int, c2: Int): Int {
    val q1 = c1 - (r1 - r1.and(1)) / 2
    val r1Ax = r1
    val s1 = -q1 - r1Ax

    val q2 = c2 - (r2 - r2.and(1)) / 2
    val r2Ax = r2
    val s2 = -q2 - r2Ax

    return (abs(q1 - q2) + abs(r1Ax - r2Ax) + abs(s1 - s2)) / 2
}

fun isLOSBlocked(r1: Int, c1: Int, r2: Int, c2: Int, tiles: List<HexTileState>): Boolean {
    val dist = hexDist(r1, c1, r2, c2)
    if (dist <= 1) return false
    
    // Fractional hex line tracing
    val results = mutableListOf<HexPosState>()
    for (i in 0..dist) {
        val t = i.toFloat() / dist.toFloat()
        // attacker cube coords
        val aq = c1 - (r1 - r1.and(1)) / 2
        val ar = r1
        val as_ = -aq - ar

        // target cube coords
        val bq = c2 - (r2 - r2.and(1)) / 2
        val br = r2
        val bs = -bq - br

        val lq = aq + (bq - aq) * t
        val lr = ar + (br - ar) * t
        val ls = as_ + (bs - as_) * t

        // round
        var rq = lq.roundToInt()
        var rr = lr.roundToInt()
        var rs = ls.roundToInt()
        val qDiff = abs(rq - lq)
        val rDiff = abs(rr - lr)
        val sDiff = abs(rs - ls)
        if (qDiff > rDiff && qDiff > sDiff) {
            rq = -rr - rs
        } else if (rDiff > sDiff) {
            rr = -rq - rs
        } else {
            rs = -rq - rr
        }

        // back to offset
        val col = rq + (rr - rr.and(1)) / 2
        results.add(HexPosState(rr, col))
    }

    if (results.size <= 2) return false
    for (i in 1 until results.size - 1) {
        val h = results[i]
        val tile = tiles.find { it.row == h.row && it.col == h.col }
        if (tile?.terrain == TerrainType.MOUNTAINS) {
            return true
        }
    }
    return false
}

fun isTileVisibleFromPlayer(player: PlayerType, row: Int, col: Int, state: GameSessionState): Boolean {
    if (state.fogOfWarMode == FogOfWarMode.FULL_VISIBILITY) return true
    
    // Castle visibility
    val castlePos = if (player == PlayerType.HOST) state.hostCastlePos else state.clientCastlePos
    if (row == castlePos.row && col == castlePos.col) return true

    val units = if (player == PlayerType.HOST) state.hostDeployedUnits else state.clientDeployedUnits
    for (unit in units) {
        val dist = hexDist(unit.row, unit.col, row, col)
        if (dist <= unit.visionRange) {
            if (!isLOSBlocked(unit.row, unit.col, row, col, state.tiles)) {
                return true
            }
        }
    }
    return false
}

fun isMyTurn(state: GameSessionState, player: PlayerType): Boolean {
    return state.activeTurn == player
}

fun getNeighbors(row: Int, col: Int, maxRows: Int, maxCols: Int): List<HexPosState> {
    val directions = if (row % 2 == 0) {
        listOf(
            HexPosState(row - 1, col - 1), HexPosState(row - 1, col),
            HexPosState(row, col - 1),     HexPosState(row, col + 1),
            HexPosState(row + 1, col - 1), HexPosState(row + 1, col)
        )
    } else {
        listOf(
            HexPosState(row - 1, col),     HexPosState(row - 1, col + 1),
            HexPosState(row, col - 1),     HexPosState(row, col + 1),
            HexPosState(row + 1, col),     HexPosState(row + 1, col + 1)
        )
    }
    return directions.filter { it.row in 0 until maxRows && it.col in 0 until maxCols }
}
