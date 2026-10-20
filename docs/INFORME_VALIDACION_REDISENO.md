# Validación del rediseño visual V1.0.6

Fecha: 20 de octubre de 2026
Alcance: implementación de las seis pantallas de `PLAN_REDISENO_VISUAL.md`, comprobaciones funcionales y entrega APK. La revisión no ejecutó consultas reales a SerpApi.

## Resultado

Las seis pantallas se implementaron en Jetpack Compose y se revisaron en un emulador Pixel 5 con Android 16 (API 36), escala de fuente 1.0 y viewport ajustado a 1080 × 2400 px con densidad 443 dpi (aprox. 390.5 × 866.8 dp). Esto reproduce la proporción objetivo de 390 × 867 dp. La barra de estado y navegación pertenecen al sistema Android; las pantallas secundarias conservan desplazamiento vertical.

| Boceto | Captura ejecutada | Estado real mostrado |
|---|---|---|
| Alertas | [01-alertas-real.png](design/v1/capturas/01-alertas-real.png) | Alerta CTG–CLO pausada, sin búsquedas guardadas |
| Crear / editar | [02-crear-editar-real.png](design/v1/capturas/02-crear-editar-real.png) | Formulario nuevo con valores iniciales y controles de fecha/ruta |
| Detalle | [03-detalle-real.png](design/v1/capturas/03-detalle-real.png) | Estado vacío de resultados de la alerta de muestra |
| Ajustes | [04-ajustes-real.png](design/v1/capturas/04-ajustes-real.png) | Sin clave configurada, presupuesto local y accesos |
| Diagnóstico | [05-diagnostico-real.png](design/v1/capturas/05-diagnostico-real.png) | Programación, prueba plegada y registro local de arranque |
| Actividad | [06-actividad-real.png](design/v1/capturas/06-actividad-real.png) | Estado vacío de avisos |

### Diferencias de contenido frente a las imágenes de referencia

Los bocetos muestran datos ilustrativos: tarifas verificadas, una alerta activa y avisos previos. Las capturas de validación no falsifican esos datos. La instalación de prueba no tiene una clave SerpApi configurada ni un resultado real guardado, por eso Detalle y Actividad aparecen vacíos y Alertas muestra la alerta de ejemplo pausada. En uso, los resultados reales ocuparán esos mismos componentes. No se muestran horas de vuelos; las horas de revisión y del sistema se conservan donde explican el estado.

Durante la comparación se ajustaron los estados de controles Material para eliminar el color morado por defecto: interruptores activos teal e inactivos grises; selección de navegación y chips teal. Se preservaron márgenes de seguridad, desplazamiento, etiqueta accesible y controles táctiles.

## Tema y componentes aplicados

| Token | Valor |
|---|---|
| Fondo | `#F6F8FA` |
| Superficie | `#FFFFFF` |
| Acción principal | `#116879` |
| Texto principal | `#142632` |
| Texto secundario | `#60727D` |
| Borde | `#E0E6EB` |
| Contenedor teal | `#E3F2F3` |
| Verde de éxito | `#26744A` |

La tipografía usa la familia del sistema Material con escala definida en el tema (cuerpo 12–16 sp, titulares 22–36 sp); radios de 8–28 dp. Navegación principal: Alertas, Actividad y Ajustes. Crear/editar, Detalle y Diagnóstico son destinos secundarios con retorno. El catálogo de aerolíneas sigue siendo informativo dentro de Ajustes/Detalle.

## Huellas SHA-256 de las imágenes

| Sección | Boceto de referencia | Captura de la app |
|---|---|---|
| Alertas | `42aa52bf90b6ab412b2b736aa253824a5d0e726a87843994285eb747aeee5f38` | `83b2431b77f77c51fe4d6e3b10fb359f4718a146465ff280d58d31f23a5624dc` |
| Crear / editar | `b5307a73ffaaa7009739966d381405ba68d69686213eb59eec8839e095dc74b3` | `df85b64bddb06f8e4751f217dc29218372b3c658c671f9c12b251b5dba4c1d3d` |
| Detalle | `48f2dd64f772529315785e58f7b6645d7b48c68859003705abd1919e6afd5bee` | `29ea7d472b7c29ecb586b412e85d39d4d888353da59d19c4b1d4cab065e688a5` |
| Ajustes | `f827220c05ec7dee9fd16cc7ddc6ba8562c155f6971624685c785a856240508c` | `275c7a0b1a0bc5d0b57ed1e22dcea982ebf761f5ff5c397acf40cedf6e898197` |
| Diagnóstico | `17ec53f1ce7b7547dfa0bca869b4fe0292cf65b1ccdae51e5ed99d056979908b` | `c613f544a5b5a1ffaf9c67d6e1a1ff83aeba1307027e25c22396f5527cbdc1f2` |
| Actividad | `b38116f70275cae394672cd637bcbb5b65c833ce52cf4fcce8813561f79dfb7f` | `19c5d9361084455087792d381054da9f9118eb7e6cf44aef2c435054c81cb225` |

## Comprobaciones

- `:app:testDebugUnitTest`: 48 pruebas, 0 fallas.
- `:app:connectedDebugAndroidTest`: 29 pruebas en Pixel 5 API 36, 0 fallas. Incluye navegación sin GET de vuelos, pestañas de diagnóstico y CRUD de alertas.
- `:app:lintDebug` y `:app:lintRelease`: completan sin errores. Quedan cinco avisos informativos de versiones más nuevas de Gradle, Android Gradle Plugin, OkHttp y Kotlin Serialization; no se actualizaron dependencias dentro de este rediseño.
- `scripts/Build-Apk.ps1`: compila variantes debug y release y ejecuta pruebas unitarias y Lint.
- Instalación de actualización: el emulador instaló V1.0.5 y luego la sustituyó por V1.0.6. Android aceptó la actualización con el mismo certificado; el paquete abre con `versionCode=8`, `versionName=1.0.6`, `applicationId=com.flightnotifications.app`.
- Firma release SHA-256: `c08db5c00f3a02e14d656b9143c3479949a8963c39584a8e2c8e0472298d6351` (igual a V1.0.5).
- No se hicieron GET reales para obtener las capturas. La pantalla de Diagnóstico registró sólo eventos `app_started` durante la inspección.

La prueba física final de notificaciones, ejecución de WorkManager con el teléfono bloqueado y reinicio depende del modelo y ajustes de batería del teléfono propietario; no puede certificarse desde el emulador.

## APK de entrega

Paquete: `com.flightnotifications.app` · versión: `1.0.6` · `versionCode`: `8` · minSdk: `33` · targetSdk: `37`.

| Archivo | Tamaño | SHA-256 |
|---|---:|---|
| `flight-notifications-1.0.6-release.apk` | 3,349,010 bytes | `6426e3830c766a5e1376f15d075b3445ee359924b552aa78b1c84abec117ee9e` |
| `flight-notifications-1.0.6-debug.apk` | 36,040,776 bytes | `76f41d3ba51a26e66129f84da09eee05b28f525b10984ee71a02100fec6eb9ef` |

Los dos APK están copiados a `G:\Mi unidad\APK android car\` y a `outputs/` en el espacio de trabajo. El release conserva el certificado anterior y está listo para instalar como actualización. Para uso normal se debe instalar el APK **release**.
