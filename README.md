# Flight Notifications — prueba técnica de SerpApi

APK privada para validar, primero con respuestas simuladas y luego manualmente, la conexión con SerpApi. Esta primera versión no crea alertas, no consulta en segundo plano y no envía notificaciones.

## Qué incluye

- Pantalla en español con ruta fija Cartagena (`CTG`) ↔ Cali (`CLO`), fechas editables, un adulto, clase económica, COP y opción de solo vuelos directos.
- Entrada de la clave privada en el dispositivo, almacenada con AES/GCM y una clave AES que permanece en Android Keystore. La clave se excluye de backup.
- `Probar conexión` llama únicamente a Account API.
- Una prueba manual solicita consentimiento explícito y puede iniciar hasta dos GET de vuelos. Antes consulta Account API, comprueba cuota local y oficial, reserva cada GET antes de enviarlo y no reintenta ni hace polling.
- La búsqueda no incluye filtros de aerolínea ni precio máximo. Se recorren las listas `best_flights` y `other_flights`; se muestran los operadores recibidos, incluida cualquier compañía desconocida.
- El saldo local reserva hasta 200 GET de vuelos en una ventana conservadora de 30 días; el tiempo que el teléfono permanece apagado no adelanta su vencimiento. Las llamadas de cuenta no descuentan ese contador.
- Las tarifas se presentan como importes observados por opción, no como total verificado. La app no suma ida y regreso.

## Requisitos para compilar

- JDK 21 para Gradle (JDK 17 es el mínimo publicado para AGP 9.4).
- Android SDK API 37 y Android Build Tools 36.0.0.
- Android 13 (API 33) o superior para instalar y probar.
- Gradle Wrapper 9.6.0; las dependencias están fijadas en `gradle/libs.versions.toml`.

El entorno revisado tiene JDK 21, pero no tiene Gradle Wrapper, Android SDK, ADB ni emulador localizados. Por eso la app todavía no cuenta con una compilación, APK ni pruebas ejecutadas. Este proyecto no descarga ni instala herramientas automáticamente en Windows. Consulta [el estado del entorno](docs/ENTORNO_Y_VERSIONES.md) y [el informe de pruebas](docs/INFORME_PRUEBAS_POC.md).

Para compilar se necesita un entorno Android ya preparado y permitido por la política de red. La configuración Gradle usa Google Maven, Maven Central y Gradle Plugin Portal; resolver la compilación puede descargar AGP, Kotlin, AndroidX y otras dependencias si no están en caché. El Wrapper necesitaría la distribución Gradle 9.6.0 y el SDK debería incluir API 37 y Build Tools 36.0.0. No se debe iniciar esa resolución hasta confirmar que las fuentes están permitidas. Cuando el Wrapper y SDK ya estén disponibles:

```powershell
.\gradlew.bat --version
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

Para generar un APK release también se necesita la clave privada de firma elegida por el propietario. No hay claves de firma en el repositorio; no firmes el release con una clave temporal si luego se espera actualizar esa instalación.

## Uso de la app

1. Instala la variante debug en un dispositivo Android 13 o superior para esta etapa de pruebas. Gradle firma automáticamente el APK debug con la clave de desarrollo local.
2. Ingresa allí la clave privada de SerpApi y selecciona **Guardar cifrada**. No la pegues en el código, terminal, documentación ni chat.
3. Pulsa **Probar conexión** para llamar exclusivamente a Account API y revisar el saldo informado.
4. Ajusta fechas y, si lo deseas, permite escalas.
5. Pulsa **Probar vuelo de ida y vuelta**. El diálogo muestra los criterios y el máximo de dos GET; solo **Continuar** autoriza esa tanda. Cancelar en el diálogo inicia cero GET de vuelos.
6. Revisa el itinerario, los operadores devueltos y la etiqueta de precio. Una tarifa permanece sin verificar hasta validar la respuesta real y su significado.

Las pruebas automáticas usan exclusivamente claves y respuestas ficticias servidas desde un MockWebServer local. No requieren una cuenta SerpApi y no consumen cuota del proveedor.

## Documentos

- [Plan técnico de la prueba](PLAN_PRIMERA_APP_SERPAPI.md)
- [Requisitos de la aplicación completa](REQUIREMENTS.md)
- [Entorno y versiones](docs/ENTORNO_Y_VERSIONES.md)
- [Informe de pruebas](docs/INFORME_PRUEBAS_POC.md)
- [Semántica del precio](docs/SEMANTICA_PRECIO_SERPAPI.md)
