package com.app.smishing.ui.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.app.smishing.R
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing

/**
 * Pantalla de carga: Smishy a pantalla completa sobre el azul de la marca, con su nombre y los tres puntos de "cargando".
 *
 * Smishy va **quieto** ([animated] en `false`): la carga es tan rápida que la animación no se alcanza a ver. La animación de
 * [SmishyInvestigating] sigue en el código: con [animated] en `true` se reproduce una vez y [onFinished] avisa cuando termina (de
 * inmediato si las animaciones del sistema están desactivadas). Con Smishy quieto, [onFinished] no se llama.
 */
@Composable
fun SmishySplashScreen(modifier: Modifier = Modifier, animated: Boolean = false, onFinished: () -> Unit = {}) {
    Column(
        modifier = modifier.fillMaxSize().background(SmishingPalette.Primary),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (animated) {
            SmishyInvestigating(modifier = Modifier.width(180.dp), onFinished = onFinished)
        } else {
            Image(
                painter = painterResource(R.drawable.smishy_splash),
                contentDescription = null,
                modifier = Modifier.size(width = 160.dp, height = 195.dp),
            )
        }
        Spacer(Modifier.height(Spacing.Md))
        Text(stringResource(R.string.app_name), style = SmishingTypography.Wordmark, color = Color.White)
        Spacer(Modifier.height(Spacing.Sm))
        SmishyLoadingDots()
    }
}
