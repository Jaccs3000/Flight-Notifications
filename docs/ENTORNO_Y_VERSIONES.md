# Entorno y versiones

## Entrega vigente: V1.0.6

La release actual está en `outputs/flight-notifications-1.0.6-release.apk`, paquete `com.flightnotifications.app`, `versionCode=8`, firmada con la clave RSA 3072 estable. `apksigner verify` confirmó firma v2 y el certificado SHA-256 `c08db5c00f3a02e14d656b9143c3479949a8963c39584a8e2c8e0472298d6351`; `aapt` confirmó el paquete y la versión. Los hashes debug/release vigentes están en [SHA256SUMS.txt](../outputs/SHA256SUMS.txt). La compilación y `lintVitalRelease` finalizaron correctamente; no se ejecutaron pruebas automatizadas ni se instaló esta versión en teléfono. No se copió a Google Drive.

Entorno verificado el 19 de octubre de 2026 al generar V1 y revalidado durante la auditoría/build de V1.0.2.

## Equipo de desarrollo

| Elemento | Estado |
|---|---|
| Sistema | Windows, workspace `C:\Flight-Notifications-main` |
| JDK para Gradle | JDK 21 de Android Studio JBR; build final comprobado |
| Android Studio | `C:\Program Files\Android\Android Studio` |
| Android SDK | `C:\Users\jesus\AppData\Local\Android\Sdk`; Platform 37 y Build Tools 36.0.0 instalados |
| ADB / emulador | ADB disponible; AVD Pixel 5 con Android 16 (API 36) |
| Gradle Wrapper | 9.6.0, wrapper y suma SHA-256 fijados en el proyecto |
| Dispositivo/emulador | Pixel 5 AVD Android 16 (API 36); pruebas instrumentadas, instalación de PoC, actualización a V1 y apertura release comprobadas. No se conectó teléfono físico. |

Se instalaron los componentes Android requeridos y se resolvieron las dependencias Gradle. El build se ejecutó con `scripts/Build-Apk.ps1`; el wrapper obtiene Gradle 9.6.0 y el SDK desde las ubicaciones detectadas por `scripts/Android-Environment.ps1`. El script escribe `local.properties` para el SDK y usa un caché Gradle dedicado a este proyecto.

Los destinos de red que el build probablemente necesita son `services.gradle.org` (distribución Gradle), `dl.google.com` (Android SDK y Google Maven, incluyendo `dl.google.com/dl/android/maven2`), `repo.maven.apache.org` (Maven Central) y `plugins.gradle.org` (Gradle Plugin Portal). Los repositorios pueden redirigir a otros hosts, por lo que esta lista debe validarse con la política local antes de sincronizar. Una prueba real de la aplicación también necesitará que el teléfono pueda acceder a `serpapi.com`; las pruebas offline con MockWebServer no requieren ese acceso ni consumen cuota.

La ubicación del JDK puede variar entre equipos; el script busca `JAVA_HOME` y la instalación de Android Studio. No se fija una ruta absoluta de máquina en los archivos de Gradle.

## Versiones fijadas en el proyecto

| Componente | Versión | Estado de verificación |
|---|---:|---|
| Android Gradle Plugin | 9.4.0 | Compilación debug/release y Lint completados. |
| Gradle Wrapper | 9.6.0 | Wrapper ejecutado; distribución y checksum configurados. |
| JDK de Gradle | 21 | Compilación completada con JDK 21. |
| Kotlin | 2.4.21 | Código debug/release compilado. |
| KSP | 2.3.12 | Generación de Room completada. |
| Compose BOM | 2026.09.00 | Compose compilado; prueba instrumentada de interfaz aprobada. |
| Activity Compose | 1.13.0 | Fijada y resuelta en la compilación. |
| Lifecycle | 2.11.0 | Fijada y resuelta en la compilación. |
| DataStore Preferences | 1.2.1 | Solo guarda fechas y la preferencia de vuelos directos, nunca secretos. |
| Room | 2.8.5 | Runtime/compiler alineados; pruebas instrumentadas de cuota aprobadas. |
| WorkManager | 2.12.0 | Coordinador periódico único; integrado y compilado. No se verificó Doze/reinicio en teléfono físico. |
| Retrofit + converter kotlinx.serialization | 3.0.0 | Converter de primera parte incluido en la misma familia Retrofit. |
| OkHttp / MockWebServer | 5.3.0 | Cliente y servidor local de pruebas alineados. |
| kotlinx.serialization | 1.11.0 | Parser y build debug/release resueltos; fixtures de parser aprobados. |
| kotlinx.coroutines | 1.11.0 | Android fijado; las pruebas usan la misma versión. |
| JUnit | 4.13.2 | Pruebas JVM. |
| AndroidX Test runner/rules | 1.7.0 | 14 pruebas instrumentadas aprobadas en Pixel 5 AVD / API 36. |
| AndroidX Test JUnit | 1.3.0 | Pruebas instrumentadas ejecutadas. |
| Espresso | 3.7.0 | Dependencia resuelta; sin fallo de Lint. |
| `minSdk` | 33 | Android 13, como pide el plan. |
| `compileSdk` / `targetSdk` | 37 | APK compilada contra API 37; se instaló y abrió en emulador API 36. |
| Bytecode Java/Kotlin | 17 | Alineado en ambos compiladores; runtime de Gradle permanece en JDK 21. |

Las versiones y su evidencia primaria se mantienen en el catálogo `gradle/libs.versions.toml`. La documentación de publicación consultada incluye las [notas de AGP 9.4](https://developer.android.com/build/releases/agp-9-4-0-release-notes), la guía de [Kotlin integrado en AGP](https://developer.android.com/build/migrate-to-built-in-kotlin), las [versiones de Kotlin](https://kotlinlang.org/docs/releases.html), la [BOM de Compose](https://developer.android.com/develop/ui/compose/bom/bom-mapping), las páginas oficiales de [Room](https://developer.android.com/jetpack/androidx/releases/room) y [SerpApi Account API](https://serpapi.com/account-api), más los repositorios oficiales de [OkHttp](https://github.com/square/okhttp) y [Retrofit](https://github.com/square/retrofit).

## Resultado histórico de compilación V1.0.2

La release actual es `G:\Mi unidad\APK android car\flight-notifications-1.0.2-release.apk`, package `com.flightnotifications.app`, `versionCode=4`, `versionName=1.0.2`, firmada con la misma clave RSA 3072 estable. `apksigner verify` la validó; el SHA-256 es `bce12fc4c99d6bee8e6029217756494e4de1341074dc29ed1de26fa2a17658ef`. Debug SHA-256: `25d691dea52e2ac8ef575d1e53bfefd5dcff4edf6a1ab9f5b2eefb5f0bf4b66e`. Ambas copias G: coinciden con los artefactos de `outputs/`; el manifiesto vigente está en `SHA256SUMS.txt`.

La ejecución final del script para V1.0.2 aprobó `testDebugUnitTest` (20/20), `lintDebug`, `lintRelease`, `assembleDebug` y `assembleRelease`. `connectedDebugAndroidTest` aprobó 14/14 pruebas en Pixel 5 AVD/API 36. La actualización de release 1.0.1→1.0.2 se instaló con `adb install -r` y la actividad principal quedó abierta sin excepción fatal. No se consultó SerpApi durante el build ni en el AVD.

La ubicación de salida se puede cambiar en una ejecución futura con `scripts/Build-Apk.ps1 -OutputDirectory "C:\ruta\de\destino"`. La firma privada no se guarda en el repositorio; consérvese su copia cifrada para poder publicar actualizaciones sobre esta instalación.
