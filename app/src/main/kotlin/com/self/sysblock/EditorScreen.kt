package com.self.sysblock

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EditorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("SysBlockPrefs", Context.MODE_PRIVATE)
    
    var configText by remember {
        mutableStateOf(prefs.getString("raw_config", ConfigParser.getDefaultConfig()) ?: "")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        // Header
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Text("CONFIG EDITOR", color = Color.Green, fontFamily = FontFamily.Monospace)
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("CLOSE", fontSize = 12.sp)
            }
        }

        // Editor Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .padding(8.dp)
        ) {
            BasicTextField(
                value = configText,
                onValueChange = { configText = it },
                textStyle = TextStyle(
                    color = Color.LightGray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(Color.Green),
                modifier = Modifier.fillMaxSize()
            )
        }

        // Save Button
        Button(
            onClick = {
                prefs.edit().putString("raw_config", configText).apply()
                Toast.makeText(context, "Config Saved", Toast.LENGTH_SHORT).show()
                onBack() // Go back to Home after save
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text("APPLY & EXIT", color = Color.White)
        }
    }
}