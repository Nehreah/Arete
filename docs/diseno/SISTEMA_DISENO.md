# Sistema de Diseño Visual — Aréte

Documento de especificación del sistema de diseño visual de **Aréte**. La interfaz combina una estética táctica de alto contraste con principios modernos de diseño en Jetpack Compose y Material 3 (M3).

---

## 1. Identidad Cromática y Tokens Semánticos

Aréte implementa una identidad basada en **blanco, grafito y rojo táctico**:

| Token Semántico | Modo Claro | Modo Oscuro | Propósito |
|---|---|---|---|
| **Primary (Acento)** | Rojo Carmín (`#D92332`) | Rojo Coral (`#FF646E`) | Acciones primarias, botones destacados, radar de mapa y estados activos. |
| **OnPrimary** | Blanco Puro (`#FFFFFF`) | Blanco Puro (`#FFFFFF`) | Texto e iconos sobre superficies de acento primario. |
| **Background (Fondo)** | Blanco Nieve (`#FAFAFA`) | Grafito Profundo (`#121214`) | Lienzo de fondo de la aplicación. |
| **Surface (Superficie)** | Blanco Puro (`#FFFFFF`) | Grafito Medio (`#1E1E22`) | Tarjetas, paneles elevados y diálogos. |
| **SurfaceVariant** | Gris Perla (`#F0F0F2`) | Carbón Oscuro (`#2A2A30`) | Contenedores secundarios y campos de texto. |
| **OnSurface** | Negro Azabache (`#171717`) | Blanco Humo (`#F5F5F7`) | Tipografía principal y títulos. |
| **OnSurfaceVariant** | Gris Pizarra (`#66666E`) | Gris Claro (`#A0A0A8`) | Subtítulos, metadatos y etiquetas secundarias. |
| **Outline** | Borde Sutil (`#E2E2E6`) | Borde Grafito (`#383840`) | Divisores y contornos de tarjetas tácticas. |

---

## 2. Ortogonalidad entre Modo Visual y Ciclo Ambiental

La interfaz desacopla estrictamente la apariencia visual del reloj del mundo:
- **`ModoTema` (CLARO / OSCURO):** Configurado libremente por la persona en cualquier momento desde los ajustes de la app. Rige la paleta de colores de toda la interfaz y componentes Material 3.
- **`CicloDia` (DIA / NOCHE):** Responde a la hora solar local (07:00 a 19:00 Día; 19:00 a 07:00 Noche). Modula el renderizado de OpenStreetMap/osmdroid y las rutinas biológicas de Michi sin forzar el cambio del tema de la UI general.

---

## 3. Componentes Tácticos y Shaders Visuales

Para mantener una estética de instrumentación táctica sin comprometer la legibilidad:
1. **Contenedor con Corchetes (`TarjetaTacticalBox`):**
   - Esquinas demarcadas mediante corchetes tácticos de 90° calculados en Canvas (`drawLine`).
   - Contorno sutil con soporte para elevación M3 o estética Cyberpunk retro.
2. **Filtro Cyber Matrix (`EfectoFiltroMatrix`):**
   - Líneas de barrido CRT (*scanlines*) transparentes de 4 dp calculadas en Canvas.
   - Haz de escaneo descendente infinito y viñeta radial suave sin pérdida de contraste.
3. **Indicador Cardíaco / Telemetría (`IconoOndaCardiaca`):**
   - Onda de pulso ECG vectorial en tiempo real para publicaciones de bitácora y estados de transmisión.
4. **Retícula Concéntrica (`FondoRadarReticle`):**
   - Círculos concéntricos y mira radial cruzada en el fondo del perfil.
