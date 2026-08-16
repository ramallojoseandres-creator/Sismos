package com.mlh.skinanalyzer.analysis

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.mlh.skinanalyzer.analysis.oem.OemCaptureFiles
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
        sb.appendLine("Skin Analyzer Pro · 100% offline")
        sb.appendLine("Motor: ${result.analysisEngine}")
        if (!result.isClinicalLicensed) {
            sb.appendLine("⚠ INFORME NO CLÍNICO — cifras de simulación / no medidas por SkinDetect")
        }
        sb.appendLine("────────────────────────────────────────")
        sb.appendLine("Paciente: ${patient.fullName}")
        sb.appendLine("Sexo: ${patient.sexLabel} · Edad: ${patient.currentAge()}")
        if (patient.phoneRaw.isNotBlank()) sb.appendLine("Teléfono: ${patient.phoneRaw}")
        if (patient.email.isNotBlank()) sb.appendLine("Email: ${patient.email}")
        if (patient.address.isNotBlank()) sb.appendLine("Dirección: ${patient.address}")
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
        sb.appendLine("INDICADORES (Mínimo→Urgente; mayor atención = más prioridad)")
        result.metrics.forEach { m ->
            sb.appendLine(
                "• ${m.name} [${m.layer}] — ${m.level.label} · score ${m.score.round1()}" +
                    if (m.spectrumLabel.isNotBlank()) " · ${m.spectrumLabel}" else "",
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
        sessionDir: String? = null,
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
                drawDemoWatermark(canvas, pageWidth, pageHeight, result.isClinicalLicensed)
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
        drawWrapped("Motor: ${result.analysisEngine}", muted)
        drawDemoWatermark(canvas, pageWidth, pageHeight, result.isClinicalLicensed)
        if (!result.isClinicalLicensed) {
            drawWrapped(
                "ADVERTENCIA: este PDF no proviene del motor Gushang licenciado. No usar como informe clínico.",
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(160, 40, 40)
                    textSize = 10f
                },
            )
            y += 4f
        }
        y += 6f
        canvas.drawLine(40f, y, pageWidth - 40f, y, body)
        y += 20f
        drawWrapped("Paciente: ${patient.fullName} · ${patient.sexLabel} · ${patient.currentAge()} años", body)
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
            drawWrapped(
                "${m.name} — ${m.level.label} (${m.score.round1()})",
                titlePaint.apply { textSize = 12f },
            )
            titlePaint.textSize = 16f
            if (m.spectrumLabel.isNotBlank()) {
                drawWrapped("Espectro: ${m.spectrumLabel}", muted)
            }
            drawWrapped(m.description, muted)
            drawWrapped("Recomendación: ${m.recommendation}", body)
            y += 8f
        }

        if (!sessionDir.isNullOrBlank()) {
            val findings = listOf(
                Triple("acne", "Acné · Luz azul", OemCaptureFiles.BLUE),
                Triple("uv_spots", "Manchas UV · Ultravioleta", OemCaptureFiles.UV),
                Triple("blackheads", "Puntos negros · Polarizada paralela", OemCaptureFiles.POSITIVE),
            )
            var anyPhoto = false
            findings.forEach { (key, caption, fileName) ->
                val baseFile = File(sessionDir, fileName)
                val overlayFile = File(sessionDir, "gushang/${key}_out.jpg")
                val src = when {
                    overlayFile.exists() && overlayFile.length() > 0 -> overlayFile
                    baseFile.exists() && baseFile.length() > 0 -> baseFile
                    else -> null
                } ?: return@forEach
                if (!anyPhoto) {
                    y += 6f
                    drawWrapped("Fotos de hallazgos", titlePaint.apply { textSize = 12f })
                    titlePaint.textSize = 16f
                    anyPhoto = true
                }
                val bmp = runCatching {
                    BitmapFactory.decodeFile(
                        src.absolutePath,
                        BitmapFactory.Options().apply { inSampleSize = 2 },
                    )
                }.getOrNull() ?: return@forEach
                try {
                    val maxW = (pageWidth - 80).toFloat()
                    val aspect = 1040f / 1350f
                    val drawW = maxW
                    val drawH = drawW / aspect
                    newPageIfNeeded(drawH + 36f)
                    drawWrapped(caption, body)
                    val dst = RectF(40f, y, 40f + drawW, y + drawH)
                    canvas.drawBitmap(bmp, null, dst, null)
                    y += drawH + 12f
                } finally {
                    bmp.recycle()
                }
            }
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

        val dir = File(context.filesDir, "reports").apply { mkdirs() }
        val file = File(dir, "MLH_informe_${patient.lastName}_${patient.firstName}_$sessionTime.pdf".replace(" ", "_"))
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun drawDemoWatermark(
        canvas: Canvas,
        pageWidth: Int,
        pageHeight: Int,
        isClinicalLicensed: Boolean,
    ) {
        if (isClinicalLicensed) return
        val stamp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 180, 40, 40)
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.save()
        canvas.rotate(-28f, pageWidth / 2f, pageHeight / 2f)
        canvas.drawText("SIMULACIÓN — NO CLÍNICO", 60f, pageHeight / 2f, stamp)
        canvas.restore()
    }

    private fun Float.round1(): String = "%.1f".format(this)
}
