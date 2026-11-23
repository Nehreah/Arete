package com.renea.arete_social.gui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Diálogo de EULA (Acuerdo de Licencia de Usuario Final), Términos de Servicio y Normas
 * Comunitarias de Aréte.
 *
 * Establece la mayoría de edad estricta (+18 años), la exoneración de responsabilidad
 * en encuentros físicos/eventos presenciales, la responsabilidad individual por contenido (UGC),
 * la política de tolerancia cero ante acoso/odio, mecanismos obligatorios de reporte/bloqueo
 * y el compromiso de moderación activa comunitaria en menos de 24 horas.
 */
@Composable
fun DialogoEulaNormas(
    onDismiss: () -> Unit,
    onAceptar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "📜",
                    fontSize = 22.sp
                )
                Text(
                    text = "Términos, EULA y Normas",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🔞 EXCLUSIVO PARA MAYORES DE EDAD (+18 AÑOS)\n" +
                            "⚠️ TOLERANCIA CERO ANTE ACOSO, ODIO O CONDUCTAS ILEGALES:\n" +
                            "Aréte es un espacio de descubrimiento y convivencia respetuosa. Al utilizar esta aplicación, aceptas regirte incondicionalmente por estas normas.",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                // 1. Mayoría de Edad Estricta
                SeccionNorma(
                    titulo = "1. Restricción Estricta de Mayoría de Edad (+18 Años)",
                    contenido = "• Aréte está estrictamente reservada para personas mayores de 18 años (18 años cumplidos en adelante).\n" +
                        "• Queda terminantemente prohibido el acceso, registro o utilización de esta plataforma a menores de 18 años.\n" +
                        "• Al crear una cuenta, declaras bajo la gravedad de juramento ser mayor de edad legal.\n" +
                        "• Toda cuenta sospechosa o denunciada que pertenezca a un menor de edad será cancelada y eliminada de forma inmediata, definitiva y sin derecho a reclamo."
                )

                // 2. Encuentros Físicos y Eventos Presenciales
                SeccionNorma(
                    titulo = "2. Descargo en Encuentros Presenciales y Eventos",
                    contenido = "• Aréte es exclusivamente una herramienta tecnológica de contacto y radar de proximidad. Aréte NO organiza, NO avala, NO supervisa ni garantiza la seguridad o legitimidad de ningún encuentro físico, cita o evento presencial entre usuarios.\n" +
                        "• Cualquier encuentro en el mundo real, asistencia a eventos o interacción fuera de la aplicación se realiza de forma libre, voluntaria y bajo la EXCLUSIVA RESPONSABILIDAD de los usuarios participantes.\n" +
                        "• Reglas de autoprotección: reúnete siempre en lugares públicos concurridos, informa a familiares o amistades de confianza, y jamás entregues dinero, datos bancarios ni contraseñas a personas que acabas de conocer."
                )

                // 3. Contenido Generado por el Usuario (UGC)
                SeccionNorma(
                    titulo = "3. Contenido de Usuarios (UGC) y Responsabilidad Individual",
                    contenido = "• Cada usuario es el único y exclusivo responsable civil y penal de los textos, imágenes, opiniones, enlaces y publicaciones que comparta en perfiles, murales y comunidades.\n" +
                        "• Aréte y sus desarrolladores no asumen responsabilidad alguna por las conductas, declaraciones u opiniones expresadas por los usuarios.\n" +
                        "• Los chats directos privados cuentan con cifrado de extremo a extremo (E2EE): la plataforma no tiene la capacidad técnica de descifrar ni monitorear tus mensajes privados, siendo el intercambio exclusivo resorte de los interlocutores."
                )

                // 4. Diversidad y Convivencia
                SeccionNorma(
                    titulo = "4. Diversidad, Inclusión y Principios de Convivencia",
                    contenido = "• Aréte es una red progresista que respalda y celebra la diversidad humana en todo su esplendor: género, credo, etnia, orientación sexual y pensamiento.\n" +
                        "• Nuestro objetivo es tejer lazos humanos locales de respeto mutuo, nunca formar trincheras, odio ni divisiones.\n" +
                        "• Aquí se tolera todo, menos la intolerancia: la libertad de expresión termina donde comienza la agresión, la calumnia o el acoso hacia otros."
                )

                // 5. Conductas y Contenidos Estrictamente Prohibidos
                SeccionNorma(
                    titulo = "5. Conductas y Contenidos Estrictamente Prohibidos",
                    contenido = "Al usar Aréte, te comprometes a NO publicar, compartir ni promover:\n" +
                        "• Discursos de odio, racismo, xenofobia, homofobia, misoginia o discriminación.\n" +
                        "• Acoso, intimidación, amenazas, calumnias, chantaje o persecución a otros.\n" +
                        "• Contenido sexualmente explícito, pornografía o desnudez.\n" +
                        "• Explotación o abuso infantil de cualquier tipo (CSAM/CSAE).\n" +
                        "• Comercialización o incitación al consumo de sustancias ilícitas, armas o drogas.\n" +
                        "• Apología al delito, violencia o autolesión.\n" +
                        "• Suplantación de identidad, estafas, fraude, captación de dinero o spam."
                )

                // 6. Estándares de Seguridad Infantil (Tolerancia Cero CSAM/CSAE)
                SeccionNorma(
                    titulo = "6. Estándares de Seguridad Infantil (Tolerancia Cero CSAM)",
                    contenido = "• Aréte prohíbe de forma absoluta e incondicional cualquier material de abuso sexual infantil (CSAM) y toda explotación o abuso sexual infantil (CSAE).\n" +
                        "• Mantenemos tolerancia cero ante pornografía infantil, acoso o solicitud sexual a menores (grooming) y sextorsión.\n" +
                        "• Cualquier infracción conlleva la eliminación inmediata del contenido, expulsión irreversible de la cuenta y reporte penal formal a las autoridades pertinentes (NCMEC, Te Protejo y Fiscalía/Policía Judicial).\n" +
                        "• Puedes denunciar dentro de la app con el botón 🚩 seleccionando 'Abuso infantil / CSAM' o al correo: politicaarete@gmail.com con asunto 'DENUNCIA CSAM'."
                )

                // 7. Reporte y Bloqueo
                SeccionNorma(
                    titulo = "7. Herramientas de Protección Activa: Reportar y Bloquear",
                    contenido = "• Dispones de herramientas integradas en todo momento: botón de Reportar (🚩) y botón de Bloquear (🚫) en perfiles, mensajes y publicaciones.\n" +
                        "• Al bloquear a un usuario, se corta de forma instantánea toda vía de contacto y desaparece de inmediato la visibilidad mutua en el radar de cercanía y perfiles."
                )

                // 8. Moderación Rápida
                SeccionNorma(
                    titulo = "8. Moderación Humana Activa (Plazo < 24 Horas)",
                    contenido = "• Todo reporte es investigado por nuestro equipo de moderación en un plazo menor a 24 horas (atención inmediata prioritaria en seguridad infantil).\n" +
                        "• El incumplimiento de estas normas faculta a Aréte a retirar de inmediato el contenido infractor y aplicar la suspensión temporal o expulsión definitiva e irreversible de la cuenta infractora."
                )

                // 9. Geolocalización Voluntaria y Privacidad
                SeccionNorma(
                    titulo = "9. Geolocalización Voluntaria y Privacidad de Datos",
                    contenido = "• Compartir tu ubicación o aparecer en el radar de personas cercanas es 100% voluntario y configurable desde tus ajustes de privacidad (modo fantasma o sin permisos de ubicación).\n" +
                        "• El tratamiento de tus datos personales se apega a la Ley 1581 de 2012 de Hábeas Data de Colombia y normativas internacionales. No vendemos tus datos a terceros ni comercializamos publicidad invasiva."
                )

                // 10. Limitación de Responsabilidad
                SeccionNorma(
                    titulo = "10. Limitación de Responsabilidad e Indemnidad",
                    contenido = "• La aplicación se provee «tal cual» («as is»). En la medida máxima permitida por la ley, el desarrollador no responde por daños derivados de disputas entre usuarios o fallos de conexión.\n" +
                        "• Aceptas mantener indemne a Aréte y a sus creadores frente a cualquier reclamación de terceros derivada del mal uso que hagas del servicio."
                )

                // 11. Eliminación de Cuenta y Soporte
                SeccionNorma(
                    titulo = "11. Eliminación Definitiva de Cuenta y Soporte",
                    contenido = "• Tienes derecho a eliminar tu cuenta y todos tus datos personales en cualquier momento desde Perfil → Ajustes → Eliminar cuenta.\n" +
                        "• Para reportes de seguridad, dudas legales o denuncias, contáctanos a: politicaarete@gmail.com."
                )

                val context = androidx.compose.ui.platform.LocalContext.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://github.com/Nehreah/AntiRed/blob/main/docs/POLITICA_PRIVACIDAD.md#seguridad-infantil")
                            )
                            runCatching { context.startActivity(intent) }
                        }
                    ) {
                        Text(
                            text = "🛡️ Estándares de Seguridad Infantil",
                            fontSize = 11.5.sp,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                androidx.compose.material3.TextButton(
                    onClick = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/Nehreah/AntiRed/blob/main/docs/POLITICA_PRIVACIDAD.md")
                        )
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "🌐 Ver Política y Términos completos en web",
                        fontSize = 11.5.sp,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAceptar,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Acepto los Términos (+18)",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
private fun SeccionNorma(
    titulo: String,
    contenido: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = titulo,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = contenido,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
