package com.clippilot.android

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Cyan = Color(0xFF09DFE8)
private val Panel = Color(0xFF17212E)
private val Muted = Color(0xFFABB8C7)
private val Green = Color(0xFF34DBA3)

@Composable
internal fun VideoExportScreen(
    clips: List<Uri>,
    projectName: String,
    onOpenExports: () -> Unit
) {
    val context = LocalContext.current
    val exporter = remember(clips) { ExportVideoEngine(context) }
    var state by remember { mutableStateOf("working") }
    var percent by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(exporter) {
        if (clips.size != 3) {
            state = "error"
            error = "São necessários três clipes para esta montagem."
        } else {
            exporter.start(
                videos = clips,
                onCompleted = {
                    state = "done"
                    percent = 100
                },
                onFailed = {
                    error = it
                    state = "error"
                }
            )
            while (state == "working") {
                exporter.percent()?.let { percent = it }
                delay(400)
            }
        }
    }
    DisposableEffect(exporter) {
        onDispose { exporter.cancel() }
    }

    Spacer(Modifier.height(6.dp))
    Text("Edição automática", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
    Text("Projeto: $projectName", color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(18.dp))
    Box(
        Modifier.fillMaxWidth().background(
            Brush.horizontalGradient(listOf(Color(0xFF055564), Color(0xFF0C2F69))),
            RoundedCornerShape(20.dp)
        ).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (state == "done") Icons.Filled.CheckCircle else Icons.Filled.Movie,
                null,
                tint = if (state == "done") Green else Cyan,
                modifier = Modifier.size(65.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (state == "done") "MP4 pronto!" else if (state == "error") "Exportação interrompida" else "Montando os 3 clipes",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 21.sp
            )
            Text(
                if (state == "done") "Vídeo criado com áudio original."
                else if (state == "error") "Confira o erro abaixo."
                else "Unindo cenas e áudio em sequência",
                color = Muted, fontSize = 12.sp
            )
        }
    }
    Spacer(Modifier.height(22.dp))
    if (state != "error") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (state == "done") "Exportação concluída" else "Renderizando MP4", color = Color.White, fontWeight = FontWeight.Bold)
            Text("$percent%", color = Cyan, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.fillMaxWidth().height(9.dp),
            color = Cyan,
            trackColor = Panel
        )
        Spacer(Modifier.height(18.dp))
    }
    Column(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Etapas da montagem", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("✓ Três vídeos preparados", color = Green)
        Text(if (state == "working") "● Montagem e codificação em andamento" else "✓ Montagem dos clipes", color = if (state == "working") Cyan else Green)
        Text(if (state == "done") "✓ MP4 validado" else "○ Validação do arquivo final", color = if (state == "done") Green else Muted)
    }
    Spacer(Modifier.height(16.dp))
    Text("Nesta entrega: cortes retos entre os clipes, áudio original e exportação MP4. Transições, correção de cor e legendas serão adicionadas depois.", color = Muted, fontSize = 12.sp)
    if (state == "error") {
        Spacer(Modifier.height(16.dp))
        Text(error, color = Color(0xFFFF9999), fontSize = 13.sp)
    }
    Spacer(Modifier.height(20.dp))
    if (state == "done") {
        Button(
            onClick = onOpenExports,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan)
        ) {
            Text("Ver vídeo exportado", fontWeight = FontWeight.Bold)
        }
    } else if (state == "working") {
        OutlinedButton(
            onClick = { exporter.cancel(); state = "error"; error = "Exportação cancelada." },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancelar exportação")
        }
    }
}
