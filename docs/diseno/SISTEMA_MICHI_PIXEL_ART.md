# Michi y su Mundo Social — Especificación de Pixel Art

Especificación técnica y visual de **Michi** y el ecosistema de mascotas virtuales en **Aréte**.

---

## 1. Concepto y Filosofía de Diseño

Michi no es una simple mascota estática; constituye una presencia viva e interactiva que acompaña a la persona en el mapa táctico (como avatar o "Mimi"), habita en su propia casa interactiva, explora ambientes compartidos con amigos y reacciona de forma autónoma al ciclo ambiental local.

---

## 2. Escenarios y Superficies de Visualización

1. **Pantalla de Michi (`PetScene`):**
   - Vista principal de cuidado, alimentación, juego y descanso.
   - Renderizado pixel art con escalado de píxel entero (`FilterQuality.None`) para evitar distorsiones o desenfoques bilineales.
2. **Casa Interactiva:**
   - Cuadrícula isométrica/top-down donde se ubican muebles y objetos coleccionables.
   - Admite visitas de mascotas de amigos recibidas a través de la malla Bluetooth o LAN.
3. **Ambientes Sociales Compartidos:**
   - Tres entornos públicos: **Parque**, **Gimnasio** y **Café**.
   - Admite hasta 6 personajes simultáneos sincronizados mediante el reloj de animación `withFrameNanos`.
4. **Mimis en el Radar de Mapa:**
   - Representación de la mascota caminando por el mapa en la posición aproximada o visible del usuario.
   - Intercambio de paleta cromática (*palette swap*) por código para diferenciar los pelajes de las personas cercanas.

---

## 3. Estados de Simulación y Ciclo Vital

- **Especies Soportadas:** Gato, Perro, Zorro, Mapache.
- **Etapas de Crecimiento:** Cría, Joven, Adulto, Sabio, Mítico.
- **Modos Especiales:**
  - **Modo Súper:** Aura dorada temporal activada tras completar logros o racha de cuidados, que amplía la percepción de salas y eventos en el radar.
  - **Turbo:** Aceleración motriz durante entrenamientos o minijuegos.
- **Temperamentos:** Juguetón, Curioso, Perezoso, Protector, Solitario, Aventurero.
