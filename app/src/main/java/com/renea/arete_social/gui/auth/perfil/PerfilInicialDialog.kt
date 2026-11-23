package com.renea.arete_social.gui.auth.perfil

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.renea.arete_social.domain.model.Usuario

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilInicialDialog(
    usuario: Usuario,
    onGuardar: (nombre: String, hobby: String, deporte: String, ritmo: String, serie: String, libro: String, signo: String) -> Unit,
    onCerrar: () -> Unit
) {
    var nombre by remember(usuario.id) { mutableStateOf(usuario.nombre) }
    var hobby by remember(usuario.id) { mutableStateOf(usuario.hobby.orEmpty()) }
    var deporte by remember(usuario.id) { mutableStateOf(usuario.deporte.orEmpty()) }
    var ritmo by remember(usuario.id) { mutableStateOf(usuario.ritmoMusical.orEmpty()) }
    var serie by remember(usuario.id) { mutableStateOf(usuario.serieFavorita.orEmpty()) }
    var libro by remember(usuario.id) { mutableStateOf(usuario.libroFavorito.orEmpty()) }
    var signo by remember(usuario.id) { mutableStateOf(usuario.signoZodiacal.orEmpty()) }
    Dialog(onDismissRequest = {}) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState()).padding(22.dp)) {
                Text("Tu perfil", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text("Cuéntanos un poco de ti para personalizar tu espacio.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                CampoPerfil("Nombre", nombre) { nombre = it }
                CampoPerfil("Hobby", hobby) { hobby = it }
                CampoPerfil("Deporte", deporte) { deporte = it }
                CampoPerfil("Ritmo musical", ritmo) { ritmo = it }
                CampoPerfil("Serie favorita", serie) { serie = it }
                CampoPerfil("Libro favorito", libro) { libro = it }
                var signoExpandido by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = signoExpandido, onExpandedChange = { signoExpandido = it }) {
                    OutlinedTextField(value = signo, onValueChange = { signo = it }, label = { Text("Signo zodiacal") }, readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = signoExpandido) }, modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth())
                    ExposedDropdownMenu(expanded = signoExpandido, onDismissRequest = { signoExpandido = false }) {
                        listOf("Aries", "Tauro", "Géminis", "Cáncer", "Leo", "Virgo", "Libra", "Escorpio", "Sagitario", "Capricornio", "Acuario", "Piscis", "Prefiero no decirlo").forEach {
                            DropdownMenuItem(text = { Text(it) }, onClick = { signo = it; signoExpandido = false })
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Button(onClick = { onGuardar(nombre.trim(), hobby.trim(), deporte.trim(), ritmo.trim(), serie.trim(), libro.trim(), signo.trim()) }, enabled = nombre.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Guardar y continuar") }
                TextButton(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) { Text("Omitir encuesta") }
            }
        }
    }
}

@Composable
private fun CampoPerfil(etiqueta: String, valor: String, cambio: (String) -> Unit) {
    OutlinedTextField(value = valor, onValueChange = cambio, label = { Text(etiqueta) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
}
