# Plan técnico de desarrollo — Primera APK de prueba de SerpApi

**Versión del plan:** 1.0  
**Fecha:** 19 de octubre de 2026, America/Bogota  
**Proyecto:** `C:\Flight Notifications`  
**Versión prevista de la APK básica:** `0.1.0`, `versionCode = 1`  
**Nombre visible propuesto:** `Prueba de vuelos — SerpApi`  
**Estado:** implementación inicial en curso. Hay código Kotlin, configuración Gradle, pruebas y documentación; compilación, instalación y ejecución aún no verificadas. No se han hecho llamadas a SerpApi.  
**Fuente principal de requisitos:** [REQUIREMENTS.md](REQUIREMENTS.md), versión 1.4, especialmente las secciones 4, 7, 8, 12, 13 y 17.

## 1. Propósito y relación con la aplicación completa

Desarrollar primero una aplicación Android pequeña e instalable que permita comprobar desde un teléfono la conexión con SerpApi, la validez de la credencial, el saldo oficial, la búsqueda de salidas y el regreso asociado a una salida. La comprobación central es determinar qué campo representa el precio total observado de ida y vuelta para un adulto, sin sumar importes indebidamente.

El propietario ha propuesto validar este servicio antes de desarrollar las funcionalidades de monitoreo de la aplicación completa. Este plan concreta esa primera entrega y desarrolla la prueba de concepto ya prevista en la sección 12 de `REQUIREMENTS.md`.

Este documento complementa el documento maestro. No elimina sus requisitos de la aplicación completa ni presenta como cumplidos los que se difieren. Si aparece una incompatibilidad sustantiva, se citarán las cláusulas afectadas y se resolverá antes de implementar esa parte.

La creación inicial de este plan, por sí sola, no ejecutó instalaciones, no generó claves, no inició programación ni autorizó búsquedas reales. Se conservan las autorizaciones indicadas en la sección 0 del documento maestro. Durante el desarrollo, las decisiones internas reversibles se resolverán dentro del alcance autorizado, sin volver a pedir confirmación por cada archivo o prueba offline.

## 2. Qué debe demostrar esta entrega

1. La APK abre y funciona en Android 13 o superior.
2. El propietario introduce su clave en la aplicación y puede guardarla cifrada, sustituirla y eliminarla.
3. `Probar conexión` obtiene información real de Account API sin consumir búsquedas de vuelos.
4. Una acción manual autorizada consulta salidas CTG–CLO para las fechas de prueba, sin filtros de compañías.
5. Se procesan las aerolíneas de todas las opciones retornadas, incluidas las desconocidas para la aplicación.
6. Se obtiene un `departure_token` de una salida válida y se solicita el regreso asociado.
7. Se comprueban aeropuertos, fechas, secuencia de segmentos, escalas, moneda, pasajeros y semántica del precio completo.
8. La aplicación diferencia un resultado completo verificable de uno parcial, sin resultados o con error.
9. Se identifica cuántas solicitudes de vuelos se iniciaron y qué datos de consumo informó el proveedor.
10. Queda evidencia reproducible de las pruebas efectivamente realizadas y de los bloqueos pendientes.

Un HTTP 200 o una lista de salidas no bastan para declarar verificada la integración de ida y vuelta. Si no hay vuelos para la ruta y fechas ensayadas, puede verificarse el transporte HTTP y el manejo de la respuesta vacía, pero queda pendiente demostrar un itinerario completo real.

## 3. Alcance funcional de la APK básica

### 3.1. Funcionalidades incluidas

| Área | Comportamiento previsto |
|---|---|
| Plataforma | Android nativo, Kotlin, `minSdk = 33` |
| Interfaz | Jetpack Compose y Material 3, español, tema claro permanente |
| Credencial | Introducción en campo oculto, guardado cifrado mediante Android Keystore, sustitución y eliminación |
| Cuenta | Botón de comprobación gratuita, saldo oficial y momento de verificación |
| Consulta | Exclusivamente manual y en primer plano; ninguna búsqueda se inicia al instalar o abrir |
| Ruta inicial | Cartagena `CTG` ↔ Cali `CLO`, ruta fija para esta prueba de concepto |
| Fechas | Iniciales `2027-03-25` y `2027-03-28`; editables para ensayar el servicio y evitar que el prototipo dependa de fechas vencidas |
| Pasajero y tarifa | Un adulto, económica, ida y vuelta, COP, sin modificación en esta entrega |
| Escalas | Checkbox `Solo vuelos directos`, marcado inicialmente; afecta ambas etapas y se valida localmente |
| Aerolíneas | Sin selector, lista blanca, lista negra ni parámetros de inclusión/exclusión |
| Resultados | Salidas detectadas, regreso comprobado, operadores, segmentos, escalas, precio y clasificación de verificación |
| Consumo | Saldo oficial, contador conservador persistente y contador de la prueba actual |
| Enlaces | Enlace de Google Flights solo si lo devuelve la fuente y pasa las comprobaciones de seguridad |
| Entrega | Código, pruebas, documentación, APK debug y APK release firmado, o bloqueo específico de cualquier artefacto faltante |

La ruta fija corresponde al caso de integración inicial ya definido. El cliente HTTP y los validadores recibirán un objeto de criterios; no se incrustarán CTG, CLO ni las fechas en el algoritmo de interpretación. El formulario general de aeropuertos se incorporará con las funciones completas.

Los $400.000 COP del ejemplo del documento maestro no se utilizarán como filtro remoto ni como condición de éxito de esta prueba: debe poder comprobarse el servicio aunque todas las tarifas superen ese valor. No hay notificaciones en esta entrega.

### 3.2. Funciones que se incorporarán después de validar el servicio

| Función de la aplicación completa | Situación en esta entrega |
|---|---|
| Crear, editar, activar, pausar y eliminar múltiples alertas | Diferida |
| Presupuesto individual y elegibilidad de ofertas | Diferida; el parser sí valida el precio total |
| Frecuencias de 6/8/12/24 horas y reparto entre alertas | Diferidas |
| WorkManager, ejecución con pantalla bloqueada y tras reinicio | Diferidos y pendientes de sus pruebas específicas |
| Notificaciones, permiso `POST_NOTIFICATIONS`, deduplicación de avisos | Diferidos |
| Persistencia del último precio por alerta y registro de 100 avisos | Diferidos |
| Pantalla completa del catálogo nacional informativo | Diferida; los operadores de las respuestas se procesan desde esta entrega |
| Exploración adaptativa de hasta tres salidas por revisión | Diferida como comportamiento productivo; sus fixtures pueden prepararse sin consultar vuelos reales |
| Auditoría progresiva de todas las compañías devueltas | Se documenta la cobertura observada del PoC; la estrategia completa exige resolver presupuesto y amplitud antes de esa fase |

No se incorporarán backend, Firebase, cuentas de usuario, reservas, pagos, fuentes de aerolíneas adicionales, gráficos ni historial de precios.

## 4. Búsqueda de todas las aerolíneas y caso Wingo

La consulta inicial no enviará `include_airlines` ni `exclude_airlines`. No habrá controles para parametrizar compañías. El catálogo informativo nunca se usará para filtrar resultados.

Se recorrerán `best_flights` y `other_flights`, cuando existan, y los segmentos de cada opción. Se incluirán los operadores nuevos y las combinaciones de compañías diferentes. Los nombres y códigos se mostrarán según los datos efectivamente recibidos; no se inventará la distinción entre comercializador y operador.

Wingo está incluida en los requisitos como compañía de interés. Habrá fixtures que demuestren que una opción de Wingo es procesada sin filtros y que otra aerolínea desconocida también se incorpora. En la prueba real se informará una de estas situaciones:

| Situación | Interpretación |
|---|---|
| Wingo devuelta con itinerario completo comprobado | Se muestran sus segmentos y el total asociado; se identifica una combinación mixta si corresponde |
| Wingo detectada en opciones no verificadas por completo | `Detectada; ida y vuelta pendiente de comprobar` |
| Wingo no aparece en las respuestas obtenidas | `Sin tarifa devuelta por esta fuente para los criterios consultados` |

El primer ensayo real de ida y vuelta verificará una salida y su regreso, con un máximo de dos GET de vuelos autorizados. Eso no acredita todas las combinaciones de todas las aerolíneas detectadas. Se mostrarán el número de candidatos retornados, los examinados y los operadores pendientes de verificación.

La amplitud de una futura auditoría de compañías ausentes del proveedor no se considera resuelta por este PoC. La fuente aprobada sigue siendo SerpApi. Google declara que su buscador no incluye todos los vuelos ni todas las aerolíneas; este límite impide certificar cobertura universal a partir de una búsqueda sin filtros. [Cobertura de Google Flights](https://support.google.com/travel/answer/2475306?hl=es).

## 5. Diagnóstico del entorno ya realizado

Estas observaciones proceden de comandos de lectura ejecutados el 19 de octubre de 2026; no son resultados de compilación.

| Elemento | Evidencia actual | Trabajo pendiente |
|---|---|---|
| Carpeta del proyecto | Contiene `REQUIREMENTS.md`; no existe proyecto Gradle Android | Crear el proyecto cuando se autorice programar |
| JDK por `JAVA_HOME` | Temurin OpenJDK `21.0.8+9`, con compilador disponible | Comprobar ejecución del Wrapper con este JDK y registrar la versión definitiva |
| `java` encontrado en `PATH` | Oracle Java Runtime `1.8.0_281` | Evitar que Gradle utilice ese runtime por accidente |
| `javac` y `gradle` en `PATH` | No encontrados | Usar el compilador del JDK y Gradle Wrapper; no depender de Gradle global |
| `adb`, `sdkmanager`, `emulator` en `PATH` | No encontrados | Localizar o configurar Android SDK y herramientas |
| Variables de SDK | `ANDROID_HOME` y `ANDROID_SDK_ROOT` sin configurar | Documentar la ruta efectiva del SDK |
| Rutas comunes de Android Studio/SDK | No localizadas en las rutas revisadas | Comprobar otras instalaciones antes de descargar |
| Dispositivo Android | Todavía no inventariado mediante ADB | Detectar dispositivo o preparar emulador compatible |
| Cuenta y clave de SerpApi | No probadas con credenciales del propietario | Introducción local y validación en la app |

No se afirma que Android Studio o el SDK no existan en todo el equipo: solo se ha comprobado que no aparecen en las rutas y comandos revisados.

## 6. Dependencias y versiones: selección y comprobación

### 6.1. Reglas de elección

Se elegirán versiones estables publicadas y compatibles entre sí. Se distinguirán tres estados: referencia comprobada en documentación, versión candidata para el proyecto y combinación realmente compilada. La tabla siguiente no afirma que se haya sincronizado Gradle.

Los números definitivos se fijarán en `gradle/libs.versions.toml`; no se usarán versiones dinámicas con `+`, snapshots ni dependencias alpha, beta o RC. Antes de programar se contrastarán los parches estables disponibles y las matrices de compatibilidad. Una alternativa por incompatibilidad se documentará; no se rebajarán requisitos funcionales para resolverla. **Actualización:** la matriz quedó fijada en el catálogo y se registra en `docs/ENTORNO_Y_VERSIONES.md`; todavía no se ha compilado.

### 6.2. Matriz técnica inicial

| Componente | Referencia o selección inicial | Validación requerida |
|---|---|---|
| Android Studio | Referencia oficial: Rabbit 1, `2026.2.1` | Confirmar canal estable y compatibilidad con el AGP elegido; el IDE es opcional para compilar por CLI |
| Android Gradle Plugin | Candidato `9.4.0` | Confirmar parche estable vigente, resolución del artefacto y sincronización |
| Gradle Wrapper | Para AGP 9.4, referencia `9.6.0` | Confirmar matriz oficial y verificar checksum de distribución |
| JDK para ejecutar Gradle | JDK 21 disponible; AGP 9.4 documenta mínimo JDK 17 | Verificar con `gradlew.bat --version`; registrar cualquier actualización estable necesaria |
| Bytecode Java/Kotlin | Objetivo JVM 17 en ambos compiladores | Alinear opciones; no confundir versión del runtime de Gradle con bytecode de la app |
| Kotlin | Referencia estable comprobada `2.4.21` | Resolver integración con Kotlin incorporado de AGP y comprobar compatibilidad efectiva |
| Compose Compiler y plugin de serialización | Alineados con el compilador Kotlin efectivo | No asumir que la versión de la BOM selecciona el compilador |
| `minSdk` | `33`, obligatorio | Instalar y probar al menos en Android 13 |
| `compileSdk` / `targetSdk` | Candidato API `37`, sujeto a SDK estable y compatibilidad de librerías | Confirmar plataforma y Build Tools instalables, metadatos AAR y comportamiento en dispositivos |
| Jetpack Compose + Material 3 | Última BOM estable resoluble de Google Maven | Número exacto pendiente de fijar; no adivinarlo a partir de la fecha |
| Activity Compose | Referencia estable `1.13.0` | Resolver junto con la BOM y el SDK |
| Lifecycle ViewModel/Runtime Compose | Referencia estable `2.11.0` | Comprobar colección de estado ligada al ciclo de vida |
| Retrofit | Candidato estable `3.0.0` y convertidor oficial kotlinx.serialization de la misma familia | Confirmar coordenadas, repositorio de mantenimiento y resolución |
| OkHttp | Línea estable compatible con Retrofit; número exacto pendiente | Validar publicación primaria, versión JVM/Android y compatibilidad; alinear MockWebServer |
| kotlinx.serialization JSON | Referencia estable `1.11.0` | La publicación `1.12.0-RC` observada es candidata, no se seleccionará por figurar como Latest |
| kotlinx.coroutines Android | Referencia estable `1.11.0` | Resolver con Kotlin; la concurrencia principal se comprueba con `Mutex` y tests de repositorio |
| DataStore Preferences | Referencia estable `1.2.1` | Solo ajustes no sensibles |
| Room | Referencia estable `2.8.5` | Persistencia del presupuesto; comprobar runtime, compiler y testing de la misma versión |
| KSP | Versión estable compatible pendiente de fijar | Necesario para generar Room; comprobar con Kotlin incorporado de AGP, sin añadir kapt |
| Pruebas JVM | JUnit y MockWebServer | Fijar versiones estables y motor compatible con el módulo Android; añadir `coroutines-test` solo cuando una prueba de flujo lo necesite |
| Pruebas Android | AndroidX Test Runner, reglas y Compose UI Test | Fijar versiones compatibles; Compose UI Test alineado con la BOM |

Fuentes de estas referencias: [Android Studio](https://developer.android.com/studio/releases), [AGP 9.4 y compatibilidad](https://developer.android.com/build/releases/agp-9-4-0-release-notes), [Kotlin](https://kotlinlang.org/docs/releases.html), [BOM de Compose](https://developer.android.com/develop/ui/compose/bom/bom-mapping), [Activity](https://developer.android.com/jetpack/androidx/releases/activity), [Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle), [Retrofit](https://github.com/square/retrofit/releases), [serialización](https://github.com/Kotlin/kotlinx.serialization/releases), [corrutinas](https://github.com/Kotlin/kotlinx.coroutines/releases), [DataStore](https://developer.android.com/jetpack/androidx/releases/datastore) y [Room](https://developer.android.com/jetpack/androidx/releases/room).

Con AGP 9 se usará su integración incorporada de Kotlin, sin aplicar adicionalmente `org.jetbrains.kotlin.android`. Si hace falta ajustar la versión incorporada, se seguirá el mecanismo oficial y se validará con Compose, serialización y KSP; no se mezclarán plugins por intuición. [Kotlin incorporado de AGP](https://developer.android.com/build/migrate-to-built-in-kotlin).

La frase de pendiente anterior describe el estado al redactar esta sección. Después se fijaron las versiones candidatas en el catálogo. Que una versión esté publicada y fijada no demuestra que toda la matriz sea compatible; esa confirmación requiere resolver dependencias y completar un build.

## 7. Arquitectura y estructura prevista

Un único módulo Android `app`, con MVVM, flujo unidireccional de estado e inyección manual mediante un contenedor de dependencias. Separación por paquetes; no se necesita un framework de inyección para este tamaño.

```text
Compose UI
    -> ProbeViewModel / StateFlow<ProbeUiState>
        -> CheckAccountUseCase / RunRoundTripProbeUseCase
            -> SerpApiRepository
                -> Retrofit + OkHttp + DTO + kotlinx.serialization
                -> Validadores puros de ruta, fechas, escalas y precio
            -> ApiBudgetRepository -> Room
            -> CredentialStore -> Android Keystore + archivo cifrado
```

Árbol conceptual previsto al redactar el plan. La implementación inicial usa las ubicaciones de los archivos que ya existen; aún faltan Wrapper y la comprobación de build:

```text
Flight Notifications/
  REQUIREMENTS.md
  PLAN_PRIMERA_APP_SERPAPI.md
  README.md
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  gradlew
  gradlew.bat
  gradle/
    libs.versions.toml
    wrapper/gradle-wrapper.jar
    wrapper/gradle-wrapper.properties
    verification-metadata.xml
  app/
    build.gradle.kts
    proguard-rules.pro
    src/main/
      AndroidManifest.xml
      java/com/flightnotifications/app/
        FlightProbeApplication.kt
        MainActivity.kt
        di/AppContainer.kt
        ui/ProbeScreen.kt
        ui/CredentialScreen.kt
        ui/ResultComponents.kt
        ui/theme/
        presentation/ProbeViewModel.kt
        presentation/ProbeUiState.kt
        domain/model/
        domain/validation/
        domain/usecase/
        data/remote/SerpApiService.kt
        data/remote/dto/
        data/repository/
        data/local/ProbeDatabase.kt
        data/local/RequestReservationEntity.kt
        data/local/RequestReservationDao.kt
        security/KeystoreCredentialStore.kt
        util/CopFormatter.kt
        util/SafeErrorMapper.kt
      res/values/strings.xml
      res/xml/data_extraction_rules.xml
    src/test/                 # Validadores, parser, cliente y orquestación
    src/test/resources/fixtures/
    src/androidTest/          # UI, Keystore, Room y arranque
  docs/
    ENTORNO_Y_VERSIONES.md
    INFORME_PRUEBAS_POC.md
    SEMANTICA_PRECIO_SERPAPI.md
  outputs/                   # APK y checksums, sin claves
```

`namespace` y `applicationId` propuestos: `com.flightnotifications.app`. Se conservará una identidad estable para permitir evolucionar a la app completa mediante actualización firmada. La identidad definitiva se fijará antes de generar el primer release.

## 8. Contratos de datos y persistencia mínima

| Modelo o componente | Información y responsabilidad |
|---|---|
| `SearchCriteria` | Origen, destino, dos `LocalDate`, `directOnly`, adulto/clase/moneda fijos; objeto inmutable por ejecución |
| `AccountSnapshot` | Saldo, límites, uso, renovación cuando exista y momento de consulta; nunca identificación personal de la cuenta |
| `FlightSegment` | Aeropuertos, fechas/horas locales, operador recibido, número de vuelo y datos verificables |
| `OutboundCandidate` | Segmentos, precio inicial como dato de la fuente y token opaco solo en memoria |
| `VerifiedRoundTrip` | Ida seleccionada, regreso asociado, total COP comprobado, criterios y evidencia de interpretación |
| `AirlineCoverage` | Nombre/código recibido, direcciones donde se detectó, verificación completa o pendiente y motivo |
| `ProbeResult` | Estado, resultado completo o parcial, operadores, intentos, instantes y enlace validado |
| `RequestReservationEntity` | ID de ejecución, etapa, timestamp UTC de creación y marca del reloj lógico de cuota; sin URL, clave, token, tarifa ni JSON |
| `CredentialStore` | Guardado, lectura en memoria, sustitución y eliminación del secreto cifrado |

Se mantendrán resultados y tokens en memoria durante la prueba. Tras morir el proceso no se restaurará automáticamente una búsqueda ni se reutilizará un token antiguo. Un nuevo ensayo exige una nueva acción manual y su control de cuota.

Room almacenará las reservas necesarias para controlar el límite local de 200 GET de vuelos iniciados en una ventana conservadora de 30 días. Para impedir que adelantar el reloj libere cuota, el contador usa una marca lógica que avanza con el tiempo monotónico del dispositivo; el tiempo apagado no acelera la expiración. El instante UTC real de creación se conserva aparte. No se crearán todavía tablas de alertas ni precios históricos. Los eventos fuera de ventana se purgarán de forma acotada. DataStore guardará únicamente preferencias no sensibles, como fechas del formulario y la casilla de directos, si se necesita conservarlas.

Las migraciones de esquema se explicitarán; no se usará borrado destructivo silencioso. Una cancelación o cierre después de reservar una llamada no devuelve automáticamente esa unidad al presupuesto, porque se desconoce si el proveedor llegó a procesarla.

## 9. Seguridad de credenciales y comunicaciones

### 9.1. Tres claves con finalidades diferentes

| Clave | Origen y uso | Custodia |
|---|---|---|
| API key de SerpApi | La proporciona el propietario dentro de la app; autentica los GET | Cifrada en almacenamiento privado; nunca en el código, chat o documentación |
| Clave AES de cifrado | La genera Android Keystore localmente; cifra la API key | No se exporta desde Keystore |
| Clave de firma release | Identifica la APK y permite sus actualizaciones | Archivo y contraseñas bajo custodia local segura del propietario, fuera de VCS y entregables compartidos |

### 9.2. Implementación prevista del secreto

1. Capturar la API key en una pantalla con entrada oculta, sin autocompletado ni autocorrección. No incluirla en `SavedStateHandle`, `rememberSaveable`, preferencias sin cifrar ni reportes.
2. Generar o recuperar una clave AES de 256 bits en `AndroidKeyStore`, con GCM y sin padding.
3. Obtener un IV nuevo del proveedor criptográfico en cada cifrado; nunca reutilizarlo con la misma clave. Guardar IV, versión del formato y ciphertext autenticado, no la clave AES.
4. Guardar ese contenedor de forma atómica en almacenamiento privado excluido de backup, por ejemplo `noBackupFilesDir`. DataStore queda reservado a datos no sensibles.
5. Descifrar solo al preparar peticiones. No mantener copias innecesarias en modelos de UI; evitar prometer borrado perfecto de `String` administrados por la JVM.
6. Sustituir o eliminar la credencial solo cuando no haya una ejecución activa, o cancelar primero de forma controlada. Invalidar saldo y validación de cuenta de la credencial anterior.
7. Si Keystore pierde la clave o el contenido no puede autenticarse, solicitar reintroducción local. No convertir un fallo criptográfico en un secreto vacío supuestamente válido.
8. Configurar exclusiones de backup y de transferencia de datos para credenciales; verificar el comportamiento en el dispositivo disponible. No restaurar ciphertext suponiendo que también se restaurará la clave de Keystore.

La política de acceso a la clave de cifrado será compatible con el uso futuro en segundo plano, sin exigir autenticación biométrica en cada descifrado. No se añadirá un permiso o flujo biométrico ajeno al alcance aprobado.

La pantalla de credenciales bloqueará capturas cuando corresponda; las evidencias se tomarán en pantallas sin secretos. Las limitaciones de dispositivos comprometidos se documentarán. [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [backup y transferencia Android](https://developer.android.com/identity/data/autobackup).

### 9.3. Cliente HTTPS y filtrado de información

- Base URL de producción fija: `https://serpapi.com/`; rutas de cuenta y búsqueda definidas por el cliente.
- TLS y validación de certificados estándar. No usar certificados aceptados indiscriminadamente ni desactivar verificación de hostname.
- Ningún interceptor registrará URLs completas, cuerpos, parámetros de consulta o mensajes de excepción sin filtrar. No se añadirá un SDK de analítica o crash reporting remoto.
- La API usa `api_key` en la query; por eso se controlan también errores HTTP, pantallas, enlaces, capturas, reportes de test y mensajes del parser.
- Los errores se traducirán a códigos propios y mensajes seguros. No se imprimirá directamente `exception.message`, `errorBody` ni el `error` libre del proveedor.
- Redactar `api_key`, `departure_token`, `booking_token` y datos de cuenta antes de cualquier evidencia sanitizada. Usar una lista permitida de campos para guardar diagnósticos.
- Desactivar reintentos transparentes de llamadas y redirecciones automáticas que puedan originar peticiones adicionales no contabilizadas. Comprobar esta configuración con MockWebServer y el protocolo real.
- Caché HTTP local de producción desactivada para evitar guardar URLs con secretos. Esto es independiente de aceptar la caché del propio proveedor.
- Timeouts iniciales propuestos para vuelos: conexión 20 s, lectura 90 s y llamada 120 s. Son valores internos ajustables según evidencia; ninguna espera será ilimitada.
- En pruebas HTTP locales, cualquier permiso de tráfico sin TLS se limitará a la configuración de pruebas/debug y al servidor local. La variante release mantendrá HTTPS obligatorio.

Permisos de la APK básica: `INTERNET` y `ACCESS_NETWORK_STATE` si el diagnóstico lo necesita. No solicitar `POST_NOTIFICATIONS` en este PoC porque todavía no hay avisos; tampoco ubicación, almacenamiento amplio, contactos, cámara ni otros permisos ajenos al servicio.

## 10. Account API: validación gratuita y saldo

Endpoint: `GET https://serpapi.com/account.json`, con la credencial incorporada exclusivamente en memoria al construir la petición.

El DTO leerá únicamente los datos operativos necesarios: `plan_searches_left`, `total_searches_left`, `searches_per_month`, `this_month_usage`, `account_rate_limit_per_hour`, uso horario cuando exista y `plan_renewal_date` nullable. Se tolerarán campos nuevos sin almacenarlos. `api_key`, `account_email` y `account_id` no formarán parte de los modelos visibles ni persistentes.

Si el servicio informa estado de cuenta, se comprobará también como dato operativo; un estado de cuenta inhabilitada o un error de negocio no se aceptará solo porque el HTTP sea 200. La ausencia de un campo opcional se interpretará según el contrato comprobado, no se sustituirá por un estado activo inventado.

`Probar conexión` servirá para verificar la credencial y presentar saldo real y hora de lectura. Una cuenta autenticada con saldo cero es diferente de una credencial inválida: la conexión puede estar comprobada mientras la búsqueda de vuelos está bloqueada. Una renovación nula se mostrará como no informada, sin inventar una fecha.

La Account API es gratuita y no descuenta el cupo mensual de búsquedas. Se registrará su número de llamadas aparte del contador local de GET de vuelos. [Account API oficial](https://serpapi.com/account-api).

Antes de cada prueba manual de vuelos se consultará de nuevo la cuenta, bajo el bloqueo global de ejecución, aunque el propietario ya hubiera usado `Probar conexión`. Si esa comprobación falla, el PoC no iniciará los GET de vuelos de esa tanda. No se reemplazará un saldo fallido por un valor antiguo o por la estimación local.

Si el saldo o su formato son desconocidos, negativos o contradictorios, se mostrará el problema y se bloqueará el inicio hasta aclararlo. Se respetarán también los límites horarios devueltos y su interpretación documentada.

## 11. Contrato de búsqueda de vuelos

### 11.1. Primera etapa

Endpoint: `GET https://serpapi.com/search.json`.

Parámetros derivados del caso aprobado en `REQUIREMENTS.md`:

| Parámetro | Valor de esta prueba |
|---|---|
| `engine` | `google_flights` |
| `departure_id` | `CTG` |
| `arrival_id` | `CLO` |
| `outbound_date` | Fecha elegida, inicial `2027-03-25` |
| `return_date` | Fecha elegida, inicial `2027-03-28` |
| `type` | `1`, ida y vuelta |
| `adults` | `1` |
| `travel_class` | `1`, económica |
| `currency` | `COP` |
| `gl` / `hl` | `co` / `es` |
| `stops` | `1` con directos marcado; `0` al permitir escalas |
| `sort_by` | `2`, precio, usado como ordenación de la fuente |
| `api_key` | Valor local secreto, nunca una constante del proyecto |

No enviar filtros de aerolíneas ni `max_price`. No activar `no_cache=true`, `show_hidden`, `deep_search` ni búsquedas adicionales por defecto. Su evaluación futura requiere medir coste y cobertura y contar con autorización específica para los GET adicionales.

### 11.2. Segunda etapa

Repetir los criterios de la primera petición y añadir exclusivamente el `departure_token` de una salida efectivamente recibida y validada. No invertir origen y destino en la solicitud con token. No decodificar ni modificar el token; se trata como un valor opaco.

La validación local sí espera segmentos de regreso desde CLO hacia CTG en la fecha de vuelta. El token y el snapshot inmutable de criterios vinculan ambas etapas; cambios de fecha o checkbox durante la ejecución no pueden alterar solo la segunda petición.

Las opciones de reserva con `booking_token` quedan fuera de esta primera prueba; no se consultará otro endpoint de vuelos para obtener una URL de compra. [Referencia del servicio Google Flights](https://serpapi.com/google-flights-api).

## 12. Flujo exacto de una prueba manual

1. El usuario introduce y guarda la API key; la app no realiza búsquedas de vuelos por ese hecho.
2. Si pulsa `Probar conexión`, se ejecuta únicamente Account API y se informa el resultado.
3. El formulario presenta CTG–CLO, fechas, un adulto, económica, COP y checkbox de directos.
4. Al pulsar `Probar vuelo de ida y vuelta`, se validan fechas y datos locales antes de cualquier HTTP de vuelos.
5. Se muestra una confirmación con ruta, fechas, filtro y **hasta 2 GET de vuelos** para este ensayo. Cancelar no inicia búsquedas; aceptar autoriza solo esa ejecución y ese máximo.
6. Se adquiere el bloqueo de ejecución y se comprueba nuevamente la cuenta. Si falla o no hay capacidad conservadora para dos GET, se informa el bloqueo sin iniciar vuelos.
7. Se reserva una unidad local y capacidad oficial conservadora para la primera etapa; se hace el GET de salidas una sola vez.
8. Se validan HTTP, estado y estructura; se recorren todas las opciones recibidas para identificar operadores y salidas compatibles.
9. Se elige para el PoC una salida válida con token, usando como criterio técnico inicial el menor precio numérico observado entre salidas compatibles. Ese precio inicial es una referencia de ordenación, no una cotización completa ya comprobada. Si no hay candidata válida con token, se detiene el ensayo tras el primer GET.
10. Se comprueba cancelación y presupuesto restante; se reserva otra unidad y se hace un único GET con el token elegido y los mismos criterios.
11. Se inspeccionan todas las opciones de regreso devueltas para esa salida y se validan sus segmentos y precios completos. Si hay varias completas comprobables, se muestra la menor tarifa verificada junto con las demás opciones disponibles en memoria.
12. Si ninguna vuelta permite demostrar un total inequívoco, se clasifica como `NO_VERIFICADO` o parcial y se explica el motivo. No se consume una tercera llamada buscando otra salida dentro de esta autorización.
13. Se presenta el resultado, operadores detectados, cobertura comprobada y pendiente, tiempos e intentos de la ejecución.
14. Se puede refrescar Account API para contrastar el consumo informado, sin prometer que su actualización sea instantánea. Si ese refresco posterior falla, el resultado de vuelos conserva su evidencia y timestamp, pero el saldo queda marcado como no verificado y la siguiente tanda exige una comprobación válida.
15. Se libera el bloqueo. Un nuevo ensayo requiere una nueva acción y confirmación; no se encadena automáticamente por recomposición, reconexión o error.

Seleccionar un solo token es el alcance del primer ensayo descrito en la sección 12 del documento maestro. Si ese candidato no da un regreso válido, no se concluye que la ruta carezca de vuelos; queda registrada la necesidad de examinar otras opciones en un ensayo posterior autorizado o en el motor completo.

## 13. Validadores de resultados y semántica de precio

### 13.1. Respuesta y parser

- Comprobar código HTTP, `search_metadata.status` y existencia de un error de negocio, sin revelar su texto sensible.
- Tolerar campos adicionales; no convertir campos críticos ausentes en ceros, cadenas vacías o `Success` predeterminados.
- Ausencia de `best_flights` no es por sí sola un error; combinarla con `other_flights` de manera segura. Distinguir listas vacías de una estructura inválida.
- Una respuesta no finalizada se muestra como tal; no se implementará polling automático que oculte peticiones adicionales.
- Rechazar tokens vacíos, precios no numéricos, cantidades negativas, overflow, tipos incompatibles y segmentos incompletos para la verificación completa.
- Un candidato mal formado no debe ocultar otras opciones parseables de la misma respuesta; registrar el motivo de descarte sin fabricar datos.

### 13.2. Ruta, fechas y escalas

- Ida: primer aeropuerto CTG, último CLO y fecha local de salida igual a la seleccionada.
- Regreso: primer aeropuerto CLO, último CTG y fecha local de salida igual a la elegida para volver.
- Con directos marcado: un segmento aéreo y cero escalas en cada sentido; cualquier dato contradictorio se rechaza.
- Con escalas permitidas: comprobar orden, continuidad de aeropuertos, salidas y llegadas, cruces de medianoche y destino final. No aceptar un cambio de aeropuerto como si fueran códigos equivalentes.
- Comprobar que el regreso sucede después de completar la ida; no aceptar combinaciones temporalmente imposibles.
- No reinterpretar fechas de calendario como fechas UTC. Usar `LocalDate` y `LocalDateTime` para datos de aeropuerto y `Instant` para intentos/observaciones.
- Para el caso CTG/CLO se empleará `America/Bogota`; si aparecen conexiones con otras zonas, se requerirá una correspondencia verificable para comparaciones que dependan de instantes. No aplicar la zona del teléfono a todos los aeropuertos.
- El formulario admite ida desde hoy y regreso posterior a la ida. Si se busca el mismo día, descartar salidas ya pasadas; no declarar vencido todo el día a medianoche.
- Cambiar criterios invalida la compatibilidad del resultado anterior; mostrarlo solo como dato previo con sus criterios originales. Una nueva verificación requiere otra ejecución manual.

### 13.3. Precio, moneda y pasajeros

1. Interpretar el campo de precio usando conjuntamente la solicitud controlada, la respuesta real y el contrato del proveedor. Registrar en `SEMANTICA_PRECIO_SERPAPI.md` qué evidencia vincula el precio al itinerario elegido.
2. Comprobar `type` y los metadatos o parámetros reflejados por el servicio cuando existan. Un texto aislado `Round trip` o un importe inicial no demuestra por sí solo la combinación de regreso seleccionada.
3. Usar el importe de la etapa que represente inequívocamente la combinación completa. **Nunca sumar automáticamente el precio de la primera etapa y el de regreso**.
4. Comprobar COP, un adulto y económica con la evidencia contractual de la solicitud y los datos devueltos. Una contradicción o falta de evidencia suficiente produce `NO_VERIFICADO`; no se inventan campos ausentes ni se interpreta su ausencia automáticamente como USD.
5. Convertir importes de forma exacta a entero COP positivo (`Long`), comprobando límites y ausencia de fracción. Si el JSON usa una representación decimal integral, normalizarla con aritmética exacta; evitar `Float` y `Double` para dinero.
6. Mostrar desglose de ida/regreso solo si está expresamente acreditado; de lo contrario, `No disponible`.
7. Distinguir total observado de precio contractual de compra. No afirmar equipaje, impuestos adicionales, ocho cupos o precio familiar si no existe evidencia.
8. Si la semántica sigue ambigua tras el ensayo real, documentar el bloqueo y mantener ese resultado sin verificar. No habilitar después el motor de avisos con una suposición.

### 13.4. Enlaces y frescura

Se usará `search_metadata.google_flights_url` cuando exista. Comprobar HTTPS, dominio permitido de Google y ausencia de claves o tokens sensibles. Si no se acredita que conserva el itinerario exacto, etiquetar `Abrir Google Flights para verificar`; no afirmar que conserva la tarifa. Si no hay enlace seguro, el resultado sigue siendo visible sin fabricar uno.

Mostrar por separado hora del intento, momento real de respuesta, momento de observación o metadatos del proveedor cuando existan y antigüedad. Aceptar caché de SerpApi no significa disponer de un precio instantáneo. Un error posterior no transforma un precio anterior en reciente.

## 14. Cuota, concurrencia y cancelación

| Control | Regla de la APK básica |
|---|---|
| Autorización de ensayo | Hasta 2 GET de vuelos por ejecución inicial; una llamada fallida también cuenta como intento conservador |
| Límite persistente | 200 GET de vuelos iniciados en ventana móvil de 30 días, conforme al documento maestro |
| Saldo oficial | Consultado antes de la tanda; bloquear por el menor disponible entre los controles aplicables |
| Capacidad de tanda | Reducirla con cada GET de vuelos iniciado, además de registrar su reserva local |
| Cuenta gratuita | No descuenta el límite local de vuelos; se informa aparte |
| Reintentos | Ningún reintento de vuelos automático; un error requiere otra acción y autorización de ensayo |
| Caché del proveedor | Aceptada; no anticipar ahorro ni devolver reservas solo por suponer que un resultado fue cacheado |
| Concurrencia | Un `Mutex` compartido por casos de uso y reserva transaccional Room; botones inhabilitados inmediatamente durante la ejecución |
| Cancelación | Cancelar la coroutine y la llamada HTTP; no lanzar el regreso si se canceló después de la ida |
| Cierre o muerte del proceso | No reanudar el ensayo automáticamente; conservar reservas ya registradas |
| Cambios de reloj | No borrar consumo ni liberar capacidad por timestamps sospechosos; mostrar diagnóstico y revisar antes de continuar |

La unidad local se registra justo antes de despachar cada llamada. Si el proceso muere entre esa reserva y el envío, puede sobreestimar consumo; es una decisión conservadora. No se prometerá una transacción única entre la base local y el servidor externo.

Se empleará `viewModelScope`, `StateFlow` y observación ligada al ciclo de vida; las composables no harán GET desde su cuerpo. Una rotación o recomposición no inicia otra consulta. No se usarán `GlobalScope` ni llamadas bloqueantes de red en el hilo principal.

El presupuesto local cubre esta instalación. Otros programas o aplicaciones con la misma cuenta pueden consumir saldo externo; por eso el contador local no reemplaza Account API. En las pruebas normales se usarán mocks y la validación real se concentrará en la variante release.

## 15. Pantallas, estados y mensajes

### 15.1. Pantalla de credencial

Campo oculto, acciones guardar/sustituir/eliminar y botón `Probar conexión`. Mostrar `Clave configurada` sin revelar su contenido, resultado de conexión, saldo y hora de comprobación. Una operación destructiva explícita, como eliminar la credencial, se confirma; no se incluye la clave en capturas o mensajes.

### 15.2. Pantalla de prueba

- Ruta CTG ↔ CLO, fechas editables y controles de calendario.
- Indicadores fijos: `1 adulto`, `Económica`, `Ida y vuelta`, `COP`, `Aerolíneas: Todas`.
- Checkbox de vuelos directos marcado inicialmente y explicación de que desmarcar permite conexiones.
- Acción manual de prueba, confirmación del máximo de llamadas y progreso de las etapas.
- Contador de GET iniciados durante el ensayo y estado del presupuesto local/oficial.
- Acción de cancelar mientras existe una llamada activa, con presupuesto ya reservado conservado.

### 15.3. Resultado y diagnóstico

Mostrar ruta y fechas aplicadas, estado de verificación, total COP si se comprobó, ida y regreso con segmentos/horarios/operadores, escalas, momento de observación, advertencia de precio observado y enlace seguro cuando exista.

Mostrar también todas las compañías detectadas en las respuestas, separando detectadas de comprobadas para ida y vuelta. Un total de una combinación mixta se atribuye a esa combinación, no se presenta como una tarifa íntegramente operada por una sola compañía.

Estados técnicos mínimos: `SIN_CLAVE`, `LISTO`, `COMPROBANDO_CUENTA`, `ESPERANDO_AUTORIZACION`, `CONSULTANDO_IDA`, `CONSULTANDO_REGRESO`, `VERIFICADO`, `PARCIAL`, `SIN_RESULTADOS_DEVUELTOS`, `NO_VERIFICADO`, `ERROR_CREDENCIAL`, `ERROR_RED`, `SALDO_SIN_VERIFICAR`, `CUOTA_AGOTADA`, `ERROR_SERVICIO` y `CANCELADO`.

`SIN_RESULTADOS_DEVUELTOS` describe la respuesta consultada; no equivale a afirmar que no existen vuelos en el mercado. `VERIFICADO` significa que se comprobó el itinerario y su precio observado, no una reserva.

La interfaz será desplazable, accesible con tamaños de fuente mayores, controles táctiles adecuados, contraste legible y tratamiento de teclado e insets. El formulario puede restaurar datos no sensibles; ni la clave ni los tokens se guardarán en estado de instancia.

## 16. Paso a paso de implementación y comprobación

Los pasos se ejecutarán en orden. Cada salida se revisará antes de depender de ella en el paso siguiente. No se fija una duración ficticia: descargas, disponibilidad de dispositivo y respuesta del proveedor pueden modificar los tiempos.

### Paso 0. Revisar y cerrar el plan de la prueba

- Contrastar este documento con `REQUIREMENTS.md` y conservar la separación entre PoC y aplicación completa.
- Registrar el alcance inicial: dos GET como máximo, una salida con regreso asociado y todas las compañías de las respuestas procesadas sin filtros.
- Confirmar la autorización de programación según el documento maestro; la cuota real tiene una autorización distinta.
- **Salida:** plan revisable y alcance de desarrollo delimitado. **GET de vuelos:** 0.

### Paso 1. Preparar y comprobar el entorno Android

- Localizar instalaciones existentes de Android Studio, SDK, Gradle, ADB y dispositivos antes de proponer cualquier otra fuente de herramientas.
- Configurar para este proyecto el JDK correcto; comprobar que el Java 8 de `PATH` no ejecuta Gradle.
- No instalar programas o bibliotecas en Windows, no modificar el registro y no aceptar licencias/descargar componentes sin permiso explícito del propietario y compatibilidad con la política de red.
- Si faltan herramientas, detener la compilación y documentar qué componentes y repositorios faltan; continuar solo cuando exista un entorno preconfigurado autorizado o se aclaren las fuentes permitidas.
- Con herramientas permitidas ya disponibles, documentar versiones y rutas sin publicar datos personales. Detectar un teléfono Android 13+ mediante ADB, registrar modelo/versión/API y confirmar conectividad y reloj razonable.
- **Archivos:** `docs/ENTORNO_Y_VERSIONES.md`. **Salida:** inventario verificable y límites de la política documentados. **GET de vuelos:** 0.

### Paso 2. Crear el esqueleto Android y fijar dependencias

- Crear `settings.gradle.kts`, builds Kotlin DSL, catálogo de versiones, Wrapper y módulo `app`.
- Añadir `.gitignore` para `.gradle`, builds, SDK local, rutas de máquina, archivos de credenciales y firma; conservar los archivos necesarios del Wrapper y las pruebas.
- Configurar namespace, variantes, `minSdk`, SDKs de compilación/objetivo, objetivo JVM común, Compose y recursos en español.
- Usar en debug un `applicationIdSuffix` como `.debug` para evitar reemplazar una app release con firma diferente. El release conserva la identidad estable prevista para la evolución del proyecto.
- Resolver dependencias y registrar versiones exactas y repositorios. La verificación de dependencias se revisará contra fuentes de confianza; generar metadatos por sí solo no acredita autenticidad.
- Compilar e instalar una pantalla mínima clara, sin cliente real activado por el arranque.
- **Salida:** app mínima que abre y evidencia de primera compilación/arranque. **GET de vuelos:** 0.

### Paso 3. Implementar modelos y validadores puros

- Crear criterios inmutables, modelos de segmentos y resultados tipados.
- Implementar validación de fechas, continuidad, escalas y dinero exacto con reloj inyectable.
- Añadir fixtures sintéticos, identificados como tales, para vuelos directos, conexiones, datos incompletos y respuestas vacías.
- Probar límites y casos de error antes de usar esos modelos en UI o red.
- **Salida:** validadores con pruebas JVM aprobadas. **GET de vuelos:** 0.

### Paso 4. Implementar la custodia local de la API key

- Crear `CredentialStore` y su implementación Android Keystore; resolver cifrado, sustitución, eliminación e invalidación.
- Construir la pantalla de credencial y exclusiones de backup/transferencia.
- Ejecutar pruebas instrumentadas con secretos ficticios, reinicio del proceso y datos cifrados manipulados.
- Verificar que no aparezcan valores de prueba sensibles en errores, estado guardado ni logs. No introducir una clave real en las herramientas de desarrollo.
- **Salida:** una clave de prueba se conserva cifrada y se recupera o rechaza de forma controlada. **GET de vuelos:** 0.

### Paso 5. Implementar Account API con pruebas offline

- Crear el servicio Retrofit, DTO mínimos, mapeo seguro y caso de uso de cuenta.
- Configurar OkHttp sin logs de secretos, sin reintentos ocultos y con timeouts acotados.
- Probar autenticación válida, saldo cero, respuesta mal formada, límites, renovación nula, errores de red y datos sensibles adicionales.
- Verificar con MockWebServer que `Probar conexión` llama al endpoint de cuenta y no al de vuelos.
- **Salida:** cuenta y errores correctamente mostrados usando respuestas simuladas. **GET reales de vuelos:** 0; las llamadas de test son locales.

### Paso 6. Implementar el parser de vuelos y la asociación ida/regreso

- Crear DTO y mapeo de ambas etapas sin convertir campos ausentes en datos válidos.
- Procesar todas las listas y compañías recibidas; probar Wingo, operadores desconocidos y combinaciones mixtas.
- Construir el segundo GET con el token de una salida validada y exactamente los mismos criterios.
- Probar que dos precios totales no se suman y que un regreso ausente o incompatible impide clasificar la cotización como completa.
- **Salida:** flujo de dos etapas reproducible con fixtures y evidencia de peticiones correctas. **GET reales de vuelos:** 0.

### Paso 7. Implementar presupuesto, exclusión mutua y experiencia completa

- Crear Room y el DAO transaccional de reservas; probar persistencia, ventana móvil y límites.
- Unir el caso de uso de cuenta, presupuesto, salidas, token, regreso y validación bajo un coordinador manual único.
- Implementar confirmación de hasta dos GET, progreso, cancelación, estados y resultado visible.
- Probar doble toque, rotación, edición concurrente, cierre del proceso y fallo entre reserva y respuesta.
- Comprobar que el regreso no se solicita si se agotó capacidad, se canceló o faltó una salida válida.
- **Salida:** ensayo completo offline desde UI sin consumo externo, con llamadas locales contadas. **GET reales de vuelos:** 0.

### Paso 8. Ejecutar QA offline y generar un primer release de prueba

- Ejecutar unitarias, pruebas HTTP locales, instrumentadas disponibles y análisis Android Lint.
- Corregir fallos y revisar seguridad, accesibilidad, dependencias y arranque.
- Preparar la configuración segura de firma y generar APK debug y release. La clave release queda local, bajo custodia del propietario; no se incluye con los APK.
- Verificar firma, metadatos y SHA-256; instalar el release para las pruebas reales. Las instrumentadas de debug usan fakes y no comparten secretos con release.
- **Salida:** release instalable de prueba y reportes offline; cualquier prueba sin dispositivo se registra como no ejecutada. **GET de vuelos:** 0.

### Paso 9. Validar conexión real y saldo desde el release

- El propietario introduce su API key directamente en la aplicación instalada.
- Pulsar `Probar conexión`, comprobar respuesta real, saldo, hora y errores sanitizados.
- Confirmar que no se ejecutó ningún GET de vuelos y que los datos identificativos de la cuenta no aparecen en la UI ni en evidencias.
- Si no hay clave, cuenta válida, conectividad o saldo suficiente, corregir la condición o registrar bloqueo antes de la prueba siguiente.
- **Salida:** Account API comprobada contra la cuenta real. **GET de vuelos:** 0; registrar por separado las llamadas gratuitas de cuenta.

### Paso 10. Ejecutar el primer ensayo real de ida y vuelta

- Obtener autorización específica para **hasta dos GET de vuelos** con ruta, fechas y directos indicados.
- Ejecutar el flujo de la sección 12, primero con la casilla de directos marcada.
- Guardar evidencia sanitizada mínima del vínculo token/salida/regreso, campos de precio, moneda y criterios; nunca el secreto ni tokens completos.
- Documentar si existe total inequívoco, qué operadores se detectaron, si apareció Wingo y qué quedó sin comprobar.
- **Salida:** resultado real completo verificable o bloqueo concreto de la interpretación. **GET de vuelos:** máximo 2 en esta autorización; pueden ser 0 o 1 si el flujo se bloquea antes.

### Paso 11. Ampliar pruebas únicamente cuando sean necesarias

- El modo de conexiones se comprueba primero con fixtures. Si se autoriza comprobarlo contra el proveedor, realizar otra tanda con la casilla desmarcada y presupuesto propio de hasta dos GET.
- Si hace falta otro candidato, otras fechas o comparar cobertura, definir antes objetivo, solicitudes máximas y saldo disponible. No recorrer alternativas automáticamente fuera del límite ya autorizado.
- `show_hidden`, `deep_search` y cualquier otro experimento adicional no se activarán para acumular búsquedas sin una pregunta técnica concreta.
- No repetir llamadas reales por rutina cuando la evidencia disponible ya resuelve el objetivo. Los cambios o fallos nuevos justificarán qué comprobación se repite.
- **Salida:** informe que distingue pruebas offline, escenarios reales ejecutados y escenarios reales pendientes. **GET adicionales:** solo los expresamente autorizados.

### Paso 12. Corregir, comprobar actualización y cerrar binarios

- Si el JSON real obliga a ajustar modelos o semántica, convertir la evidencia sanitizada en un fixture mínimo y añadir una prueba que reproduzca el problema.
- Ejecutar las pruebas afectadas, Lint y compilación de los artefactos finales; ampliar la regresión solo si los cambios o fallos lo justifican.
- Verificar al menos una actualización release sobre release con la misma identidad/firma y un `versionCode` superior, conservando credencial cifrada y presupuesto local. Registrar las versiones reales utilizadas y el nombre final de cada APK.
- No instalar release sobre debug suponiendo que sus certificados coinciden ni desinstalar silenciosamente para ocultar un fallo de actualización.
- **Salida:** artefactos finales identificables, firma válida y evidencia de actualización o prueba explícitamente no ejecutada. **GET de vuelos:** 0 para las comprobaciones de instalación y conservación.

### Paso 13. Entregar y decidir el paso a la aplicación completa

- Entregar código Kotlin y Gradle íntegros, dos APK cuando sea posible, checksums, README e informes.
- Revisar todos los criterios de la sección 22 y clasificar cada uno como aprobado, fallido o no ejecutado.
- Informar búsquedas reales iniciadas, consumo oficial observado, llamadas de cuenta y límites de cobertura.
- Presentar el resultado de la puerta técnica: servicio validado para el alcance comprobado, integración pendiente de evidencia o bloqueo demostrado.
- Avanzar a alertas, programación y avisos después de cerrar esa puerta y contar con la autorización correspondiente. La auditoría de la app completa seguirá el checklist maestro, no se dará por completada con este PoC.

## 17. Matriz de pruebas previstas

**Situación actual de todos los casos de aplicación:** no ejecutados. Esta matriz define qué se hará; la lectura del documento y la consulta de documentación no constituyen pruebas de la APK.

| ID | Nivel | Caso y resultado esperado |
|---|---|---|
| ENV-01 | Entorno | Wrapper ejecutado con el JDK previsto, sin usar el Java 8 accidental de `PATH` |
| ENV-02 | Entorno | SDK, dependencias y dispositivo/API identificados; compilación reproducible con versiones fijas |
| SEC-01 | Android | Guardar/reabrir una clave ficticia; el archivo persistido no contiene su texto plano |
| SEC-02 | Android | Cifrados sucesivos usan IV distintos; ciphertext alterado se rechaza |
| SEC-03 | Android | Sustituir/eliminar la clave invalida su saldo anterior; no usar otra credencial en mitad de una tanda |
| SEC-04 | Android | Clave perdida o invalidada requiere reintroducción, sin crash ni fallback inseguro |
| SEC-05 | JVM/Android | Excepciones HTTP/JSON con claves y tokens ficticios no los revelan en UI, logs ni reportes |
| SEC-06 | Revisión/Android | Configuración de backup/transferencia y pantalla de clave no exponen credenciales; registrar límites de la prueba disponible |
| ACC-01 | HTTP local | Probar conexión invoca cuenta; cero solicitudes al endpoint de vuelos |
| ACC-02 | JVM/UI | Saldo cero con credencial válida se distingue de autenticación fallida |
| ACC-03 | JVM/UI | Renovación nula, campos nuevos y campos sensibles no rompen el modelo ni se muestran indebidamente |
| ACC-04 | HTTP local | Fallo de cuenta previo a la tanda bloquea todos los GET de vuelos, aunque exista saldo antiguo o contador local disponible |
| REQ-01 | HTTP local | Primera búsqueda usa ruta, fechas, adulto/clase/COP y filtros acordados; no incluye/excluye compañías |
| REQ-02 | HTTP local | `stops=1` y `stops=0` se transmiten coherentemente en ambas etapas |
| REQ-03 | HTTP local | Segunda búsqueda conserva criterios e incluye solo el token correcto, sin invertir CTG/CLO |
| PAR-01 | JVM | `best_flights` ausente, listas vacías, `other_flights` presente y campos nuevos tienen tratamiento correcto |
| PAR-02 | JVM | Error de negocio, JSON inválido o estado no final no se convierten en precio válido |
| PAR-03 | JVM | Una opción inválida no oculta otras parseables; se registra su descarte |
| VAL-01 | JVM | Ida y regreso directos válidos producen un itinerario completo cuando la evidencia de precio es suficiente |
| VAL-02 | JVM | Escala en cualquiera de las direcciones se rechaza con directos marcado |
| VAL-03 | JVM | Conexiones y cruce de medianoche se aceptan solo con continuidad y fechas locales correctas |
| VAL-04 | JVM | Aeropuerto, fecha, secuencia o regreso anteriores a completar la ida producen rechazo |
| VAL-05 | JVM | Cambio de zona del móvil no cambia las fechas de aeropuerto; búsqueda del mismo día descarta horas ya pasadas |
| VAL-06 | JVM | Sin token o sin regreso comprobable: resultado parcial/no verificado, nunca éxito completo |
| PRI-01 | JVM | Dos campos con total ida/vuelta no se suman; evidencia ambigua impide resultado verificado |
| PRI-02 | JVM | COP/un adulto/económica contradictorios o no acreditados no se sustituyen por supuestos |
| PRI-03 | JVM | Precio cero, negativo, fraccionario COP, overflow o no numérico se rechaza; representación integral decimal se normaliza exactamente |
| PRI-04 | JVM/UI | Desglose ausente se muestra como no disponible; no se inventa equipaje o precio por trayecto |
| AIR-01 | JVM/HTTP local | Wingo devuelta en un fixture se procesa y se muestra sin filtros |
| AIR-02 | JVM/HTTP local | Compañía desconocida y combinación mixta se conservan sin lista blanca |
| AIR-03 | JVM/UI | Compañía no retornada no se declara inexistente; detectada sin regreso no se declara comprobada |
| AIR-04 | JVM/UI | Cobertura indica todas las compañías recibidas y cuáles quedaron pendientes al usar un único token |
| BUD-01 | Room/Android | Reservas de GET de vuelos sobreviven al cierre; Account API no descuenta ese límite |
| BUD-02 | JVM/Room | Menor presupuesto oficial/local bloquea ida o regreso cuando corresponde; ventana móvil no se reinicia arbitrariamente |
| BUD-03 | HTTP local | Exactamente 0, 1 o 2 GET de vuelos según bloqueo, ausencia de token o éxito; nunca una tercera petición automática |
| BUD-04 | JVM/Room | Timeout/cancelación conserva la reserva; no se restituye por asumir caché gratuita |
| CON-01 | UI/HTTP local | Doble toque, recomposición y rotación no duplican llamadas |
| CON-02 | JVM/HTTP local | Cancelación entre etapas impide el regreso; una tanda activa usa criterios inmutables |
| CON-03 | Android | Muerte del proceso no reinicia la búsqueda; credencial y reservas conservan su tratamiento previsto |
| ERR-01 | HTTP local/UI | 401/403, 429, 5xx, timeout y red desconectada producen estados seguros, sin bucles de reintentos |
| UI-01 | Compose | Formulario inválido o confirmación cancelada genera cero GET de vuelos |
| UI-02 | Compose | Tema claro, textos COP, estados, accesibilidad, scroll, teclado y fuente ampliada son utilizables |
| UI-03 | Compose | Cambio de fechas/filtro marca el resultado anterior con sus criterios originales y exige nueva prueba |
| URL-01 | JVM | Enlaces peligrosos o con secretos se descartan; ausencia de enlace no inventa uno |
| APK-01 | Build/Android | Debug y release se generan, la firma se verifica y el release abre en Android 13+ disponible |
| APK-02 | Android | Actualización release con firma/identidad estables conserva secreto cifrado y presupuesto sin desinstalar |
| LIVE-01 | Real | Account API autentica y refleja saldo; ninguna búsqueda de vuelos por probar conexión |
| LIVE-02 | Real | Ensayo autorizado con token demuestra combinación y semántica del total, o documenta por qué no puede demostrarlas |
| LIVE-03 | Real opcional | Modo con escalas u otra salida, solo con autorización adicional; no se marca ejecutado a partir de un fixture |

No se gastará cuota para simular 429, claves inválidas, caída de red, concurrencia o JSON mal formado: esos escenarios se ensayarán con fakes/MockWebServer. Ambos endpoints se simularán en pruebas offline; sustituir solo uno podría producir consumo accidental.

## 18. Comandos previstos y evidencia de ejecución

Los siguientes comandos son una guía para cuando exista el proyecto y estén configuradas las herramientas. **No se han ejecutado sobre una app en este proyecto.** Se invocarán por separado y con el JDK/SDK documentados.

### 18.1. Compilación y pruebas

```powershell
.\gradlew.bat --version
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintRelease
.\gradlew.bat :app:assembleRelease
```

La tarea instrumentada requiere dispositivo o emulador compatible conectado. La firma release requiere configuración local segura; no se pasarán contraseñas como argumentos visibles de terminal ni se escribirán en este documento. Un fallo de dependencia, SDK o firma se registrará con diagnóstico sanitizado y su estado real.

### 18.2. Inspección e instalación

Con Platform Tools y Build Tools localizados, se emplearán estos comandos, sustituyendo las rutas por las comprobadas:

```powershell
adb devices
apksigner verify --verbose --print-certs .\outputs\serpapi-poc-0.1.0-release.apk
Get-FileHash -LiteralPath .\outputs\serpapi-poc-0.1.0-release.apk -Algorithm SHA256
adb install -r .\outputs\serpapi-poc-0.1.0-release.apk
adb shell am start -n com.flightnotifications.app/.MainActivity
```

La selección de dispositivo será explícita si hay más de uno conectado; se usará su serial local sin publicarlo en informes. El nombre `0.1.0` ilustra el primer artefacto: si una corrección o prueba de actualización cambia versión, se registrarán la ruta y versión finales efectivamente generadas.

`install -r` conserva datos solo si Android admite esa actualización y coinciden los requisitos de identidad/firma; un error no autoriza desinstalar automáticamente. [Firma y actualizaciones Android](https://developer.android.com/studio/publish/app-signing).

## 19. Registro de llamadas y pruebas reales

Se mantendrán dos mediciones diferentes: GET de vuelos iniciados por el PoC y consumo/saldo informado por el proveedor. No se declararán iguales si la caché, los fallos o el momento de actualización producen diferencias.

| Ensayo | Objetivo | Cuenta gratuita | Máximo de GET de vuelos | Condición |
|---|---|---|---|---|
| Offline | Parser, UI, seguridad, cuota y errores | 0 reales | 0 reales | Endpoints simulados |
| Conexión real | Credencial y saldo | Una comprobación manual, con reintentos solo justificados | 0 | Clave introducida localmente |
| Primera ida/vuelta directa | Semántica del total y regreso asociado | Comprobación previa; refresco posterior si procede | 2 | Autorización expresa de ese ensayo |
| Ida/vuelta con conexiones | Comprobar otro modo contra el proveedor | Comprobación de su tanda | 2 adicionales | Autorización adicional |
| Más candidatos o cobertura | Resolver una incertidumbre concreta | Según tandas autorizadas | A definir antes de ejecutar | Presupuesto y objetivo explícitos |

Cada ensayo real registrará fecha/hora, criterios, variante/versión, autorización y máximo acordados, número de GET iniciados por etapa, resultado, saldo antes/después cuando esté disponible y explicación de diferencias. El ID interno del ensayo no incluirá secretos.

El presupuesto se refiere a solicitudes potencialmente descontables, no a una autorización para comprar un plan, cargar dinero o activar servicios de pago. El saldo oficial de la cuenta se comprobará; no se asumirá que todos los usuarios tienen el mismo cupo publicado.

## 20. Evidencias, fixtures y documentación resultante

| Archivo futuro | Contenido |
|---|---|
| `README.md` | Requisitos, preparación, build, pruebas, instalación, uso de la prueba, configuración de clave, consumo y limitaciones |
| `docs/ENTORNO_Y_VERSIONES.md` | JDK, SDK, Build Tools, Wrapper, plugins y dependencias resueltos; comandos y dispositivo/API de validación |
| `docs/INFORME_PRUEBAS_POC.md` | Casos aprobados/fallidos/no ejecutados, comandos, resultados, evidencias sanitizadas y bloqueos |
| `docs/SEMANTICA_PRECIO_SERPAPI.md` | Solicitud sin secretos, campos relevantes de ambas etapas, vínculo con regreso y conclusión sobre el total |
| `src/test/resources/fixtures/` | Casos sintéticos identificados y extractos reales mínimos sanitizados cuando existan |
| `outputs/` | APK debug, APK release firmado y SHA-256 de los binarios reales |

Los JSON completos reales se inspeccionarán transitoriamente en el entorno local seguro solo cuando haga falta. Para pruebas reproducibles se conservarán extractos mínimos sanitizados: eliminar claves, tokens completos, datos de cuenta y URLs sensibles. Para reproducir la relación de tokens, sustituirlos de forma consistente por identificadores ficticios en ambas etapas.

El informe de precio explicará por qué el total corresponde a la combinación o por qué no puede demostrarse; no bastará una captura de un número. Incluirá la ausencia de desglose cuando corresponda y distinguirá observación de precio de reserva.

Los informes automáticos de Gradle se revisarán antes de compartirlos. Las capturas mostrarán únicamente pantallas sin secretos. Los fixtures nunca se presentarán como tarifas reales: si aparecen en una UI de desarrollo, llevarán `DATOS DE PRUEBA`; la variante release destinada al ensayo utilizará el servicio real y no un fallback simulado.

## 21. Riesgos, dependencias y respuesta prevista

| Riesgo o dependencia | Efecto | Tratamiento |
|---|---|---|
| SDK/ADB no localizados y dispositivo no inventariado | Impide compilar o ejecutar Android hasta resolverlo | Preparar entorno y registrar rutas/versiones; no declarar instalación aprobada sin evidencia |
| Java 8 en `PATH` frente a JDK 21 configurado | Gradle puede fallar o ejecutarse con runtime incorrecto | Invocación y configuración explícitas; comprobación del Wrapper |
| Combinación de plugins todavía sin compilar | Puede existir incompatibilidad Kotlin/AGP/Compose/serialización/KSP | Resolver matriz y artefactos antes de construir funciones; registrar números definitivos |
| Sin cuenta/clave real | Permite avanzar con mocks, pero no certificar integración real | Entrada local del propietario y prueba gratuita de conexión |
| Sin autorización de cuota o saldo suficiente | Impide el ensayo real de vuelos | Mantener pruebas offline y marcar la integración real pendiente |
| Proveedor no devuelve resultados o token | No hay evidencia de un regreso asociado | Informar respuesta obtenida, candidatos y causa; no fabricar una cotización |
| Precio ambiguo o campos cambiados | Riesgo de duplicar el total o atribuir precio a otra combinación | Resultado no verificado, fixture mínimo y bloqueo de futuras notificaciones hasta resolver |
| Wingo u otra compañía no aparece | No permite afirmar ausencia de vuelos ni auditoría completa | Estado de cobertura sin comprobar; mantener búsqueda sin filtros |
| Un único token por primer ensayo | Cobertura de combinaciones parcial | Explicitar qué quedó pendiente y presupuestar cualquier ampliación |
| Caché y tarifas cambiantes | El precio puede diferir al abrir la fuente | Fecha/antigüedad visibles y texto de precio observado |
| Red, timeout o cierre tras enviar | El cobro real puede ser desconocido | Conservar reserva, mostrar intento y no reintentar automáticamente |
| Uso de la misma cuenta desde otras herramientas | Saldo oficial difiere del contador de esta instalación | Verificar cuenta antes de cada tanda y mostrar ambos controles |
| Firma release perdida o cambiada | Puede impedir actualizaciones conservando datos | Custodia de clave/credenciales y prueba release sobre release |
| Dispositivo comprometido | Riesgo residual de extracción en memoria | Keystore, mínima exposición y explicación del límite; no prometer invulnerabilidad |
| Restricciones de Android en segundo plano | Afectarán al monitoreo completo | No se consideran probadas en el PoC manual; se ensayarán con WorkManager en su fase |

Cambiar de proveedor, consultar directamente aerolíneas, comprar un plan o ampliar funcionalidades por un bloqueo exige una decisión expresa del propietario. Un bloqueo técnico no autoriza sustituir el alcance acordado.

## 22. Criterios de aceptación de la APK básica

Todos estos criterios están **pendientes** al crear el plan. Al entregar se marcarán con evidencia y resultado; un criterio no ejecutado no se contabiliza como aprobado.

- [ ] El proyecto abre o se construye por CLI con toolchain documentado y dependencias fijas; no depende del Java 8 accidental.
- [ ] Se genera y localiza el APK debug.
- [ ] Se genera y localiza el APK release firmado con clave propia estable; se verifica su firma y SHA-256.
- [ ] El release se instala y abre en Android 13+ disponible, y se registra dispositivo/API y versión real probada.
- [ ] Una actualización release sobre release conserva API key cifrada y presupuesto local; si falta medio de prueba, se registra no ejecutada.
- [ ] La app inicia en tema claro, español y sin búsquedas automáticas de vuelos.
- [ ] La API key se introduce localmente y puede guardarse cifrada, sustituirse y eliminarse sin exposición en archivos, UI de resultados, logs o informes.
- [ ] Keystore y fallos de descifrado se comprueban con credenciales ficticias, incluyendo la exclusión de backup/transferencia aplicable.
- [ ] `Probar conexión` llama únicamente a Account API, muestra saldo real/antigüedad y distingue credencial inválida de saldo agotado.
- [ ] Fallo de cuenta previo a la tanda bloquea los GET de vuelos aunque haya saldo antiguo o presupuesto local.
- [ ] Los criterios CTG–CLO, fechas, adulto, económica, COP y checkbox se muestran y transmiten correctamente; no hay selector ni filtros de aerolíneas.
- [ ] El primer ensayo solicita autorización de hasta dos GET y respeta el límite; cancelarlo no consume búsquedas.
- [ ] Ida y token/regreso conservan criterios inmutables; no se invierten aeropuertos en la solicitud con token.
- [ ] Se validan localmente segmentos, fechas, horas pasadas cuando corresponda, continuidad, escalas y combinación completa.
- [ ] El precio total no se obtiene sumando ciegamente las dos etapas; la interpretación se documenta con evidencia real o se declara bloqueada.
- [ ] La ausencia de desglose, equipaje u otras condiciones se informa sin inventar importes ni garantías.
- [ ] Todos los operadores de las respuestas se procesan; Wingo y una compañía desconocida se cubren en fixtures, y la presencia/ausencia real de Wingo se reporta honestamente.
- [ ] Se distingue cobertura detectada, comprobada y pendiente; un único token no se presenta como auditoría exhaustiva del mercado.
- [ ] Se respeta el límite conservador persistente; Account API no descuenta ese contador y cada GET de vuelos iniciado reserva capacidad antes del envío.
- [ ] Doble toque, rotación, cancelación, fallo y muerte del proceso no generan solicitudes adicionales ocultas.
- [ ] Errores de autenticación, cuota, red, servidor y JSON tienen estados visibles sin secretos ni reintentos ilimitados.
- [ ] Enlaces y fechas de observación son seguros y verificables; ningún precio previo se presenta como recién confirmado después de un error.
- [ ] Pasan las pruebas offline e instrumentadas aplicables; se informa por ID cuáles fallaron o no se pudieron ejecutar.
- [ ] Se ejecuta al menos un ensayo real completo autorizado con `departure_token`, o se declara que la integración real todavía no puede darse por comprobada.
- [ ] La variante release utiliza el servicio real y no respuestas simuladas como sustitución de una integración fallida.
- [ ] README, versiones, semántica de precio, resultados de pruebas, llamadas exactas y pendientes quedan entregados sin secretos.

**Puerta de salida:** solo se declara comprobado el servicio de ida y vuelta cuando exista evidencia de al menos una combinación real y de la semántica de su total. Se puede entregar código o APK con integración bloqueada, pero se describirá como entrega parcial, no como servicio demostrado. La aceptación de esta APK básica no equivale a la aceptación de toda la aplicación de alertas.

## 23. Trazabilidad con el documento maestro

| Sección de `REQUIREMENTS.md` | Aplicación en este plan |
|---|---|
| 0. Prompt, precedencia y autorizaciones | Conservados; desarrollo y cuota real se distinguen de redactar este plan |
| 1–2. Decisiones y alcance V1 | Se conserva plataforma/fuente/seguridad; se ejecuta primero el subconjunto manual acordado y se explicitan funciones diferidas |
| 3. Arquitectura Android | Kotlin, Compose, MVVM, repositorios, Retrofit/OkHttp, una serialización, Keystore, Room para cuota y DataStore no sensible |
| 4. Integración SerpApi | Caso CTG–CLO, dos etapas, token, precio completo y operadores sin filtros; ensayo inicial de un candidato |
| 5. Reglas funcionales | Se implementan validaciones aplicables al formulario y a la cotización; reglas de alertas/avisos quedan para la app completa |
| 6. Segundo plano | Diferido expresamente; este PoC no declara WorkManager probado |
| 7. Cuota y saldo | Account API previa, bloqueo por fallo, límite local persistente, reservas y ausencia de GET no autorizados |
| 8. Seguridad | Keystore, backup, HTTPS, permisos mínimos y redacción de secretos |
| 9. Pantallas | Credencial, formulario manual y resultado; pantallas de alertas/avisos y catálogo completo diferidas |
| 10. Persistencia | Solo presupuesto y ajustes necesarios para el PoC; sin tablas de histórico de precios |
| 11. Errores | Tratamiento y pruebas de los casos pertinentes a cuenta y consulta manual |
| 12. Fases | Se desarrolla primero la fase de prueba de concepto, con paso a paso y puerta técnica de validación |
| 13. Pruebas | Casos de parser, fechas, escalas, precio, aerolíneas, cuota, secretos y UI adaptados al alcance; notificaciones/Workers pendientes |
| 14. Instrucciones para Codex | Sin afirmaciones de ejecución sin evidencia ni cambios de fuente/alcance por iniciativa propia |
| 15. Dependencias de prueba real | Cuenta, clave local y autorización de cuota; herramientas/dispositivo se verifican por separado |
| 16. Fuentes | Referencias oficiales de API y Android, ampliadas con versiones y build |
| 17. Entrega completa | Solo se evalúa aquí el subconjunto del PoC; la auditoría completa permanece pendiente |
| 18. Seguridad operativa y calidad | Inicio sin búsquedas, fechas de aeropuerto, timestamps UTC, secretos fuera de informes y APK reales |
| 19. Decisiones consolidadas | Se conservan; las funciones completas se desarrollarán tras resolver la integración |

## 24. Reutilización y transición a la aplicación completa

Se reutilizarán el cliente HTTPS, DTO comprobados, validadores, repositorio SerpApi, custodia de credencial, control de presupuesto, formateadores y fixtures sanitizados.

Después se ampliarán los modelos Room con alertas y último resultado, formularios de rutas nacionales, catálogo informativo versionado y selección adaptativa de candidatos. Se añadirá la deduplicación persistente y el menor precio notificado por itinerario, manteniendo la ausencia de historial de cotizaciones.

El caso de uso de consulta podrá invocarse desde UI y desde WorkManager mediante la misma protección de concurrencia y cuota. Posteriormente se incorporarán notificaciones y su permiso, frecuencias, reparto de presupuesto y las pruebas reales con interfaz cerrada, pantalla bloqueada y reinicio. Ninguna prueba manual del PoC demostrará por sí sola esa ejecución en segundo plano.

Antes de cerrar la estrategia de auditoría de compañías para la aplicación completa se concretará la amplitud deseada dentro de la fuente y presupuesto aprobados. Este plan no introduce fuentes adicionales ni afirma que ya se haya decidido cómo comprobar compañías ausentes de SerpApi.

## 25. Referencias técnicas y alcance de su comprobación

Las páginas siguientes se consultaron para elaborar el plan. Consultar documentación no acredita que las dependencias se hayan descargado ni que sus combinaciones se hayan compilado. Las versiones se volverán a comprobar al preparar el proyecto.

| Tema | Referencia |
|---|---|
| Servicio de vuelos y parámetros | [SerpApi Google Flights](https://serpapi.com/google-flights-api) |
| Cuenta y consulta gratuita | [SerpApi Account API](https://serpapi.com/account-api) |
| Cobertura de aerolíneas | [Ayuda de Google Flights](https://support.google.com/travel/answer/2475306?hl=es) |
| Android Studio estable | [Android Studio releases](https://developer.android.com/studio/releases) |
| AGP y Gradle/JDK compatibles | [AGP 9.4](https://developer.android.com/build/releases/agp-9-4-0-release-notes) |
| Kotlin estable | [Kotlin releases](https://kotlinlang.org/docs/releases.html) |
| Kotlin incorporado de AGP | [Built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin) |
| Compose BOM | [BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping) |
| Activity | [Activity releases](https://developer.android.com/jetpack/androidx/releases/activity) |
| Lifecycle | [Lifecycle releases](https://developer.android.com/jetpack/androidx/releases/lifecycle) |
| Retrofit | [Repositorio de publicaciones](https://github.com/square/retrofit/releases) |
| Serialización | [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization/releases) |
| Corrutinas | [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines/releases) |
| Ajustes locales | [DataStore releases](https://developer.android.com/jetpack/androidx/releases/datastore) |
| Base local de presupuesto | [Room releases](https://developer.android.com/jetpack/androidx/releases/room) |
| Cifrado | [Android Keystore](https://developer.android.com/privacy-and-security/keystore) |
| Backup/transferencia | [Android Auto Backup](https://developer.android.com/identity/data/autobackup) |
| APK y actualización | [Firma de aplicaciones Android](https://developer.android.com/studio/publish/app-signing) |

Las consultas a sitios de referencia no usaron una clave ni ejecutaron búsquedas de vuelos. No se confirma una versión exacta de BOM, OkHttp, KSP o bibliotecas de pruebas mientras no se resuelvan desde sus publicaciones y se valide su compatibilidad.

## 26. Situación histórica al guardar este documento

| Elemento | Estado actual |
|---|---|
| Plan y alcance técnico | Documentados en este archivo |
| Inventario inicial de entorno | Comprobado por comandos de lectura, con límites descritos en sección 5 |
| Documentación pública de referencia | Consultada; versiones candidatas diferenciadas de combinación compilada |
| Código Android/Gradle | Todavía no creado |
| SDK y dispositivo de prueba | Pendientes de localizar/configurar y verificar |
| APK debug/release | Todavía no generados |
| Pruebas de la aplicación | No ejecutadas |
| Account API con la cuenta del propietario | No ejecutada |
| GET reales de vuelos realizados para este plan | **0** |
| Integración real y cobertura de Wingo | Pendientes de ensayo autorizado; sin afirmaciones de cobertura garantizada |

Esta tabla registra el estado inicial de la planificación y no el estado vigente del código. La decisión de pasar al monitoreo completo dependerá de los resultados comprobados de esta primera APK y de la aprobación del propietario.

## 27. Estado de implementación al 19 de octubre de 2026

| Paso o puerta | Trabajo realizado | Verificación pendiente o límite |
|---|---|---|
| Paso 0, alcance | Se conservan CTG–CLO, búsqueda sin filtro de aerolínea, una salida con token y máximo dos GET de vuelos | Ninguna solicitud real fue autorizada ni iniciada en este trabajo |
| Paso 1, entorno | Confirmados JDK Temurin 21, ausencia de Gradle/Wrapper, SDK, ADB, emulador y Android Studio en ubicaciones revisadas; se respetará la política que prohíbe instalar herramientas en Windows | Compilación bloqueada hasta disponer de un entorno preconfigurado permitido o de componentes/repositorios aprobados; no se reintentarán descargas |
| Paso 2, esqueleto | Gradle Kotlin DSL, módulo `app`, variantes, API 33/37, catálogo de versiones, manifest y recursos | Generar Wrapper con checksum oficial, resolver dependencias y compilar |
| Paso 3, dominio | Modelos, criterios, parsers y validación de ruta, fechas, escalas y secuencia | Pruebas JVM escritas, pendientes de ejecución |
| Paso 4, credencial | Android Keystore AES/GCM y archivo cifrado en área excluida de backup | Prueba instrumentada escrita, pendiente de ejecución en Android |
| Pasos 5 y 6, red y vuelos | Account API, errores acotados, cliente HTTPS sin reintento, parser de ambas listas y enlace por `departure_token` | Suite MockWebServer escrita; no se ha usado clave del propietario ni servicio real |
| Paso 7, UI y presupuesto | Compose, confirmación manual, bloqueo de concurrencia, reserva antes de cada GET y contador persistente | Tests de Room/UI escritos; ejecución pendiente. Las fechas se validan en zona Bogotá |
| Paso 8, QA/APK | README, informes, fixtures sintéticos y comandos previstos | Unitarias, Lint, build APK e instrumentadas todavía no ejecutadas; release requiere firma estable del propietario |
| Pasos 9 y 10, servicio real | No iniciados | Requieren clave introducida directamente en la app, saldo suficiente y autorización concreta de hasta dos GET con criterios visibles |
| Precio total | La interfaz evita sumar precios y marca el total como no verificado | Requiere comparar una respuesta real sanitizada antes de cambiar la etiqueta |

Estado detallado: [entorno y versiones](docs/ENTORNO_Y_VERSIONES.md), [informe de pruebas](docs/INFORME_PRUEBAS_POC.md) y [semántica del precio](docs/SEMANTICA_PRECIO_SERPAPI.md). La clave de SerpApi nunca debe pegarse en este documento, el código, la consola o el chat; se introduce directamente en la app instalada.
