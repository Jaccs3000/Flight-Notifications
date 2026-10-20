# Plan técnico de la app definitiva de alertas de vuelos

**Versión del plan:** 1.2, auditada y delimitada al alcance aprobado el 19 de octubre de 2026.  
**Proyecto:** `C:\Flight-Notifications-main`.  
**Producto:** aplicación Android personal en español, distribuida como APK.  
**Estado:** implementación funcional V1. La fuente Room v4 está empaquetada como V1.0.4 (`versionCode=6`); debug/release y hashes se copiaron a la ruta de entrega y se verificó la firma estable. La instalación de V1.0.4 sobre una instalación previa y la aceptación en teléfono físico siguen pendientes; véase [informe de implementación](docs/INFORME_IMPLEMENTACION_V1.md).  
**Requisitos funcionales:** [REQUIREMENTS.md](REQUIREMENTS.md), V1.4, con las instrucciones posteriores del propietario indicadas en la sección 1.

Este documento concreta la implementación paso a paso y los criterios para declarar terminada la V1. Las secciones 3–11 contienen las reglas; los pasos de la sección 12 las convierten en entregables y la sección 13 define cómo comprobarlas. Un criterio de aceptación pendiente no equivale a una función ya implementada.

## 1. Fuentes, precedencia y alcance de la revisión

1. Las instrucciones directas y posteriores del propietario prevalecen. Para V1, los horarios no son un criterio comercial ni un detalle que interese comparar en la interfaz; las capturas de pantalla están permitidas; cada APK se guarda en `G:\Mi unidad\APK android car\`. La instrucción posterior de avisar en cada revisión la tarifa verificada más baja, aunque se haya notificado antes el mismo itinerario o importe, prevalece sobre la regla V1.4 de mejora estricta.
2. `REQUIREMENTS.md` gobierna las decisiones funcionales restantes, especialmente sus secciones 4 (precio), 5.1 (ediciones), 5.4 (avisos), 7 (cuota) y 17 (entrega).
3. Este plan define el procedimiento técnico. Los hechos sobre implementación se contrastan con el código y los reportes; los planes históricos no demuestran ejecución.
4. [PLAN_PRIMERA_APP_SERPAPI.md](PLAN_PRIMERA_APP_SERPAPI.md) describe el PoC. [docs/INFORME_PRUEBAS_POC.md](docs/INFORME_PRUEBAS_POC.md), [docs/ENTORNO_Y_VERSIONES.md](docs/ENTORNO_Y_VERSIONES.md) y [docs/SEMANTICA_PRECIO_SERPAPI.md](docs/SEMANTICA_PRECIO_SERPAPI.md) aportan contexto y evidencia con el alcance señalado en la sección 2.

El propietario autorizó la implementación completa después de aprobar este plan. Las pruebas locales e instalación en el emulador están dentro de ese alcance. En esta ejecución no se ingresó la clave del propietario ni se iniciaron nuevas llamadas reales a SerpApi; las pruebas automáticas sustituyen los endpoints. Las revisiones manuales/automáticas en el teléfono del usuario sólo comienzan tras su acción explícita en la app.

**Contradicciones históricas encontradas al auditar el plan:** el documento maestro y el README describían el producto como no implementado y el informe del PoC como la versión actual. Esas frases preceden a la autorización y a los cambios de V1. La app y la documentación principal se actualizaron; los informes del PoC se conservan como evidencia histórica. V1 no muestra horarios de vuelos. Los segmentos/horas siguen en los datos internos para validación temporal e identidad del itinerario.

### 1.1. Control del alcance

Este plan desarrolla el alcance aprobado; **no autoriza ampliarlo**. Una función, pantalla, integración o costo nuevo necesita una instrucción expresa del propietario antes de incorporarse. Una recomendación técnica no se convierte por su redacción en una función nueva ni en un requisito adicional de entrega.

| Elemento del plan | Límite y origen |
|---|---|
| Gestión multi-alerta y búsqueda manual/programada | Funciones aprobadas en REQUIREMENTS.md §§1–2, 5–6 y 9 |
| Precio total verificado, directos/escalas, operadores y cuota | Reglas aprobadas en §§4–7; se conserva la estrategia conservadora probada |
| Último resultado, avisos e idempotencia de despacho | Persistencia mínima aprobada en §§5 y 10; no crea historial de precios |
| Catálogo informativo de aerolíneas | Aprobado en §4.1; se entrega con la APK, sin actualización remota |
| Datos de aeropuertos, versiones de criterios y estado interno de despacho | Medios técnicos para validar país/fechas, impedir resultados obsoletos y reintentar el mismo evento sin duplicarlo; no añaden herramientas de administración ni pantallas nuevas |
| Diagnóstico de programación | Panel simple aprobado en §6 y §9.5; puede integrarse en Configuración |
| Textareas de cuenta/vuelos y capturas habilitadas | Soporte solicitado directamente por el propietario, temporal y acotado |
| Kotlin, Room, WorkManager, firma y APK debug/release | Plataforma y entrega aprobadas en §§3, 17 y 18.5 |

Elegir la implementación más sencilla que cumpla esas reglas. Tablas auxiliares, contadores de versión, composición de dependencias y recuperación de interrupciones descritos después son propuestas internas; se admite una solución equivalente más simple que preserve el comportamiento aprobado. No exigir frameworks, servicios ni subsistemas adicionales por seguir literalmente un ejemplo de diseño.

La matriz T01–T33 verifica estas funciones y sus errores; **no amplía el producto**. Una comprobación técnica aplicable sólo a una solución no elegida se adapta al mecanismo usado. Mejoras futuras no solicitadas se omiten de V1 y no bloquean su entrega.

## 2. Estado real y evidencia disponible

### 2.1. Código y pruebas locales

La implementación actual incluye la pantalla del PoC para diagnóstico, CRUD de alertas nacionales, Room con migraciones v1→v4, catálogo de aeropuertos y operadores, presupuesto compartido, búsquedas manuales y por lote, WorkManager único, notificaciones locales con reintento idempotente por evento, diagnóstico de programación, almacenamiento de credencial con Keystore y dos registros saneados con copiar/limpiar. En cada revisión avisa una vez la tarifa verificada más baja bajo el máximo, incluso si se repite un itinerario o importe; los IDs de evento solo evitan duplicar un mismo despacho al recuperarlo. No muestra horarios de vuelos. Los APK V1.0.4 firmados/copiados están verificados; la evidencia completa y los límites de aceptación están en el informe V1.

| Comprobación existente | Evidencia local o procedencia | Alcance |
|---|---|---|
| Pruebas JVM: 45, sin fallos ni errores ni omisiones | XML en `app/build/test-results/testDebugUnitTest/`, ejecución 20-10-2026 | Parser, HTTP simulado, validación, cuotas, alertas, deduplicación, recuperación y concurrencia; incluye regresión de repetir tarifa en nueva revisión con idempotencia por revisión |
| Pruebas instrumentadas: 24, sin fallos ni errores | XML en `app/build/outputs/androidTest-results/connected/debug/TEST-Pixel_5(AVD) - 16.xml`, 20-10-2026 COT | Compose/CRUD, Room v1→v4, presupuesto concurrente, outbox, WorkManager y Keystore; emulador Pixel 5, API 36 |
| Compilación y Lint debug/release | `outputs/build-final.log` y ejecución Gradle del 20-10-2026 | `lintDebug` y `lintRelease` aprobados sin errores; Gradle mantiene avisos de APIs/declaraciones deprecadas |
| Migración y persistencia | `FlightAlertMigrationTest` y `FlightAlertDaoTest` | v1→v4 conserva presupuesto/reloj; una falla conserva precio previo, opciones verificadas y criterios; no resembra ejemplo y mantiene idempotencia de eventos |
| Instalación/update del release | `docs/INFORME_IMPLEMENTACION_V1.md` | 1.0.1→1.0.2 actualizado en AVD. Para 1.0.4 se verificaron firma estable y versionCode ascendente; falta instalar esta versión sobre la anterior |
| Credencial y capturas | `KeystoreCredentialStore.kt`, manifest y `MainActivity.kt` | AES-GCM/Keystore, backup desactivado, capturas permitidas |

Las pruebas se ejecutaron sobre la fuente de V1.0.4: 45 pruebas JVM y 24 instrumentadas en Pixel 5 AVD / API 36, todas aprobadas; también pasaron `lintDebug` y `lintRelease`. La regresión comprueba la nueva preferencia de repetir el mismo precio en una revisión distinta sin duplicar reintentos del mismo evento. Esta suite usa respuestas simuladas y no valida llamadas reales adicionales a SerpApi.

Las pruebas instrumentadas informan fixtures locales y cero consultas de vuelos a SerpApi durante la implementación. No prueban Doze/reinicio en el teléfono del propietario; ese escenario requiere su dispositivo físico y queda explícito en el informe V1.

### 2.2. Ejecución real aportada por el propietario

Prueba observada el **19 de octubre de 2026**, búsqueda **CTG–CLO, ida 2027-01-25 y regreso 2027-02-03**, un adulto, económica, COP, sólo directos:

- Account API devolvió HTTP 200 y estado `Active`; la capacidad consultada antes del lote fue 248. Es un saldo histórico, no el saldo actual.
- Hubo **dos GET de cuenta** en el fragmento compartido: uno al probar conexión y otro antes del lote. La prueba inició **dos GET de vuelos**, ambos HTTP 200.
- Se devolvieron 3 opciones en la primera respuesta y 3 en la segunda, sin opciones malformadas; el log informa 3 regresos válidos por ruta/fecha y 3 aerolíneas detectadas. Sólo se solicitó el regreso de **una salida candidata**; esto no equivale a explorar tres salidas.
- El verificador registró `Round trip` en ambas opciones seleccionadas, precios presentes e iguales, y estado `VERIFICADO`. La app mostró **COP 599.350 una sola vez** como total observado.
- La captura manual de Google Flights mostró **COP 599.350 ida y vuelta** para la opción JetSMART de ida correspondiente. Confirma la coincidencia observada del importe; no acredita el regreso manualmente seleccionado, todas las combinaciones, equipaje ni disponibilidad al pagar.

Las evidencias son los logs y las capturas compartidos. No se conserva aquí un JSON real completo ni un token real; los fixtures del proyecto son sintéticos. El caso basta para continuar el desarrollo sobre la regla conservadora existente, con sus límites. Nuevos formatos no demostrados deben permanecer sin verificar.

Las ejecuciones anteriores con `NO_VERIFICADO` reflejan el estado y las comprobaciones del PoC de entonces; no prueban por sí mismas que la API rechazara la clave o devolviera una tarifa incorrecta. El historial de pruebas incluye también el caso de marzo documentado en el archivo de semántica. No se calcula un consumo total histórico de la cuenta a partir de estos fragmentos.

### 2.3. Prueba real manual reportada para V1.0.2

El propietario compartió el **20 de octubre de 2026** la ejecución manual de CTG–CLO, ida **2027-01-25**, regreso **2027-02-03**, un adulto, económica, COP y vuelos directos:

- Account API informó HTTP 200 y estado `Active`. Antes de la tanda el log registra capacidad oficial 244, presupuesto local restante 198 y reloj confiable.
- La tanda inició los **2 GET de vuelos previstos**, ida y regreso; ambos respondieron HTTP 200. La ida tardó 7.896 ms y devolvió 3 opciones con token; la búsqueda de regreso asociado tardó 11.496 ms y devolvió 1 opción válida.
- Las opciones seleccionadas se validaron como `Round trip`; ambos precios estaban presentes e iguales. La app declaró `VERIFICADO` y mostró **COP 599.350 una vez** como tarifa observada de ida y vuelta. Coincide con la opción JetSMART de COP 599.350 de la captura manual previa de Google Flights para las mismas fechas. El propietario indicó que no necesita comparar horarios.
- Tras la tanda la interfaz muestra 4/200 GET locales usados (196 disponibles), coherente con 2 reservas anteriores más estas 2. La cuenta de SerpApi indica 6/250 usos mensuales y capacidad 244; dos búsquedas de cuenta respecto del registro local no se atribuyen a esta tanda ni a la app sin más evidencia.
- El log compartido de conexión muestra una comprobación a las 08:10:57 COT, mientras el preflight y la tarjeta de cuenta marcan 08:11:57. `runRoundTrip` hace una nueva consulta Account API y el repositorio la registra, pero el fragmento copiado no contiene esa segunda entrada. La prueba confirma el resultado funcional y deja pendiente cerrar esa discrepancia de diagnóstico con el log completo posterior a la tanda.

Este es un caso real satisfactorio, no una verificación de todas las salidas ni de la cobertura del mercado. Solo se consultó el regreso de una salida elegida; no se compartió ni se conserva JSON crudo o `departure_token`. La tarifa puede cambiar al comprar y el test manual no valida ejecución automática de WorkManager.

## 3. Alcance fijo de V1 y configuración inicial

- Kotlin nativo; Android 13+ (`minSdk 33`); Compose y Material 3; español con formato es-CO; tema claro permanente.
- Aplicación local para datos, ajustes y notificaciones. Las consultas HTTPS salen del teléfono a SerpApi; sin backend, Firebase, telemetría, publicidad, login ni sincronización.
- Múltiples alertas para rutas entre aeropuertos de Colombia; ida y vuelta, fechas exactas, un adulto, económica y COP.
- Campos por alerta: nombre, origen, destino, ida, regreso, máximo total COP, frecuencia **6/8/12/24 h**, estado y checkbox **“Solo vuelos directos”**. Frecuencia inicial 24 h; checkbox inicialmente marcado.
- Todas las aerolíneas devueltas por la fuente, incluso desconocidas o combinadas. Catálogo informativo separado, versionado con la APK.
- Un último resultado por alerta, con último total verificado e instante original si existe; lista visible de hasta 100 avisos; ID persistente solo para reintentar idempotentemente un mismo despacho pendiente. Sin series de precios ni gráficos.
- Búsqueda manual y automática con cuota global compartida. Como máximo un aviso por alerta y revisión, sólo por oferta elegible según la sección 9.
- Sin compra, reserva, pago, consulta de pasajeros familiares, multiplicación del precio por ocho, fuentes de otras aerolíneas, vuelos internacionales, solo ida, multidestino ni fechas flexibles automáticas.

**Ejemplo de instalación nueva:** “Cartagena ↔ Cali — marzo 2027”; CTG–CLO; 2027-03-25 / 2027-03-28; máximo COP 400.000; un adulto; económica; directos; frecuencia 24 h; **pausada**. Se crea una vez y no reaparece al eliminarlo, abrir, reiniciar o actualizar.

**Fechas de prueba del PoC:** 2027-01-25 / 2027-02-03. Su precarga solicitada sirvió para comparar la API; no cambia el ejemplo definitivo ni debe sobrescribir criterios futuros de alertas.

## 4. Arquitectura y entorno reproducible

### 4.1. Capas y composición

Usar MVVM, repositorios, corrutinas y una composición de dependencias compartida por UI y Worker:

```text
presentation / Compose + ViewModels
    → domain / criterios, validadores, casos de uso
        → repositories
            → data/local / Room y DataStore
            → data/remote / Retrofit, OkHttp, kotlinx.serialization
        → review coordinator / manual y WorkManager
        → notification / preparación, despacho e idempotencia por evento
security / credencial cifrada      diagnostics / eventos saneados
```

Reutilizar `SerpApiRepository`, `SerpApiJsonParser`, `FlightOptionValidator`, `LocalBudgetRepository`, `CredentialStore` y `DiagnosticLog`, extrayendo sus responsabilidades y quitando las restricciones propias de CTG–CLO donde corresponda. La integración ya está en un repositorio; no se debe describir como lógica HTTP incrustada en Compose.

La UI no decide precios ni hace HTTP. Los casos de uso reciben criterios inmutables; devuelven resultados normalizados. Inyectar reloj, cliente HTTP, presupuesto y despachador de avisos para pruebas offline. Un coordinador singleton del proceso conecta los disparadores manual y programado; Room protege reservas y eventos mediante transacciones e índices únicos.

### 4.2. Matriz local comprobada

| Componente | Configuración existente |
|---|---|
| JDK que ejecuta Gradle | 21, JBR de Android Studio en el entorno registrado |
| Java/Kotlin bytecode | 17; distinto del JDK que ejecuta Gradle |
| Gradle Wrapper / AGP | 9.6.0 / 9.4.0 |
| Kotlin / KSP | 2.4.21 / 2.3.12 |
| Compose BOM / Activity / Lifecycle | 2026.09.00 / 1.13.0 / 2.11.0 |
| Room / DataStore | 2.8.5 / 1.2.1 |
| Retrofit / OkHttp | 3.0.0 / 5.3.0 |
| kotlinx.serialization / coroutines | 1.11.0 / 1.11.0 |
| JUnit / AndroidX Test runner / Test JUnit | 4.13.2 / 1.7.0 / 1.3.0 |
| SDK mínimo / compilación / destino | 33 / 37 / 37 |
| SDK local registrado | Platform 37 y Build Tools 36.0.0 |
| WorkManager / pruebas de Worker / migraciones | WorkManager y `work-testing` 2.12.0; Room testing y `MigrationTestHelper` 2.8.5; migraciones v1→v3 declaradas y probadas |

La fuente primaria de versiones es `gradle/libs.versions.toml`, el wrapper y `app/build.gradle.kts`. Se trata de una matriz que compiló localmente, sin afirmar en esta revisión que cada componente siga siendo la última versión estable. Antes de cambiarla, revisar documentación oficial y compatibilidad; evitar actualizaciones masivas junto a la refactorización.

## 5. Datos persistidos y migración desde el PoC

### 5.1. Esquema lógico

Los nombres y la separación en tablas son propuestas técnicas. Conservar los datos funcionales exigidos en REQUIREMENTS.md §10 y añadir sólo el estado operativo mínimo necesario; no es obligatorio crear una tabla por cada fila de esta descripción.

| Entidad / estado | Campos y reglas principales |
|---|---|
| `AlertEntity` | UUID, nombre, IATA origen/destino, fechas ISO, `maxTotalCop: Long`, frecuencia, `directOnly`, `isEnabled`, estado funcional, `createdAt`, `updatedAt`, `lastAttemptAt`, último intento manual/automático, `nextDueAt`, próximo reintento y versiones de búsqueda/política de avisos |
| `LastResultEntity` | PK/FK `alertId`; estado/error de último intento, `lastAttemptAt`, momento de recepción; bloque del último total verificado con `verifiedAt`, COP, ida/regreso normalizados, escalas, enlace y criterios originales. `verifiedOffersJson` guarda las opciones de la última revisión exitosa para mostrar alternativas; una falla conserva el último bloque como dato previo, sin guardar historial de consultas |
| Resumen de última búsqueda | Operadores detectados y opciones verificadas recibidas, candidatos consultados/disponibles, `explorationLimited` y motivo. El detalle muestra todas las opciones verificadas, no solo la más barata que puede generar aviso |
| `SentNotificationEntity` | ID de evento, alerta, total, instante de despacho y resumen; conservar globalmente las últimas 100 enviadas |
| `NotifiedFingerprintEntity` | En la versión actual, clave única por ID de despacho para confirmar/reintentar el mismo evento; no deduplica tarifas futuras |
| Estado legado de precio | Room puede conservar mínimos por itinerario de versiones anteriores por compatibilidad; no se consultan para decidir nuevos avisos |
| Reservas de presupuesto | Reutilizar registros de GET y observación del reloj del PoC; sin tarifas, credenciales ni payload |
| Estado operativo de despacho, si se utiliza | Estado mínimo de la propuesta de sección 9.3 o mecanismo equivalente; sin cola visible, reenvío manual ni registro de cotizaciones |
| Metadatos de instalación | Fila Room con marca de inicialización del ejemplo, creada/transicionada en la misma transacción que el ejemplo o la migración |
| Ajustes no sensibles | DataStore: límite local y preferencias de UI; no guardar la API key aquí |

Usar índices para alertas pendientes, reservas por tiempo, eventos únicos y huellas. Definir claves foráneas y borrados en cascada para datos propios de una alerta; **eliminar una alerta no borra presupuesto global, credencial ni ajustes globales**.

### 5.2. Resultado reciente frente al último precio verificado

Una revisión con error reemplaza el estado del último intento, pero no borra el único precio previamente verificado. Conservar ese bloque en la misma fila con su instante y criterios originales: “dato previo; no confirmado en la última búsqueda”. Una nueva verificación sustituye ese bloque. No presentar un precio previo como si fuera el de la consulta fallida.

El resumen de aerolíneas corresponde a la última búsqueda efectiva y debe indicar si fue parcial. Si falla antes de recibir datos, no atribuirle operadores de una revisión anterior como si fueran recién consultados. Las opciones completas de la búsqueda actual pueden mantenerse en memoria mientras se navega, sin persistir todas las cotizaciones.

Distinguir turno previsto, inicio/fin real de ejecución y momento de observación/recepción. Si el proveedor aporta la fecha original de datos cacheados, conservarla como procedencia; si no, la recepción sólo acredita cuándo la app los obtuvo, no cuándo cambió la tarifa. Mostrar esa limitación sin inventar una fecha de actualización del precio.

### 5.3. Migración obligatoria y ejemplo inicial

La base existente es `flight_probe.db`, esquema v1, con `flight_request_reservations` y `budget_clock_observation`. Ampliarla mediante migración explícita, conservar reservas y reloj, y exportar el esquema de cada nueva versión. No cambiar de nombre la base y perder el presupuesto; no usar migración destructiva.

Mantener el archivo cifrado, alias de Keystore y formato de credencial, o implementar una migración segura comprobada. Las preferencias de fechas del PoC no deben convertirse en criterios globales ni sobreescribir alertas.

La migración desde una instalación existente marca el alta automática del ejemplo como procesada; la creación automática se reserva para una instalación nueva de la app definitiva. Crear ejemplo y marca dentro de una sola transacción Room; no repartir ese compromiso entre Room y DataStore. La ausencia del ejemplo, por sí sola, nunca decide recrearlo.

Si existiera una versión anterior de alertas sin `directOnly`, la migración debe asignar `true`. Vaciar avisos solo borra el registro visible y cierra notificaciones visibles del sistema; no bloquea avisos en revisiones futuras. Las reservas de cuota sólo se purgan al salir de su ventana, con reloj confiable.

## 6. Criterios, estados y efectos de edición

### 6.1. Validación

- Nombre obligatorio, recortado, 3–80 caracteres.
- IATA normalizado a mayúsculas; identidad y país comprobados en catálogo de aeropuertos; origen y destino distintos, ambos Colombia. Si no se puede verificar identidad/país, no aceptar el código sólo porque tenga tres letras.
- Selectores de calendario; ida no anterior a hoy en la referencia local de origen y regreso posterior a ida.
- Máximo COP entero positivo representable en `Long`; rechazar decimales, cero, negativos y desbordamiento.
- Frecuencia de la lista permitida; checkbox de directos persistido. Estimar consumo antes de guardar/activar, conforme a la sección 8.
- Parámetros fijos: un adulto, económica, COP, ida/vuelta, todas las aerolíneas.

### 6.2. Ciclo de vida y tiempos

Separar estado funcional (`ACTIVA`, `PAUSADA`, `VENCIDA`, `ERROR_CONFIGURACION`) del resultado de consulta (`VERIFICADO`, `NO_VERIFICADO`, `SIN_RESULTADOS_DEVUELTOS`, `ERROR_RED`, `LIMITE_CUOTA`, etc.) y del bloqueo de credencial (`CONFIGURAR_API_KEY`). Un 429 o falta de red no elimina alertas.

Vencer automáticamente al terminar la fecha **local** de ida; no simplemente al comenzar ese día. Durante ese día, descartar vuelos que ya salieron, usando la hora internamente sin añadir filtros por franja horaria. Usar zona del aeropuerto del catálogo; documentar la referencia si falta. Guardar fechas de viaje como fechas de calendario y observaciones/ejecuciones como instantes UTC, mostrados en zona del dispositivo. Un cambio de zona del teléfono no cambia el día contratado del viaje.

Una alerta nueva queda pausada por defecto; el usuario puede elegir activarla explícitamente si tiene clave validada y conoce el consumo. El ejemplo siempre inicia pausado. Abrir o instalar no inicia búsquedas de vuelos sin esa activación/acción explícita.

### 6.3. Tabla vinculante de ediciones

| Cambio | Última cotización | Programación y avisos |
|---|---|---|
| Nombre | Sigue siendo compatible | Actualizar texto; sin consulta ni aviso por guardar |
| Frecuencia | Sigue siendo compatible | Recalcular próximo turno; no consumir API al guardar |
| Ruta, ida/regreso o directos | Se conserva sólo como dato previo con sus criterios originales; incompatible con la búsqueda nueva | Nueva consulta y verificación antes de avisar; descartar resultados en curso de criterios anteriores |
| Máximo COP | El itinerario observado sigue siendo compatible | Exigir consulta nueva antes de avisar por el nuevo máximo; no avisar al guardar con el dato almacenado |
| Pausar | Conservar datos | Evitar siguientes GET automáticos y avisos; no recuperar una reserva de GET ya iniciado |
| Reanudar | Conservar datos con su antigüedad | Validar vigencia, clave, presupuesto y programar; no avisar desde caché local |
| Eliminar | Eliminar datos de esa alerta según política de FK | Cancelar su participación y sus eventos pendientes; confirmar en UI; conservar cuota global |

La búsqueda manual es posible en una alerta pausada y no la reactiva; por estar pausada no emite un aviso de oferta. En vencidas, impedir GET inútiles. Los resultados de una ejecución antigua no deben sobrescribir datos de criterios nuevos: comprobar ID/versión antes de persistir y despachar. Cada revisión nueva puede volver a avisar la tarifa más baja bajo el máximo; no hay filtro por precio notificado anteriormente.

Una opción interna es separar `searchCriteriaVersion` (ruta, fechas, directos) de `notificationPolicyVersion` (máximo y cambios de activación); también sirve una comparación segura de los valores capturados. Nombre/frecuencia no cambian la compatibilidad de búsqueda. Un máximo cambiado durante HTTP permite conservar el resultado como itinerario compatible, pero no autoriza avisarlo hasta una consulta iniciada bajo la nueva política. Pausar/reanudar no revive eventos pendientes antiguos. No usar un simple `updatedAt` de cualquier edición para invalidar indiscriminadamente cotizaciones. `isEnabled` representa la decisión del usuario; el estado efectivo además refleja vencimiento o configuración inválida, sin reactivar por sí solo esa decisión.

## 7. Contrato de búsqueda y verificación del precio

### 7.1. Solicitudes y transporte

Cuenta: `GET https://serpapi.com/account.json`. Vuelos: `GET https://serpapi.com/search.json`. La clave va como parámetro secreto de la petición, nunca en enlaces externos ni logs.

| Parámetro de vuelos | Valor |
|---|---|
| `engine` | `google_flights` |
| `departure_id` / `arrival_id` | Origen/destino de la alerta |
| `outbound_date` / `return_date` | Fechas ISO exactas |
| `type` / `adults` / `travel_class` | `1` / `1` / `1` |
| `currency` / `gl` / `hl` | `COP` / `co` / `es` |
| `sort_by` | `2`, precio |
| `stops` | `1` sólo directos; `0` permite escalas |
| `departure_token` | Sólo en segunda etapa, correspondiente a la salida seleccionada |

No enviar `include_airlines`, `exclude_airlines` ni `max_price`. No forzar `no_cache=true`; la caché del proveedor puede producir desfase. `show_hidden` y `deep_search` quedan desactivados por defecto y sólo se evalúan si su beneficio y costo se justifican; no son garantía de cobertura total. No agregar consultas de booking, polling o redirecciones/reintentos ocultos.

Mantener límites de timeout y cancelación explícitos y la configuración existente sin reintento automático de conexión. Una respuesta HTTP 200 requiere además comprobar `search_metadata.status`, campo `error`, JSON y parámetros reflejados cuando existan. Distinguir resultado vacío válido de estructura ausente/corrupta; no etiquetar fallo de parser como inexistencia de vuelos.

### 7.2. Revisión y candidatos

1. Bajo el coordinador global, validar clave, vigencia y versión de criterios; consultar cuenta para el lote y comprobar presupuesto.
2. Reservar un GET antes de la búsqueda inicial. Leer `best_flights` y `other_flights`; tolerar una sección ausente sin perder la otra y no aceptar opciones malformadas.
3. Validar salidas por ruta, fecha local, secuencia y directos/escalas. Formar candidatos con token; equilibrar precio y diversidad de operadores, con rotación persistida mínima entre revisiones.
4. Para hasta **tres salidas candidatas**, según capacidad y justicia entre alertas, reservar un GET y consultar con el token. Repetir los mismos parámetros de la búsqueda inicial, sin invertir origen/destino. En el resultado, validar la dirección real de regreso.
5. Para cada candidata consultada, evaluar **todas sus opciones de regreso** válidas y verificables que ya llegaron en esa respuesta; eso no requiere un GET por cada regreso devuelto.
6. Intentar otra salida cuando la primera no produzca cotización válida, supere el máximo o no sea elegible por duplicación. Se admite terminar antes con una oferta elegible para aviso, rotulando la exploración parcial. Seleccionar conforme a la sección 9 entre lo realmente comprobado.
7. Guardar el resultado y los conteos de exploración, sin afirmar que se agotaron todas las opciones o compañías.

**Costo ordinario:** una búsqueda inicial compartida + una búsqueda de regreso por salida candidata. Para K candidatas: **1 + K GET**, normalmente **2–4 GET** para K=1–3; puede terminar en 1 si no hay salida/token. No repetir el GET inicial para cada candidata. No iniciar una revisión nueva si ya se sabe que no queda capacidad para la secuencia mínima de dos GET; si se interrumpe después, registrar parcialidad y el gasto iniciado.

### 7.3. Ruta, segmentos y precio

Validar primer/último aeropuerto, fecha local de salida de cada dirección, continuidad de conexiones, orden temporal y regreso posterior a llegada de ida. Permitir cruce de medianoche cuando la secuencia sea válida. Con directos, exigir un único tramo aéreo en cada dirección y cero escalas; con conexiones permitidas, contar y mostrar escalas en ambos sentidos. No confiar sólo en `stops` remoto.

**Estrategia de precio actualmente validada:** ida y regreso asociado están etiquetados `Round trip`, ambos precios son enteros positivos exactos, el contexto de la consulta confirma COP/un adulto y los importes coinciden. Informar el valor coincidente una sola vez como tarifa total observada. Esta es una regla conservadora del PoC apoyada en la evidencia disponible, no la afirmación de que toda respuesta posible de SerpApi tenga esa semántica.

Si cambian tipo/precio/moneda/asociación o la evidencia es insuficiente, devolver `NO_VERIFICADO` con motivo y bloquear avisos de esa oferta. Una discrepancia no demuestra por sí sola que no existan tarifas; exige otro caso demostrado antes de ampliar el verificador. Nunca sumar etapas ni convertir moneda con tasa supuesta. El desglose por trayecto es “No disponible” salvo que la fuente lo acredite inequívocamente; no mostrar dos totales como si fueran tarifas individuales.

Rechazar moneda, adulto, clase, fechas o filtros reflejados incompatibles. No exigir campos que el JSON no garantice sin definir el estado no verificable correspondiente. No afirmar equipaje, impuestos completos, reserva, ocho cupos ni precio de pago por inferencia. La ausencia de esos detalles no autoriza inventarlos.

### 7.4. Operadores y enlaces

Mostrar todos los operadores devueltos, aunque no tengan precio completo comprobado; distinguir detectado, verificado y sin tarifa. Conservar nombres/códigos y diferencias entre comercializador/operador únicamente si la respuesta las aporta. Para combinaciones mixtas, indicar compañías de ambos sentidos.

El catálogo informativo de aerolíneas se distribuye con cada APK, con fecha y fuentes. No filtra resultados; un operador nuevo se muestra inmediatamente. Separar “Aerolíneas encontradas en esta búsqueda” de otras del catálogo con “Sin tarifa devuelta por esta fuente” o “Cobertura sin comprobar”. No decir “no opera”, “sin vuelos” o “todas revisadas” por ausencia de datos.

Priorizar `search_metadata.google_flights_url` cuando exista y pase validación HTTPS, host/ruta permitidos y ausencia de secretos incluso codificados. El validador actual admite `www.google.com/travel/flights`; cualquier ampliación de dominios debe justificarse. No fabricar enlaces de compra. Si no se puede demostrar que conserva los criterios, rotular “Abrir Google Flights para verificar”. La notificación abre primero el detalle exacto de la alerta; el enlace externo se ofrece desde allí.

## 8. Cuota compartida, cuenta y reloj

- Consultar Account API al iniciar la app y después de guardar/cambiar clave, sin bucles; **siempre antes de cada lote de vuelos** manual o automático bajo el control global. Un lote puede revisar varias alertas secuencialmente.
- Exigir cuenta activa y campos de capacidad válidos. Leer saldo total, saldo del plan, uso mensual, renovación y capacidad horaria cuando estén disponibles; no confundir `this_hour_searches` con otros intervalos. Si falta la información necesaria para calcular capacidad con seguridad, bloquear con explicación.
- Capacidad operativa del lote: saldo oficial total limitado por capacidad horaria restante, con validación de consistencia. No tratar créditos extra como permiso para un gasto fuera de lo autorizado. Descontar cada GET iniciado de la capacidad conservadora del lote para no reutilizar el mismo saldo.
- Si Account API falla, incluso con saldo guardado y cuota local, iniciar **cero GET de vuelos** en ese lote. Mostrar “Saldo oficial sin verificar; búsquedas suspendidas”. Recuperar con backoff prudente, sin usar datos antiguos como permiso.
- Tope local por defecto: **200 GET de vuelos iniciados / ventana conservadora de 30 días**. Permitir reducirlo. Esta V1 no añade una función para aumentarlo, contratar planes o comprar créditos; la posibilidad condicional mencionada en el documento maestro no obliga a implementarla.
- Cambiar ese límite usa la misma coordinación que las reservas. Reducirlo por debajo de lo ya reservado bloquea nuevos GET; no elimina consumo previo ni genera capacidad ficticia.
- Reservar localmente de forma atómica antes de cada GET. No liberar automáticamente por timeout/fallo después de reservar. El presupuesto es conservador; no afirmar cargo oficial ni que una reserva incierta llegó necesariamente al servidor.
- Account API no reserva unidades de ese contador. Caché del proveedor puede reducir facturación real; cada GET de vuelo sigue contando localmente por seguridad.
- Al bloquear cualquiera de los límites, suspender vuelos globalmente, conservar datos y mostrar la causa; reanudar sólo con cuenta recién comprobada y capacidad local suficiente. Cambiar o borrar clave, limpiar logs, eliminar alertas o actualizar APK no reinicia reservas.
- Reutilizar el reloj conservador del PoC. Cambiar la fecha del teléfono no puede liberar cuota; el tiempo apagado no adelanta la ventana existente. Comprobar cambio de hora, reinicio e identificación fiable del arranque; no declarar infalible una comparación aislada de `elapsedRealtime`. Un reloj no confiable bloquea nuevas reservas con estado visible, sin reset automático.

**Estimación al editar/activar:** sumar para las alertas activas aproximadamente `(720 / frecuenciaHoras) × GET_por_revision` en 30 días, mostrando un rango ordinario de 2–4 GET por revisión y que faltan las consultas manuales. Una alerta cada 24 h estima 60–120 GET; cuatro alertas equivalentes, 240–480, superan 200. Es una proyección de consumo, no un saldo o promesa de ejecución.

Distribuir turnos por antigüedad de próxima revisión y round-robin, con diversidad de candidatos dentro de cada alerta. Advertir si las frecuencias solicitadas exceden la capacidad; permitir guardar alertas pausadas y mostrar cobertura parcial/pendiente. No permitir que una alerta agote todas las oportunidades mientras otras esperan.

## 9. Elegibilidad, entrega e idempotencia de avisos

### 9.1. Selección

Sólo avisar con alerta activa y no vencida, permiso/canal habilitados, criterios vigentes, ida y regreso completos, directos/escalas correctos y total COP verificado **menor o igual** al máximo. Una tarifa parcial o una revisión fallida nunca produce aviso positivo.

La identidad del itinerario sigue utilizándose para presentar resultados y recuperar el detalle; no determina si una tarifa puede volver a notificarse.

En cada revisión válida se filtran las ofertas completas por vigencia y máximo, y se selecciona la de menor precio. El resultado se notifica aunque ese mismo itinerario y precio ya se haya notificado en una revisión anterior.

Escoger **la oferta verificada más barata bajo el máximo**, como máximo una por alerta y revisión, manual o automática. Las demás opciones no generan avisos en esa revisión. Si ninguna tarifa verificada cumple el máximo, no enviar aviso.

### 9.2. Contenido y retención

El aviso usa “Tarifa más baja verificada”, ruta, fechas, un adulto, total COP ida/vuelta, compañía(s) y “Precio observado; verifica condiciones antes de comprar”. Indicar cobertura parcial cuando corresponda. El toque abre el detalle de esa alerta; no modifica ni elimina datos.

Conservar las 100 últimas notificaciones enviadas globalmente. Limpiarlas requiere confirmación y solo afecta el historial visible; futuras revisiones pueden notificar la misma tarifa. Sin permiso/canal, registrar el resultado y motivo de no despacho, **sin crear registro enviado**. Tras habilitar notificaciones, una nueva revisión vigente puede avisar.

### 9.3. Diseño interno propuesto para transacciones e interrupciones

Room y NotificationManager no comparten una transacción. No marcar primero una oferta como enviada para luego intentar notificar: un permiso revocado o fallo podría hacer perder el aviso.

Preparar un evento pendiente con ID único por revisión/alerta bajo transacción, verificar de nuevo vigencia/criterios/permiso y despachar con un identificador Android estable para ese evento. Si se interrumpe, recuperar el mismo evento con el mismo ID; una revisión posterior genera un ID nuevo, incluso con la misma tarifa. Sólo tras despachar con `notify` sin error y con permiso/canal habilitados, confirmar en una transacción el registro enviado y retirar el evento pendiente. No mantener una huella de itinerario/precio que bloquee avisos futuros. No existe confirmación de lectura o entrega visible por parte de `notify`; “enviado” representa ese despacho local y no garantiza que el usuario lo vea.

Si se usa estado pendiente, recuperarlo tras interrupción usando el mismo identificador y política de actualización sin alerta repetida cuando sea posible. Si se volvió antiguo o cambiaron criterios, descartarlo y esperar la siguiente revisión manual o programada permitida; no iniciar búsquedas adicionales por recuperar un evento. Limpiar ese estado al resolverlo para no crear otra lista histórica. Comprobar interrupciones en los puntos de despacho/confirmación del diseño elegido. No prometer entrega exactamente una vez ante cualquier muerte del proceso ni añadir un sistema de mensajería para intentar garantizarla.

## 10. Programación y pantallas

### 10.1. WorkManager

Un único trabajo periódico coordinador, por ejemplo cada hora, con trabajo único y `NetworkType.CONNECTED`. No un Worker por alerta, alarmas exactas ni servicio foreground permanente. Registrar sólo alertas activas cuyo **turno de consulta** esté vencido; distinguirlas de las alertas con **fecha de viaje** vencida. Si ninguna requiere revisión, no llamar a SerpApi.

Las frecuencias 6/8/12/24 h son objetivos; Android puede aplazar trabajo por Doze/batería. Persistir turno y ejecución real, establecer backoff acotado y evitar una recuperación que ejecute en ráfaga todos los turnos perdidos. La búsqueda manual no reactiva la alerta ni consume/elimina silenciosamente su turno automático; puede compartir un resultado simultáneo compatible para evitar GET duplicados. Definir y probar esa coordinación con IDs/versión de revisión y los tiempos, no depender sólo de que un mutex serialice dos búsquedas completas.

Como política técnica inicial, al activar dejar el turno automático disponible para el siguiente barrido y, tras una revisión automática completada, calcular el siguiente objetivo desde su finalización real más la frecuencia, sin acumular turnos históricos. Un bloqueo de cuenta/red/cuota usa un próximo intento con backoff, sin convertir el tick horario en consultas constantes. Cambiar frecuencia recalcula el turno desde la última ejecución automática, o desde la activación si aún no existe. Esta política puede ajustarse técnicamente conservando las frecuencias aprobadas, la independencia de la búsqueda manual y el límite de cuota; no añade opciones de programación al formulario.

Al pausar/eliminar, el lote revalida antes de cada siguiente GET y despacho. Si el HTTP ya inició, su cuota se conserva. `CancellationException` se propaga; una cancelación no se convierte en falsa respuesta ni aviso. Credenciales inválidas, 429 y errores de negocio no provocan bucles de reintento.

Comprobar cierre normal, retirada de Recientes, pantalla bloqueada/apagada, red perdida/recuperada, Doze, cambio de hora/zona, actualización y reinicio. Forzar detención desde Ajustes puede suspender trabajo hasta abrir de nuevo; teléfono apagado no consulta. Diagnóstico visible sin exigir desactivar protecciones ni exenciones agresivas de batería.

### 10.2. Pantallas mínimas

| Pantalla | Contenido y acciones |
|---|---|
| Inicio | “Alertas de vuelos”, contador de activas, clave/conexión/cuota; tarjetas con criterios, máximo, último total y antigüedad, último intento, frecuencia/estado; nueva, buscar, editar, pausar/reanudar, eliminar con confirmación |
| Crear/editar | Campos y validaciones de sección 6, DatePicker, checkbox de directos, frecuencia, estimación de consumo y parámetros fijos informativos; guardar/cancelar |
| Detalle | Resultado/causa, último precio con su origen temporal, criterios aplicados, escalas por sentido, operadores y sus mejores opciones realmente verificadas; alternativas actuales en memoria, cobertura parcial y enlace seguro |
| Avisos | Lista inversa de hasta 100 enviados, abrir alerta, borrar con confirmación; sin historial de todas las búsquedas |
| Configuración | Guardar/reemplazar/eliminar clave, probar conexión gratis, prueba de vuelos separada con confirmación de consumo, saldo oficial y local separados, límite reducible, fecha del saldo, permiso/canal, catálogo nacional y privacidad |
| Diagnóstico del programador | Última consulta efectiva, próxima estimada, último error, estado de Worker, restricción de batería observable y botón “Comprobar programación” sin llamadas SerpApi |

La fila de diagnóstico es una sección de Configuración; no exige una pantalla adicional. Los datos de aeropuertos se usan para validar la entrada IATA aprobada, sin añadir un explorador geográfico o un gestor de catálogos.

Mantener estados vacíos, cargando, clave ausente/ilegible, sin resultados, no verificable, red, cuota y permiso denegado. No ocultar errores por mostrar un precio anterior. Formato COP claro, etiquetas es-CO, contraste, tamaño de fuente ampliado y objetivos táctiles accesibles. Los horarios no se exigen en presentación ni como filtro V1; permanecen disponibles internamente.

Los dos textareas de diagnóstico existentes, con copiar y limpiar por separado para cuenta y vuelos, se conservan durante validación hasta retirar esa UI de soporte por decisión posterior. No convertir sus logs en historial de precios.

## 11. Seguridad, errores y diagnóstico

Conservar AES-256/GCM, IV fresco por cifrado y clave no exportable en Android Keystore; archivo cifrado en `noBackupFilesDir`, escritura atómica, backup desactivado. Gestionar clave ilegible/perdida y reinstalación pidiendo reconfigurar sin exponer contenido. TLS estándar; no certificados “trust all”, cleartext ni envío de parámetros secretos a navegador. El campo de credencial sigue oculto; capturas permitidas como pidió el propietario. Un dispositivo comprometido mantiene riesgo residual de extracción.

Permisos: `INTERNET`, `ACCESS_NETWORK_STATE` para conectividad/WorkManager y `POST_NOTIFICATIONS` solicitado en contexto. Revisar también el manifest fusionado al incorporar WorkManager y sus componentes de reinicio. No añadir permisos de almacenamiento amplio, ubicación, cámara, micrófono o contactos.

Redactar key, tokens, email/ID de cuenta y otros identificadores sensibles. No guardar secretos en BuildConfig, recursos, informes, fixtures, copias de APK o salida de terminal. No habilitar un interceptor HTTP que imprima URLs/cuerpos. Diagnóstico por revisión: ID local, etapa, HTTP, duración, conteos, GET reservados/iniciados y categorías/motivos permitidos; no cuerpos de error ni mensajes remotos sin sanear. Limitar tamaño y retención; conservar diagnóstico mínimo de última ejecución por alerta y registros de soporte acotados durante validación.

| Situación | Resultado exigido |
|---|---|
| Sin clave o cifrado ilegible | Configuración requerida, sin HTTP de vuelos |
| Cuenta inválida/inactiva, 401/403 | Mensaje claro; detener intentos repetidos, sin revelar credencial |
| Cuenta inaccesible o datos insuficientes | Saldo sin verificar; bloquear lote incluso con saldo antiguo |
| 429, cuota oficial/local insuficiente | Suspender GET, causa visible y recuperación prudente |
| Timeout, desconexión o 5xx | Error de intento; precio anterior marcado como previo; reserva no liberada |
| Estado API no exitoso, error JSON o estructura inválida | No verificado/error con causa; no tratarlo como falta de vuelos |
| Respuesta válida vacía | Sin resultados devueltos para esa consulta; sin afirmar ausencia en el mercado |
| Sólo ida, regreso incompatible o precio ambiguo | Parcial/no verificado, nunca notificar |
| Operador ausente del catálogo o respuesta | Mostrar desconocido si llega; ausencia sólo como sin tarifa de la fuente |
| Criterios editados, pausa/eliminación durante revisión | Evitar siguientes GET/avisos incompatibles y datos obsoletos |
| Permiso/canal bloqueado | Resultado guardado, sin marcar envío ni mínimo notificado |

Las simulaciones que se muestren en desarrollo llevan “DATOS DE PRUEBA”. Fixtures y MockWebServer sustituyen ambos endpoints. No incrustar ejemplos como producción.

## 12. Ejecución por pasos y salidas comprobables

### Paso 0 — Sincronizar estado y preparar la base

Leer íntegro el documento maestro y registrar los cambios posteriores del propietario. Actualizar los estados históricos señalados en sección 1, agregar evidencia de sección 2 al informe/semántica y corregir la mención “pendiente de resolución” de serialization en el informe de entorno si los reportes ya prueban compilación. Conservar la procedencia de cada evidencia; no inventar JSON real.

Inventariar esquema Room, archivos de credencial, firma instalada y versiones. Esta carpeta actualmente no tiene repositorio Git detectado; asegurar un respaldo local recuperable del código antes de cambios amplios, sin incluir credenciales en documentación o sincronización pública. No borrar ni reemplazar el proyecto. Elegir dependencias nuevas compatibles y fijarlas en el catálogo.

**Salida:** contrato coherente, respaldo, matriz de entorno, esquema de migración y fuente de evidencia identificados.

### Paso 1 — Extraer y proteger el dominio probado

Separar cliente/DTO, parser, validadores, estrategia de precio y orquestación de revisión; conservar reglas de sección 7 y estados tipados. Generalizar `SearchCriteria` para rutas nacionales; inyectar reloj y dependencias. Mantener la prueba manual separada, hasta dos GET, si continúa como diagnóstico.

**Salida:** casos probados del PoC conservan resultados; no se introduce una llamada real para validar la refactorización.

### Paso 2 — Catálogos y validación de criterios

Distribuir catálogo de aeropuertos con IATA, ciudad, país y zona; catálogo informativo de operadores separado con revisión/fuentes. Aplicar sección 6, incluido vencimiento intradía, fechas locales y valores monetarios exactos.

**Salida:** validaciones offline, internacional rechazado, desconocidos no filtrados en resultados de aerolíneas.

### Paso 3 — Room y migración no destructiva

Implementar sección 5 y migración real desde v1 preservando cuota/reloj/credencial. Añadir repositorios Flow, transacciones e índices. Resolver inicialización única del ejemplo y no recreación en actualización.

**Salida:** pruebas de migración/cierre y retención; datos de PoC conservados, instalación limpia diferenciada.

### Paso 4 — Interfaz multi-alerta y ajustes

Implementar las pantallas de sección 10.2 y tabla de ediciones, incluida versión de criterios. Mantener tema claro, validación de UI, confirmación al borrar y acceso a permisos. Guardar no dispara consultas ni avisos; la acción explícita sí usa los casos de uso.

**Salida:** CRUD y navegación completos con fakes claramente identificados en desarrollo.

### Paso 5 — Motor compartido de búsqueda y presupuesto

Aplicar secciones 7 y 8: cuenta por lote, saldo horario/total, reservas atómicas, búsqueda inicial única, hasta tres candidatas, diversidad/equidad, estados parciales y reglas de precio. Evitar reintentos/redirecciones ocultos. Cancelar o descartar ejecuciones desactualizadas.

**Salida:** transporte offline y costos verificables, ningún GET que omita presupuesto.

### Paso 6 — Evaluación y despacho de notificaciones

Aplicar sección 9 con canal Android, permiso, tarifa verificada más baja bajo el máximo e ID único de despacho por revisión. El ID solo permite reintentar el mismo evento sin duplicarlo; no bloquear avisos futuros por itinerario o precio repetido. Mostrar demás opciones sin generar otro aviso en esa revisión; comprobar permisos revocados e interrupciones.

**Salida:** como máximo un aviso por revisión y alerta, sin repeticiones por concurrencia, sin avisos falsamente registrados.

### Paso 7 — Coordinador periódico y diagnóstico

Añadir WorkManager único, composición compartida, restricciones, backoff y reglas de turnos; una worker factory sólo si la integración elegida la necesita. Integrar búsqueda manual y automática sin duplicar consultas simultáneas. Implementar diagnóstico de programación sin API.

**Salida:** pruebas offline de planificación y ejecución real Android clasificada por escenario.

### Paso 8 — Robustez, privacidad y revisión de requisitos

Revisar manifest fusionado, cifrado, backups, logs, enlaces, R8/release y accesibilidad. Completar matriz de sección 13 y criterios del documento maestro §17. Documentar cobertura parcial y funciones pendientes; no activar avisos sobre datos ambiguos.

**Salida:** hallazgos corregidos y evidencia aprobada/fallida/no ejecutada, sin afirmaciones de pruebas no hechas.

### Paso 9 — Prueba de aceptación y entrega

Aplicar sección 14. Conservar evidencia previa real como regresión. Una nueva llamada de desarrollo sólo se realiza con autorización vigente, presupuesto máximo explicado y clave introducida localmente; contar GET iniciados, no afirmar el cargo facturado a partir de ellos. Para Android de fondo, distinguir pruebas con fakes de consultas reales autorizadas.

**Salida:** código y README completos, APK debug y release firmado, informe de aceptación, hashes y copias verificadas en la ruta de entrega.

## 13. Matriz de cobertura y pruebas

Estos casos son criterios de aceptación del producto. El estado actual de cada uno (aprobado, parcial, fallido o no ejecutado) y su evidencia se registran en [el informe V1](docs/INFORME_IMPLEMENTACION_V1.md). Las pruebas automáticas de red usan MockWebServer/fakes y cero consultas reales.

| ID | Caso que debe comprobarse | Requisitos maestros |
|---|---|---|
| T01 | Listas best/other separadas o combinadas; una ausente, ambas vacías, estructura inválida, estado no Success y campo error; fallos no se rotulan como sin vuelos | §4, §11, §13.6 |
| T02 | Dos opciones asociadas Round trip con COP entero positivo e igual: total usado una vez; nunca sumar | §4, §13.1/5/29 |
| T03 | Tipo/precio/moneda faltante o distinto, cero, decimales, desbordamiento, parámetros reflejados incompatibles y sólo ida/token ausente: sin aviso | §4, §11, §13.4/7/8 |
| T04 | Directos en ambos sentidos; ida directa/regreso con escala y caso inverso, checkbox true rechaza y false acepta si todo es válido; stops coherente en ambos GET | §4, §13.2/3/9a |
| T05 | Conexiones, aeropuertos continuos, tiempos ordenados, medianoche, fechas locales y regreso posterior a llegada; vuelo ya salido y vencimiento al acabar día | §5.2, §18.2 |
| T06 | Costo 1+K, K≤3, evaluar regresos ya recibidos, alternativa tras fallo/tope/sin mejora, diversidad/rotación y cierre temprano elegible; cobertura parcial | §4 selección, §13.9/9d/28 |
| T07 | Operador desconocido, mixto, comercializador/operador cuando existan; ausentes sin precio inventado; catálogo informativo versionado | §4.1, §13.9b/9c/9f/9g/35 |
| T08 | Enlaces HTTPS/host/ruta, parámetros secretos incluidos codificados, criterios no garantizados y ausencia de enlace; notificación abre alerta exacta | §4 enlaces, §8 |
| T09 | Umbral inclusivo: igual elegible, superior no; una tarifa observada no prueba venta/equipaje/ocho cupos | §5.4, §17, §18.3 |
| T10 | Misma oferta repetida en revisiones válidas puede avisarse en cada una; una sola por alerta/revisión y siempre la más barata bajo el máximo | §5.4, §13.11/12/13 |
| T11 | Oferta absoluta mínima ya avisada no bloquea otra elegible; escoger más barata elegible; máximo un aviso; no marcar el resto | §5.4, §13.13a/13b |
| T12 | Pausada/vencida/sin permiso o canal; habilitar después requiere nueva revisión; no marcar enviado ni mínimo sin despacho | §5.2/5.4, §13.14/22/30 |
| T13 | CRUD, nombre/IATA/país, origen=destino, fechas, máximo inválido, frecuencia; presupuesto proyectado visible y activación explícita | §5.1, §13.15 |
| T14 | Tabla de ediciones: nombre/frecuencia compatibles, ruta/fechas/directos incompatibles, nuevo máximo exige nueva consulta; no filtrar por precios de avisos anteriores | §5.1, §13.9e/15a |
| T15 | Último intento fallido + precio verificado antiguo/criterios originales en una fila; resumen actual y opciones en memoria sin historial | §5.3, §9.3, §10 |
| T16 | 100 avisos, borrado confirmado y limpieza de avisos del sistema; la revisión siguiente puede notificar de nuevo el mismo precio | §5.4, §10, §13.19/31 |
| T17 | Cuenta gratuita, estado/capacidad válidos, cero/negativos/campos críticos ausentes, límite horario; sin datos sensibles en logs | §7, §13.26/27 |
| T18 | Cuenta falla antes de lote con saldo antiguo/cupo local: cero GET; recuperación sólo tras nueva cuenta válida | §7, §13.26a |
| T19 | Reservar antes de cada GET, cuenta no descuenta, timeout conserva reserva, capacidad del lote baja, 200 local/reducido, justicia entre alertas | §7, §13.16/17/26b |
| T20 | Ventana de cuota, cambio de hora, reinicio/tiempo apagado, reloj incierto bloquea, cambio de key o APK no resetea | §7/8, §18.2 |
| T21 | Manual y Worker simultáneos: sin GET ni eventos duplicados; cancelar/editar/eliminar durante HTTP, resultado desactualizado no sobrescribe | §6/7, §13.18 |
| T22 | Atomicidad del mecanismo de despacho elegido: interrupción entre notify/confirmación, permiso revocado y datos antiguos; recuperación de pendiente sólo si ese diseño se usa | §5.4 atomicidad, §13.18/30 |
| T23 | Offline, timeout, 401/403/429/5xx, JSON/errores de negocio; sin bucle/polling ni fuga de excepciones o URLs | §8/11/13 |
| T24 | Worker único, frecuencia/turno justo, sin peticiones si no hay alertas debidas, constraints y backoff; comprobar programación cuesta cero | §6/7, §9.5 |
| T25 | Ejecución real con app cerrada y fuera de Recientes, pantalla bloqueada, Doze/red y reinicio; force-stop documentado y recuperación al abrir | §6, §13.23/25/32, §17 |
| T26 | Migración real desde flight_probe.db v1, cuota/reloj/credencial y nuevos esquemas; directOnly true para esquema que carezca de ese campo | §10, §17 |
| T27 | Ejemplo sólo instalación nueva, pausado; borrado no reaparece al abrir/reiniciar/actualizar; PoC actualizado no pierde cuota ni se resembra | §1 ejemplo, §13.34 |
| T28 | API key guardar/reemplazar/eliminar/ilegible; Keystore, backup, TLS, permisos mínimos y ausencia de secretos en logcat/APK/fixtures | §8, §13.20 |
| T29 | UI español/claro, COP, fuentes ampliadas, contraste, táctil, rotación/proceso, estados vacíos/errores, capturas habilitadas | §9, §13.21/24 |
| T30 | Debug y release firmados, apksigner, versionCode ascendente, identidad/certificado, instalación y actualización sin perder datos | §13.33, §17, §18.5 |
| T31 | Catálogo fuentes/fecha visible y actualización por APK conserva alertas; operador nuevo visible sin actualizar catálogo | §4.1, §13.35 |
| T32 | Evidencia real aportada conservada; llamada adicional sólo autorizada, GET exactos, sin confundir fixture/HTTP 200 con integración completa | §12 fase1, §14, §17 |
| T33 | README reproducible, documentación y código íntegro, ambos APK y hashes en G:, enlace de entrega y reporte implementado/pendiente/bloqueado | §17, §18.5 |

Para T25, WorkManager Test y un worker con fakes comprueban planificación sin cuota; no sustituyen la evidencia de ejecución real del sistema con pantalla apagada/reinicio. Una prueba en emulador no acredita por sí sola el comportamiento del teléfono físico del propietario. Clasificar explícitamente escenarios que no pudieron ejecutarse.

## 14. Compilación, firma y entrega reproducible

### 14.1. Identidad y firma

Release actual: `applicationId=com.flightnotifications.app`, `versionCode=6`, `versionName=1.0.4`. Debug usa `.debug` y datos separados; no es una actualización instalable sobre release. Para cada versión nueva, aumentar `versionCode`, documentar nombre de versión y mantener el mismo certificado release.

La firma privada existente vive fuera del proyecto y se administra con `scripts/Release-Signing.ps1`; `scripts/Backup-ReleaseSigning.ps1` proporciona copia protegida. Conservar una copia segura recuperable de clave y credenciales. Si falta la clave de una instalación ya entregada, no crear silenciosamente otra pretendiendo que actualiza: recuperar la original o informar el bloqueo.

No cambiar `applicationId`, alias/formato de credencial ni nombre de base por renombrar el producto. Probar actualización release sobre release con Room, presupuesto, ajustes y registro de avisos preservados. Instalación limpia/reinstalación tiene datos nuevos y puede volver a crear el ejemplo; no promete conservar DB ni credencial.

### 14.2. Comandos y alcance de los scripts

Desde PowerShell en la raíz:

```powershell
.\scripts\Build-Apk.ps1
```

El script actual prepara entorno y firma, ejecuta `testDebugUnitTest`, `lintDebug`, `lintRelease`, `assembleDebug` y `assembleRelease`; deriva los nombres `flight-notifications-<version>-{debug,release}.apk`, conserva copias en `outputs/` y copia ambos a la ruta G: junto con `SHA256SUMS.txt`. El log está en `outputs/build-final.log`. **No ejecuta las pruebas instrumentadas** ni certifica por sí mismo instalación o WorkManager.

Con un dispositivo/emulador compatible conectado y entorno preparado:

```powershell
.\scripts\Android-Environment.ps1
.\gradlew.bat :app:connectedDebugAndroidTest --project-cache-dir .gradle-flight --no-daemon --console=plain
```

La firma release para compilación manual requiere la configuración segura del script de firma. No documentar contraseñas reales en comandos. No usar `-SkipChecks` como evidencia de haber pasado controles.

Los nombres se derivan de `versionName`, la ruta predeterminada exigida se conserva y cada versión queda en archivos separados. Si G: no está disponible, el script debe fallar y el informe debe distinguir las copias locales de las copias externas.

### 14.3. Lista de cierre

- Generar realmente **APK debug y APK release firmado**, verificar firma con `apksigner`, metadatos y SHA-256; los hashes pertenecen a esos binarios concretos, no al informe viejo.
- Instalar/abrir release y actualizar sobre la anterior; verificar conservación de datos, no desinstalar para fingir que una actualización pasó.
- Entregar ambos en **`G:\Mi unidad\APK android car\`**, comprobar existencia y hash de las copias. Proporcionar en la respuesta un enlace al APK release real para abrir/descargar desde el entorno, además de la ruta; si la interfaz no soporta descarga adjunta, indicarlo con precisión.
- Entregar código Kotlin íntegro, recursos, wrapper, schemas/migraciones, fixtures saneados, README y reporte de aceptación. README: requisitos de entorno, comandos, sideload, permisos, clave, cuota, firma/actualizaciones y límites de segundo plano/cobertura.
- Reportar implementado / pendiente / bloqueado, pruebas aprobadas/fallidas/no ejecutadas, dispositivo usado y GET reales iniciados en esa fase; no confundirlos con consumo facturado ni con saldo actual.
- Recorrer `REQUIREMENTS.md` §17 completo y la matriz T01–T33. Si falta un artefacto o prueba obligatoria, declararlo pendiente con causa; no declarar la app terminada por haber generado un binario.

## 15. Registro de correcciones

### Auditoría 1.1

| Hallazgo en versión 1.0 | Corrección aplicada |
|---|---|
| Frecuencia/máximo invalidaban cotizaciones indiscriminadamente | Tabla de edición compatible con requisitos §5.1 |
| Migración permitía una “transición limpia” sin reconocer la base existente | Migración desde Room v1 conserva cuota, reloj y credencial |
| Notificación podía figurar enviada antes del despacho | Pendiente separado; confirmación sólo tras despacho; sin mínimo ni huella por permiso denegado |
| Se confundían pruebas locales, logs reales y validación de todo el itinerario manual | Evidencia con procedencia, conteos exactos y límites; no se afirma disponer de JSON real |
| Costo de tres candidatas y diversidad no estaban precisados | Una búsqueda inicial + hasta tres regresos, 2–4 GET; diversidad/rotación y equidad |
| Sobrescribir último resultado podía borrar el único precio verificado | Último intento y último verificado en una fila con instantes/criterios propios |
| Faltaban vencimiento intradía y protección frente a ediciones durante HTTP | Estados, tiempo local y versiones separadas de búsqueda/política con revalidación |
| Faltaban ajustes, diagnóstico de programación y detalle de operadores | Pantallas completas y estados por operador, sin exigir horarios en UI |
| Reloj conservador y proyección de consumo quedaban sin contrato | Preservación, bloqueos por reloj incierto, rango de GET y estimación por alertas |
| Entrega se centraba en release y confundía instalación limpia con actualización | Ambos APK, firma estable, versionCode, hashes y pruebas separadas |
| Reglas se repetían en arquitectura, pasos y cierre | Reglas centralizadas; pasos con salidas y matriz de trazabilidad en lugar de repetirlas |
| El final sugería no volver a validar horarios en ningún sentido | Sin horarios comerciales/UI; validación interna de tiempos sigue obligatoria |

### Revisión de alcance 1.2

Se agregó el control de alcance de §1.1. Las técnicas auxiliares de concurrencia, despacho y persistencia se identifican como propuestas sustituibles; no requieren nuevos módulos de producto. Se excluyó implementar aumentos de presupuesto o compra de créditos, se integró el diagnóstico dentro de Configuración y se prohibieron búsquedas adicionales para recuperar avisos pendientes. Las funciones aprobadas y la regla de precio probada se conservan.

**Estado de ejecución:** se realizaron las tareas de implementación y entrega de los pasos 0–9 en el entorno disponible. Esto no significa que todos sus criterios de salida hayan sido aprobados: la matriz T01–T33 registra pruebas parciales/no ejecutadas y la aceptación de fondo en teléfono físico que falta. El propietario probó manualmente V1.0.2 con SerpApi el 20-10-2026: dos GET HTTP 200 y una tarifa de ida/vuelta `VERIFICADO`; se registra en §2.3 y en el informe. La ejecución automática con pantalla bloqueada/Doze/reinicio sigue pendiente.
