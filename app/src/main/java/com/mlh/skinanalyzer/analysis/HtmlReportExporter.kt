package com.mlh.skinanalyzer.analysis

import android.content.Context
import com.mlh.skinanalyzer.data.AnalysisSession
import com.mlh.skinanalyzer.data.ClinicProfile
import com.mlh.skinanalyzer.data.Patient
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Informe editable en HTML (alternativa al PDF para que la médico añada observaciones).
 * El PDF sigue usándose para entrega; este archivo se puede abrir en PC.
 */
object HtmlReportExporter {

    fun writeHtml(
        context: Context,
        patient: Patient,
        session: AnalysisSession,
        result: SkinAnalysisResult,
        clinic: ClinicProfile,
    ): File {
        val chronological = session.ageAtAnalysis.takeIf { it > 0 } ?: patient.ageAt(session.createdAt)
        val delta = result.skinAge - chronological
        val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES"))
        val watermark = if (!result.isClinicalLicensed) {
            """<div class="wm">SIMULACIÓN — NO CLÍNICO</div>"""
        } else ""
        val priorities = result.metrics.sortedByDescending { it.score }.take(3).joinToString("") { m ->
            "<li><strong>${esc(m.name)}</strong> — nivel ${m.level.value} (${esc(m.level.label)}): ${esc(m.recommendation)}</li>"
        }
        val surface = result.metrics.filter { it.layer.contains("super", true) || it.layer == "surface" }
            .joinToString("") { metricBlock(it) }
        val deep = result.metrics.filter { !it.layer.contains("super", true) && it.layer != "surface" }
            .joinToString("") { metricBlock(it) }
        val html = """
            <!DOCTYPE html>
            <html lang="es"><head><meta charset="utf-8"/>
            <title>Informe — ${esc(patient.fullName)}</title>
            <style>
              body{font-family:Georgia,serif;max-width:800px;margin:40px auto;padding:0 24px;color:#1a1a1a;line-height:1.45}
              h1{font-size:22pt;margin-bottom:4px} h2{font-size:14pt;margin-top:28px;border-bottom:1px solid #ccc;padding-bottom:4px}
              .muted{color:#666;font-size:10pt} .hero{font-size:28pt;color:#8b3a3a;margin:12px 0 4px}
              .delta{font-size:12pt;margin-bottom:16px} .wm{position:fixed;top:40%;left:10%;font-size:36pt;color:rgba(180,40,40,.18);
                transform:rotate(-28deg);pointer-events:none;z-index:0;font-weight:bold}
              .metric{margin:14px 0;padding:10px 0;border-bottom:1px solid #eee}
              .footer{margin-top:36px;font-size:9pt;color:#555;border-top:1px solid #ccc;padding-top:12px}
              @media print{body{margin:16mm}}
            </style></head><body>
            $watermark
            <h1>${esc(clinic.doctorName)}</h1>
            <div class="muted">${esc(clinic.clinicName)} · ${esc(clinic.specialty)}</div>
            <div class="muted">Motor: ${esc(result.analysisEngine)}</div>
            <h2>Paciente</h2>
            <p><strong>${esc(patient.fullName)}</strong> · ${esc(patient.sexLabel)} · ${chronological} años al análisis<br/>
            ${df.format(Date(session.createdAt))}</p>
            <h2>Resumen</h2>
            <div class="hero">Edad cutánea: ${result.skinAge} años</div>
            <div class="delta">Edad real: $chronological · ${
            when {
                delta > 0 -> "aparenta +$delta años (estimación cosmética comparativa)"
                delta < 0 -> "aparenta ${-delta} años menos (estimación cosmética comparativa)"
                else -> "alineada con la edad cronológica"
            }
        }</div>
            <p>Tipo de piel: <strong>${esc(result.skinType)}</strong></p>
            <p>${esc(result.overview)}</p>
            <h3>Hallazgos prioritarios</h3>
            <ol>$priorities</ol>
            <h2>Superficie</h2>
            $surface
            <h2>Profundo</h2>
            $deep
            <h2>Recomendaciones</h2>
            <p>${esc(session.editableRecommendations.ifBlank { session.recommendations })}</p>
            <div class="footer">
              Análisis cosmético de piel. No constituye diagnóstico médico. Cualquier lesión sospechosa
              requiere evaluación dermatológica presencial.<br/>
              Motor: ${esc(result.analysisEngine)} · ${esc(clinic.footerNote)}
            </div>
            </body></html>
        """.trimIndent()

        val dir = File(context.filesDir, "reports").apply { mkdirs() }
        val file = File(
            dir,
            "MLH_informe_${patient.lastName}_${patient.firstName}_${session.createdAt}.html"
                .replace(" ", "_"),
        )
        file.writeText(html, Charsets.UTF_8)
        return file
    }

    private fun metricBlock(m: SkinMetric): String =
        """
        <div class="metric">
          <strong>${esc(m.name)}</strong> — nivel ${m.level.value} (${esc(m.level.label)}) · score ${"%.1f".format(m.score)}
          <div class="muted">${esc(m.description)}</div>
          <div>${esc(m.recommendation)}</div>
        </div>
        """.trimIndent()

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
