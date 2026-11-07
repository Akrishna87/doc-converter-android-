package com.akrishna.docconverter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent { AppTheme { Surface(Modifier.fillMaxSize()) { Screen(vm) } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    @Suppress("DEPRECATION")
    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { vm.setFiles(listOf(it), false) }
            Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { vm.setFiles(it, false) }
        }
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private val PICK_TYPES = arrayOf("image/*", "application/pdf", "text/*", DOCX_MIME, "application/octet-stream")

private fun chipLabel(f: Fmt, allPdf: Boolean) = if (f == Fmt.PDF && allPdf) "PDF pages (extract/split)" else f.label

@Composable
private fun Screen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val replace = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.setFiles(it, false) }
    val append = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.setFiles(it, true) }
    val target = vm.target
    val opts = vm.options

    Scaffold { pad ->
        Column(
            Modifier.padding(pad).padding(20.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Doc Converter", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Convert images, PDF, Word, text and Markdown on your device. Nothing is uploaded.",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (vm.sources.isEmpty()) {
                Button(onClick = { replace.launch(PICK_TYPES) }, modifier = Modifier.fillMaxWidth()) { Text("Choose files") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { append.launch(PICK_TYPES) }) { Text("Add more") }
                    TextButton(onClick = vm::clear) { Text("Clear") }
                }
                vm.sources.forEach { s ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.name, style = MaterialTheme.typography.bodyMedium)
                                Text(s.fmt.label, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { vm.remove(s) }) { Text("Remove") }
                        }
                    }
                }
            }

            if (vm.sources.isNotEmpty() && vm.targets.isEmpty()) {
                Text("These files have no conversion in common. Remove some to continue.", color = MaterialTheme.colorScheme.error)
            }

            if (vm.targets.isNotEmpty()) {
                Text("Convert to", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.targets.forEach { f ->
                        FilterChip(selected = target == f, onClick = { vm.selectTarget(f) }, label = { Text(chipLabel(f, vm.allPdf)) })
                    }
                }

                // ---- options relevant to the current selection ----
                if (target == Fmt.JPG || target == Fmt.WEBP) {
                    Text("Image quality: ${opts.imageQuality}%", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = opts.imageQuality.toFloat(),
                        onValueChange = { v -> vm.update { it.copy(imageQuality = v.toInt()) } },
                        valueRange = 50f..100f,
                    )
                }
                if (vm.anyPdf && target?.isImage == true) {
                    Text("PDF page resolution", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1f, 2f, 3f).forEach { sc ->
                            FilterChip(selected = opts.pdfScale == sc, onClick = { vm.update { it.copy(pdfScale = sc) } }, label = { Text("${sc.toInt()}x") })
                        }
                    }
                }
                if (target == Fmt.PDF && vm.anyTextLike) {
                    Text("Page size", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PageSize.entries.forEach { ps ->
                            FilterChip(selected = opts.pageSize == ps, onClick = { vm.update { it.copy(pageSize = ps) } }, label = { Text(ps.label) })
                        }
                    }
                    Text("Font size: ${opts.fontSize.toInt()} pt", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = opts.fontSize,
                        onValueChange = { v -> vm.update { it.copy(fontSize = Math.round(v).toFloat()) } },
                        valueRange = 8f..18f,
                    )
                }
                if (target == Fmt.PDF && vm.sources.size > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = opts.combine, onCheckedChange = { c -> vm.update { it.copy(combine = c) } })
                        Text("Combine all files into one PDF")
                    }
                }
                if (target == Fmt.PDF && vm.allPdf && !opts.combine) {
                    OutlinedTextField(
                        value = opts.pageRange,
                        onValueChange = { v -> vm.update { it.copy(pageRange = v) } },
                        label = { Text("Pages to keep (e.g. 1-3,5,8-)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = opts.splitEach, onCheckedChange = { c -> vm.update { it.copy(splitEach = c) } })
                        Text("Split into one file per page")
                    }
                }

                Button(onClick = vm::convert, enabled = !vm.busy && target != null, modifier = Modifier.fillMaxWidth()) {
                    if (vm.busy) {
                        CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (target == Fmt.PDF && opts.combine && vm.sources.size > 1) "Combine" else "Convert")
                    }
                }
                if (vm.busy) Text(vm.progress, style = MaterialTheme.typography.bodySmall)
            }

            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (vm.results.isNotEmpty()) {
                Text("Saved to Downloads/DocConverter", style = MaterialTheme.typography.titleSmall)
                vm.results.forEach { out ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(out.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { open(ctx, out) }) { Text("Open") }
                        }
                    }
                }
                OutlinedButton(onClick = { share(ctx, vm.results) }, modifier = Modifier.fillMaxWidth()) { Text("Share") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun open(ctx: Context, out: Output) {
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(out.uri, out.mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { ctx.startActivity(i) }
}

private fun share(ctx: Context, outs: List<Output>) {
    val i = if (outs.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, outs[0].uri).setType(outs[0].mime)
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(outs.map { it.uri }))
            .setType("*/*")
    }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(i, "Share"))
}
