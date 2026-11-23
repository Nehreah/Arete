package com.renea.arete_social

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.compose.ui.Modifier
import com.renea.arete_social.gui.navigation.AreteNavHost
import com.renea.arete_social.gui.theme.Arete_SocialTheme
import com.renea.arete_social.gui.theme.PreferenciaTemaApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as AreteApp
        val contenedor = app.contenedor
        PreferenciaTemaApp.cargar()

        if (contenedor != null) {
            com.renea.arete_social.feature.auth.GestorSesionLocal.inicializar(applicationContext)
            com.renea.arete_social.data.local.perfil.GestorInteresesUsuario.inicializar(applicationContext)
        }

        setContent {
            Arete_SocialTheme(modoTema = PreferenciaTemaApp.modo) {
                if (contenedor != null) {
                    AreteApp(contenedor)
                }
            }
        }
    }
}

@Composable
fun AreteApp(contenedor: ContenedorApp) {
    val view = LocalView.current
    val fondoClaro = MaterialTheme.colorScheme.background.luminance() > 0.5f
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = fondoClaro
                insetsController.isAppearanceLightNavigationBars = fondoClaro
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
        ) {
            AreteNavHost(contenedor)
        }
    }
}
