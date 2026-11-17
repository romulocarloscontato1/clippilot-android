package com.clippilot.android

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONArray

private val Background = Color(0xFF090E16)
private val Panel = Color(0xFF17212E)
private val PanelLight = Color(0xFF1D2B3A)
private val Cyan = Color(0xFF09DFE8)
private val Blue = Color(0xFF0754B9)
private val Muted = Color(0xFFABB8C7)
private val Green = Color(0xFF34DBA3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 14, 22)
        window.navigationBarColor = android.graphics.Color.rgb(9, 14, 22)
        setContent { ClipPilotApp() }
    }
}

private fun prefs(context: Context) =
    context.getSharedPreferences("clippilot-project", Context.MODE_PRIVATE)

private fun savedClips(context: Context): List<Uri> = try {
    val array = JSONArray(prefs(context).getString("clips", "[]"))
    (0 until array.length()).map { Uri.parse(array.getString(it)) }
} catch (_: Exception) { emptyList() }

private fun persistClips(context: Context, uris: List<Uri>) {
    val array = JSONArray()
    uris.forEach { array.put(it.toString()) }
    prefs(context).edit().putString("clips", array.toString()).apply()
}

private fun duration(context: Context, uri: Uri): String {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val millis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        "%02d:%02d".format(millis / 60000, (millis / 1000) % 60)
    } catch (_: Exception) { "--:--" }
    finally { try { retriever.release() } catch (_: Exception) {} }
}

private fun thumbnail(context: Context, uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?.let { Bitmap.createScaledBitmap(it, 240, 150, true) }
    } catch (_: Exception) { null }
    finally { try { retriever.release() } catch (_: Exception) {} }
}

@Composable
private fun ClipPilotApp() {
    val context = LocalContext.current
    val clips = remember { mutableStateListOf<Uri>().apply { addAll(savedClips(context)) } }
    var screen by remember { mutableStateOf("home") }
    var projectName by remember { mutableStateOf(prefs(context).getString("name", "Meu primeiro vídeo") ?: "Meu primeiro vídeo") }
    var style by remember { mutableStateOf("Viral dinâmico") }
    var previewIndex by remember { mutableIntStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selected ->
        if (selected.isNotEmpty()) {
            selected.take(3).forEach {
                try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                catch (_: Exception) {}
            }
            clips.clear()
            clips.addAll(selected.take(3))
            persistClips(context, clips)
            Toast.makeText(context, "${clips.size} clipe(s) carregado(s)", Toast.LENGTH_SHORT).show()
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Cyan, onPrimary = Background, secondary = Color(0xFF56A8F7),
        background = Background, surface = Panel, onSurface = Color.White
    )) {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                Navigation(screen) { screen = it }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                BrandHeader()
                Spacer(Modifier.height(20.dp))
                when (screen) {
                    "home" -> HomeScreen(clips.size) { screen = "create" }
                    "create" -> CreateScreen(
                        projectName = projectName,
                        setName = { projectName = it; prefs(context).edit().putString("name", it).apply() },
                        clips = clips, style = style, setStyle = { style = it },
                        pick = { picker.launch(arrayOf("video/*")) },
                        clear = { clips.clear(); persistClips(context, clips) },
                        preview = { previewIndex = it; screen = "preview" }
                    )
                    "preview" -> PreviewScreen(
                        projectName = projectName,
                        clips = clips,
                        initialIndex = previewIndex,
                        style = style,
                        back = { screen = "create" }
                    )
                    "projects" -> ProjectsScreen(projectName, clips.size) { screen = "create" }
                }
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(listOf(Cyan, Blue))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Background, modifier = Modifier.size(35.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("ClipPilot", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Edição automática de vídeos", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.weight(1f))
        Text("v0.1", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HomeScreen(clipCount: Int, onNew: () -> Unit) {
    Column {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF044D60), Color(0xFF072852), Blue)))
                .padding(20.dp)
        ) {
            Column {
                Text("Crie vídeos incríveis", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 25.sp)
                Text("com seus próprios clipes", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Spacer(Modifier.height(12.dp))
                Text("Importe três clipes, organize o projeto e acompanhe a evolução do editor.", color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(22.dp))
                Button(onClick = onNew, colors = ButtonDefaults.buttonColors(containerColor = Cyan)) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Novo projeto", fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("Projetos", "1", Modifier.weight(1f))
            Metric("Clipes", clipCount.toString(), Modifier.weight(1f))
            Metric("Exportados", "0", Modifier.weight(1f))
        }
        Spacer(Modifier.height(26.dp))
        SectionTitle("Seu projeto atual")
        Spacer(Modifier.height(10.dp))
        SurfaceCard {
            Row(Modifier.fillMaxWidth().clickable(onClick = onNew).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Movie, null, tint = Cyan, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Meu primeiro vídeo", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("$clipCount de 3 clipes selecionados", color = Muted, fontSize = 12.sp)
                }
                Text("Abrir", color = Cyan, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(26.dp))
        SectionTitle("Modelos disponíveis")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StyleCard("Viral dinâmico", "Cortes rápidos", selected = false, onClick = onNew)
            StyleCard("Profissional", "Discreto e limpo", selected = false, onClick = onNew)
            StyleCard("Cinematográfico", "Ritmo suave", selected = false, onClick = onNew)
        }
        Spacer(Modifier.height(16.dp))
        Text("A montagem automática, legendas e exportação serão adicionadas nas próximas versões.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    SurfaceCard(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = Muted, fontSize = 11.sp)
            Spacer(Modifier.height(3.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        }
    }
}

@Composable
private fun CreateScreen(
    projectName: String, setName: (String) -> Unit,
    clips: SnapshotStateList<Uri>, style: String, setStyle: (String) -> Unit,
    pick: () -> Unit, clear: () -> Unit, preview: (Int) -> Unit
) {
    Text("Novo projeto", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
    Text("Selecione seus clipes", color = Muted, fontSize = 14.sp)
    Spacer(Modifier.height(22.dp))
    Text("Nome do projeto", color = Color.White, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = projectName, onValueChange = setName,
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF5F7187),
            focusedTextColor = Color.White, unfocusedTextColor = Color.White
        )
    )
    Spacer(Modifier.height(22.dp))
    Text("Etapa 1 de 3", color = Cyan, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    Text("Adicione até 3 clipes para montar seu projeto", color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(14.dp))
    for (index in 0..2) {
        if (index < clips.size) {
            ClipItem(index, clips[index]) { preview(index) }
        } else {
            SurfaceCard {
                Row(Modifier.fillMaxWidth().clickable(onClick = pick).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(92.dp, 62.dp).background(PanelLight, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, null, tint = Cyan, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Clipe ${index + 1}", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Toque para selecionar vídeos", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(9.dp))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = pick, colors = ButtonDefaults.buttonColors(containerColor = Cyan), modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.VideoLibrary, null)
            Spacer(Modifier.width(6.dp))
            Text("Selecionar vídeos", fontSize = 12.sp)
        }
        if (clips.isNotEmpty()) {
            OutlinedButton(onClick = clear) { Text("Limpar", fontSize = 12.sp) }
        }
    }
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallOption("Formato", "9:16")
        SmallOption("Legendas", "Em breve")
        SmallOption("Música", "Original")
    }
    Spacer(Modifier.height(22.dp))
    SectionTitle("Escolha o estilo")
    Text("Preferência visual — aplicação automática em desenvolvimento", color = Muted, fontSize = 11.sp)
    Spacer(Modifier.height(12.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StyleCard("Viral dinâmico", "Ritmo rápido", style == "Viral dinâmico") { setStyle("Viral dinâmico") }
        StyleCard("Profissional", "Limpo", style == "Profissional") { setStyle("Profissional") }
        StyleCard("Cinematográfico", "Suave", style == "Cinematográfico") { setStyle("Cinematográfico") }
    }
    Spacer(Modifier.height(25.dp))
    Button(
        onClick = { preview(0) }, enabled = clips.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().height(53.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Cyan)
    ) {
        Icon(Icons.Filled.PlayArrow, null)
        Spacer(Modifier.width(8.dp))
        Text("Prévia dos clipes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
    Spacer(Modifier.height(8.dp))
    Text("A prévia reproduz cada clipe original; ainda não há vídeo final renderizado.", color = Muted, fontSize = 11.sp)
}

@Composable
private fun ClipItem(index: Int, uri: Uri, preview: () -> Unit) {
    val context = LocalContext.current
    val bmp = remember(uri) { thumbnail(context, uri) }
    val videoDuration = remember(uri) { duration(context, uri) }
    SurfaceCard {
        Row(Modifier.fillMaxWidth().clickable(onClick = preview).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(100.dp, 64.dp).clip(RoundedCornerShape(11.dp)).background(PanelLight),
                contentAlignment = Alignment.Center) {
                if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Filled.Movie, null, tint = Cyan)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Clipe ${index + 1}", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("✓ Carregado • $videoDuration", color = Green, fontSize = 12.sp)
            }
            Icon(Icons.Filled.PlayArrow, "Reproduzir", tint = Cyan)
        }
    }
}

@Composable
private fun SmallOption(title: String, value: String) {
    SurfaceCard {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(title, color = Muted, fontSize = 10.sp)
            Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StyleCard(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) Cyan else Color(0xFF274861)
    Column(
        Modifier.width(138.dp).height(114.dp).border(1.5.dp, borderColor, RoundedCornerShape(15.dp))
            .clip(RoundedCornerShape(15.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF064D61), Color(0xFF112138))))
            .clickable(onClick = onClick).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SmartDisplay, null, tint = Cyan, modifier = Modifier.size(21.dp))
            Spacer(Modifier.weight(1f))
            if (selected) Icon(Icons.Filled.CheckCircle, null, tint = Green, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(9.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2)
        Text(detail, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PreviewScreen(projectName: String, clips: List<Uri>, initialIndex: Int, style: String, back: () -> Unit) {
    if (clips.isEmpty()) {
        Text("Nenhum clipe disponível", color = Color.White)
        return
    }
    var current by remember { mutableIntStateOf(initialIndex.coerceIn(0, clips.lastIndex)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back) { Icon(Icons.Filled.ArrowBack, "Voltar", tint = Color.White) }
        Column {
            Text("Prévia dos clipes", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(projectName, color = Muted, fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(16.dp))
    VideoPlayer(clips[current])
    Spacer(Modifier.height(15.dp))
    Text("Selecione um clipe", color = Color.White, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        clips.forEachIndexed { index, _ ->
            FilterChip(
                selected = current == index,
                onClick = { current = index },
                label = { Text("Clipe ${index + 1}") },
                leadingIcon = if (current == index) {{ Icon(Icons.Filled.PlayArrow, null, Modifier.size(16.dp)) }} else null
            )
        }
    }
    Spacer(Modifier.height(22.dp))
    SectionTitle("Ajustes do projeto")
    Spacer(Modifier.height(10.dp))
    SurfaceCard {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            PropertyRow("Estilo selecionado", style)
            Spacer(Modifier.height(10.dp))
            PropertyRow("Vídeos importados", "${clips.size} de 3")
            Spacer(Modifier.height(10.dp))
            PropertyRow("Trilha", "Áudio original")
        }
    }
    Spacer(Modifier.height(18.dp))
    SurfaceCard {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Montagem automática em desenvolvimento", color = Cyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(
                "Nesta versão você consegue importar e assistir aos clipes reais. A junção dos vídeos, as transições, a correção de cores, as legendas e a exportação MP4 serão disponibilizadas em próximas entregas.",
                color = Muted, fontSize = 13.sp
            )
        }
    }
    Spacer(Modifier.height(18.dp))
    OutlinedButton(onClick = back, modifier = Modifier.fillMaxWidth()) { Text("Voltar ao projeto") }
}

@Composable
private fun VideoPlayer(uri: Uri) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { PlayerView(it).apply { useController = true; this.player = player } },
        update = { it.player = player },
        modifier = Modifier.fillMaxWidth().height(325.dp).clip(RoundedCornerShape(17.dp))
            .background(Color.Black)
    )
}

@Composable
private fun ProjectsScreen(projectName: String, clipCount: Int, open: () -> Unit) {
    SectionTitle("Projetos")
    Spacer(Modifier.height(8.dp))
    Text("Seus projetos locais", color = Muted)
    Spacer(Modifier.height(18.dp))
    SurfaceCard {
        Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Folder, null, tint = Cyan, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text(projectName, color = Color.White, fontWeight = FontWeight.Bold)
                Text("$clipCount de 3 clipes • Rascunho local", color = Muted, fontSize = 12.sp)
            }
            Text("Editar", color = Cyan)
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("Esta versão mantém um projeto local. O catálogo com vários projetos virá nas próximas entregas.", color = Muted, fontSize = 12.sp)
}

@Composable
private fun PropertyRow(name: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, color = Muted, fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Panel)) { content() }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun Navigation(selected: String, navigate: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF101720)).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        NavItem(Icons.Filled.Home, "Início", selected == "home") { navigate("home") }
        NavItem(Icons.Filled.Folder, "Projetos", selected == "projects") { navigate("projects") }
        Box(Modifier.size(55.dp).clip(RoundedCornerShape(17.dp)).background(Cyan)
            .clickable { navigate("create") }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, "Novo projeto", tint = Background, modifier = Modifier.size(32.dp))
        }
        NavItem(Icons.Filled.Movie, "Criar", selected == "create" || selected == "preview") { navigate("create") }
    }
}

@Composable
private fun NavItem(image: androidx.compose.ui.graphics.vector.ImageVector, name: String, selected: Boolean, click: () -> Unit) {
    Column(Modifier.clickable(onClick = click).padding(horizontal = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(image, name, tint = if (selected) Cyan else Muted, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(3.dp))
        Text(name, color = if (selected) Cyan else Muted, fontSize = 11.sp)
    }
}
