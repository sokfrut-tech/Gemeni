package com.jarvis.breeno.assistant.ui.breeno

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BreenoAssistantSheet(text: String, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF101722),
        contentColor = Color.White,
        dragHandle = { }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101722), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Джарвис", fontWeight = FontWeight.Bold)
            Text(text, color = Color(0xFFC5D0DE))
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FD9FF), contentColor = Color(0xFF061017))
            ) { Text("Готово") }
            Spacer(Modifier.height(12.dp))
        }
    }
}
