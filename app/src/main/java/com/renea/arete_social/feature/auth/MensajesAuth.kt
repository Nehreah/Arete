package com.renea.arete_social.feature.auth

/**
 * Mensajes claros y en español para el registro y el inicio de sesión: tanto las
 * validaciones previas como la traducción de los errores técnicos. Así la persona nunca ve textos como
 * "invalid_credentials" o "UnknownHostException".
 */
object MensajesAuth {

    const val USUARIO_MIN = 3
    const val USUARIO_MAX = 20
    const val PASSWORD_MIN = 8

    /** Solo letras (sin tildes ni ñ), números y guion bajo: así se arma el acceso interno. */
    private val regexUsuario = Regex("^[A-Za-z0-9_]+$")

    /** Devuelve un aviso si los datos no sirven todavía, o null si están bien. */
    fun validar(usuario: String, password: String, esRegistro: Boolean): String? {
        val u = usuario.trim()
        if (u.isEmpty() && password.isEmpty()) return "Escribe tu usuario y tu contraseña para continuar."
        if (u.isEmpty()) return "Escribe tu nombre de usuario."
        if (password.isEmpty()) return "Escribe tu contraseña."
        if (u.length < USUARIO_MIN) return "Tu usuario debe tener al menos $USUARIO_MIN caracteres."
        if (u.length > USUARIO_MAX) return "Tu usuario puede tener hasta $USUARIO_MAX caracteres."
        if (!regexUsuario.matches(u)) {
            return "El usuario solo puede tener letras sin tildes, números y guion bajo (_), sin espacios."
        }
        if (esRegistro && password.length < PASSWORD_MIN) {
            return "Tu contraseña debe tener al menos $PASSWORD_MIN caracteres."
        }
        return null
    }

    /** Traduce cualquier error de red o de autenticación a una frase clara con qué hacer. */
    fun traducirError(error: Throwable, esRegistro: Boolean): String {
        val texto = buildString {
            append(error.message.orEmpty())
            append(' ')
            append(error.cause?.message.orEmpty())
            append(' ')
            append(error.javaClass.simpleName)
        }.lowercase()

        fun contiene(vararg claves: String) = claves.any { texto.contains(it) }

        return when {
            contiene("unknownhost", "unable to resolve", "connectexception", "sockettimeout",
                "timeout", "timed out", "network", "no address associated", "failed to connect",
                "ssl", "unreachable", "connection abort", "connection reset") ->
                "No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo."

            contiene("user_already_exists", "already registered", "already been registered", "already exists") ->
                "Ese nombre de usuario ya está en uso. Prueba con otro."

            contiene("weak_password", "password should be", "at least 6", "password is too") ->
                "Tu contraseña es muy débil. Usa al menos $PASSWORD_MIN caracteres."

            contiene("invalid_credentials", "invalid login", "invalid_grant", "invalid email or password") ->
                "Usuario o contraseña incorrectos. Revisa los datos e inténtalo de nuevo."

            contiene("email_not_confirmed", "not confirmed") ->
                "Tu cuenta aún no está confirmada. Contacta con soporte para activarla."

            contiene("over_request_rate_limit", "over_email_send_rate_limit", "rate limit", "too many requests") ->
                "Demasiados intentos seguidos. Espera un momento antes de volver a intentarlo."

            contiene("signup_disabled", "signups not allowed", "signups are disabled") ->
                "Por ahora no se pueden crear cuentas nuevas. Inténtalo más tarde."

            contiene("validation_failed", "unable to validate", "invalid format", "invalid email") ->
                "El usuario tiene un formato no válido. Usa solo letras, números y guion bajo (_)."

            contiene("unexpected_failure", "internal server", "bad gateway", "service unavailable") ->
                "El servidor tiene problemas en este momento. Inténtalo de nuevo en unos minutos."

            esRegistro -> "No pudimos crear tu cuenta. Revisa los datos e inténtalo de nuevo."
            else -> "No pudimos iniciar sesión. Revisa los datos e inténtalo de nuevo."
        }
    }
}
