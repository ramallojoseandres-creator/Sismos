package com.mlh.skinanalyzer.analysis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.mlh.skinanalyzer.data.Patient
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportGenerator {

    fun buildTextReport(
        patient: Patient,
        result: SkinAnalysisResult,
        moisture: Float?,
        sessionTime: Long = System.currentTimeMillis(),
    ): String {
        val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES"))
        val sb = StringBuilder()
        sb.appendLine("Dra María Laura Hernández Skin Analyzer Pro")
        sb.appendLine("Médico Cirujano · Informe de análisis de piel")
        sb.appendLine("────────────────────────────────────────")
        sb.appendLine("Paciente: ${patient.name}")
        sb.appendLine("Sexo: ${patient.gender} · Edad: ${patient.age}")
        if (patient.phone.isNotBlank()) sb.appendLine("Teléfono: ${patient.phone}")
        if (patient.email.isNotBlank()) sb.appendLine("Email: ${patient.email}")
        sb.appendLine("Fecha: ${df.format(Date(sessionTime))}")
        sb.appendLine()
        sb.appendLine("RESUMEN")
        sb.appendLine(result.overview)
        sb.appendLine("Tipo de piel: ${result.skinType}")
        sb.appendLine("Edad cutánea estimada: ${result.skinAge} años")
        moisture?.let { sb.appendLine("Humedad medida: ${"%.1f".format(it)}%") }
        sb.appendLine()
        sb.appendLine("INDICADORES (nivel 1–5; mayor = más atención)")
        result.metrics.forEach { m ->
            sb.appendLine(
                "• ${m.name} [${m.layer}] — Nivel ${m.level.value} (${m.level.label}) · score ${m.score.round1()}"
            )
            sb.appendLine("  Base: ${m.description}")
            sb.appendLine("  Causas: ${m.causes}")
            sb.appendLine("  Precauciones: ${m.precautions}")
            sb.appendLine("  Recomendación: ${m.recommendation}")
            sb.appendLine()
        }
        sb.appendLine(result.facialRatioNote)
        sb.appendLine()
        sb.appendLine("Este informe es orientativo para consulta estética y no sustituye diagnóstico médico.")
        sb.appendLine("— Dra. María Laura Hernández · Skin Analyzer Pro")
        return sb.toString()
    }

    fun writePdf(
        context: Context,
        patient: Patient,
        result: SkinAnalysisResult,
        moisture: Float?,
        sessionTime: Long = System.currentTimeMillis(),
    ): File {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 20, 20)
            textSize = 16f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 30, 30)
            textSize = 10f
        }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 90, 90)
            textSize = 9f
        }
        var y = 48f
        fun newPageIfNeeded(needed: Float = 48f) {
            if (y + needed > pageHeight - 40) {
                doc.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = doc.startPage(pageInfo)
                canvas = page.canvas
                y = 48f
            }
        }
        fun drawWrapped(text: String, paint: Paint, x: Float = 40f, maxWidth: Float = pageWidth - 80f) {
            val words = text.split(" ")
            var line = ""
            for (w in words) {
                val trial = if (line.isEmpty()) w else "$line $w"
                if (paint.measureText(trial) > maxWidth) {
                    newPageIfNeeded()
                    canvas.drawText(line, x, y, paint)
                    y += paint.textSize + 4f
                    line = w
                } else line = trial
            }
            if (line.isNotEmpty()) {
                newPageIfNeeded()
                canvas.drawText(line, x, y, paint)
                y += paint.textSize + 4f
            }
        }

        canvas.drawText("Dra María Laura Hernández", 40f, y, titlePaint)
        y += 22f
        canvas.drawText("Skin Analyzer Pro — Informe de piel", 40f, y, body)
        y += 18f
        canvas.drawLine(40f, y, pageWidth - 40f, y, body)
        y += 20f
        drawWrapped("Paciente: ${patient.name} · ${patient.gender} · ${patient.age} años", body)
        drawWrapped(
            "Fecha: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES")).format(Date(sessionTime))}",
            muted,
        )
        y += 8f
        drawWrapped(result.overview, body)
        y += 6f
        drawWrapped("Tipo: ${result.skinType} · Edad cutánea: ${result.skinAge}", body)
        moisture?.let { drawWrapped("Humedad: ${"%.1f".format(it)}%", body) }
        y += 10f
        result.metrics.forEach { m ->
            newPageIfNeeded(70f)
            drawWrapped("${m.name} — Nivel ${m.level.value} (${m.level.label})", titlePaint.apply {
                textSize = 12f
            })
            titlePaint.textSize = 16f
            drawWrapped(m.description, muted)
            drawWrapped("Recomendación: ${m.recommendation}", body)
            y += 8f
        }
        y += 10f
        drawWrapped("Informe orientativo. No sustituye diagnóstico médico.", muted)
        doc.finishPage(page)

        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "MLH_informe_${patient.name.replace(" ", "_")}_$sessionTime.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun Float.round1(): String = "%.1f".format(this)
}
