package com.mlh.skinanalyzer.share

import android.content.ClipData
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
            pdf?.let { attachPdf(context, this, it) }
            setPackage(null)
        }
        val chooser = Intent.createChooser(intent, "Enviar informe por email")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        grantChooserUri(context, chooser, pdf)
        context.startActivity(chooser)
    }

    fun shareWhatsAppBusiness(context: Context, body: String, pdf: File?, phoneE164: String?) {
        val packages = listOf("com.whatsapp.w4b", "com.whatsapp")
        var launched = false
        for (pkg in packages) {
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = if (pdf != null) "application/pdf" else "text/plain"
                    setPackage(pkg)
                    putExtra(Intent.EXTRA_TEXT, body)
                    pdf?.let { attachPdf(context, this, it) }
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                launched = true
                break
            } catch (_: Exception) {
            }
        }
        if (!launched) {
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

    private fun attachPdf(context: Context, intent: Intent, pdf: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdf)
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri("", uri)
    }

    private fun grantChooserUri(context: Context, chooser: Intent, pdf: File?) {
        if (pdf == null) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdf)
        val resInfoList = context.packageManager.queryIntentActivities(chooser, 0)
        for (resolveInfo in resInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            runCatching {
                context.grantUriPermission(
                    packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }
}
