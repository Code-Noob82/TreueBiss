package com.dominikbaki.treuebiss.feature_vouchers.presentation.composables

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import com.dominikbaki.treuebiss.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Zeigt [data] als scanbaren QR-Code an.
 *
 * @param data Der Inhalt, der in den Code kodiert wird (z. B. die Gutschein-ID).
 * @param sizePx Kantenlänge der erzeugten Bitmap in Pixeln. Der Code wird beim
 *   Zeichnen auf die Größe des [modifier] skaliert.
 */
@Composable
internal fun QrCodeImage(
    data: String,
    modifier: Modifier = Modifier,
    sizePx: Int = 512,
    foregroundColor: Color = Color.Black,
    backgroundColor: Color = Color.White
) {
    val qrBitmap = remember(data, sizePx, foregroundColor, backgroundColor) {
        encodeAsQrCode(
            data = data,
            sizePx = sizePx,
            foregroundArgb = foregroundColor.toArgb(),
            backgroundArgb = backgroundColor.toArgb()
        )
    }

    if (qrBitmap != null) {
        Image(
            bitmap = qrBitmap,
            contentDescription = stringResource(R.string.voucher_qr_description),
            modifier = modifier,
            // Ohne Interpolation bleiben die Kanten scharf und der Code scanbar.
            filterQuality = FilterQuality.None
        )
    } else {
        // Fällt die Kodierung aus, zeigen wir den Rohwert an, damit der Gutschein
        // an der Kasse trotzdem manuell eingelöst werden kann.
        Box(
            modifier = modifier.background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = data,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = foregroundColor
            )
        }
    }
}

/**
 * Kodiert [data] als QR-Code-Bitmap oder gibt `null` zurück, wenn der Inhalt
 * nicht kodierbar ist (z. B. leer oder zu lang).
 */
private fun encodeAsQrCode(
    data: String,
    sizePx: Int,
    foregroundArgb: Int,
    backgroundArgb: Int
): ImageBitmap? {
    if (data.isBlank()) return null

    return try {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            // Höhere Fehlerkorrektur: der Code bleibt auch auf einem
            // verschmutzten oder gespiegelten Display lesbar.
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val matrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)

        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val rowOffset = y * sizePx
            for (x in 0 until sizePx) {
                pixels[rowOffset + x] = if (matrix[x, y]) foregroundArgb else backgroundArgb
            }
        }

        Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            .apply { setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx) }
            .asImageBitmap()
    } catch (e: WriterException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
