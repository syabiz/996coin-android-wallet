package com.coin996.wallet.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.bitcoinj.core.Coin
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Coin formatting ───────────────────────────────────────────────────────────

private val NNS_FORMAT = DecimalFormat("#,##0.########")
private val USD_FORMAT = DecimalFormat("$#,##0.00######")

fun Coin.toNnsString(): String = NNS_FORMAT.format(this.toBtc()) + " NNS"

fun Coin.toUsdString(priceUsd: Double): String {
    val usd = this.toBtc().toDouble() * priceUsd
    return if (usd < 0.01) "< $0.01" else USD_FORMAT.format(usd)
}

fun Double.formatPrice(): String = when {
    this < 0.000001  -> String.format("$%.8f", this)
    this < 0.01      -> String.format("$%.6f", this)
    this < 1.0       -> String.format("$%.4f", this)
    else             -> String.format("$%.2f", this)
}

fun Double.formatChange(): String {
    val sign = if (this >= 0) "+" else ""
    return "$sign${String.format("%.2f", this)}%"
}

// ─── Date formatting ──────────────────────────────────────────────────────────

private val DATE_FORMAT = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
private val DATE_SHORT  = SimpleDateFormat("dd MMM", Locale.getDefault())

fun Long.toDateString(): String = DATE_FORMAT.format(Date(this))
fun Long.toShortDate(): String = DATE_SHORT.format(Date(this))

// ─── QR Code generation ───────────────────────────────────────────────────────

fun generateQrCode(content: String, size: Int = 512): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size) {
        bmp.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
    }
    return bmp
}

/**
 * Create a 996coin BIP21 URI for QR display.
 * e.g.: 996coin:NAddressXXX?amount=10.5&label=Payment
 */
fun buildPaymentUri(address: String, amount: Double? = null, label: String? = null): String {
    val sb = StringBuilder("996coin:$address")
    val params = mutableListOf<String>()
    amount?.let { params.add("amount=$it") }
    label?.let { params.add("label=${java.net.URLEncoder.encode(it, "UTF-8")}") }
    if (params.isNotEmpty()) sb.append("?${params.joinToString("&")}")
    return sb.toString()
}

/**
 * Parse a 996coin payment URI.
 */
data class PaymentRequest(
    val address: String,
    val amount: Double? = null,
    val label: String? = null
)

fun parsePaymentUri(uri: String): PaymentRequest? {
    val cleaned = uri.removePrefix("996coin:").removePrefix("996COIN:")
    if (cleaned.isBlank()) return null
    val parts = cleaned.split("?")
    val address = parts[0]
    if (address.isBlank()) return null
    var amount: Double? = null
    var label: String? = null
    if (parts.size > 1) {
        parts[1].split("&").forEach { param ->
            val kv = param.split("=")
            when (kv.getOrNull(0)) {
                "amount" -> amount = kv.getOrNull(1)?.toDoubleOrNull()
                "label"  -> label = kv.getOrNull(1)?.let {
                    java.net.URLDecoder.decode(it, "UTF-8")
                }
            }
        }
    }
    return PaymentRequest(address, amount, label)
}

// ─── UI helpers ───────────────────────────────────────────────────────────────

fun View.visible() { visibility = View.VISIBLE }
fun View.gone()    { visibility = View.GONE }
fun View.invisible() { visibility = View.INVISIBLE }

fun Context.copyToClipboard(label: String, text: String) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

fun Context.showToast(message: String, long: Boolean = false) {
    Toast.makeText(this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

// ─── Address validation ───────────────────────────────────────────────────────

fun isValid996coinAddress(address: String): Boolean {
    return try {
        val params = com.coin996.wallet.core.network.Coin996NetworkParams.get()
        org.bitcoinj.core.Address.fromString(params, address)
        true
    } catch (e: Exception) { false }
}

// ─── Satoshi / NNS conversion ─────────────────────────────────────────────────

fun Long.satoshiToNns(): Double = this / 100_000_000.0
fun Double.nnsToSatoshi(): Long = (this * 100_000_000).toLong()
