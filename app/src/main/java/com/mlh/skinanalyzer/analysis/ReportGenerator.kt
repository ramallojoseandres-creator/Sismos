package com.mlh.skinanalyzer.analysis

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.util.Log
import com.mlh.skinanalyzer.data.CareGuide
import com.mlh.skinanalyzer.data.ClinicProfile
import com.mlh.skinanalyzer.data.Patient
import com.mlh.skinanalyzer.data.ProductRec
import com.mlh.skinanalyzer.wifi.EditableRecommendations
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
        editableRecommendations: String = "",
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
        val doctorNotes = EditableRecommendations.parse(editableRecommendations)
        if (!doctorNotes.isBlank()) {
            sb.appendLine("TRATAMIENTO INDICADO POR LA MÉDICO")
            doctorNotes.displayText().lines().forEach { sb.appendLine(it) }
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
        editableRecommendations: String = "",
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
            // 6 fotos con overlay, 2 filas × 3 columnas, en una sola página.
            val fotoKeys = listOf("acne", "uv_spots", "blackheads", "pigmentation", "deep_pigment", "sensitivity")
            data class FotoLista(val bmp: android.graphics.Bitmap, val caption: String)
            val fotos = fotoKeys.mapNotNull { key ->
                val m = result.metrics.firstOrNull { it.key == key } ?: return@mapNotNull null
                val baseFile = File(sessionDir, m.spectrumFile)
                val overlayFile = File(sessionDir, "gushang/${key}_out.jpg")
                val src = when {
                    overlayFile.exists() && overlayFile.length() > 0 -> overlayFile
                    baseFile.exists() && baseFile.length() > 0 -> baseFile
                    else -> return@mapNotNull null
                }
                val bmp = runCatching {
                    BitmapFactory.decodeFile(
                        src.absolutePath,
                        BitmapFactory.Options().apply { inSampleSize = 2 },
                    )
                }.getOrNull() ?: return@mapNotNull null
                FotoLista(bmp, "${m.name} · ${m.spectrumLabel}")
            }
            if (fotos.isNotEmpty()) {
                try {
                    doc.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = doc.startPage(pageInfo)
                    canvas = page.canvas
                    y = 48f
                    drawDemoWatermark(canvas, pageWidth, pageHeight, result.isClinicalLicensed)
                    drawWrapped("Fotos de hallazgos", titlePaint.apply { textSize = 12f })
                    titlePaint.textSize = 16f
                    y += 4f

                    val cols = 3
                    val filas = 2
                    val margen = 40f
                    val gap = 14f
                    val aspecto = 1040f / 1350f
                    val anchoCelda = (pageWidth - margen * 2 - gap * (cols - 1)) / cols
                    val altoFoto = anchoCelda / aspecto
                    val altoCelda = altoFoto + 22f // espacio para el pie de foto

                    val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(70, 70, 70)
                        textSize = 8f
                        textAlign = Paint.Align.CENTER
                    }

                    fotos.forEachIndexed { i, foto ->
                        val fila = i / cols
                        val col = i % cols
                        val x = margen + col * (anchoCelda + gap)
                        val cellY = y + fila * (altoCelda + gap)
                        try {
                            val dst = RectF(x, cellY, x + anchoCelda, cellY + altoFoto)
                            canvas.drawBitmap(foto.bmp, null, dst, null)
                            canvas.drawText(
                                foto.caption,
                                x + anchoCelda / 2f,
                                cellY + altoFoto + 12f,
                                captionPaint,
                            )
                        } finally {
                            foto.bmp.recycle()
                        }
                    }
                    y += filas * (altoCelda + gap) + 10f
                } catch (e: Exception) {
                    Log.e("ReportGenerator", "grid de fotos falló", e)
                }
            }
        }

        // Récipe / tratamiento — lo completa la médico (PC o tablet).
        newPageIfNeeded(260f)
        y += 10f
        drawWrapped("Récipe / Tratamiento indicado", titlePaint.apply { textSize = 14f })
        titlePaint.textSize = 16f
        y += 4f
        drawWrapped(
            "Paciente: ${patient.fullName}     Fecha: ${
                SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES")).format(Date(sessionTime))
            }",
            muted,
        )
        y += 10f
        val doctorNotes = EditableRecommendations.parse(editableRecommendations)
        if (!doctorNotes.isBlank()) {
            if (doctorNotes.medicamentos.isNotBlank()) {
                drawWrapped("Medicamentos / tratamiento farmacológico", titlePaint.apply { textSize = 11f })
                titlePaint.textSize = 16f
                doctorNotes.medicamentos.lines().forEach { line ->
                    if (line.isNotBlank()) drawWrapped("• $line", body)
                }
                y += 6f
            }
            if (doctorNotes.rutinas.isNotBlank()) {
                drawWrapped("Rutinas y cuidados", titlePaint.apply { textSize = 11f })
                titlePaint.textSize = 16f
                doctorNotes.rutinas.lines().forEach { line ->
                    if (line.isNotBlank()) drawWrapped("• $line", body)
                }
                y += 6f
            }
            if (doctorNotes.observaciones.isNotBlank()) {
                drawWrapped("Observaciones", titlePaint.apply { textSize = 11f })
                titlePaint.textSize = 16f
                doctorNotes.observaciones.lines().forEach { line ->
                    if (line.isNotBlank()) drawWrapped(line, body)
                }
            }
        } else {
            val lineaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(190, 190, 190)
                strokeWidth = 1f
            }
            repeat(10) {
                y += 26f
                canvas.drawLine(40f, y, pageWidth - 40f, y, lineaPaint)
            }
        }
        y += 36f
        val lineaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(190, 190, 190)
            strokeWidth = 1f
        }
        canvas.drawLine(40f, y, 40f + 220f, y, lineaPaint)
        canvas.drawText(clinic.doctorName, 40f, y + 14f, muted)
        if (clinic.specialty.isNotBlank()) canvas.drawText(clinic.specialty, 40f, y + 27f, muted)
        canvas.drawText("Firma y sello", pageWidth - 40f - 100f, y + 14f, muted)

        y += 45f
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
