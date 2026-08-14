package com.mlh.skinanalyzer.analysis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.mlh.skinanalyzer.data.CareGuide
import com.mlh.skinanalyzer.data.ClinicProfile
import com.mlh.skinanalyzer.data.Patient
import com.mlh.skinanalyzer.data.ProductRec
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
        clinic: ClinicProfile = ClinicProfile(),
        guides: List<CareGuide> = emptyList(),
        products: List<ProductRec> = emptyList(),
    ): String {
        val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES"))
        val sb = StringBuilder()
        sb.appendLine(clinic.doctorName)
        sb.appendLine(clinic.clinicName)
        if (clinic.specialty.isNotBlank()) sb.appendLine(clinic.specialty)
        sb.appendLine("Skin Analyzer Pro · 100% offline (sin servidor chino)")
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
        sb.appendLine("PROPORCIONES FACIALES (3 tercios / 5 ojos)")
        sb.appendLine(result.facialRatioNote)
        result.facial?.let { sb.appendLine(it.summary) }
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
        if (guides.isNotEmpty()) {
            sb.appendLine("GUÍAS DE CUIDADO (catálogo local)")
            guides.forEach { g ->
                sb.appendLine("• ${g.title}: ${g.body}")
            }
            sb.appendLine()
        }
        if (products.isNotEmpty()) {
            sb.appendLine("PRODUCTOS SUGERIDOS (catálogo local)")
            products.forEach { p ->
                sb.appendLine("• ${p.name} [${p.category}] — ${p.description}")
                if (p.howToUse.isNotBlank()) sb.appendLine("  Uso: ${p.howToUse}")
            }
            sb.appendLine()
        }
        sb.appendLine(clinic.footerNote)
        if (clinic.phone.isNotBlank() || clinic.whatsapp.isNotBlank() || clinic.email.isNotBlank()) {
            sb.appendLine(
                listOfNotNull(
                    clinic.phone.takeIf { it.isNotBlank() }?.let { "Tel $it" },
                    clinic.whatsapp.takeIf { it.isNotBlank() }?.let { "WhatsApp $it" },
                    clinic.email.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
            )
        }
        if (clinic.address.isNotBlank()) sb.appendLine(clinic.address)
        sb.appendLine("— ${clinic.doctorName} · Skin Analyzer Pro")
        return sb.toString()
    }

    fun writePdf(
        context: Context,
        patient: Patient,
        result: SkinAnalysisResult,
        moisture: Float?,
        sessionTime: Long = System.currentTimeMillis(),
        clinic: ClinicProfile = ClinicProfile(),
        guides: List<CareGuide> = emptyList(),
        products: List<ProductRec> = emptyList(),
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

        canvas.drawText(clinic.doctorName, 40f, y, titlePaint)
        y += 20f
        drawWrapped(clinic.clinicName, body)
        drawWrapped("Skin Analyzer Pro — Informe offline", muted)
        y += 6f
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
        y += 8f
        drawWrapped("Proporciones faciales", titlePaint.apply { textSize = 12f })
        titlePaint.textSize = 16f
        drawWrapped(result.facialRatioNote, muted)
        result.facial?.let { drawWrapped(it.summary, body) }
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
        if (guides.isNotEmpty()) {
            y += 6f
            drawWrapped("Guías de cuidado (local)", titlePaint.apply { textSize = 12f })
            titlePaint.textSize = 16f
            guides.forEach { g ->
                drawWrapped("${g.title}: ${g.body}", muted)
            }
        }
        if (products.isNotEmpty()) {
            y += 6f
            drawWrapped("Productos sugeridos (local)", titlePaint.apply { textSize = 12f })
            titlePaint.textSize = 16f
            products.forEach { p ->
                drawWrapped("${p.name} — ${p.description}", body)
            }
        }
        y += 10f
        drawWrapped(clinic.footerNote, muted)
        doc.finishPage(page)

        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "MLH_informe_${patient.name.replace(" ", "_")}_$sessionTime.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun Float.round1(): String = "%.1f".format(this)
}
