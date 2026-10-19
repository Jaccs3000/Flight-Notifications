# Entorno y versiones

Estado registrado el 19 de octubre de 2026 para la primera implementación de Flight Notifications.

## Equipo de desarrollo

| Elemento | Estado |
|---|---|
| Sistema | Windows, workspace `C:\Flight Notifications` |
| JDK de `JAVA_HOME` | Eclipse Temurin OpenJDK 21.0.8+9; `javac` disponible dentro de ese JDK |
| `java` que resuelve el PATH global | Java Runtime 8.0.281; no debe usarse para Gradle |
| Android Studio | No localizado en las rutas habituales comprobadas |
| Android SDK / `sdkmanager` | No localizado |
| ADB / emulador | No localizados |
| Gradle global / Wrapper | No localizados; el Wrapper aún necesita generarse con la distribución oficial |
| Dispositivo Android 13+ | No conectado ni identificado |

No se instalarán herramientas en Windows ni se modificará el registro. Se inició una descarga de la distribución oficial Gradle 9.6.0 (ZIP de aproximadamente 43 MB) dentro de una carpeta del proyecto; se detuvo antes de extraerla o ejecutarla y el archivo se retiró. No se descargó el SDK ni se resolvieron dependencias. La verificación de compilación queda pendiente hasta disponer de un entorno preconfigurado/autorizado o de los repositorios y componentes aprobados por la política de red.

Los destinos de red que el build probablemente necesita son `services.gradle.org` (distribución Gradle), `dl.google.com` (Android SDK y Google Maven, incluyendo `dl.google.com/dl/android/maven2`), `repo.maven.apache.org` (Maven Central) y `plugins.gradle.org` (Gradle Plugin Portal). Los repositorios pueden redirigir a otros hosts, por lo que esta lista debe validarse con la política local antes de sincronizar. Una prueba real de la aplicación también necesitará que el teléfono pueda acceder a `serpapi.com`; las pruebas offline con MockWebServer no requieren ese acceso ni consumen cuota.

La ubicación del JDK puede variar entre equipos. Debe comprobarse que `JAVA_HOME` apunta a JDK 21 antes de compilar; no se fija una ruta absoluta de máquina en el proyecto.

## Versiones fijadas en el proyecto

| Componente | Versión | Estado de verificación |
|---|---:|---|
| Android Gradle Plugin | 9.4.0 | Referencia oficial: requiere Gradle 9.6.0 y soporta hasta API 37. No sincronizado localmente. |
| Gradle Wrapper | 9.6.0 | Versión requerida por AGP 9.4. Wrapper ausente; no se volverá a descargar sin confirmar la política de red. |
| JDK de Gradle | 21 | Detectado localmente; supera el mínimo documentado de 17. |
| Kotlin | 2.4.21 | Último parche estable documentado al 19-oct-2026. AGP 9 mantiene Kotlin integrado; el build eleva el KGP integrado con dependencia fija. Pendiente de compilar. |
| KSP | 2.3.12 | Release estable fijado; generación Room pendiente de probar con la combinación AGP/Kotlin elegida. |
| Compose BOM | 2026.09.00 | Última BOM estable identificada. La línea 2026.10 disponible es alpha/beta y no se incorpora. |
| Activity Compose | 1.13.0 | Fijada; pendiente de resolución local. |
| Lifecycle | 2.11.0 | Fijada; pendiente de resolución local. |
| DataStore Preferences | 1.2.1 | Solo guarda fechas y la preferencia de vuelos directos, nunca secretos. |
| Room | 2.8.5 | Runtime/compiler alineados; código generado por KSP pendiente de validar. |
| Retrofit + converter kotlinx.serialization | 3.0.0 | Converter de primera parte incluido en la misma familia Retrofit. |
| OkHttp / MockWebServer | 5.3.0 | Cliente y servidor local de pruebas alineados. |
| kotlinx.serialization | 1.11.0 | Parser JSON fijado; pendiente de resolución local. |
| kotlinx.coroutines | 1.11.0 | Android fijado; las pruebas usan la misma versión. |
| JUnit | 4.13.2 | Pruebas JVM. |
| AndroidX Test runner/rules | 1.7.0 | Pruebas instrumentadas pendientes de SDK/dispositivo. |
| AndroidX Test JUnit | 1.3.0 | Pruebas instrumentadas pendientes de SDK/dispositivo. |
| Espresso | 3.7.0 | Pruebas instrumentadas pendientes de SDK/dispositivo. |
| `minSdk` | 33 | Android 13, como pide el plan. |
| `compileSdk` / `targetSdk` | 37 | Alineados con el máximo API soportado por AGP 9.4; falta instalar y compilar contra el SDK. |
| Bytecode Java/Kotlin | 17 | Alineado en ambos compiladores; runtime de Gradle permanece en JDK 21. |

Las versiones y su evidencia primaria se mantienen en el catálogo `gradle/libs.versions.toml`. La documentación de publicación consultada incluye las [notas de AGP 9.4](https://developer.android.com/build/releases/agp-9-4-0-release-notes), la guía de [Kotlin integrado en AGP](https://developer.android.com/build/migrate-to-built-in-kotlin), las [versiones de Kotlin](https://kotlinlang.org/docs/releases.html), la [BOM de Compose](https://developer.android.com/develop/ui/compose/bom/bom-mapping), las páginas oficiales de [Room](https://developer.android.com/jetpack/androidx/releases/room) y [SerpApi Account API](https://serpapi.com/account-api), más los repositorios oficiales de [OkHttp](https://github.com/square/okhttp) y [Retrofit](https://github.com/square/retrofit).

## Pendiente para cerrar el paso de entorno

1. Identificar un entorno Android ya instalado/preparado que la política permita usar, o recibir las fuentes/repositorios y componentes aprobados. No se instalará software ni se modificará el registro de Windows.
2. Antes de sincronizar Gradle, confirmar acceso permitido a la distribución 9.6.0, Google Maven, Maven Central, Gradle Plugin Portal y a cualquier host de redirección que use la política corporativa. No se reintentarán descargas mientras esto no esté aclarado.
3. Con Wrapper, SDK API 37 y Build Tools 36.0.0 ya disponibles, ejecutar sincronización, unitarias, Lint, build debug y pruebas instrumentadas; resolver incompatibilidades observadas antes de declarar la matriz comprobada.
4. Si la red no permite obtener dependencias, utilizar solo una caché o mirror interno autorizado y registrar sus límites. No se intentará evadir el control de red.
5. La firma release estable y la prueba en dispositivo se realizarán solo cuando sus herramientas/credenciales ya estén disponibles de forma autorizada.
