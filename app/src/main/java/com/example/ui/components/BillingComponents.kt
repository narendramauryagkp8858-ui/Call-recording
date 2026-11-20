package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.billing.ImageStampHelper
import com.example.data.Invoice
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.WhatsAppGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (action != null) {
            action()
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    description: String,
    actionLabel: String? = null,
    testTag: String = "empty_state_action",
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ReceiptLong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .heightIn(min = 48.dp)
                        .testTag(testTag)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun PdfPreviewDialog(
    invoice: Invoice,
    pdfFile: File,
    onDismiss: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onPrintThermal: () -> Unit
) {
    var pages by remember(pdfFile.absolutePath) { mutableStateOf<List<Bitmap>>(emptyList()) }

    LaunchedEffect(pdfFile.absolutePath) {
        pages = withContext(Dispatchers.IO) {
            val rendered = mutableListOf<Bitmap>()
            try {
                val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                fd.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        for (i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use { page ->
                                val scale = 2
                                val bmp = Bitmap.createBitmap(
                                    page.width * scale,
                                    page.height * scale,
                                    Bitmap.Config.ARGB_8888
                                )
                                val c = AndroidCanvas(bmp)
                                c.drawColor(AndroidColor.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                rendered.add(bmp)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
            rendered
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "GST Bill ${invoice.invoiceNumber}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = invoice.customerName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_pdf_preview")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close preview",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(pages) { pageBitmap ->
                        Card(
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Image(
                                bitmap = pageBitmap.asImageBitmap(),
                                contentDescription = "GST invoice PDF page",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onPrintThermal,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("preview_print_thermal_button")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Thermal Print")
                    }
                    Button(
                        onClick = onShareWhatsApp,
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                        modifier = Modifier
                            .weight(1.2f)
                            .heightIn(min = 48.dp)
                            .testTag("preview_whatsapp_share_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("WhatsApp PDF")
                    }
                }
            }
        }
    }
}

@Composable
fun SignaturePadDialog(
    onDismiss: () -> Unit,
    onSaveBase64: (String) -> Unit
) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var activeStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize(600, 240)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Draw Shop Signature", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Sign inside the box below. It will be saved to your shop profile for GST bills.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { start -> activeStroke = listOf(start) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    activeStroke = activeStroke + change.position
                                },
                                onDragEnd = {
                                    if (activeStroke.size > 1) {
                                        strokes.add(activeStroke)
                                    }
                                    activeStroke = emptyList()
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val allStrokes = strokes + listOf(activeStroke)
                        allStrokes.forEach { pts ->
                            if (pts.size > 1) {
                                val path = Path().apply {
                                    moveTo(pts.first().x, pts.first().y)
                                    for (i in 1 until pts.size) {
                                        lineTo(pts[i].x, pts[i].y)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = Color(0xFF0F172A),
                                    style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            strokes.clear()
                            activeStroke = emptyList()
                        },
                        modifier = Modifier.testTag("clear_signature_pad")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Clear")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Button(
                            onClick = {
                                val w = canvasSize.width.coerceAtLeast(300)
                                val h = canvasSize.height.coerceAtLeast(150)
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                val c = AndroidCanvas(bmp)
                                c.drawColor(AndroidColor.TRANSPARENT)
                                val p = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                    color = AndroidColor.rgb(15, 23, 42)
                                    style = AndroidPaint.Style.STROKE
                                    strokeWidth = 6f
                                    strokeCap = AndroidPaint.Cap.ROUND
                                    strokeJoin = AndroidPaint.Join.ROUND
                                }
                                strokes.forEach { pts ->
                                    if (pts.size > 1) {
                                        val ap = AndroidPath().apply {
                                            moveTo(pts.first().x, pts.first().y)
                                            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                                        }
                                        c.drawPath(ap, p)
                                    }
                                }
                                onSaveBase64(ImageStampHelper.bitmapToCompactBase64(bmp, 320))
                            },
                            enabled = strokes.isNotEmpty(),
                            modifier = Modifier.testTag("save_signature_pad")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonoCodeText(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontWeight: FontWeight = FontWeight.Medium
) {
    Text(
        text = text,
        fontFamily = MonoFontFamily,
        fontWeight = fontWeight,
        style = MaterialTheme.typography.labelMedium,
        color = color
    )
}
