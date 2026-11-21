package com.app.smishing.ui.designsystem.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * La foto de perfil de un contacto, redonda. Si [photoUri] es `null` o no se puede leer, muestra una imagen por defecto sencilla (una
 * silueta sobre un círculo claro). La foto se lee del teléfono, no sale de él, y no se guarda. Es decorativa: el nombre ya va escrito al
 * lado, así que no la lee el lector de pantalla.
 */
@Composable
fun Avatar(photoUri: String?, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(initialValue = null, photoUri) {
        value = if (photoUri == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
    val image = photo
    if (image != null) {
        Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.size(size).clip(CircleShape))
    } else {
        Box(modifier = modifier.size(size).clip(CircleShape).background(SmishingPalette.PrimaryLight), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = SmishingPalette.Primary, modifier = Modifier.size(size * 0.6f))
        }
    }
}
