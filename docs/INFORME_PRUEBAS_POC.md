# Informe de pruebas de la prueba de concepto

Actualizado el 19 de octubre de 2026. Este informe distingue pruebas escritas de pruebas ejecutadas. La falta de SDK/Wrapper impide marcar las pruebas de aplicación como aprobadas.

## Estado de ejecución

| Área | Casos preparados | Estado actual | Evidencia disponible |
|---|---|---|---|
| Parseo JSON JVM | Listas `best_flights`/`other_flights`, Wingo, aerolínea desconocida, URL con marcador sensible incluso codificado y opción malformada | Implementado, no ejecutado | `SerpApiJsonParserTest` y fixtures ficticios |
| Validación de ruta JVM | Aeropuertos, fechas, directo, regreso y secuencia temporal | Implementado, no ejecutado | `FlightOptionValidatorTest` |
| HTTP local JVM | Cuenta sola, ida/token/regreso, filtros ausentes, tope de dos GET, sin token, opciones malformadas, reloj bloqueado y HTTP 503 sin reintento | Implementado, no ejecutado | `SerpApiRepositoryTest`, MockWebServer y JSON sintético |
| Room / cuota | Máximo de 200 reservas, ventana conservadora y bloqueo por salto del reloj; un avance de fecha al reiniciar no libera reservas | Instrumentado, no ejecutado | `LocalBudgetRepositoryTest` |
| Android Keystore | Guardar, recuperar, reemplazar y eliminar una clave ficticia en un alias/directorio aislado | Instrumentado, no ejecutado | `KeystoreCredentialStoreTest` |
| Interfaz Compose | Idioma, estado inicial sin clave y acción de vuelos deshabilitada | Instrumentado, no ejecutado | `ProbeScreenTest` |
| Android Lint / build / APK | AGP 9.4, Kotlin, Compose, Room/KSP y manifest | No ejecutado | No hay Gradle Wrapper ni Android SDK local |
| Account API real | Cuenta del propietario | No ejecutado | No se recibió una clave y no se necesita para desarrollar pruebas offline |
| GET reales de vuelos | CTG–CLO ida y vuelta | No ejecutado | No se inició ninguna solicitud a SerpApi |

## Controles automáticos diseñados

- Las pruebas de transporte apuntan a `MockWebServer` local, incluyen una clave ficticia y verifican la secuencia real construida por Retrofit.
- El flujo consulta Account API antes de buscar vuelos; no descuenta llamadas de cuenta del contador local.
- Cada GET de vuelos reserva espacio en Room antes de despacharse. Una respuesta HTTP fallida también conserva la reserva.
- Una ejecución no inicia más de dos GET. Si no hay salida válida con token, la prueba termina después de la primera petición.
- Se rechaza o filtra la URL de Google Flights salvo que use HTTPS, dominio `www.google.com` y la ruta `/travel/flights`, sin parámetros conocidos como secretos.
- No se registran URLs completas, cuerpos, respuestas de error ni excepciones remotas.

## Próxima actualización

Después de contar con Wrapper y SDK, se deben ejecutar primero las unitarias y compilar debug. Corregir todos los fallos, después ejecutar Lint e instrumentadas y registrar salida, versiones y dispositivo/API. Las pruebas reales se anotarán como un bloque separado; ninguna prueba offline equivale a haber consultado la cuenta o el servicio de vuelos real.
