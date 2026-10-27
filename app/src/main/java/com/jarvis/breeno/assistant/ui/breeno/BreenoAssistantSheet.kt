package com.jarvis.breeno.assistant.ui.breeno
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
@Composable fun BreenoAssistantSheet(text:String,onDismiss:()->Unit){AlertDialog(onDismissRequest=onDismiss,confirmButton={TextButton(onClick=onDismiss){Text("Закрыть")}},title={Text("Джарвис")},text={Text(text)})}
