package com.example.nivelbolha

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nivelbolha.ui.theme.NivelBolhaTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NivelBolhaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(
                        modifier = Modifier.padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        NivelScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun NivelScreen() {
    val context = LocalContext.current
    
    // Estados para armazenar os ângulos de inclinação (Pitch e Roll)
    var pitch by remember { mutableFloatStateOf(0f) }
    var roll by remember { mutableFloatStateOf(0f) }

    // Obtenção do SensorManager e do Vibrator
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // Gerenciamento do ciclo de vida do sensor
    DisposableEffect(Unit) {
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        
        val sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    val orientationValues = FloatArray(3)

                    // 1. Converte o vetor de rotação em uma matriz de rotação (Fusão de sensores interna do Android)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    
                    // 2. Extrai os ângulos de orientação (Azimuth, Pitch, Roll)
                    SensorManager.getOrientation(rotationMatrix, orientationValues)

                    // 3. Converte de Radianos para Graus
                    // Pitch (inclinação para frente/trás) e Roll (inclinação lateral)
                    val newPitch = Math.toDegrees(orientationValues[1].toDouble()).toFloat()
                    val newRoll = Math.toDegrees(orientationValues[2].toDouble()).toFloat()

                    pitch = newPitch
                    roll = newRoll

                    // 4. Aciona vibração se estiver próximo de 0° ou 90°
                    checkVibration(newPitch, newRoll, vibrator)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // Registra o ouvinte do sensor
        sensorManager.registerListener(sensorEventListener, rotationSensor, SensorManager.SENSOR_DELAY_UI)

        // Remove o ouvinte quando o componente sai da tela para economizar bateria
        onDispose {
            sensorManager.unregisterListener(sensorEventListener)
        }
    }

    // Interface Principal
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        
        Text(text = "Nível de Bolha Digital", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        
        Spacer(modifier = Modifier.weight(1f))

        // Componente visual da bolha
        BubbleLevelDisplay(pitch = pitch, roll = roll)

        Spacer(modifier = Modifier.weight(1f))

        // Dados numéricos
        Text(text = "Pitch: ${pitch.roundToInt()}°", fontSize = 20.sp)
        Text(text = "Roll: ${roll.roundToInt()}°", fontSize = 20.sp)
        
        Spacer(modifier = Modifier.height(60.dp))
    }
}

@Composable
fun BubbleLevelDisplay(pitch: Float, roll: Float) {
    val containerSize = 300.dp
    val bubbleSize = 50.dp
    
    Box(
        modifier = Modifier
            .size(containerSize)
            .border(2.dp, Color.Black, CircleShape)
            .background(Color.LightGray, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Desenha as linhas de referência no fundo
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            // Linha horizontal e vertical
            drawLine(Color.Gray, Offset(0f, center.y), Offset(size.width, center.y), 2f)
            drawLine(Color.Gray, Offset(center.x, 0f), Offset(center.x, size.height), 2f)
            // Círculo central (alvo)
            drawCircle(Color.Gray, radius = 40f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        }

        // A bolha verde que se move
        // Multiplicamos os graus por um fator para dar sensibilidade visual
        val sensitivity = 5f
        val maxOffset = 120f // Limite para a bolha não sair do círculo visual
        
        val offsetX = (roll * sensitivity).coerceIn(-maxOffset, maxOffset)
        val offsetY = (pitch * sensitivity).coerceIn(-maxOffset, maxOffset)

        Box(
            modifier = Modifier
                .size(bubbleSize)
                .align(Alignment.Center)
                .graphicsLayer {
                    translationX = offsetX
                    translationY = offsetY
                }
                .background(Color.Green, CircleShape)
                .border(1.dp, Color.DarkGray, CircleShape)
        )
    }
}

// Lógica de vibração controlada por tempo
private var lastVibrateTime: Long = 0

fun checkVibration(pitch: Float, roll: Float, vibrator: Vibrator) {
    val now = System.currentTimeMillis()
    if (now - lastVibrateTime < 600) return // Intervalo mínimo entre vibrações

    val threshold = 1.0f // Margem de erro de 1 grau
    
    // Verifica se está nivelado (0°) ou em ângulo reto (90° / -90°)
    val isLevel = (pitch in -threshold..threshold && roll in -threshold..threshold)
    val isVerticalRoll = (roll in (90f - threshold)..(90f + threshold) || roll in (-90f - threshold)..(-90f + threshold))
    val isVerticalPitch = (pitch in (90f - threshold)..(90f + threshold) || pitch in (-90f - threshold)..(-90f + threshold))

    if (isLevel || isVerticalRoll || isVerticalPitch) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
        lastVibrateTime = now
    }
}
