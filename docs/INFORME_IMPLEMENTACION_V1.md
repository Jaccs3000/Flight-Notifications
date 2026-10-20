# Informe de implementación y aceptación V1

**Fecha:** 20 de octubre de 2026 (COT). Este informe conserva la evidencia histórica de V1.0.3 y añade el estado vigente de V1.0.4.<br>
**Proyecto:** `C:\Flight-Notifications-main`<br>
**Especificación:** [REQUIREMENTS.md](../REQUIREMENTS.md) V1.4; [plan técnico](../PLAN_APP_DEFINITIVA.md) v1.2.<br>
**Estado actual:** APK V1.0.4 debug y release generados desde Room v4 y copiados a `G:\Mi unidad\APK android car\`. La release usa `versionCode=6`, `versionName=1.0.4`, conserva el certificado estable y tiene firma verificada. Se implementó la preferencia posterior: avisar en cada revisión la tarifa verificada más baja bajo el máximo aunque ese mismo precio ya se haya notificado. En esta fuente pasaron 45 pruebas JVM, 24 instrumentadas en emulador y `lintDebug`/`lintRelease`; ninguna usó SerpApi real. La actualización y las comprobaciones de ejecución en segundo plano en el teléfono físico siguen pendientes.

## Entrega vigente: V1.0.4

La elegibilidad selecciona la oferta completa y verificada de menor precio que cumple el máximo de la alerta. Se genera un ID de despacho nuevo por revisión, por lo que una revisión posterior puede volver a notificar el mismo itinerario y precio. La idempotencia evita duplicar el mismo despacho durante recuperación; no se usa para suprimir tarifas ya avisadas. El esquema Room continúa en v4, sin migración de base de datos para este cambio.

| Variante | Identificador | Versión | Tamaño | SHA-256 |
|---|---|---:|---:|---|
| Release | `com.flightnotifications.app` | 1.0.4 (6) | 3.209.458 bytes | `904a26a4a3b19bc208419c948dfe39c36af85493f0f0e8d353da732d88cc492f` |
| Debug | `com.flightnotifications.app.debug` | 1.0.4-debug (6) | 35.703.516 bytes | `bfac7e97ccb655cc4641f2bf0acc9fe6d0d4752abbbac68419bf070dcdf67d5e` |

`apksigner` verificó la firma v2 de ambos APK. El certificado release SHA-256 `c08db5c00f3a02e14d656b9143c3479949a8963c39584a8e2c8e0472298d6351` coincide con la clave estable anterior. `aapt` confirmó los identificadores, `versionCode=6` y `versionName=1.0.4`; hashes de `outputs/` y G: coinciden. Compilación: `BUILD SUCCESSFUL`; `lintVital` release, `lintDebug` y `lintRelease` finalizaron sin errores. La suite automatizada de esta fuente se ejecutó después: 45/45 pruebas JVM y 24/24 instrumentadas aprobadas. No se instaló esta versión en un dispositivo físico.

- [APK release 1.0.4 para instalar](../outputs/flight-notifications-1.0.4-release.apk)
- [APK debug 1.0.4](../outputs/flight-notifications-1.0.4-debug.apk)
- [Hashes SHA-256 actuales](../outputs/SHA256SUMS.txt)
- [Log de compilación](../outputs/build-final.log)

Los archivos también están en `G:\Mi unidad\APK android car\` con los mismos nombres y hashes. Se puede instalar la release encima de la 1.0.3; el paquete, la firma y la base de datos se conservaron.

## Evidencia histórica V1.0.3

Los apartados siguientes registran la implementación y los criterios auditados al empaquetar V1.0.3. La entrega vigente y el cambio de política de avisos quedan documentados en el addendum anterior.

## Resultado histórico V1.0.3

Se implementó V1 en Kotlin/Compose con gestión de varias alertas nacionales, selector directos/escalas, Room con migración no destructiva desde la base del PoC, catálogos locales, consultas SerpApi protegidas por cuenta/cuota, búsqueda manual y programada, notificaciones locales, deduplicación, diagnóstico de WorkManager, almacenamiento AES-GCM/Android Keystore y logs saneados copiables. No se muestran horarios de vuelos y las capturas de pantalla están permitidas.

La revisión instrumentada descubrió y corrigió un efecto importante de SQLite: `INSERT OR REPLACE` de una alerta podía borrar sus resultados relacionados mediante `ON DELETE CASCADE`. Se cambió a Room `@Upsert`. La V1.0.1 guarda todas las ofertas verificadas de la última revisión exitosa, añade migración Room v2→v3 y purga huellas/mínimos al expirar, conservando el registro visible de avisos. Esta auditoría detectó que el contador local visible no se actualizaba tras las reservas hechas por el Worker mientras la pantalla seguía abierta. La V1.0.2 publica snapshots del presupuesto como `StateFlow` compartido desde `AppContainer`; cada reserva confirmada refresca el snapshot, que consume `ProbeViewModel`. La prueba instrumentada del presupuesto comprueba el contador antes y después de reservar y al reducir el límite.

En los pasos 1–3 se añadió Room v4 con una bandeja de salida persistente para avisos, de modo que una entrega interrumpida pueda recuperarse antes de iniciar otra consulta. También se amplió la cobertura del parser y de validaciones, cuotas, edición de alertas, deduplicación, concurrencia y planificación WorkManager. Estos cambios están incluidos en V1.0.3. El empaquetado no hizo llamadas reales a SerpApi. En esta ejecución se compilaron los APK sin volver a ejecutar pruebas ni Lint; los resultados automatizados indicados abajo corresponden a las ejecuciones previas sobre esta fuente.

## APKs entregados

| Variante | Identificador | Versión | Tamaño | SHA-256 |
|---|---|---:|---:|---|
| Release | `com.flightnotifications.app` | 1.0.3 (5) | 3.209.458 bytes | `218519ecb3121f823e20a42464b6bf69b4f6154dab720c1560d1c2f5ab8e7798` |
| Debug | `com.flightnotifications.app.debug` | 1.0.3-debug (5) | 35.719.900 bytes | `1d42d08d0f47cb5b035cf6ac1ccc5e78e3f122d000f47b15c103ed265bfcd14a` |

Los APK V1.0.3 y `SHA256SUMS.txt` se copiaron a `G:\Mi unidad\APK android car\`; los hashes de G: coinciden con `outputs/`. `apksigner` verifica las firmas; el certificado release SHA-256 es `c08db5c00f3a02e14d656b9143c3479949a8963c39584a8e2c8e0472298d6351`, igual al de V1.0.2. `aapt` confirma para release `com.flightnotifications.app`, `versionCode=5`, `versionName=1.0.3`. Aún no se instaló V1.0.3 sobre la instalación anterior. En Pixel 5 AVD / Android 16 (API 36), sí se actualizó V1.0.1 a V1.0.2; las pruebas de migración automatizadas verifican la apertura hasta Room v4 y la conservación de estado cubierto por sus fixtures.

Enlaces históricos de los artefactos V1.0.3:

- [APK release para instalar](../outputs/flight-notifications-1.0.3-release.apk)
- [APK debug](../outputs/flight-notifications-1.0.3-debug.apk)
- [Hashes SHA-256](../outputs/SHA256SUMS.txt)
- [Log de compilación](../outputs/build-final.log)

La evidencia siguiente corresponde a la actualización anterior V1.0.2 y no acredita la instalación de V1.0.3.

- [APK release V1.0.2](../outputs/flight-notifications-1.0.2-release.apk)
- [APK debug V1.0.2](../outputs/flight-notifications-1.0.2-debug.apk)

## Entorno y pruebas locales

- Windows; Gradle 9.6.0 con JDK 21; bytecode 17; Android SDK/compile SDK 37, Build Tools 36.0.0; `minSdk=33`, `targetSdk=37`.
- AGP 9.4.0, Kotlin 2.4.21, KSP 2.3.12, Compose BOM 2026.09.00, Room 2.8.5 y WorkManager 2.12.0.
- `:app:testDebugUnitTest`: **45 pruebas, 0 fallos, 0 errores, 0 omitidas** (20-10-2026). Incluye parser/estados de SerpApi, respuestas HTTP simuladas, límites y reservas concurrentes, validación de vuelos y alertas, precios, deduplicación, entrega/recuperación de avisos y política de reintento. La regresión añadida verifica que una tarifa igual puede notificarse en una revisión distinta y que reintentar el mismo ID de revisión sigue siendo idempotente.
- `:app:connectedDebugAndroidTest`: **24 pruebas, 0 fallos, 0 errores** en Pixel 5 AVD / Android 16 (API 36). Incluye CRUD Compose de crear/editar/eliminar preservando el ejemplo, Room, carreras de duplicados, retención de 100 avisos, bandeja de salida, migraciones v1→v4 y v2→v4, presupuesto concurrente, scheduler WorkManager y Android Keystore.
- `:app:lintDebug` y `:app:lintRelease`: ambas tareas finalizaron correctamente sobre esta fuente, sin errores de Lint. Gradle reporta avisos de APIs/declaraciones deprecadas; no impiden la compilación.
- Suite instrumentada: se completó en `Pixel_5(AVD) - 16`. Una primera compilación incremental tuvo una colisión de assets generados; tras limpiar los artefactos de build y repetir la ejecución, los 24 casos pasaron sin modificar la configuración de fuentes.
- Se abrió la release actualizada en el AVD y se revisó la pantalla inicial con una captura: tema claro, español y sin horas de vuelos.

Las pruebas automáticas de red usan MockWebServer y respuestas sintéticas; no consumen cuota del proveedor. El build, las pruebas JVM, la suite instrumentada y Lint no iniciaron GET reales. Como evidencia histórica separada, el propietario compartió una prueba manual ejecutada el 20-10-2026 con la app entregada como V1.0.2: Account API HTTP 200 / Active; dos GET de vuelos HTTP 200 para CTG–CLO, 2027-01-25/2027-02-03, directos; respuesta de ida con 3 opciones y 3 tokens; respuesta asociada de regreso con 1 opción válida; ambas seleccionadas Round trip, precios iguales y resultado VERIFICADO por COP 599.350. La app contó exactamente 2 GET de vuelos en esa tanda, con 198 disponibles antes y 196 después (4/200 acumulados localmente). SerpApi mostraba uso mensual 6/250 y capacidad 244; la diferencia de dos búsquedas frente al contador local corresponde a uso que ese contador local no registra y no se atribuye a esta tanda. El precio coincide con COP 599.350 de JetSMART en la captura manual previa de Google Flights para las mismas fechas; esto confirma una observación, no disponibilidad al pagar ni cobertura exhaustiva. No se compartió ni conserva el JSON crudo o el token.

La captura de conexión sitúa la respuesta de Account API a las 08:10:57 COT, mientras el preflight y la tarjeta de cuenta indican comprobación a las 08:11:57 COT. El código consulta y registra Account API antes de cada tanda. El extracto copiado solo muestra la comprobación de las 08:10:57; por el resultado y la hora de comprobación, probablemente se copió antes de la tanda o quedó fuera su cola. Esto no indica un rechazo del API ni demuestra que se omitiera el preflight. Copiar el registro completo después de una próxima prueba permitiría comprobar esa traza de punta a punta.

## Trazabilidad T01–T33

“Parcial” significa que el código existe y algunos casos están comprobados, pero no se ejecutó toda la matriz descrita en el plan. Las pruebas offline no se presentan como validación con SerpApi real.

| ID | Estado | Evidencia / pendiente principal |
|---|---|---|
| T01 | Parcial | Parser prueba las formas de lista, campos/estados de error y opciones malformadas; falta agotar todas las variantes del proveedor. |
| T02 | Aprobado | Fixture Round trip asociado con precio igual: total se usa una sola vez. |
| T03 | Parcial | Cubre token ausente, respuesta de error, precio fraccionario, cero/negativo y desbordamiento; faltan matriz completa de moneda y parámetros reflejados. |
| T04 | Parcial | Filtro directo y fechas probados; no toda la matriz ida/regreso con escalas. |
| T05 | Parcial | Orden, aeropuerto/fecha y regreso posterior cubiertos; faltan pruebas exhaustivas de medianoche y zonas. |
| T06 | Parcial | Candidatas diversas, 1+K, cuota insuficiente y fallo parcial probados; falta la matriz completa de topes/alternativas. |
| T07 | Parcial | Operadores desconocidos sobreviven al parseo; el detalle ya guarda/muestra todas las ofertas verificadas de la última revisión. Falta agotar la matriz de mezcla/comercializador y actualizar catálogo en una instalación con datos. |
| T08 | Parcial | Se rechazan enlaces inseguros y marcadores secretos; no se probó el flujo de apertura en todos los hosts. |
| T09 | Aprobado | Igual al máximo es elegible; precio o máximo no positivo se rechaza. |
| T10 | Parcial | DAO prueba idempotencia/fingerprint, mínimo persistido, limpieza y retención; falta ejecutar la secuencia explícita de diez revisiones con todos los precios. |
| T11 | Parcial | Política pura selecciona la oferta elegible más barata y resuelve empates de forma estable; falta integración completa con varias ofertas y Android NotificationManager. |
| T12 | Parcial | Coordinador simula proceso interrumpido, permiso revocado entre cola y publicación, duplicados concurrentes y elemento obsoleto; falta revocación real del permiso del sistema durante el despacho. |
| T13 | Parcial | Validator cubre campos y frecuencias; Compose instrumentado crea, edita y elimina una alerta y conserva el ejemplo. Falta recorrer cada error/campo y activación con permiso desde la UI. |
| T14 | Parcial | Pruebas Room cubren nombre/frecuencia, máximo, ruta, fechas, directos y pausa/reactivación; falta completar todas las combinaciones y confirmar conservación de huellas en cada una. |
| T15 | Aprobado | Intento fallido nuevo conserva tarifa anterior, importe, oferta y criterios originales en Room. |
| T16 | Parcial | Prueba Room mantiene 100 avisos, huellas y mínimo; limpiar conserva deduplicación y expirar purga solo lo debido. Falta verificar el ciclo tras reinicio con base persistente y borrar alerta sin tocar presupuesto. |
| T17 | Parcial | Pruebas cubren HTTP 401/403/429/4xx/5xx y campos críticos malformados/negativos; falta completar todos los límites oficiales y revisar cada ruta de log. |
| T18 | Parcial | Cuenta inactiva/ inválida y errores bloquean búsquedas; falta probar explícitamente saldo en caché seguido de fallo y recuperación con una cuenta nueva. |
| T19 | Parcial | Reserva por cada GET, concurrencia del presupuesto y serialización de preflight están cubiertas; timeout conserva reserva y no reintenta; falta la matriz completa de justicia y reducción de capacidad por lote. |
| T20 | Parcial | Cambios de reloj, reinicio simulado y ventana probados; no se validó el teléfono físico apagado. |
| T21 | Parcial | Repository descarta un resultado cuya versión de criterios quedó obsoleta tras editar; falta prueba integrada de manual y Worker simultáneos y de eliminar durante una petición HTTP suspendida. |
| T22 | Parcial | Outbox persistente, commits atómicos y coordinador cubren recuperación simulada tras publicar, permisos revocados y concurrencia; falta matar el proceso Android real entre publicación y commit y comprobar recuperación del sistema. |
| T23 | Parcial | Respuestas HTTP de error no se reintentan y se cubren 401/403/429/4xx/5xx; faltan desconexión/timeout y todas las excepciones de red sin fuga. |
| T24 | Parcial | WorkManager Test confirma un único coordinador periódico conectado; política de retry respeta frecuencia. Falta instrumentar el Worker completo con fakes, sin alertas debidas y backoff/reintento de punta a punta. |
| T25 | No ejecutado | Requiere teléfono físico: app cerrada normalmente, bloqueo, Doze, red y reinicio. Force-stop no se declara probado. |
| T26 | Parcial | Instrumentación Room valida v1→v4 y v2→v4, presupuesto/reloj, resultados y outbox; falta actualizar sobre una instalación real previa que tenga credencial y confirmar su ciclo completo. |
| T27 | Parcial | DAO comprueba semilla única y no recreación al borrar; el CRUD Compose conserva el ejemplo; falta instalar, borrar, reiniciar y actualizar en el dispositivo objetivo. |
| T28 | Parcial | Keystore cifra/lee/reemplaza/elimina; falta auditoría exhaustiva del APK/logcat para cada error HTTP. |
| T29 | Parcial | Compose instrumentado cubre pantalla inicial y CRUD; faltan accesibilidad, rotación, fuentes ampliadas, contraste y estados completos. Capturas permitidas. |
| T30 | Parcial | V1.0.3 debug/release compiladas; firma release, certificado estable y metadatos ascendentes verificados. Falta instalar V1.0.3 sobre una instalación previa y comprobar conservación de datos. |
| T31 | Parcial | Catálogos empaquetados con fuentes/fecha y operadores desconocidos; falta actualizar APK con datos ya creados. |
| T32 | Aprobado | Prueba real V1.0.2 del propietario: 2 GET de vuelos HTTP 200 y resultado `VERIFICADO`; los 2 GET locales de esa tanda y el uso mensual informado se distinguen. No se atribuyen los otros 2 usos de cuenta ni se confunden fixtures con API. |
| T33 | Aprobado | README, informe de estado, APKs V1.0.3, SHA-256 y enlaces descargables están disponibles; copias en G: cotejadas con `outputs/`. Los casos pendientes de aceptación se detallan por separado en esta matriz. |

## Pendiente para cerrar la aceptación en uso real

El propietario ya ejecutó una búsqueda manual real con V1.0.2 y confirmó una tarifa de ida/vuelta para el caso compartido. Queda verificar en el teléfono que una actualización conserve los datos previos y observar WorkManager con la app cerrada normalmente, pantalla apagada/bloqueada y tras reinicio; Doze, ahorro de batería y detención forzada pueden aplazar o detener el trabajo según Android/fabricante. La búsqueda manual no demuestra la ejecución automática en segundo plano.

## Resultado de la auditoría del documento maestro §17

| Criterio | Estado | Evidencia o límite |
|---|---|---|
| Proyecto/Gradle (1) | Parcial | Compilación, 44 pruebas JVM, 24 instrumentadas y LintDebug pasan en esta fuente; LintRelease no se volvió a ejecutar y falta abrir/sincronizar el proyecto desde Android Studio. |
| APK, firma y actualización (2) | Aprobado | 1.0.2 debug/release, firma estable, G: y actualización 1.0.1→1.0.2 comprobadas en AVD. El teléfono físico no se probó. |
| Clave, Keystore, cuenta y ausencia de secretos (3) | Parcial | Flujo/cifrado/Account API implementados y Keystore probado; no se auditó exhaustivamente Logcat de todos los errores HTTP ni cada superficie del APK. |
| JSON real y `departure_token` (4) | Aprobado para un escenario | La prueba real V1.0.2 produjo 3 tokens de ida, una opción asociada válida de regreso, ambas opciones Round trip y el mismo total COP 599.350. No hay JSON crudo; solo se exploró una salida candidata. |
| Directos, escalas, fechas y tarifa (5) | Parcial | Validador implementado; la cobertura automatizada de ambos sentidos, conexiones y bordes de fechas/precio no cubre toda la matriz. |
| Rutas nacionales (6) | Parcial | Catálogo colombiano y validación están implementados; falta la matriz de rechazo/identidad de aeropuertos como prueba automatizada completa. |
| Operadores y catálogo (7) | Parcial | Desconocidos y todas las ofertas verificadas se muestran; faltan combinaciones de operadores y prueba de actualización conservando datos. |
| Advertencias sobre precios observados (8) | Aprobado | README y UI advierten que tarifa observada no garantiza compra, equipaje ni cupos múltiples. |
| CRUD, frecuencia y programación (9) | Parcial | Código/UI presentes; pruebas instrumentadas no recorren todo el CRUD ni escenarios de ejecución real. |
| Efectos de edición (10) | Parcial | Versiones de criterios/política y dato previo implementados; falta la tabla completa de casos automatizados. |
| Cuenta y presupuesto (11) | Parcial | La cuenta real devolvió Active/capacidad 244 y la tanda reservó 2 de los 198 GET locales disponibles; la matriz de errores, concurrencia y recuperación es incompleta y falta confirmar la línea de Account API del preflight en el log copiado. |
| Política de avisos (12) | Parcial | Selector barato y estable, deduplicación y recuperación simulada pasan; falta notificación OS con varias ofertas y permiso revocado en vivo. |
| Resultado, avisos y retención (13) | Parcial | Resultado, ofertas y dedup persisten; retención de 100 está probada en Room, falta ciclo persistente de purga tras reinicio real. |
| Ejemplo inicial (14) | Parcial | Semilla única, pausada y migración probadas; falta repetir ciclo completo en instalación física limpia, borrado y reinicio. |
| WorkManager en teléfono físico (15) | No ejecutado | Pendiente en teléfono del propietario: pantalla apagada, cierre, red, Doze y reinicio. |
| Pruebas offline completas (16) | Parcial | 44 JVM y 24 instrumentadas pasan; la matriz T01–T33 marca explícitamente los escenarios que aún faltan. |
| README (17) | Aprobado | Incluye entorno, compilación, instalación, permisos, cuota, seguridad y limitaciones. |
| Informe, conteo y pendientes (18) | Aprobado | Este informe separa build/pruebas automáticas (0 GET reales) de la prueba manual V1.0.2 (2 GET de vuelos) y registra verificaciones restantes. |

El APK V1.0.2 incluye las correcciones previas de ofertas/expiración y el snapshot visible del presupuesto, y tiene una prueba manual aprobada para un itinerario. V1.0.3 incorpora Room v4 y los pasos 1–3 completados, y ya está empaquetada y entregada. **No significa que toda la definición de aceptación esté completada**: quedan casos de matriz y validaciones físicas de WorkManager, además de instalar V1.0.3 como actualización y confirmar los datos conservados.
