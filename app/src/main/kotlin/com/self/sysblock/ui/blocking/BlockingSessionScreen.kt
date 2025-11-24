package com.self.sysblock.ui.blocking

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.self.sysblock.data.config.ConfigParser
import com.self.sysblock.features.penalty.PenaltyManager
import kotlinx.coroutines.delay

@Composable
fun BlockingSessionScreen(pkgName: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var progress by remember { mutableFloatStateOf(0.0f) }
    var canSelectSession by remember { mutableStateOf(false) }
    var usageStats by remember { mutableStateOf(Pair(0, 0)) }
    var sessionOptions by remember { mutableStateOf(listOf(300, 600, 1200, 1800)) } // Default
    
    var isLockedOut by remember { mutableStateOf(false) }
    var penaltyStatus by remember { mutableStateOf(PenaltyManager.Status(0, false, 0L, 2)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isLockedOut = PenaltyManager.isLockedOut(context, pkgName)
                penaltyStatus = PenaltyManager.getStatus(context, pkgName)
                usageStats = getUsageInfo(context, pkgName)
                
                // Load Dynamic Config
                val prefs = context.getSharedPreferences("SysBlockPrefs", Context.MODE_PRIVATE)
                val rawConfig = prefs.getString("raw_config", ConfigParser.getDefaultConfig()) ?: ""
                val config = ConfigParser.parse(rawConfig)
                if (config.sessionTimes.isNotEmpty()) {
                    sessionOptions = config.sessionTimes
                }
                
                val isLimitReached = usageStats.second > 0 && usageStats.first >= usageStats.second
                if (!isLockedOut && !isLimitReached) {
                    progress = 0f
                    canSelectSession = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isLimitReached = usageStats.second > 0 && usageStats.first >= usageStats.second

    LaunchedEffect(isLockedOut, isLimitReached) {
        if (!isLockedOut && !isLimitReached) {
            val steps = 100
            for (i in 1..steps) {
                delay(50)
                progress = i / 100f
            }
            canSelectSession = true
        }
    }

    BackHandler {
         val homeIntent = Intent(Intent.ACTION_MAIN)
         homeIntent.addCategory(Intent.CATEGORY_HOME)
         homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
         context.startActivity(homeIntent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()), // Added scroll for safety with many buttons
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
        if (isLockedOut) {
            var timeRemainingMs by remember { 
                mutableLongStateOf(penaltyStatus.lockoutEndTime - System.currentTimeMillis()) 
            }

            LaunchedEffect(Unit) {
                while (timeRemainingMs > 0) {
                    delay(1000)
                    val remaining = penaltyStatus.lockoutEndTime - System.currentTimeMillis()
                    timeRemainingMs = remaining
                    if (remaining <= 0) {
                        isLockedOut = false
                        penaltyStatus = PenaltyManager.getStatus(context, pkgName)
                    }
                }
            }
            
            val seconds = (timeRemainingMs / 1000) % 60
            val minutes = (timeRemainingMs / 1000) / 60
            val timeString = String.format("%02d:%02d", minutes, seconds)

            Text("🔒", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Text("PENALTY ACTIVE", color = Color.Red, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text("You are locked out for", color = Color.Gray, fontSize = 16.sp)
            Text(
                text = timeString,
                color = Color.Yellow,
                fontSize = 64.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text("Swipe BACK to exit", color = Color.Gray)
            return@Column 
        }

        if (isLimitReached) {
            Text("⛔", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Text("LIMIT REACHED", color = Color.Red, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Used: ${usageStats.first}m / ${usageStats.second}m",
                color = Color.Yellow,
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "You have exhausted your\nallowance for today.",
                color = Color.White,
                textAlign = TextAlign.Center,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(48.dp))
            Text("Swipe BACK to exit", color = Color.Gray)
            return@Column
        }

        Text(text = "✋", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = if (penaltyStatus.isPenaltyNext) "WARNING" else "STRICT MODE",
            color = if (penaltyStatus.isPenaltyNext) Color(0xFFFF8800) else Color.Red,
            fontSize = 24.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = pkgName.takeLast(15),
            color = Color.Gray,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        val limitText = if (usageStats.second > 0) "${usageStats.second}m" else "Daily Limit"
        Text(
            text = "You used ${usageStats.first}m of your $limitText",
            color = Color.Yellow,
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (penaltyStatus.isPenaltyNext) 
                "Rapid re-entry detected.\nNext session triggers ${penaltyStatus.nextMultiplier}x Penalty." 
                else "Breathe.\nWhy do you need to open this?",
            color = if (penaltyStatus.isPenaltyNext) Color(0xFFFF8800) else Color.White,
            textAlign = TextAlign.Center,
            fontSize = 18.sp,
            lineHeight = 26.sp
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier.height(30.dp).fillMaxWidth(0.7f),
            contentAlignment = Alignment.Center
        ) {
            if (!canSelectSession) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    color = Color.Yellow,
                    trackColor = Color(0xFF333333),
                )
            } else {
                Text(
                    text = "Select Session", 
                    color = Color.Green, 
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        val buttonAlpha by animateFloatAsState(if (canSelectSession) 1f else 0.3f, label = "alpha")

        // --- DYNAMIC GRID GENERATION ---
        // Chunks the options into rows of 2 items each
        val rows = sessionOptions.chunked(2)
        
        rows.forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth().alpha(buttonAlpha)) {
                rowItems.forEachIndexed { index, seconds ->
                    SessionButton(
                        context = context, 
                        pkg = pkgName, 
                        seconds = seconds, 
                        enabled = canSelectSession, 
                        status = penaltyStatus, 
                        modifier = Modifier.weight(1f)
                    )
                    // Add spacer between buttons
                    if (index == 0 && rowItems.size > 1) {
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                }
                // If row has only 1 item, add an invisible spacer to keep the button left-aligned/sized correctly
                if (rowItems.size == 1) {
                     Spacer(modifier = Modifier.width(16.dp))
                     Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(text = "Or swipe BACK to give up", color = Color.DarkGray, fontSize = 14.sp)
    }
}