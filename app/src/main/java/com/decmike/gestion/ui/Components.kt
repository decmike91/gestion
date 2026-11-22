package com.decmike.gestion.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.decmike.gestion.audio.Audiogram
import com.decmike.gestion.audio.EqEngine
import com.decmike.gestion.data.FileCategory
import com.decmike.gestion.data.FileItem
import com.decmike.gestion.ui.theme.LeftEarColor
import com.decmike.gestion.ui.theme.RightEarColor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes o"
    val units = listOf("Ko", "Mo", "Go", "To")
    var v = bytes / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return String.format(Locale.FRANCE, if (v < 10) "%.1f %s" else "%.0f %s", v, units[i])
}

private val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
private val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.FRANCE)
fun formatDate(ms: Long): String = if (ms <= 0) "—" else dateFmt.format(Date(ms))
fun formatMonth(ms: Long): String = if (ms <= 0) "Date inconnue" else monthFmt.format(Date(ms)).replaceFirstChar { it.uppercase() }
fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

fun FileCategory.icon(): ImageVector = when (this) {
    FileCategory.AUDIO -> Icons.Filled.AudioFile
    FileCategory.VIDEO -> Icons.Filled.VideoFile
    FileCategory.IMAGE -> Icons.Filled.Image
    FileCategory.PDF -> Icons.Filled.PictureAsPdf
    FileCategory.DOCUMENT -> Icons.Filled.Description
    FileCategory.ARCHIVE -> Icons.Filled.Archive
    FileCategory.APK -> Icons.Filled.Android
    FileCategory.OTHER -> Icons.Filled.FilePresent
}

fun FileCategory.color(): Color = when (this) {
    FileCategory.AUDIO -> Color(0xFF8E44AD)
    FileCategory.VIDEO -> Color(0xFFE74C3C)
    FileCategory.IMAGE -> Color(0xFF27AE60)
    FileCategory.PDF -> Color(0xFFC0392B)
    FileCategory.DOCUMENT -> Color(0xFF2980B9)
    FileCategory.ARCHIVE -> Color(0xFF7F8C8D)
    FileCategory.APK -> Color(0xFF16A085)
    FileCategory.OTHER -> Color(0xFF95A5A6)
}

/** URI partageable avec d'autres applications. */
fun shareableUri(context: Context, item: FileItem): Uri =
    if (item.path != null) FileProvider.getUriForFile(context, context.packageName + ".fileprovider", File(item.path))
    else Uri.parse(item.uri)

fun openWith(context: Context, item: FileItem) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(shareableUri(context, item), item.mime.ifBlank { "*/*" })
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(Intent.createChooser(intent, "Ouvrir avec").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Aucune application pour ce fichier", Toast.LENGTH_SHORT).show()
    }
}

fun share(context: Context, items: List<FileItem>) {
    if (items.isEmpty()) return
    val uris = ArrayList(items.map { shareableUri(context, it) })
    val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    intent.type = if (items.size == 1) items[0].mime.ifBlank { "*/*" } else "*/*"
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Partager").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

/** Courbe d'égalisation gauche / droite. */
@Composable
fun EqCurve(left: FloatArray, right: FloatArray, modifier: Modifier = Modifier, range: Float = 15f) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val zero = MaterialTheme.colorScheme.outline
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val n = left.size
            fun x(i: Int) = size.width * i / (n - 1).toFloat()
            fun y(db: Float) = size.height / 2 - (db / range) * (size.height / 2)
            for (db in listOf(-10f, -5f, 5f, 10f)) {
                drawLine(grid, Offset(0f, y(db)), Offset(size.width, y(db)), 1f)
            }
            drawLine(zero, Offset(0f, y(0f)), Offset(size.width, y(0f)), 2f)
            fun curve(values: FloatArray, color: Color, dashed: Boolean) {
                val p = Path()
                values.forEachIndexed { i, v -> if (i == 0) p.moveTo(x(i), y(v)) else p.lineTo(x(i), y(v)) }
                drawPath(
                    p, color,
                    style = Stroke(width = 5f, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(16f, 10f)) else null)
                )
                values.forEachIndexed { i, v -> drawCircle(color, 6f, Offset(x(i), y(v))) }
            }
            curve(right, RightEarColor, false)
            curve(left, LeftEarColor, true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            EqEngine.BAND_LABELS.forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Legend(RightEarColor, "Droite")
            Spacer(Modifier.width(16.dp))
            Legend(LeftEarColor, "Gauche")
            Spacer(Modifier.width(16.dp))
            Text("±${range.toInt()} dB", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(12.dp)) { drawCircle(color) }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Audiogramme classique : 0 dB en haut, perte vers le bas ; O = droite, X = gauche. */
@Composable
fun AudiogramChart(a: Audiogram, modifier: Modifier = Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val zones = listOf(
        20 to Color(0x2227AE60), 40 to Color(0x22F1C40F), 70 to Color(0x22E67E22), 90 to Color(0x22E74C3C), 120 to Color(0x228E44AD)
    )
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(200.dp)) {
            val n = Audiogram.FREQS.size
            val pad = 24f
            fun x(i: Int) = pad + (size.width - 2 * pad) * i / (n - 1).toFloat()
            fun y(db: Int) = (db + 10) / 130f * size.height
            var prev = -10
            for ((lim, c) in zones) {
                drawRect(c, Offset(0f, y(prev)), androidx.compose.ui.geometry.Size(size.width, y(lim) - y(prev)))
                prev = lim
            }
            for (db in -10..120 step 10) drawLine(grid, Offset(0f, y(db)), Offset(size.width, y(db)), 1f)
            for (i in 0 until n) drawLine(grid, Offset(x(i), 0f), Offset(x(i), size.height), 1f)
            fun line(values: List<Int>, color: Color, cross: Boolean) {
                val p = Path()
                values.forEachIndexed { i, v -> if (i == 0) p.moveTo(x(i), y(v)) else p.lineTo(x(i), y(v)) }
                drawPath(p, color, style = Stroke(4f))
                values.forEachIndexed { i, v ->
                    val c = Offset(x(i), y(v))
                    if (cross) {
                        drawLine(color, c + Offset(-10f, -10f), c + Offset(10f, 10f), 5f)
                        drawLine(color, c + Offset(-10f, 10f), c + Offset(10f, -10f), 5f)
                    } else drawCircle(color, 11f, c, style = Stroke(5f))
                }
            }
            line(a.right, RightEarColor, false)
            line(a.left, LeftEarColor, true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Audiogram.FREQS.forEach { Text(if (it >= 1000) "${it / 1000}k" else "$it", style = MaterialTheme.typography.labelSmall) }
        }
        Text(
            "O droite · X gauche — vert : normal, jaune : léger, orange : moyen, rouge : sévère",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

fun hearingLevelLabel(a: Audiogram): String {
    fun avg(v: List<Int>) = (v[1] + v[2] + v[3] + v[4]) / 4 // moyenne 500–4000 Hz
    fun label(x: Int) = when {
        x <= 20 -> "normale"
        x <= 40 -> "perte légère"
        x <= 70 -> "perte moyenne"
        x <= 90 -> "perte sévère"
        else -> "perte profonde"
    }
    return "Droite : ${label(avg(a.right))} (${avg(a.right)} dB) · Gauche : ${label(avg(a.left))} (${avg(a.left)} dB)"
}
