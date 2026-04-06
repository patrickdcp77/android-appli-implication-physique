package com.example.android_ble_judo_3capteurs

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.android_ble_judo_3capteurs.ble.DEVICE_NAME
import com.example.android_ble_judo_3capteurs.ble.DeviceConnectionStatus
import com.example.android_ble_judo_3capteurs.ble.DeviceDataState
import com.example.android_ble_judo_3capteurs.ble.ScannedBleDevice
import com.example.android_ble_judo_3capteurs.data.AccelData
import com.example.android_ble_judo_3capteurs.data.CardioData
import com.example.android_ble_judo_3capteurs.data.TempData
import com.example.android_ble_judo_3capteurs.ui.BleViewModel
import com.example.android_ble_judo_3capteurs.ui.DeviceGraphHistory
import com.example.android_ble_judo_3capteurs.ui.theme.Android_BLE_judo_3capteursTheme
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Android_BLE_judo_3capteursTheme {
                JudoSensorApp()
            }
        }
    }
}

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun JudoSensorApp(vm: BleViewModel = viewModel()) {
    val context = LocalContext.current
    val isScanning by vm.isScanning.collectAsState()
    val scanDevices by vm.scanDevices.collectAsState()
    val deviceStates by vm.deviceStates.collectAsState()
    val errorMessage by vm.errorMessage.collectAsState()
    val graphHistory by vm.graphHistory.collectAsState()
    val graphSelection by vm.graphSelection.collectAsState()

    val connectedCount = deviceStates.values.count {
        it.status == DeviceConnectionStatus.CONNECTED || it.status == DeviceConnectionStatus.CONNECTING
    }

    val permissions = requiredPermissions()
    val allGranted = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) vm.startScan()
    }

    LaunchedEffect(allGranted) {
        if (allGranted) vm.startScan()
    }

    var selectedPage by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Implication physique des judokas") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedPage) {
                Tab(selected = selectedPage == 0, onClick = { selectedPage = 0 }, text = { Text("Connexion") })
                Tab(selected = selectedPage == 1, onClick = { selectedPage = 1 }, text = { Text("Graphes") })
            }

            when (selectedPage) {
                0 -> ConnectionPage(
                    allGranted = allGranted,
                    permissions = permissions,
                    isScanning = isScanning,
                    connectedCount = connectedCount,
                    errorMessage = errorMessage,
                    scanDevices = scanDevices,
                    deviceStates = deviceStates,
                    onRequestPermissions = { permLauncher.launch(permissions) },
                    onStartScan = vm::startScan,
                    onStopScan = vm::stopScan,
                    onConnect = vm::connect,
                    onDisconnect = vm::disconnect
                )

                else -> GraphPage(
                    deviceStates = deviceStates,
                    graphHistory = graphHistory,
                    graphSelection = graphSelection,
                    onToggleSelection = vm::toggleGraphSelection
                )
            }
        }
    }
}

@Composable
private fun ConnectionPage(
    allGranted: Boolean,
    permissions: Array<String>,
    isScanning: Boolean,
    connectedCount: Int,
    errorMessage: String?,
    scanDevices: List<ScannedBleDevice>,
    deviceStates: Map<String, DeviceDataState>,
    onRequestPermissions: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatusCard(isScanning = isScanning, connectedCount = connectedCount)

        if (!allGranted) {
            Button(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) {
                Text("Autoriser BLE")
            }
            Text("Permissions requises: ${permissions.joinToString()}", style = MaterialTheme.typography.bodySmall)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStartScan, modifier = Modifier.weight(1f)) {
                    Text(if (isScanning) "Scan actif" else "Demarrer scan")
                }
                Button(onClick = onStopScan, modifier = Modifier.weight(1f)) {
                    Text("Stop scan")
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        ScanListCard(
            devices = scanDevices,
            states = deviceStates,
            connectedCount = connectedCount,
            onConnect = onConnect,
            onDisconnect = onDisconnect
        )

        val activeDevices = deviceStates.values.filter {
            it.status == DeviceConnectionStatus.CONNECTED || it.status == DeviceConnectionStatus.CONNECTING
        }

        if (activeDevices.isEmpty()) {
            Text("Aucun BLE connecte. Selectionner 1 ou 2 peripheriques.")
        } else {
            activeDevices.forEach { state ->
                DeviceDataCard(state)
            }
        }
    }
}

@Composable
private fun GraphPage(
    deviceStates: Map<String, DeviceDataState>,
    graphHistory: Map<String, DeviceGraphHistory>,
    graphSelection: Set<String>,
    onToggleSelection: (String) -> Unit
) {
    val connected = deviceStates.values.filter { it.status == DeviceConnectionStatus.CONNECTED }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Graphes regroupes des BLE selectionnes", fontWeight = FontWeight.SemiBold)

        if (connected.isEmpty()) {
            Text("Aucun BLE connecte. Va dans Connexion pour connecter 1 ou 2 BLE.")
            return
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            connected.forEach { device ->
                FilterChip(
                    selected = graphSelection.contains(device.address),
                    onClick = { onToggleSelection(device.address) },
                    label = { Text(device.name) }
                )
            }
        }

        val selectedStates = connected.filter { graphSelection.contains(it.address) }
        if (selectedStates.isEmpty()) {
            Text("Selectionne au moins un BLE pour afficher les graphes.")
            return
        }

        val names = selectedStates.associate { it.address to it.name }

        MultiBleGraphCard(
            title = "Temperature (C)",
            unit = "C",
            series = selectedStates.associate { it.address to (graphHistory[it.address]?.temp ?: emptyList()) },
            names = names,
            clampMin = null,
            clampMax = null
        )

        MultiBleGraphCard(
            title = "BPM moyen",
            unit = " bpm",
            series = selectedStates.associate { it.address to (graphHistory[it.address]?.cardioAvg ?: emptyList()) },
            names = names,
            clampMin = 0f,
            clampMax = null
        )

        MultiBleGraphCard(
            title = "Acceleration globale (g)",
            unit = "g",
            series = selectedStates.associate { it.address to (graphHistory[it.address]?.accel ?: emptyList()) },
            names = names,
            clampMin = null,
            clampMax = null
        )

        MultiBleGraphCard(
            title = "Detection impact (0/1)",
            unit = "",
            series = selectedStates.associate { it.address to (graphHistory[it.address]?.impact ?: emptyList()) },
            names = names,
            clampMin = 0f,
            clampMax = 1f
        )
    }
}

@Composable
private fun StatusCard(isScanning: Boolean, connectedCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Cible BLE: $DEVICE_NAME", fontWeight = FontWeight.SemiBold)
            Text(if (isScanning) "Scan: actif" else "Scan: inactif")
            Text("Connexions actives: $connectedCount / 2")
        }
    }
}

@Composable
private fun ScanListCard(
    devices: List<ScannedBleDevice>,
    states: Map<String, DeviceDataState>,
    connectedCount: Int,
    onConnect: (String) -> Unit,
    onDisconnect: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Peripheriques detectes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (devices.isEmpty()) {
                Text("Aucun device trouve pour le moment")
            } else {
                devices.forEach { device ->
                    val status = states[device.address]?.status ?: DeviceConnectionStatus.DISCONNECTED
                    val isConnected = status == DeviceConnectionStatus.CONNECTED || status == DeviceConnectionStatus.CONNECTING
                    val connectEnabled = isConnected || connectedCount < 2

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(device.name, fontWeight = FontWeight.SemiBold)
                            Text(device.address, style = MaterialTheme.typography.bodySmall)
                            Text("RSSI ${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isConnected) {
                            Button(onClick = { onDisconnect(device.address) }) { Text("Deconnecter") }
                        } else {
                            Button(onClick = { onConnect(device.address) }, enabled = connectEnabled) { Text("Connecter") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceDataCard(state: DeviceDataState) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("BLE ${state.name}", fontWeight = FontWeight.Bold)
            Text(state.address, style = MaterialTheme.typography.bodySmall)
            Text("Etat: ${state.status}")
            TemperatureCard(state.tempData)
            CardioCard(state.cardioData)
            AccelCard(state.accelData)
        }
    }
}

@Composable
private fun TemperatureCard(data: TempData) {
    SensorCard(title = "Temperature") {
        BigValue(value = "%.1f".format(data.tempC), unit = "C")
    }
}

@Composable
private fun CardioCard(data: CardioData) {
    SensorCard(title = "Cardio") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ValueColumn(label = "BPM", value = "%.1f".format(data.bpm))
            ValueColumn(label = "Moy BPM", value = "%.1f".format(data.bpmAvg))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "IR brut: ${data.rawIr}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AccelCard(data: AccelData) {
    SensorCard(title = "Acceleration") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ValueColumn(label = "Moy", value = "%.3f g".format(data.avgG))
            ValueColumn(label = "Pic", value = "%.3f g".format(data.peakG))
            ValueColumn(label = "Delta", value = "%.3f g".format(data.deltaPeakG))
        }
        Spacer(modifier = Modifier.height(8.dp))
        val impactText = if (data.impact) "IMPACT DETECTE" else "Pas d'impact"
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = impactText, modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SensorCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun BigValue(value: String, unit: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text = value, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = unit, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ValueColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MultiBleGraphCard(
    title: String,
    unit: String,
    series: Map<String, List<Float>>,
    names: Map<String, String>,
    clampMin: Float?,
    clampMax: Float?
) {
    val palette = listOf(
        Color(0xFF1E88E5),
        Color(0xFFD81B60),
        Color(0xFF43A047),
        Color(0xFFFB8C00)
    )

    val nonEmpty = series.filterValues { it.size >= 2 }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

            if (nonEmpty.isEmpty()) {
                Text("Pas assez de points pour tracer.")
            } else {
                val allValues = nonEmpty.values.flatten()
                val minVal = clampMin ?: allValues.minOrNull() ?: 0f
                val maxVal = clampMax ?: allValues.maxOrNull() ?: 1f
                val adjustedMax = if (abs(maxVal - minVal) < 0.0001f) minVal + 1f else maxVal

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                ) {
                    drawLine(
                        color = Color.LightGray,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 2f
                    )

                    nonEmpty.entries.forEachIndexed { index, entry ->
                        val values = entry.value
                        val color = palette[index % palette.size]
                        val stepX = if (values.size > 1) size.width / (values.size - 1).toFloat() else size.width
                        val points = values.mapIndexed { i, v ->
                            val normalized = (v - minVal) / (adjustedMax - minVal)
                            val y = size.height - (normalized * size.height)
                            Offset(i * stepX, y)
                        }
                        for (i in 0 until points.lastIndex) {
                            drawLine(
                                color = color,
                                start = points[i],
                                end = points[i + 1],
                                strokeWidth = 4f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                Text("min ${"%.2f".format(minVal)}$unit | max ${"%.2f".format(adjustedMax)}$unit", style = MaterialTheme.typography.bodySmall)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    nonEmpty.keys.forEachIndexed { index, address ->
                        val color = palette[index % palette.size]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = color, modifier = Modifier.width(10.dp).height(10.dp), shape = MaterialTheme.shapes.extraSmall) {}
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(names[address] ?: address, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

