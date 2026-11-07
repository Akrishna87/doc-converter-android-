package com.akrishna.docconverter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import android.os.Build
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

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
        if (intent.action == Intent.ACTION_SEND) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) vm.pick(uri)
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

private val PICK_TYPES = arrayOf("image/*", "application/pdf", "text/*", "application/octet-stream")

@Composable
private fun Screen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(uri)
    }

    Scaffold { pad ->
        Column(
            Modifier.padding(pad).padding(20.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Doc Converter", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Convert images, PDFs, text and Markdown on your device. Nothing is uploaded.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Button(onClick = { picker.launch(PICK_TYPES) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (vm.source == null) "Choose a file" else "Choose another file")
            }

            vm.source?.let {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(vm.name, style = MaterialTheme.typography.titleMedium)
                        Text("Format: ${vm.srcFmt?.label ?: "unknown"}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            vm.srcFmt?.let { src ->
                Text("Convert to", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    targetsFor(src).forEach { f ->
                        FilterChip(selected = vm.target == f, onClick = { vm.selectTarget(f) }, label = { Text(f.label) })
                    }
                }
                Button(
                    onClick = vm::convert,
                    enabled = !vm.busy && vm.target != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (vm.busy) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                    else Text("Convert")
                }
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
                OutlinedButton(onClick = { share(ctx, vm.results) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Share")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun open(ctx: android.content.Context, out: Output) {
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(out.uri, out.mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { ctx.startActivity(i) }
}

private fun share(ctx: android.content.Context, outs: List<Output>) {
    val i = if (outs.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, outs[0].uri).setType(outs[0].mime)
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(outs.map { it.uri }))
            .setType("*/*")
    }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(i, "Share"))
}
