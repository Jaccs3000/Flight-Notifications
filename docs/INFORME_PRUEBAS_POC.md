# Informe de pruebas de la prueba de concepto

> **Instantánea histórica de la APK PoC 0.1.0.** Describe únicamente la primera prueba técnica; no representa la app V1. Para el estado actual de funciones, pruebas y APK usa [INFORME_IMPLEMENTACION_V1.md](INFORME_IMPLEMENTACION_V1.md).

Actualizado el 19 de octubre de 2026. Este informe distingue pruebas locales/simuladas de llamadas reales a SerpApi.

## Estado de ejecución

| Área | Casos preparados | Estado actual | Evidencia disponible |
|---|---|---|---|
| Parseo JSON JVM | Listas `best_flights`/`other_flights`, Wingo, aerolínea desconocida, URL con marcador sensible incluso codificado y opción malformada | Aprobado: 3/3 | `SerpApiJsonParserTest` |
| Registro técnico local | Persistencia entre instancias y redacción de API key y tokens | Aprobado: 1/1 | `DiagnosticLogTest` |
| Validación de ruta JVM | Aeropuertos, fechas, directo, regreso y secuencia temporal | Aprobado: 3/3 | `FlightOptionValidatorTest` |
| HTTP local JVM | Cuenta sola, ida/token/regreso, filtros ausentes, tope de dos GET, sin token, opciones malformadas, reloj bloqueado y HTTP 503 sin reintento | Aprobado: 6/6 | `SerpApiRepositoryTest`, MockWebServer y JSON sintético |
| Room / cuota | Máximo de 200 reservas, ventana conservadora y bloqueo por salto del reloj; un avance de fecha al reiniciar no libera reservas | Aprobado: 4/4 | `LocalBudgetRepositoryTest`, emulador Pixel 5 API 36 |
| Android Keystore | Guardar, recuperar, reemplazar y eliminar una clave ficticia en un alias/directorio aislado | Aprobado: 1/1 | `KeystoreCredentialStoreTest`, emulador Pixel 5 API 36 |
| Interfaz Compose | Idioma, estado inicial sin clave y acción de vuelos deshabilitada | Aprobado: 1/1 | `ProbeScreenTest`, emulador Pixel 5 API 36 |
| Android Lint / build / APK | AGP 9.4, Kotlin, Compose, Room/KSP y manifest | Debug y release del PoC compilados; Lint aprobado; release firmada | El log y el manifiesto de hashes son archivos mutables reemplazados por los builds V1; sus valores actuales no se presentan como evidencia de los binarios PoC. |
| Account API real | Cuenta del propietario | No ejecutado; 0 llamadas | No se ingresó clave real ni se llamó a SerpApi |
| GET reales de vuelos | CTG–CLO ida y vuelta | No ejecutado; 0 llamadas | Las pruebas usan solo datos sintéticos; no se inició ninguna solicitud real |

## Controles automáticos diseñados

- Las pruebas de transporte apuntan a `MockWebServer` local, incluyen una clave ficticia y verifican la secuencia real construida por Retrofit.
- El flujo consulta Account API antes de buscar vuelos; no descuenta llamadas de cuenta del contador local.
- Cada GET de vuelos reserva espacio en Room antes de despacharse. Una respuesta HTTP fallida también conserva la reserva.
- Una ejecución no inicia más de dos GET. Si no hay salida válida con token, la prueba termina después de la primera petición.
- Se rechaza o filtra la URL de Google Flights salvo que use HTTPS, dominio `www.google.com` y la ruta `/travel/flights`, sin parámetros conocidos como secretos.
- No se registran URLs completas, cuerpos, respuestas de error ni excepciones remotas.

## Artefactos

- Release instalable: `G:\Mi unidad\APK android car\serpapi-poc-0.1.0-release.apk` (Android 13/API 33 o superior; package `com.flightnotifications.app`, versión `0.1.0`).
- Debug: `G:\Mi unidad\APK android car\serpapi-poc-0.1.0-debug.apk` (package `com.flightnotifications.app.debug`).
- Los hashes del PoC no se incluyen en la instantánea del informe. El manifiesto actual `G:\Mi unidad\APK android car\SHA256SUMS.txt` corresponde a V1.0.1; no usarlo para verificar los binarios históricos `serpapi-poc-0.1.0-*.apk`.
- Build y Lint: `outputs/build-final.log`; ambos APK se compilaron, y `apksigner verify` validó las dos firmas.
- Instalación manual: la release se instaló y abrió en Pixel 5 AVD con Android 16/API 36; actividad en estado `RESUMED`, sin excepción fatal.

La firma release se mantiene fuera del repositorio, en el perfil local de Windows. Debe conservarse junto con su copia de seguridad cifrada para permitir futuras actualizaciones. Los casos reales siguen siendo una verificación manual separada y consumen cuenta/cuota según la acción elegida en la app.
