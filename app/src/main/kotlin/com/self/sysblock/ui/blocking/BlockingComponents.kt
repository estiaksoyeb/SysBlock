package com.self.sysblock.ui.blocking

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.self.sysblock.features.penalty.PenaltyManager
import com.self.sysblock.ui.blocking.BlockingActivity

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

    val minutes = seconds / 60
    
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
                text = "${minutes}m", 
                color = if (enabled) Color.White else Color.Gray,
                fontSize = 18.sp
            )
            if (isPenalty && enabled) {
                val penaltyMinutes = (seconds * multiplier) / 60
                Text(
                    text = "+${penaltyMinutes}m Lock",
                    color = Color.Red,
                    fontSize = 10.sp
                )
            }
        }
    }
}