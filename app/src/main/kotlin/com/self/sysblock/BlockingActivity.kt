package com.self.sysblock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import kotlinx.coroutines.delay

class BlockingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        val blockedPkg = intent.getStringExtra("pkg_name") ?: "App"
        setContent { BlockingSessionScreen(blockedPkg) }
    }
}

@Composable
fun BlockingSessionScreen(pkgName: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var progress by remember { mutableStateOf(0.0f) }
    var canSelectSession by remember { mutableStateOf(false) }
    var usageStats by remember { mutableStateOf(Pair(0, 0)) }
    
    // State initialization with defaults
    var isLockedOut by remember { mutableStateOf(false) }
    var penaltyStatus by remember { mutableStateOf(PenaltyManager.Status(0, false, 0L, 2)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isLockedOut = PenaltyManager.isLockedOut(context, pkgName)
                penaltyStatus = PenaltyManager.getStatus(context, pkgName)
                usageStats = getUsageInfo(context, pkgName)
                if (!isLockedOut) {
                    progress = 0f
                    canSelectSession = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isLockedOut) {
        if (!isLockedOut) {
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
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
        // --- LOCKOUT SCREEN ---
        if (isLockedOut) {
            var timeRemainingMs by remember { 
                mutableStateOf(penaltyStatus.lockoutEndTime - System.currentTimeMillis()) 
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

        // --- SELECTION SCREEN ---
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

        // DYNAMIC WARNING TEXT
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
                    progress = progress,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    color = Color.Yellow,
                    trackColor = Color(0xFF333333)
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

        val buttonAlpha by animateFloatAsState(if (canSelectSession) 1f else 0.3f)

        // Using SECONDS for testing
        Row(modifier = Modifier.fillMaxWidth().alpha(buttonAlpha)) {
            SessionButton(context, pkgName, 10, canSelectSession, penaltyStatus, Modifier.weight(1f))
            Spacer(modifier = Modifier.width(16.dp))
            SessionButton(context, pkgName, 30, canSelectSession, penaltyStatus, Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth().alpha(buttonAlpha)) {
            SessionButton(context, pkgName, 60, canSelectSession, penaltyStatus, Modifier.weight(1f))
            Spacer(modifier = Modifier.width(16.dp))
            SessionButton(context, pkgName, 300, canSelectSession, penaltyStatus, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(text = "Or swipe BACK to give up", color = Color.DarkGray, fontSize = 14.sp)
    }
}

@Composable
fun SessionButton(
    context: Context, 
    pkg: String, 
    seconds: Int, 
    enabled: Boolean,
    status: PenaltyManager.Status,
    modifier: Modifier = Modifier
) {
    val activity = context as? BlockingActivity
    val isPenalty = status.isPenaltyNext
    val multiplier = status.nextMultiplier
    
    val btnColor = if (isPenalty && enabled) Color(0xFF440000) else if (enabled) Color(0xFF1E1E1E) else Color.Black
    val borderColor = if (isPenalty && enabled) Color.Red else if (enabled) Color.Gray else Color.DarkGray

    Button(
        onClick = {
            if (!enabled) return@Button
            
            PenaltyManager.startSession(context, pkg, seconds)
            
            try {
                val pm = context.packageManager
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    context.startActivity(launchIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            activity?.finishAffinity()
        },
        colors = ButtonDefaults.buttonColors(containerColor = btnColor),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .height(60.dp)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${seconds}s",
                color = if (enabled) Color.White else Color.Gray,
                fontSize = 18.sp
            )
            if (isPenalty && enabled) {
                Text(
                    text = "+${seconds * multiplier}s Lock",
                    color = Color.Red,
                    fontSize = 10.sp
                )
            }
        }
    }
}

fun getUsageInfo(context: Context, pkgName: String): Pair<Int, Int> {
    val prefs = context.getSharedPreferences("SysBlockPrefs", Context.MODE_PRIVATE)
    val rawConfig = prefs.getString("raw_config", ConfigParser.getDefaultConfig()) ?: ""
    val config = ConfigParser.parse(rawConfig)
    val rule = config.rules.find { it.packageName == pkgName }
    val limit = rule?.limitMinutes ?: 0

    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
    val calendar = java.util.Calendar.getInstance()
    val endTime = calendar.timeInMillis
    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
    calendar.set(java.util.Calendar.MINUTE, 0)
    calendar.set(java.util.Calendar.SECOND, 0)
    val startTime = calendar.timeInMillis

    val stats = usageStatsManager.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
    val usage = stats?.find { it.packageName == pkgName }
    val used = (usage?.totalTimeInForeground ?: 0) / 1000 / 60

    return Pair(used.toInt(), limit)
}