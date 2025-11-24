package com.self.sysblock.ui.home

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.self.sysblock.data.config.AppRule

@Composable
fun ActiveRulesList(
    context: Context,
    rules: List<AppRule>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text("Active Rules", color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(rules) { rule ->
                val appLabel = remember(rule.packageName) {
                    try {
                        val pm = context.packageManager
                        val info = pm.getApplicationInfo(rule.packageName, 0)
                        info.loadLabel(pm).toString()
                    } catch (e: Exception) {
                        rule.packageName
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appLabel, 
                            color = Color.White, 
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = rule.packageName, 
                            color = Color.Gray, 
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Text(
                        if (rule.limitMinutes > 0) "${rule.limitMinutes}m LIMIT" else "INSTANT", 
                        color = if (rule.strictMode) Color.Red else Color.Yellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}