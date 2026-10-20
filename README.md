# Alertas de vuelos — Android

Aplicación privada para Android 13 o posterior que consulta SerpApi/Google Flights y avisa de precios observados de itinerarios nacionales de ida y vuelta en Colombia. No compra ni reserva tiquetes y no garantiza disponibilidad ni precio al pagar.

## Funciones

- Crear, editar, activar, pausar y eliminar varias alertas con aeropuerto IATA, fechas exactas, máximo total en COP y frecuencia de 6, 8, 12 o 24 horas.
- Elegir vuelos directos (activado inicialmente) o permitir escalas. La ruta se valida contra el catálogo local de aeropuertos colombianos.
- Revisar precios manualmente o mediante WorkManager. Android puede aplazar revisiones por batería, Doze, conectividad o detención forzada; las frecuencias son objetivos, no horarios garantizados.
- Comprobar la cuenta antes de cada tanda, limitar GET de vuelos con un presupuesto local compartido y bloquear nuevas consultas cuando no se pueda confirmar la capacidad.
- Guardar el último resultado y mostrar como histórico el último total verificado si el intento más reciente no confirmó uno con los criterios actuales.
- En cada revisión verificada, enviar como máximo una notificación por alerta para la tarifa más baja que cumpla el máximo. Se puede volver a notificar el mismo itinerario y precio en revisiones posteriores.
- Conservar registros de diagnóstico copiables para la cuenta y para vuelos. No contienen API key, tokens ni cuerpos JSON.
- La app permite capturas de pantalla.

La app consulta todas las aerolíneas que devuelve la fuente, también las que no estén en el catálogo informativo incluido. La cobertura de SerpApi/Google Flights no es universal. Los horarios de los segmentos se usan internamente para validar e identificar itinerarios; no se muestran como información de la oferta en la interfaz.

## Compilar

Entorno usado: Windows, JDK 21 para Gradle, bytecode Java 17, Android SDK API 37, Build Tools 36.0.0 y Gradle Wrapper 9.6.0. Las dependencias y versiones están fijadas en `gradle/libs.versions.toml`.

Desde PowerShell en la raíz del proyecto:

```powershell
.\scripts\Build-Apk.ps1
```

El script ejecuta pruebas unitarias y Lint debug/release, compila ambos APK, firma release con la clave estable protegida fuera del proyecto y calcula SHA-256. Copia los binarios a `outputs\` y a `G:\Mi unidad\APK android car\`.

Pruebas instrumentadas con un emulador/dispositivo conectado:

```powershell
.\scripts\Android-Environment.ps1
.\gradlew.bat :app:connectedDebugAndroidTest --project-cache-dir .gradle-flight --no-daemon --console=plain
```

## Instalar y configurar

1. Para uso habitual instala `flight-notifications-1.0.5-release.apk` en Android 13 o posterior. Autoriza a Android a instalar desde la aplicación con la que abras el archivo. El APK debug sirve para desarrollo y usa otro identificador de aplicación/datos.
2. Abre **Configuración**, ingresa la API key de SerpApi en el teléfono y pulsa **Guardar cifrada**. La clave se almacena usando Android Keystore y no se debe compartir en chats, logs o archivos del proyecto.
3. Pulsa **Probar conexión** para consultar `GET /account.json`. Esta comprobación no inicia GET de vuelos.
4. La instalación nueva incluye una alerta de ejemplo CTG–CLO para marzo de 2027, pausada. Puedes editarla o borrarla; no vuelve a aparecer al volver a abrir la app.
5. Guarda una alerta propia y activa su interruptor para programar revisiones. Android solicita permiso de notificaciones. Las búsquedas de vuelos consumen cuota de SerpApi y presupuesto local; la pantalla explica el máximo por cada revisión manual.
6. **Buscar** inicia una revisión manual tras mostrar una confirmación. Cancelar no inicia GET de vuelos. La revisión manual de una alerta pausada no la activa.

Una tarifa se presenta como total observado sólo si los datos asociados de ida y regreso pasan las validaciones conservadoras de tipo, ruta, fechas, escalas, moneda y precio. La app nunca suma los importes por etapa. `VERIFICADO` describe lo que la respuesta permitió observar; no acredita equipaje, cupos para varias personas ni disponibilidad al comprar. Comprueba las condiciones en el proveedor antes de reservar.

## Actualizaciones, firma y artefactos

La release actual usa `applicationId=com.flightnotifications.app`, `versionCode=7` y `versionName=1.0.5`. Las actualizaciones release deben conservar la misma clave privada de firma, el identificador y la base de datos para preservar alertas, cuota y registros de avisos. La clave vive en `%USERPROFILE%\.android\flight-notifications-signing\`; `scripts\Backup-ReleaseSigning.ps1` permite crear una copia protegida. No se guarda la contraseña en este repositorio.

El APK release para uso habitual se entrega en `G:\Mi unidad\APK android car\flight-notifications-1.0.5-release.apk`; el debug y `SHA256SUMS.txt` quedan en esa carpeta. También se conservan copias descargables en `outputs\`.

## Privacidad y alcance

La aplicación no utiliza backend propio, Firebase, cuentas de usuario, anuncios ni telemetría. La API key permanece en el dispositivo y las llamadas HTTPS se hacen directamente a SerpApi. Los diagnósticos son locales y saneados. La cuota oficial es administrada por SerpApi; el contador local es un límite conservador y no representa cargos facturados.

Documentos de especificación y evidencia: [plan técnico de V1](PLAN_APP_DEFINITIVA.md), [requisitos aprobados](REQUIREMENTS.md), [plan de la prueba inicial](PLAN_PRIMERA_APP_SERPAPI.md), [informe del PoC](docs/INFORME_PRUEBAS_POC.md), [entorno](docs/ENTORNO_Y_VERSIONES.md) y [semántica del precio](docs/SEMANTICA_PRECIO_SERPAPI.md). El estado real de implementación, pruebas y aceptación está en [el informe V1](docs/INFORME_IMPLEMENTACION_V1.md).
