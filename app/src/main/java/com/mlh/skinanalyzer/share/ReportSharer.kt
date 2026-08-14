package com.mlh.skinanalyzer.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ReportSharer {

    fun shareEmail(context: Context, subject: String, body: String, pdf: File?, toEmail: String?) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (pdf != null) "application/pdf" else "text/plain"
            if (!toEmail.isNullOrBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(toEmail))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            pdf?.let {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // Prefer Gmail / any email client
            setPackage(null)
        }
        val chooser = Intent.createChooser(intent, "Enviar informe por email")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun shareWhatsAppBusiness(context: Context, body: String, pdf: File?, phoneE164: String?) {
        val uriPdf: Uri? = pdf?.let {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
        }
        val packages = listOf("com.whatsapp.w4b", "com.whatsapp")
        var launched = false
        for (pkg in packages) {
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = if (uriPdf != null) "application/pdf" else "text/plain"
                    setPackage(pkg)
                    putExtra(Intent.EXTRA_TEXT, body)
                    uriPdf?.let {
                        putExtra(Intent.EXTRA_STREAM, it)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    // WhatsApp Business deep link with phone when available
                    if (!phoneE164.isNullOrBlank() && uriPdf == null) {
                        // ACTION_SEND with package is enough; jid via smsto alternative below
                    }
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                launched = true
                break
            } catch (_: Exception) {
            }
        }
        if (!launched) {
            // Fallback: wa.me URL (text only)
            val phone = phoneE164?.filter { it.isDigit() }.orEmpty()
            val url = if (phone.isNotEmpty()) {
                "https://wa.me/$phone?text=${Uri.encode(body.take(1000))}"
            } else {
                "https://wa.me/?text=${Uri.encode(body.take(1000))}"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
