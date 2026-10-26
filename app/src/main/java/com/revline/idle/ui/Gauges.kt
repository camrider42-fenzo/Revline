package com.revline.idle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revline.idle.model.OperatingPoint
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun Tachometer(rpm: Double, redline: Double, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = (size.minDimension / 90f).coerceIn(1.6f, 3f)
        val c = Offset(size.width / 2f, size.height * 0.62f)
        val r = min(size.width, size.height) * 0.42f
        val start = 150.0
        val sweep = 240.0
        val scale = (kotlin.math.ceil(redline / 1000.0) * 1000.0).coerceAtLeast(redline + 200.0)
        drawArc(
            color = White,
            startAngle = start.toFloat(),
            sweepAngle = sweep.toFloat(),
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        val redFrac = ((redline / scale).toFloat()).coerceIn(0f, 1f)
        drawArc(
            color = Red,
            startAngle = (start + sweep * redFrac).toFloat(),
            sweepAngle = (sweep * (1.0 - redFrac)).toFloat(),
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = Stroke(stroke * 1.6f, cap = StrokeCap.Round),
        )
        val ticks = (scale / 1000.0).toInt()
        for (i in 0..ticks) {
            val f = i / ticks.toFloat()
            val a = Math.toRadians(start + sweep * f)
            val inner = r * 0.82f
            drawLine(
                if (i * 1000.0 >= redline) Red else White,
                Offset(c.x + (cos(a) * inner).toFloat(), c.y + (sin(a) * inner).toFloat()),
                Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat()),
                stroke,
            )
        }
        val frac = (rpm / scale).coerceIn(0.0, 1.0)
        val na = Math.toRadians(start + sweep * frac)
        drawLine(
            Lime,
            c,
            Offset(c.x + (cos(na) * r * 0.78f).toFloat(), c.y + (sin(na) * r * 0.78f).toFloat()),
            stroke * 1.3f,
            StrokeCap.Round,
        )
        drawCircle(Lime, stroke * 1.8f, c)
    }
}

@Composable
fun DynoChart(curve: List<OperatingPoint>, rpm: Double, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("HP", color = Lime, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Text("TORQUE", color = Amber, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .then(Modifier.fillMaxWidth())
                .height(150.dp),
        ) {
            if (curve.size < 2) return@Canvas
            val stroke = (size.minDimension / 140f).coerceIn(1.4f, 2.6f)
            val left = 8f
            val right = size.width - 8f
            val top = 8f
            val bottom = size.height - 8f
            drawLine(White, Offset(left, top), Offset(left, bottom), stroke)
            drawLine(White, Offset(left, bottom), Offset(right, bottom), stroke)
            val minRpm = curve.first().rpm
            val maxRpm = curve.last().rpm
            val maxHp = curve.maxOf { it.horsepower }.coerceAtLeast(1.0)
            val maxTq = curve.maxOf { it.torqueLbFt }.coerceAtLeast(1.0)
            fun x(r: Double) = left + ((r - minRpm) / (maxRpm - minRpm)).toFloat() * (right - left)
            fun yHp(v: Double) = bottom - (v / maxHp).toFloat() * (bottom - top)
            fun yTq(v: Double) = bottom - (v / maxTq).toFloat() * (bottom - top)
            for (i in 1 until curve.size) {
                val a = curve[i - 1]
                val b = curve[i]
                drawLine(Lime, Offset(x(a.rpm), yHp(a.horsepower)), Offset(x(b.rpm), yHp(b.horsepower)), stroke)
                drawLine(Amber, Offset(x(a.rpm), yTq(a.torqueLbFt)), Offset(x(b.rpm), yTq(b.torqueLbFt)), stroke)
            }
            val cx = x(rpm.coerceIn(minRpm, maxRpm))
            drawLine(Cyan, Offset(cx, top), Offset(cx, bottom), stroke)
        }
    }
}

fun signed(value: Double, digits: Int = 1): String {
    val body = String.format(Locale.US, "%.${digits}f", kotlin.math.abs(value))
    return when {
        value > 0.05 -> "+$body"
        value < -0.05 -> "-$body"
        else -> "0"
    }
}
