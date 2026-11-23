package com.renea.arete_social.feature.tamagotchi.model

/**
 * Resultado de intentar una acción de cuidado. La lógica NUNCA falla en silencio:
 * siempre devuelve uno de estos valores y la pantalla muestra [texto] si no fue [OK].
 */
enum class ResultadoCuidado {
    OK,
    SIN_MASCOTA,
    HUEVO,
    DORMIDA,
    FUERA_DE_LINEA,
    NO_ESTA_FUERA_DE_LINEA,
    NO_TIENE_HAMBRE,
    YA_LIMPIA,
    TIENE_HAMBRE,
    POCA_ENERGIA,
    NO_TIENE_SUENO,
    NO_ESTA_ENFERMA,
    EN_ESPERA,
    SIN_CHISPAS,
    LIMITE_DIARIO,
    SIN_MEDICINA,
    BOTIQUIN_LLENO,
    NOMBRE_INVALIDO;

    val exito: Boolean get() = this == OK

    /** Mensaje corto y claro para mostrar al usuario. */
    fun texto(nombre: String): String = when (this) {
        OK -> ""
        SIN_MASCOTA -> "Todavía no hay mascota."
        HUEVO -> "$nombre sigue dentro del huevo. ¡Falta poco!"
        DORMIDA -> "$nombre está durmiendo. Despiértala primero."
        FUERA_DE_LINEA -> "$nombre está fuera de línea. Reiníciala para seguir."
        NO_ESTA_FUERA_DE_LINEA -> "$nombre está conectada, no hace falta reiniciarla."
        NO_TIENE_HAMBRE -> "$nombre no tiene hambre ahora."
        YA_LIMPIA -> "$nombre ya está limpia."
        TIENE_HAMBRE -> "$nombre tiene hambre. Dale de comer primero."
        POCA_ENERGIA -> "$nombre necesita más energía. Déjala descansar."
        NO_TIENE_SUENO -> "$nombre aún tiene energía, no tiene sueño."
        NO_ESTA_ENFERMA -> "$nombre está sana, no necesita medicina."
        EN_ESPERA -> "Espera un momento antes de repetirlo."
        SIN_CHISPAS -> "No tienes chispas. Se ganan al encontrarte con amigos."
        LIMITE_DIARIO -> "Por hoy ya fue suficiente. ¡Vuelve mañana!"
        SIN_MEDICINA -> "$nombre necesita medicina, pero nadie te ha enviado. Pídele a un amigo que te mande una desde el mapa."
        BOTIQUIN_LLENO -> "$nombre ya tiene toda la medicina que puede guardar."
        NOMBRE_INVALIDO -> "Escribe un nombre de 1 a ${PetBalance.NAME_MAX_LENGTH} letras."
    }
}
