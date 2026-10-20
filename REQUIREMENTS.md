# DOCUMENTO MAESTRO — App Android de alertas de vuelos (Kotlin)

**Versión:** 1.4, consolidada y actualizada · **Fecha:** 19 de octubre de 2026  
**Destino:** Codex en Android Studio / VS Code  
**Archivo único:** `REQUIREMENTS.md`  
**Propietario:** uso personal; no hay publicación en Google Play  
**Fase actual:** requisitos V1.4 implementados en el proyecto Android. El estado verificable de código, APKs, pruebas y pasos manuales está en [docs/INFORME_IMPLEMENTACION_V1.md](docs/INFORME_IMPLEMENTACION_V1.md); las pruebas de segundo plano en teléfono físico siguen siendo aceptación manual pendiente.

Este es el **único archivo obligatorio** para iniciar el proyecto. Contiene tanto el **prompt de ejecución para Codex** como toda la especificación funcional/técnica, el plan, los casos de prueba, los riesgos y la lista de aceptación. No requiere ninguno de los borradores anteriores.

---

## 0. PROMPT MAESTRO PARA CODEX — LEER PRIMERO

Al abrir el proyecto en Codex, usa este mensaje (copiar y pegar); el archivo debe estar en la raíz del repositorio o carpeta de trabajo:

```text
Actúa como arquitecto Android senior, desarrollador Kotlin, ingeniero de integración REST y responsable QA.

Tu única fuente vinculante de requisitos es el archivo:
REQUIREMENTS.md

Léelo ÍNTEGRAMENTE antes de hacer cambios. Implementarás una APK Android 13+ de uso exclusivamente personal, sin Play Store, sin servidor propio, con Kotlin, Jetpack Compose, Room, WorkManager, notificaciones locales y búsquedas de vuelos nacionales de Colombia, ida/vuelta, con casilla opcional «Solo vuelos directos» mediante SerpApi (Google Flights), para un adulto y presupuesto configurable por alerta.

PRIMERA RESPUESTA OBLIGATORIA: presenta solamente (1) análisis de viabilidad, (2) riesgos/limitaciones, (3) arquitectura y dependencias/versiones propuestas, (4) plan por fases, (5) matriz requisitos-pruebas y (6) bloqueos genuinos. NO modifiques archivos, NO consumas consultas de vuelos, NO crees claves ni gastos y ESPERA mi autorización expresa.

Una vez autorizado, implementa por fases completas y verificables. Primero prueba el acceso gratuito GET /account.json y pide una autorización SEPARADA antes de cualquier GET de vuelos que consuma cuota. No solicites que comparta la clave API en el chat; la ingresaré en la aplicación o entorno local seguro.

Valida con evidencia la semántica de price/departure_token en el JSON REAL; nunca sumes a ciegas precios que pueden representar el total de ida y vuelta. Respeta la casilla «Solo vuelos directos» por alerta: si está marcada, exige cero escalas en ida y regreso; si está desmarcada, admite itinerarios con escalas. Valida siempre aeropuertos y fechas exactos. Si los resultados no permiten demostrar precio único de ida y vuelta para la combinación seleccionada, bloquea notificaciones y explica por qué.

Cumple el alcance aprobado: múltiples alertas, casilla «Solo vuelos directos» por alerta (marcada por defecto), frecuencias configurables 6/8/12/24 horas, 1 adulto, económica, COP, solo tema claro, último resultado por alerta y registro de notificaciones, sin historial ni gráficos, clave cifrada con Android Keystore, Account API y contador local para cuota compartida, idempotencia del despacho pendiente y WorkManager sujeto a límites de Android. Exige pruebas instrumentadas de trabajo real en segundo plano con la interfaz cerrada, pantalla bloqueada y tras reiniciar el equipo; distingue cierre normal de detención forzosa.

Aplica las decisiones V1.4, con esta instrucción posterior del propietario: como máximo un aviso por alerta y revisión, siempre por la oferta verificada más barata que cumpla el máximo, aunque el mismo itinerario y precio ya se hayan avisado en revisiones anteriores. Comprueba el saldo oficial antes de cada tanda y bloquea las búsquedas de vuelos si falla esa comprobación, sin recurrir solo al contador local. El catálogo informativo se actualiza con nuevas versiones de la APK; los operadores retornados por SerpApi se muestran aunque no estén en él. Entrega APK release firmado con clave propia estable y APK debug para pruebas, con evidencia o bloqueo explícito.

No agregues backend, Firebase, cuentas, scraping de aerolíneas, pagos ni funciones no solicitadas. Las escalas están permitidas únicamente cuando se desmarque la casilla correspondiente. Obtén las aerolíneas dinámicamente sin listas cerradas que excluyan operadores; identifica la cobertura que no puede garantizar SerpApi. No asumas que consultas observadas garantizan reserva o precio de compra. No afirmes que algo fue probado, compilado, instalado o confirmado sin evidencia real.

En cada fase entrega: archivos cambiados, decisiones técnicas, pruebas realizadas con sus resultados, llamadas reales de API consumidas, pendientes y obstáculos. Antes del cierre revisa uno por uno los criterios de aceptación de la sección 17. Construye APK instalable y README si el entorno lo permite; si no, indica explícitamente qué no se pudo realizar.

Cuando termine tu primera respuesta de análisis, DETENTE y espera mi aprobación para programar.
```

### Reglas de precedencia

1. Las **decisiones explícitas del propietario de la sección 1** no se modifican unilateralmente.
2. La **sección 4** gobierna la validación del precio; la **sección 7** gobierna el consumo de cuota; la **sección 17** gobierna la declaración de finalización.
3. Codex puede escoger versiones compatibles y detalles internos, pero debe justificar cualquier desviación sustantiva del documento y pedir permiso previamente.
4. No poner una API key real ni datos privados en este Markdown, commits, pruebas, prompts o logs.
5. Si hay una contradicción documental, Codex debe citar ambas cláusulas y proponer una corrección antes de implementar la parte afectada.

---

# Especificación funcional, técnica y de calidad — Alertas de vuelos Android (Kotlin)

**Estado:** requisitos funcionales V1 aprobados por el propietario; especificación técnica consolidada V1.4. El propietario aportó evidencia histórica y posteriormente reportó una prueba manual real verificada con la APK V1.0.2. El build y las pruebas automatizadas no iniciaron consultas reales.
**Fecha de especificación:** 19 de octubre de 2026.  
**Documento de implementación dirigido a:** Codex / Android Studio.  
**Control de cambios V1.3:** casilla opcional de vuelos directos (activada inicialmente), requisitos y pruebas ampliadas de ejecución real en segundo plano, catálogo dinámico de aerolíneas nacionales y visualización por operador; límites verificables de la cobertura que Google Flights/SerpApi puede ofrecer. Conserva los requisitos anteriores no modificados.  
**Control de cambios V1.4:** recomendaciones aprobadas por el propietario: rutas nacionales de Colombia; avisos por itinerario nuevo o mejora de su menor precio notificado, máximo uno por alerta y revisión entre las ofertas elegibles; bloqueo de búsquedas cuando falla la comprobación de saldo oficial; catálogo informativo actualizado por versiones de APK; entrega release firmada y debug; precisión del contador de cuota, invalidación por cambios de búsqueda y creación única del ejemplo por instalación.  
**Instrucción posterior del propietario (20 de octubre de 2026):** cada revisión debe notificar la tarifa verificada más baja bajo el máximo de la alerta, aunque se haya notificado el mismo itinerario o importe antes. Esta regla sustituye únicamente el filtro V1.4 de “itinerario nuevo o mejora de su menor precio”; se mantiene un solo aviso por alerta y revisión y la idempotencia técnica de cada despacho pendiente.
**Idioma de la aplicación:** español.  
**Modalidad:** APK personal, instalación directa, sin publicación en Google Play.

> **Propósito:** desarrollar una aplicación Android nativa, completamente local en lo relativo a interfaz, configuración, datos y notificaciones, capaz de consultar periódicamente **SerpApi (motor `google_flights`)** para detectar vuelos **nacionales de Colombia, de ida y regreso, directos o con escalas según un checkbox por alerta**, en rutas y fechas exactas definidas por el propietario, y avisarle **solamente** cuando se encuentre un itinerario válido cuyo precio total por un adulto sea menor o igual al máximo de esa alerta, aplicando las reglas de elegibilidad y máximo un aviso por alerta y revisión de la sección 5.4.
>
> **Advertencia:** SerpApi es un proveedor externo de resultados de Google Flights. **No es una API pública oficial proporcionada por Google Flights.** Sus resultados son precios observados, no reservas ni garantías de que permanezcan disponibles al finalizar una compra. El propietario compartió una prueba manual real de V1.0.2 con ida y regreso asociado, precio coincidente y estado verificado. El JSON completo no se conservó y la evidencia cubre una sola salida candidata, no todas las combinaciones del mercado.

---

## 1. Decisiones explícitamente confirmadas por el propietario

| Tema | Decisión vinculante V1 |
|---|---|
| Sistema operativo | Android 13 o superior (**minSdk 33**) |
| Implementación | Kotlin nativo en Android Studio; interfaz Jetpack Compose |
| Distribución | APK release firmado con clave propia estable para uso habitual y APK debug para pruebas; instalación personal, sin publicación en Play Store. Conservar de forma segura la clave de firma y sus credenciales para futuras actualizaciones |
| Autenticación | Sin login, sin usuarios múltiples y sin cuentas de la aplicación |
| Servidor | **Sin backend propio** ni Firebase en V1 |
| Alertas | Crear, editar, activar, pausar y eliminar **múltiples alertas** de rutas y fechas distintas |
| Ámbito geográfico | **Solo vuelos nacionales de Colombia**: origen y destino deben ser aeropuertos ubicados en Colombia; rutas internacionales fuera de V1 |
| Consulta programada | **Frecuencia configurable** desde la aplicación; sujeta a restricciones de Android y del cupo de la API |
| Tipo de itinerario | Solo **ida y vuelta**; casilla `Solo vuelos directos` editable por alerta, **activada por defecto**. Marcada: cero escalas en ambos trayectos. Desmarcada: permitir escalas. |
| Aerolíneas | Consultar **todas las aerolíneas que SerpApi/Google Flights devuelva para la ruta nacional**, sin listas blancas por defecto. Catálogo nacional informativo actualizado mediante nuevas versiones de la APK, con fecha de revisión y fuentes. Incorporar operadores nuevos en los resultados aunque no figuren en el catálogo. No afirmar cobertura universal |
| Segundo plano | Búsquedas persistentes con la interfaz cerrada normalmente, pantalla apagada y tras reiniciar, según restricciones del sistema; pruebas reales obligatorias, sin prometer ejecución exacta ni funcionamiento tras forzar detención. |
| Notificaciones | Dentro del umbral, **máximo un aviso por alerta y revisión**, por la tarifa verificada más baja, incluso cuando el mismo itinerario o importe se notificó antes |
| Saldo oficial | Comprobar Account API antes de cada tanda manual o programada. Si falla, suspender las búsquedas de vuelos hasta recuperar la comprobación; conservar resultados y reintentar prudentemente. No continuar solo con el contador local |
| Fuente de tarifas | Únicamente **SerpApi / Google Flights** en V1 |
| Datos conservados | **Último precio/resultado por alerta y registro de notificaciones**, no series históricas ni gráficas |
| Tema visual | **Solo tema claro**, interfaz legible Material 3 |
| Pasajeros en la búsqueda | **Un adulto** por búsqueda; no cotizar ocho pasajeros |
| Compra/reserva | Fuera del alcance. Solo consultar y enlazar la fuente cuando sea posible |

### Alerta de ejemplo precargada (editable, inicialmente PAUSADA)

- Nombre: `Cartagena ↔ Cali — marzo 2027`.
- Estado inicial: **PAUSADA**, hasta que el propietario configure la clave y autorice las consultas.
- Origen: Cartagena, **CTG**.
- Destino: Cali, **CLO**.
- Ida: **2027-03-25**.
- Regreso: **2027-03-28**.
- Tipo: ida y vuelta, casilla **«Solo vuelos directos» marcada por defecto**; el propietario puede desmarcarla.
- Pasajeros cotizados: **1 adulto**, clase económica.
- Precio máximo total: **400.000 COP por adulto, ida y vuelta**.
- Moneda: **COP**.
- Horarios: cualquiera, sin filtros adicionales.
- Equipaje: tarifa económica disponible, sin exigir maleta adicional; mostrar condiciones solo si la respuesta las incluye de forma verificable.
- Creación: **una sola vez en una instalación nueva**, siempre pausada. Si el propietario elimina el ejemplo, no recrearlo al abrir, reiniciar o actualizar la app. Una instalación limpia después de desinstalar puede volver a crearlo; no confundir actualización con instalación limpia.

**Contexto no utilizado para cotizar:** el viaje familiar previsto contempla cinco adultos, una persona de 12 años y dos niños de 7 años. **Una tarifa para un adulto no acredita el precio ni ocho cupos para la familia.** No multiplicar la tarifa observada por ocho y etiquetarla como cotización confirmada.

---

## 2. Alcance y límites de la primera versión

### Debe ofrecer

1. Configurar, proteger, probar y eliminar una clave personal de SerpApi.
2. Crear múltiples alertas de ida y vuelta entre aeropuertos de Colombia identificados por código IATA (tres letras), fechas exactas y umbral individual en COP. Validar la pertenencia de ambos aeropuertos a Colombia, además del formato del código.
3. Modificar nombre, aeropuertos, fechas, máximo, frecuencia y estado activo/pausado.
4. Ejecutar consultas manuales y automáticas cuando haya cuota y conectividad.
5. Procesar respuesta de ida; cuando corresponda, usar el `departure_token` para obtener los vuelos de regreso asociados; manejar candidatos de forma adaptativa sin asumir que una única salida representa todas las oportunidades.
6. Aplicar el checkbox: **marcado**, exigir cero escalas tanto en ida como en regreso; **desmarcado**, permitir conexiones y verificar rigurosamente segmentos, aeropuertos y fechas reales. Guardar el número de escalas por trayecto.
7. Validar que el precio corresponda a un itinerario de ida y vuelta para las fechas exigidas; evitar doble suma.
8. Guardar **únicamente el último resultado de monitoreo** de cada alerta (y la mínima información necesaria para mostrarlo) y las notificaciones enviadas.
9. Emitir **como máximo una notificación local por alerta y revisión**, por la oferta verificada más barata bajo el máximo, aunque ya se haya notificado en otra revisión el mismo itinerario o importe. Reintentar de manera idempotente solo el mismo evento pendiente si se interrumpe el despacho.
10. Mostrar el estado de ejecución, último precio, fecha/hora, fuente, problemas de verificación y consumo estimado de cuota.
11. Mostrar las aerolíneas identificadas en las respuestas de la ruta (sin excluir ninguna por nombre/código), sus mejores itinerarios comprobados, y estado de cobertura parcial/no disponible; mantener un catálogo informativo de operadores nacionales actualizado mediante versiones de la APK, con fecha de revisión y fuentes, sin equipararlo a disponibilidad garantizada.
12. Continuar el monitoreo con pantalla apagada o tras cerrar normalmente la interfaz; restablecer programación tras reinicio y verificarlo en dispositivo/emulador Android 13+, sujeto a las limitaciones documentadas.
13. Aceptar que puede no haber resultados, cobertura total, ejecuciones puntuales ni enlace directo de reserva.

### No debe implementar en V1

- Compras, reservas, pagos, datos bancarios o tratamiento de datos personales de pasajeros.
- Consulta automatizada de páginas oficiales de Avianca, LATAM, JetSMART u otras aerolíneas; **solo SerpApi**.
- Historial de precios, gráficas de evolución o almacenamiento de respuestas JSON completas por tiempo indefinido.
- Sincronización entre dispositivos, nube, cuentas, login, servidor, Firebase Cloud Messaging o notificaciones remotas.
- Rutas internacionales o actualización automática remota del catálogo informativo de aerolíneas.
- Búsquedas de solo ida, multidestino o flexibilidad automática de fechas. **Las conexiones sí están admitidas desde V1.3 cuando el checkbox de directos está desmarcado.**
- Garantías de que el precio observado sea el disponible al comprar.
- Pantallas oscuras, cambio de tema o modo dinámico oscuro.

---

## 3. Arquitectura Android obligatoria

- **Kotlin** con versiones estables y compatibles verificadas al iniciar el proyecto; no fijar arbitrariamente versiones de plugins antes de comprobar el toolchain.
- **minSdk 33**. `targetSdk` y `compileSdk`: versiones estables vigentes y soportadas por el proyecto/Android Studio, documentar las elegidas.
- **Jetpack Compose + Material 3** (esquema claro permanente y contraste accesible).
- **MVVM + repositorios** y separación de presentación, dominio, integración, persistencia, programación y notificaciones.
- **Retrofit/OkHttp y corrutinas** para HTTPS GET; serialización con **kotlinx.serialization o Moshi** (elegir solo una librería principal).
- **Room / SQLite** para alertas, último resultado por alerta, registro de notificaciones y contadores operativos.
- **DataStore** solo para ajustes no sensibles; **Android Keystore con AES-GCM** para cifrar la clave de API introducida por el propietario. No guardar la clave sin cifrar, ni dejar una constante hardcodeada.
- **WorkManager** para revisiones periódicas en segundo plano, con ejecución única y controles para no duplicar trabajos.
- **NotificationManager** para notificaciones locales; solicitar `POST_NOTIFICATIONS` (Android 13+) en contexto y mostrar si se ha denegado.
- **Sin backend**. Cada llamada a SerpApi sale directamente del teléfono por HTTPS; dejar explícita la limitación residual de seguridad de claves en dispositivos comprometidos.
- **Sin telemetría de terceros** ni SDK publicitario.

### Organización sugerida

```text
app/
  src/main/
    java/.../
      ui/                # Compose: lista, formulario, detalle, ajustes, avisos
      presentation/      # ViewModels y UiState
      domain/            # Modelos, validadores y casos de uso
      data/local/        # Room, entidades y DAO
      data/remote/       # Cliente SerpApi y DTO
      data/repository/   # Implementaciones de repositorios
      security/          # Gestión de credenciales mediante Keystore
      background/        # WorkManager y planificador
      notification/      # Notificaciones e idempotencia del despacho
      util/              # Formatos COP, zonas horarias, IATA
    res/
  src/test/              # Pruebas unitarias con fixtures JSON
  src/androidTest/       # Pruebas de integración cuando proceda
README.md
```

Se puede reorganizar manteniendo la separación de responsabilidades; explicar diferencias antes de implementarlas.

---

## 4. Integración REST con SerpApi

**Servicio externo:** https://serpapi.com/google-flights-api  
**Endpoint:** `GET https://serpapi.com/search.json`  
**Respuesta:** JSON.  
**Credencial:** parámetro `api_key` suministrado por el usuario en ajustes; nunca en Git, tests públicos, capturas compartidas, registros o archivos de compilación.

### Solicitud inicial de ejemplo (checkbox «Solo vuelos directos» activada)

```http
GET https://serpapi.com/search.json?engine=google_flights&departure_id=CTG&arrival_id=CLO&outbound_date=2027-03-25&return_date=2027-03-28&type=1&adults=1&travel_class=1&stops=1&sort_by=2&currency=COP&gl=co&hl=es&api_key=TU_API_KEY
```

Parámetros clave:

| Parámetro | Valor / significado |
|---|---|
| `engine` | `google_flights` |
| `departure_id`, `arrival_id` | Código IATA origen y destino |
| `outbound_date`, `return_date` | Fechas `YYYY-MM-DD` configuradas |
| `type` | `1`: ida y vuelta |
| `adults` | `1` |
| `travel_class` | `1`: económica |
| `stops` | Checkbox **marcado**: `1` (solo sin escalas). Checkbox **desmarcado**: `0` (cualquier número de escalas) u omitir el parámetro, según comportamiento actual documentado |
| `sort_by` | `2`: precio |
| `currency`, `gl`, `hl` | `COP`, `co`, `es` respectivamente |
| `api_key` | Valor secreto introducido por el propietario |
| `departure_token` | Segunda etapa: seleccionar una salida para obtener opciones de regreso |

**IMPORTANTE:** el parámetro `stops=1` restringe resultados a vuelos sin escalas, mientras que `stops=0` admite cualquier número de escalas (valor predeterminado). El checkbox controla **ambas direcciones** y debe transmitirse de forma coherente en la búsqueda inicial y la búsqueda de vuelta con token. Validar los segmentos realmente retornados; no confiar exclusivamente en filtros remotos. El número de escalas debe mostrarse para ambos trayectos cuando la casilla esté desmarcada.

**Aerolíneas:** No fijar `include_airlines` ni `exclude_airlines` en la búsqueda general. Considerar `best_flights`, `other_flights` y, si es necesario y el presupuesto lo permite, opciones como `show_hidden=true` o `deep_search=true`, documentando su impacto real en cobertura, latencia y cuota. No confundir `show_hidden` con garantía de todas las aerolíneas. La lista de operadores del catálogo es informativa, no un filtro de exclusión. Identificar cada transportador tal como lo devuelva la fuente; incluir aerolíneas de ida y vuelta diferentes, código compartido y aerolíneas operadoras cuando estén explícitas.

### Flujo en dos etapas y verificación de precio

1. Ejecutar el `GET` inicial, verificar respuesta HTTP, `search_metadata.status` y campo `error` cuando exista.
2. Leer `best_flights` y `other_flights` de forma segura (ausencia de secciones = no asumir error ni disponibilidad).
3. Filtrar salidas cuyo aeropuerto origen sea CTG, destino CLO y salida el 2027-03-25. Cuando el checkbox esté marcado, exigir exactamente un tramo aéreo y cero escalas; cuando esté desmarcado, permitir varios tramos aéreos/conexiones, pero verificar orden y continuidad de aeropuertos, fechas y segmentos. En alertas adicionales sustituir por sus propios parámetros.
4. Identificar `departure_token` de las salidas candidatas. Ordenar por precio observado del itinerario cuando sea válido, sin asumir que el menor precio inicial es necesariamente la combinación completa más barata según el checkbox. Seleccionar dinámicamente candidatos según la cuota real y local.
5. Para **cada salida candidata permitida por cuota**, efectuar el siguiente `GET` con los **mismos** origen, destino, fechas, idioma, moneda, tipo, filtros y `departure_token`. No invertir origen/destino en la consulta con token.
6. Filtrar **las vueltas correspondientes a la salida seleccionada**: deben salir de CLO hacia CTG el 2027-03-28; con el checkbox marcado, exactamente un tramo y cero escalas; sin marcar, conexiones permitidas, validando continuidad de segmentos, fecha de salida local y destino final.
7. Determinar el **precio total observado de ida y vuelta** usando el campo correcto de la etapa que represente la combinación elegida. La documentación ejemplifica `price` junto con `type: "Round trip"`, pero su semántica debe comprobarse con una respuesta real de ambas etapas: **no sumar por defecto `price` inicial + `price` del regreso**, pues podría duplicar la tarifa. Si la API proporciona precios individuales reales por trayecto, mostrarlos; si no, indicar “No disponible” para el desglose y mostrar únicamente el total observado.
8. Comprobar moneda COP, precio positivo, cantidad de pasajeros 1 y que la oferta corresponda a las fechas/segmentos solicitados. Revisar el significado de cargos/impuestos: **no afirmar que todos los cargos y equipaje están incluidos si la respuesta no lo acredita**. Una alerta válida usa el **total observado para el itinerario completo** y se rotula como **precio observado en Google Flights**, sujeto a verificación en la aerolínea. No confundirlo con precio contractual de compra, reserva confirmada ni ocho cupos. Si la semántica del precio completo es ambigua, no alertar.
9. Identificar **todas las aerolíneas presentes en la respuesta**; representar combinaciones de ida/vuelta de la misma aerolínea o combinadas, según fuente. Determinar mejores precios válidos por aerolínea de ida / combinación cuando exista cobertura. Seleccionar el **precio total válido más bajo entre las combinaciones realmente verificadas**, indicando si la exploración fue limitada por el cupo. No asegurar que sea el mínimo absoluto del mercado si no se revisaron todas las combinaciones.
10. Para notificar, filtrar **todas las combinaciones verificadas en esta revisión** por vigencia, validez y máximo de la sección 5.4; escoger la de menor precio y enviar **como máximo uno por alerta y revisión**, sin excluirla porque su itinerario o importe se notificó antes. Si no hay ofertas verificadas bajo el máximo, **guardar último resultado pero no avisar**.
11. Cuando haya datos ambiguos o incompletos, registrar `NO_VERIFICADO` con causa; **no enviar notificación de oferta** ni presentar precio engañoso.

**Datos parciales:** una tarifa vista en la primera etapa sin regreso confirmado no constituye oferta válida, aunque esté por debajo de 400.000 COP. La app la puede mostrar como dato parcial si ayuda al diagnóstico, pero **no como último precio de ida y vuelta validado**.

### Selección de candidatos y cuota

- **Cobertura adaptativa:** intentar hasta **3 salidas candidatas** por revisión cuando haya suficiente saldo real y local; **no seleccionar las tres únicamente por mejor precio si ello invisibiliza otras aerolíneas**. Priorizar representantes de transportadores distintos entre candidatos disponibles y rotarlos en búsquedas posteriores para mejorar diversidad; seguir también el precio. No afirmar cobertura de todas las aerolíneas cuando solo se verificaron tres combinaciones. Seguir orden de prioridad y sin superar el presupuesto. Disminuir a 2 o 1 cuando sea necesario por cuota o equidad con otras alertas; cada solicitud de regreso consume una unidad potencial. Se permite finalizar anticipadamente al encontrar una oferta válida bajo el umbral si el objetivo es una alerta rápida, indicando que **no se verificaron todas las combinaciones**. Al finalizar, escoger como máximo un aviso entre las ofertas realmente verificadas, sin afirmar que se agotaron las demás opciones.
- **No limitar incondicionalmente a una salida:** si la primera no tiene regreso válido conforme al checkbox o su precio supera el máximo, intentar la siguiente candidata permitida por presupuesto.
- Si ninguna de las salidas exploradas conduce a un precio completo válido, mostrar **“sin cotización verificable dentro de los candidatos consultados”**; nunca afirmar que no existen ofertas para todas las combinaciones.
- Si hay evidencia suficiente de que el precio mostrado ya representa una tarifa de ida y vuelta, **igual verificar la existencia y características del regreso** antes de emitir alerta.
- No establecer `max_price` como sustituto del análisis de precio: necesitamos conservar el último resultado incluso si supera el umbral.
- Permitir resultados en caché del proveedor (`no_cache` sin forzar), sabiendo que podrían no ser instantáneos. Mostrar fecha/hora de observación, sin llamarlos precios en tiempo real confirmados.

### 4.1 Cobertura de operadores nacionales y presentación de resultados

- **Modo general por defecto: «Todas las aerolíneas»**, sin códigos preseleccionados ni listas blancas. Se buscan vuelos nacionales por aeropuertos en Colombia y se muestran **todas las aerolíneas retornadas por SerpApi** para esos criterios (incluidos resultados nuevos o transportadores desconocidos por nombre/código). No filtrar fuera compañías regionales o low cost por ser menos conocidas.
- Mantener **catálogo informativo actualizado mediante nuevas versiones de la APK**, separado de las tarifas reales, de aerolíneas con oferta de vuelos regulares domésticos en Colombia. Incluir fecha de revisión y fuentes verificables. Como lista inicial **no exhaustiva y a verificar**: Avianca, LATAM Colombia, JetSMART, Wingo, SATENA y Clic Air. No confundir Clic (antigua EasyFly) con dos compañías distintas. Revisarlo durante el mantenimiento con Aerocivil y fuentes oficiales, y distribuir los cambios con una actualización de la app; V1 no incorpora actualización automática remota del catálogo. No incluir compañías exclusivamente cargueras o chárter como si vendieran billetes domésticos regulares al público. **Un operador nuevo devuelto por SerpApi debe mostrarse inmediatamente en los resultados aunque todavía no figure en el catálogo**, sin esperar una nueva APK ni usar el catálogo como lista blanca.
- En la pantalla de resultados: sección **«Aerolíneas encontradas en esta búsqueda»** con nombre, código cuando exista, precios verificables por operador y sus condiciones; otra sección **«Otras aerolíneas nacionales identificadas»**, con estado «Sin tarifa devuelta por la fuente para esta ruta/fecha» o «Cobertura sin comprobar». **No escribir «no opera esta ruta», «sin vuelos» o «todas revisadas» basándose solamente en la ausencia de datos de SerpApi.**
- La identidad de operador puede ser diferente entre ida y vuelta; mostrar ambos y combinaciones mixtas cuando Google Flights las devuelva. Diferenciar **aerolínea que comercializa** de **aerolínea operadora** si el JSON proporciona esa distinción; no inferirla.
- Mostrar temporalmente todos los itinerarios devueltos y validados mientras se navega la búsqueda, ordenar por precio total, y persistir únicamente **el último resultado/estado y resumen de aerolíneas de la última búsqueda por alerta**, reemplazándolo en cada revisión. Esto no es un historial de precios. Permitir expandir lista de opciones disponibles en la búsqueda actual cuando estén en memoria sin guardar una serie histórica.
- **Límite que debe comunicarse con precisión:** Google Flights reconoce que **no incluye todos los vuelos y aerolíneas disponibles**. SerpApi no puede garantizar la totalidad del mercado colombiano. Si en el futuro se exige verificación exhaustiva con cada aerolínea, eso requerirá fuentes adicionales y una decisión expresa para modificar el alcance de V1. Nunca afirmar cobertura 100% con la fuente única aprobada. Fuente oficial: https://support.google.com/travel/answer/2475306 y https://serpapi.com/google-flights-api.
- Evaluar `show_hidden` y `deep_search` durante el PoC con resultados simulados y, únicamente con autorización para cuota real, comparar si aumentan operadores/itinerarios en la ruta de prueba. No activar consultas duplicadas sistemáticas sin medir gasto. Reportar «Resultados obtenidos: N operadores / itinerarios; cobertura potencialmente incompleta».

### Manejo de enlaces

- Priorizar `search_metadata.google_flights_url` **solo cuando la respuesta lo contenga** y cuando el enlace corresponda a los criterios seleccionados, si ello es verificable.
- Si no puede comprobarse que es un enlace profundo de la búsqueda, etiquetarlo como “Abrir Google Flights para verificar” y no afirmar que conserva filtros ni tarifa.
- Una URL oficial de compra puede mostrarse **solo si es realmente devuelta por la fuente y validada**; no fabricar enlaces.
- No generar automáticamente un enlace con `api_key`, `departure_token` o `booking_token` incluido en una notificación externa.

---

## 5. Reglas funcionales y validaciones

### 5.1 Formulario de alerta

Campos V1:

1. **Nombre** libre, obligatorio, 3–80 caracteres.
2. **Origen**: código IATA de tres letras de un aeropuerto ubicado en Colombia, p. ej. `CTG`; texto opcional del aeropuerto para facilitar lectura. Verificar identidad y país del aeropuerto; tres letras por sí solas no demuestran validez.
3. **Destino**: código IATA de tres letras de un aeropuerto ubicado en Colombia, distinto del origen; rechazar rutas internacionales en V1.
4. **Ida** y **regreso**: fechas seleccionadas con `DatePicker` en calendario, `ida >= hoy` y `regreso > ida` para el modelo V1; horas libres.
5. **Precio máximo** en COP: entero positivo, sin decimales. Comparación inclusiva (`precio <= máximo`).
6. **Frecuencia**: elección **6, 8, 12 o 24 horas** por alerta, con valor inicial conservador de **24 horas** (configurable); comprobar compatibilidad con la cuota.
7. **Solo vuelos directos**: `Checkbox` booleano por alerta, **activado por defecto**. Marcado = cero escalas en ambos trayectos; desmarcado = conexiones permitidas. Mantener la elección al editar y al cerrar la app.
8. **Activada**: sí/no.

Reglas generales: ida y vuelta, clase económica, un adulto y COP no son modificables en V1; **«Solo vuelos directos» sí es modificable** como checkbox. No imponer filtros de aerolíneas por defecto: «Todas las aerolíneas». Al crear/editar, estimar coste de API y avisar si la configuración supera el presupuesto. Permitir guardar alertas pausadas aunque se exceda el cálculo, pero no prometer que todas se ejecutarán a su frecuencia si el cupo no alcanza.

**Importante:** permitir múltiples alertas con cualquier combinación válida de fechas y rutas nacionales de Colombia. La ruta CTG–CLO es una **alerta inicial de ejemplo**, no una restricción del sistema. No activar búsquedas por primera vez sin clave y consentimiento explícito del propietario.

**Efectos de editar una alerta:**

- Cambiar origen, destino, fechas o checkbox de directos **invalida la compatibilidad de la cotización anterior con la búsqueda vigente**. Conservarla solo como dato previo, rotulado con la ruta, fechas y filtro originales; exigir una nueva consulta y verificación antes de notificar para la nueva configuración.
- Cambiar nombre o frecuencia **no invalida** una cotización compatible. La frecuencia modifica la programación, respetando cuota y concurrencia.
- Cambiar el precio máximo no invalida el itinerario observado, pero **requiere una nueva consulta antes de emitir un aviso por esa modificación**; no notificar al guardar usando únicamente el precio almacenado.
- Los resultados de una ejecución antigua no deben sobrescribir datos de criterios nuevos. Cada revisión nueva puede volver a notificar la menor tarifa que cumpla el máximo, sin filtrar por avisos anteriores.

### 5.2 Estados de una alerta

- `ACTIVA`, `PAUSADA`, `VENCIDA` (si llega la fecha de salida sin oportunidad útil) y `ERROR_CONFIGURACION` cuando sea pertinente.
- Permitir reactivación manual de alertas pausadas válidas.
- **Vencer automáticamente** una alerta una vez terminada la fecha local de salida (según la zona horaria del aeropuerto de origen; si no se dispone de ella, documentar la referencia temporal utilizada). En ese día, descartar cualquier vuelo cuya hora de salida ya haya pasado. No detener la búsqueda simplemente porque comenzó el día de salida.
- Si falta la clave: estado visible `CONFIGURAR_API_KEY`, no ejecución HTTP.
- Si no hay red o cuota: estado de consulta `SIN_CONEXION` o `LIMITE_CUOTA`, sin falsa alerta positiva.
- No modificar ni borrar una alerta desde una notificación sin confirmación del usuario.

### 5.3 Último resultado (sin historial)

Guardar **un único registro de resultado actual por alerta**, reemplazándolo tras cada revisión cuando corresponda. Debe poder mostrar:

- Último precio de ida y vuelta **verificado** si lo hubo, marcado con su instante de observación y antigüedad; distinguir de la búsqueda más reciente.
- Fecha/hora de la última consulta intentada, su estado (`VERIFICADO`, `SIN_VUELOS`, `NO_VERIFICADO`, `ERROR_RED`, `CUOTA`, etc.) y error resumido sin secretos.
- Mejor combinación observada: aerolínea(s), números de vuelo si existen, horas de salida/llegada de cada tramo, ambos aeropuertos, moneda, precio, condiciones verificables y enlace si hay.
- Bandera `exploracionLimitadaPorCuota` cuando no se revisaron todos los candidatos.
- En pantalla, etiqueta clara: **“Precio observado; verifica antes de comprar”**.

No guardar datos para trazar evolución, registros de todas las consultas, gráficos ni respuesta JSON completa. Conservar solo contadores necesarios para controlar la cuota y un diagnóstico mínimo **sin secretos** de la última ejecución.

### 5.4 Notificaciones: cuándo sí y cuándo no

**Enviar** solo cuando se cumplan todas estas condiciones:

- La alerta está activa, no vencida y el usuario otorgó permiso de notificaciones.
- Se verificó una combinación **completa** de ida y regreso para sus dos fechas y aeropuertos.
- Si `directOnly=true`, ida y regreso son **directos**; si `directOnly=false`, pueden tener escalas siempre que la combinación, ruta, fechas y precio completo sean válidos.
- Precio final de la combinación retornado por la fuente inequívoco, en COP, para 1 adulto.
- Precio `<=` máximo configurado.
- La oferta es la tarifa verificada más baja de esta revisión que cumple el máximo configurado. No se consulta el historial de precios ni se excluye una oferta por haberse notificado antes el mismo itinerario o importe.

**No enviar** por precios sobre el umbral, vuelos con escalas **cuando el checkbox de directos esté marcado**, tarifas solo de ida, dudas de moneda, errores de red, falta de permiso, cotizaciones incompletas ni ausencia de vuelos.

**Selección y cantidad:** enviar **como máximo una notificación por alerta y revisión**, tanto manual como automática. Filtrar las ofertas completas y verificadas por vigencia y máximo, y escoger la de menor precio aunque esa tarifa o itinerario ya se haya avisado antes. Si no hay ninguna oferta verificada bajo el máximo, guardar el resultado sin aviso. Mostrar las demás opciones verificadas en el detalle; no marcarlas como avisadas si no fueron seleccionadas. Usar el texto **“Tarifa más baja verificada”**, acompañado de la advertencia de cobertura parcial cuando corresponda; no afirmar que es el mínimo de todo el mercado.

**Repetición e idempotencia:** cada revisión crea un identificador único de despacho, distinto del identificador del itinerario. Ese ID permite recuperar el mismo evento sin duplicarlo tras una interrupción; una revisión posterior crea un ID nuevo y puede avisar de nuevo el mismo precio. No conservar un mínimo notificado para filtrar tarifas futuras.

El mismo evento de despacho no debe publicarse dos veces durante recuperación; una revisión posterior debe poder notificar de nuevo la misma combinación y precio. La lista visible contiene hasta 100 despachos y puede limpiarse sin afectar alertas futuras. Asegurar atomicidad con transacciones Room para evitar duplicar un mismo evento por reintento. **Sin permiso de notificaciones, no marcar un evento como “enviado”; tras habilitarlo, la siguiente revisión válida puede avisar de nuevo la tarifa más baja.**

Formato de ejemplo **ficticio**:

> ✈️ Oferta detectada — CTG ⇄ CLO  
> 25–28 mar. 2027 · 1 adulto · **$378.000 COP ida y vuelta**  
> Tarifa más baja verificada.  
> Aerolínea y horarios disponibles en la app. Precio observado sujeto a cambio.

El toque abre el detalle exacto de la alerta. Las notificaciones se guardan localmente en una lista (por defecto conservar las **100 últimas**, eliminado primero el registro más antiguo); incluir botón “Borrar notificaciones” con confirmación. Conservar solo el ID técnico necesario para reintentar idempotentemente el mismo despacho; no deduplicar futuras tarifas. Mostrar advertencia cuando el permiso de notificaciones esté desactivado.

---

## 6. Programación periódica y límites de Android

- Usar **WorkManager** (no `AlarmManager` con alarmas exactas) porque no se requieren ejecuciones al segundo.
- Crear un **único trabajo periódico** coordinador (por ejemplo, cada 1 h) para revisar cuáles alertas estén vencidas, pausadas o listas para consulta según su frecuencia individual. El propio coordinador no debe hacer peticiones sin una alerta realmente vencida de consulta.
- Evitar encolar un Worker por alerta y evitar búsquedas simultáneas que dupliquen el gasto de API.
- Identificar y mantener los timestamps de próxima consulta con reloj local y tiempo persistido; reprogramar de manera segura después de reinicio, cambio de hora y actualización de la app.
- Respetar `Constraints(NetworkType.CONNECTED)`. Elegir una política de backoff; no reintentar automáticamente de manera ilimitada ante 429, credencial inválida o errores de negocio.
- Mostrar **“frecuencia solicitada”** y **“última ejecución real”**; Android Doze, restricciones de batería y estado del teléfono pueden retrasar o saltar revisiones.
- La consulta manual es independiente del turno automático, pero está sujeta al mismo bloqueo de concurrencia y presupuesto total de consultas.
- **Criterio de aceptación de segundo plano:** las alertas activas continúan programadas cuando se cierra la interfaz normalmente o se retira la aplicación de recientes, sin exigir que el usuario deje la pantalla abierta ni una notificación permanente de servicio foreground. Se deben verificar trabajos realizados realmente mientras la pantalla permanece apagada, el dispositivo está bloqueado y tras un reinicio (cuando Android los permita).
- En configuración mostrar diagnóstico simple: `Última consulta efectiva`, `Próxima consulta estimada`, `Último error`, `Estado del programador`, `Restricción de batería detectada` cuando pueda observarse, y botón `Comprobar programación` **sin gastar llamadas SerpApi**. No prometer tiempos exactos.
- Crear pruebas instrumentadas con WorkManager Test, escenarios de cierre normal, eliminación de Recientes, pantalla bloqueada, modo Doze, pérdida/recuperación de red y reinicio. Confirmar logs locales sanitizados con fecha real. Si el equipo tiene restricción manual de batería, mostrar instrucciones opcionales para que el propietario las revise; no solicitar exenciones agresivas como requisito.
- No pedir al usuario que desactive las protecciones de batería como requisito obligatorio de funcionamiento.
- Diferenciar **cerrar la interfaz**, **reiniciar el teléfono** y **forzar detención desde Ajustes del sistema**: esta última puede detener los trabajos hasta que la app vuelva a iniciarse. El sistema debe informar de la limitación, no prometer alertas cuando el teléfono esté apagado o sin conexión, y comprobar que los trabajos se restauren/organicen cuando vuelva a abrirse.
- Comprobar cambios de zona horaria/hora del móvil sin alterar las fechas de salida y regreso de aeropuertos; evaluar vencimiento por hora local de salida y no por el reloj de la pantalla.

---

## 7. Control compartido de la cuota gratuita y saldo oficial de la cuenta

**Referencia comprobada el 19-oct-2026:** el plan Free de SerpApi publica **250 búsquedas/mes**, sujeto a cambios de proveedor; las búsquedas cacheadas idénticas dentro del período definido por SerpApi pueden ser gratuitas. Véase https://serpapi.com/pricing y https://serpapi.com/google-flights-api.

**REQUISITO OBLIGATORIO — saldo real:** implementar `GET https://serpapi.com/account.json?api_key=...` de la **Account API oficial**, que SerpApi documenta como gratuita y no descontada del cupo mensual: https://serpapi.com/account-api. Leer `plan_searches_left`, `total_searches_left`, `searches_per_month`, `this_month_usage`, `account_rate_limit_per_hour` y la fecha de renovación cuando el proveedor la facilite. Usar esta API para **probar conexión sin gastar una búsqueda de vuelos**. Nunca registrar ni mostrar los campos sensibles `api_key`, `account_email` y `account_id` que pudieran venir en el JSON. **Comprobar el saldo oficial antes de cada tanda de búsquedas**, tanto manual como programada, bajo el control global de concurrencia; una tanda puede revisar una o varias alertas secuencialmente. Si el saldo real es cero o no permite la siguiente consulta, detener la búsqueda. **Si Account API falla, no iniciar los GET de vuelos de esa tanda, aunque la clave estuviera validada y el contador local tenga capacidad**. Conservar resultados previos con su antigüedad visible, mostrar “Saldo oficial sin verificar; búsquedas suspendidas” y reintentar la comprobación de forma prudente, sin bucles continuos. Reanudar únicamente tras recuperar una comprobación válida con saldo suficiente y disponer también de presupuesto local. No usar el último saldo almacenado ni el contador local como sustitutos de la comprobación fallida. Si es una clave nueva sin validación, no iniciar búsquedas de vuelos.

1. Cuota global compartida por **todas** las alertas y búsquedas manuales.
2. Ajuste visible de **límite conservador por defecto: 200 solicitudes iniciadas en una ventana móvil de 30 días** (por debajo de las 250 que indica actualmente el plan Free). Este límite es **un segundo control local**, no el calendario oficial de facturación; el saldo que comunica Account API tiene prioridad para bloquear peticiones si queda menos. Permitir reducirlo; un aumento, si se implementa, debe exigir confirmación y no superar el límite del plan configurado.
3. **Antes de cada GET de vuelos**, reservar/incrementar preventivamente una unidad local, incluso cuando después falle la conexión: preferimos subcontar capacidad que arriesgar excedentes si no conocemos el cargo real. Indicar que es **estimación conservadora**, no saldo oficial de SerpApi. **Los GET gratuitos de Account API no consumen este límite local de búsquedas**; contabilizarlos por separado solo para diagnóstico. Dentro de una tanda, descontar conservadoramente cada GET de vuelos iniciado de la capacidad calculada a partir del saldo oficial recién comprobado, además de reservar su unidad local, para no reutilizar el mismo saldo entre alertas o candidatos.
4. Nunca iniciar una consulta adicional de regreso si ya no hay presupuesto. La app puede marcar la revisión como incompleta.
5. La búsqueda de ida y vuelta requiere normalmente **mínimo 2 GET** (ida y devolución por `departure_token`); pueden ser más si se verifican salidas alternativas. No dar por sentado “una revisión = una solicitud”.
6. Al editar/activar alertas, mostrar un **estimado de solicitudes en 30 días**: número de verificaciones programadas × peticiones supuestas por verificación. El sistema no debe aceptar silencio: avisar claramente cuando el plan no cubra todas las frecuencias solicitadas.
7. **Prioridad justa:** distribuir turnos entre alertas activas mediante round-robin o antigüedad de última ejecución, sin dejar que una sola consuma toda la cuota. No realizar consultas ilimitadas para una alerta mientras otras esperan.
8. Al alcanzar límite local **o agotar saldo oficial**: **suspender automáticamente todas las consultas de vuelos**, conservar configuración y resultados previos, mostrar qué límite causó el bloqueo y reanudar solo cuando ambas restricciones vuelvan a permitir búsquedas. No eliminar alertas ni inventar resultados.
8a. Refrescar saldo real al iniciar la app, después de guardar/cambiar clave y **antes de cada tanda de búsquedas de vuelos**, aunque exista un dato anterior; evitar consultas de cuenta en bucle continuo. Mostrar fecha/hora de la última verificación de saldo y cualquier fallo posterior, y **distinguir el contador local de solicitudes iniciadas del consumo oficial facturado**. Un saldo antiguo visible no autoriza una tanda cuya comprobación haya fallado.
9. Caché: usar resultados del proveedor por defecto; mostrar posible desfase. No forzar `no_cache=true` sin una razón demostrada.
10. Cualquier consulta diagnóstica real durante desarrollo también cuenta para el presupuesto y requiere permiso explícito del propietario.

**Ejemplo de dimensionamiento, no promesa de exactitud:** 2 peticiones por verificación × 1 alerta × 1 vez/día × 30 días = 60 peticiones; para 4 alertas con la misma frecuencia = 240 y superaría el límite conservador de 200. Si algunos flujos usan 3 peticiones, el consumo crece. Por eso la frecuencia es configurable **pero no ilimitada**.

---

## 8. Privacidad y seguridad

- Nunca escribir credenciales en texto plano en código, `BuildConfig`, recursos `strings.xml`, Git, fixtures, Crash Logs o `Logcat`.
- La API key se introduce por UI con campo oculto y se almacena **cifrada con AES-GCM mediante clave del Android Keystore**; gestionar inicialización, pérdida de clave y reinstalación sin exponerla.
- La consulta REST utiliza HTTPS con verificación TLS estándar; no deshabilitar comprobación de certificados ni agregar mecanismos “trust all”.
- La API exige `api_key` como parámetro de la petición GET. Evitar que la URL completa se envíe a logs HTTP, analítica o errores de UI. Si se registra, **redactar siempre `api_key`, `departure_token`, `booking_token` y otros datos sensibles**.
- **Excluir credenciales del mecanismo de backup**; idealmente deshabilitar backup automático de V1, documentando que reinstalar elimina los datos y obliga a volver a introducir la clave.
- No agregar SDK de analítica, publicidad ni servicios que suban resultados, rutas y búsquedas a una base de datos propia.
- Permisos mínimos: `INTERNET`, `ACCESS_NETWORK_STATE` cuando se requiera y `POST_NOTIFICATIONS` en Android 13+. No ubicación, almacenamiento amplio, contactos, SMS, calendario, micrófono ni cámara.
- Protección básica contra requests repetidas, timestamps sospechosos y respuestas mal formadas.
- Nunca imprimir la clave en la notificación ni transferirla a navegador externo.
- Una clave introducida en un móvil personal reduce exposición frente a distribuir una APK pública, **pero no hace imposible su extracción en dispositivos comprometidos**.

---

## 9. Diseño y pantallas de la V1

**Regla visual:** Material 3, **tema exclusivamente claro**, disposición limpia, buen contraste, colores suaves y consistentes, sin saturación decorativa. Diseño adaptable a teléfonos Android 13+; textos y precios claros, formateados con separadores de miles en COP.

### 9.1 Inicio — lista de alertas

- Título “Alertas de vuelos”.
- Contador “X activas”; estado de conexión/clave y cuota disponible estimada.
- Tarjetas independientes, una por alerta: ruta, fechas, precio objetivo, último precio verificado (o “sin precio verificado”), última revisión real, frecuencia, estado; acciones **Buscar ahora**, **Editar**, **Pausar/Reanudar**, **Eliminar**.
- Acción principal **Nueva alerta**.
- Crear la alerta CTG–CLO de ejemplo una sola vez en una instalación nueva, siempre pausada; persistir que ya se creó. No duplicarla ni recrearla al abrir, reiniciar o actualizar, incluso si fue eliminada. Una instalación limpia después de desinstalar puede crearla nuevamente; no exigir conservar datos entre instalaciones sin backup.
- Estados vacíos claros: sin alertas, sin API key, sin acceso a red, cuota agotada.

### 9.2 Crear/editar alerta

- Entradas por IATA (p. ej., `CTG`, `CLO`) con validación de aeropuerto y pertenencia a Colombia, y explicación de que V1 admite únicamente rutas nacionales.
- Selectores de fecha nativos/Compose. Umbral en COP numérico.
- Frecuencia 6/8/12/24 h, con cuota estimada.
- Indicadores informativos fijos: un adulto, ida/vuelta, económica y COP. Añadir **checkbox claramente visible «Solo vuelos directos»** (marcado por defecto), con breve explicación: «Desmarcar permite vuelos con escalas».
- Mostrar **«Aerolíneas: Todas»** como alcance de búsqueda predeterminado, no una selección restrictiva.
- Guardar/cancelar. Confirmación antes de eliminar.

### 9.3 Detalle / último resultado

- Último precio observado de itinerario completo; etiqueta “observado, no garantizado”; **desglose ida/regreso solo si SerpApi lo entrega inequívocamente**, nunca deducirlo del total.
- **Lista de aerolíneas y vuelos de la última búsqueda**, nombre/código, precio de ida y vuelta cuando sea verificable, cantidad de escalas por trayecto, y marcas «Sin cotización completa», «Sin tarifa devuelta por esta fuente» o «Cobertura parcial». Si el recorrido combina operadores, mostrar ambos.
- Fecha/hora reales de observación, aerolíneas, vuelos de salida y regreso, horarios, precio total, estado, limitaciones de la exploración y enlace devuelto por la fuente cuando exista.
- Si la última consulta falló, mostrar **también** el último precio antiguo pero inequívocamente marcado como “dato previo; no confirmado en la última búsqueda”.
- Botón “Buscar ahora” y botón contextual para abrir Google Flights cuando corresponda.
- Si se dispone de varios operadores en una búsqueda, mostrar tarjetas de todos los encontrados en lugar de solo la opción más barata, con condiciones de cobertura.
- No gráfico de precios, no tabla cronológica de consultas.

### 9.4 Notificaciones

- Lista cronológica inversa de avisos enviados: ruta, fechas, tarifa total, timestamp, itinerario asociado.
- Acción para borrar notificaciones con confirmación; conservar hasta 100 registros por defecto.
- La lista es un **registro de avisos**, no un historial de todas las cotizaciones.

### 9.5 Configuración

- API key: guardar/actualizar/eliminar. El botón **“Probar conexión” usa primero la Account API gratuita**, sin gastar búsquedas de vuelos; un botón separado **“Hacer prueba de vuelos”** advierte y requiere aprobación para gastar búsquedas reales.
- **Saldo mensual oficial** y **cuota local estimada** por separado, fecha/hora del último saldo leído, presupuesto configurable y explicación de las etapas de búsqueda. Manejar diferencias entre contador local y saldo real.
- Estado de notificaciones, acceso a ajustes Android si se denegó permiso.
- Panel de diagnóstico de segundo plano con estado del Worker, última ejecución real, próxima estimada y comprobación no facturable de programación, además de texto sobre falta de garantía de horarios exactos.
- Acceso al **catálogo de aerolíneas nacionales** (separado de los resultados obtenidos), actualizado mediante nuevas versiones de la APK, con fecha de revisión y fuentes verificables visibles; no equivale a precios ni promesa de cobertura total. Los resultados incluyen también operadores retornados por SerpApi que no estén en el catálogo.
- Información “API de terceros SerpApi”; documentación y privacidad local.

---

## 10. Modelo de datos mínimo (Room)

Diseñar el esquema exacto durante implementación, manteniendo **estos conceptos**:

### `AlertEntity`

- `id` UUID; `name`.
- `departureAirportIata`, `arrivalAirportIata`.
- `outboundDate`, `returnDate` (`LocalDate` o cadena ISO con converter).
- `maxTotalCop` (`Long`).
- `frequencyHours` (valores admitidos 6, 8, 12, 24).
- `directOnly` (`Boolean`, por defecto `true`, **migración de alertas existentes a true**), `isEnabled`, `status`, `createdAt`, `updatedAt`, `lastAttemptAt`, `nextDueAt`.

### `LastResultEntity` (clave única por alerta)

- `alertId`, `lastStatus`, `lastAttemptAt`, `lastErrorCode` (sanitizado).
- `verifiedTotalCop` nullable, `verifiedAt` nullable, `currency`.
- `outboundFlightSummary`, `returnFlightSummary` en campos normalizados o estructura mínima, incluyendo `outboundStops`/`returnStops` y transportadores de ambos trayectos.
- `lastSearchAirlineSummary` como pequeño resumen **solo de la consulta más reciente** por alerta (aerolíneas devueltas, estado por operador, mínimo válido para ruta+fechas cuando exista, cobertura parcial), sobreescribible y sin serie temporal.
- Opciones verificadas de la última revisión exitosa, suficientes para presentar las alternativas observadas en el detalle; una falla puede conservarlas como resultado previo con sus criterios/fecha originales. No almacenar consultas anteriores como historial.
- `sourceLink` opcional y comprobado, `explorationLimited`, `appliedDirectOnly` y `dataFreshnessNote`.
- Identidad de la búsqueda aplicada (origen, destino, fechas y filtro), suficiente para distinguir una cotización previa de la configuración actual al editar la alerta; no notificar con un resultado de configuración anterior.
- **Un solo resultado vigente por alerta**, no tabla histórica de tarifas.

### `SentNotificationEntity`

- `id`, `alertId`, `fingerprint`, `priceCop`, `sentAt`, `routeDateSummary`, `flightSummary`.
- Límite de retención visible por defecto: últimos **100** despachos. Limpiar ese registro no afecta las notificaciones de futuras revisiones.

### ID de despacho (puede integrarse en una tabla equivalente)

- `alertId`, `deliveryId`, `sentAt`; cada revisión tiene un ID único usado solo para reintentar idempotentemente ese despacho.
- No usar el ID de despacho para deduplicar itinerarios o tarifas de futuras revisiones.

### Compatibilidad con estado legado de precio notificado

- Si una versión previa conserva mínimos por itinerario, pueden permanecer en Room por compatibilidad, pero V1.0.4 y posteriores no los consultan para decidir si notificar.

### `ApiBudgetEntity`

- Registro mínimo de **contadores de solicitudes reservadas** y marcas temporales suficientes para estimar una ventana móvil de 30 días, sin almacenar respuesta ni credenciales. Integrar saldo real y límites horarios que devuelva Account API, sin registrar sus datos de identificación personal.
- Si requiere registros internos por GET para calcular cuota, conservar solo timestamps/códigos y purgar automáticamente los que queden fuera de ventana; **esto no es historial de precios**.

**Migraciones:** planificar migración Room estable; no usar `fallbackToDestructiveMigration` silenciosamente en versiones finales. Los cambios no deben borrar las alertas existentes.

---

## 11. Manejo de errores y criterios estrictos

| Situación | Comportamiento esperado |
|---|---|
| Sin red o timeout | Estado explícito; no alerta positiva; reintento prudente |
| API key ausente | Pedir configurar clave; no ejecutar HTTP |
| Credencial incorrecta / 401–403 / error de autenticación | Mostrar mensaje y pausar intentos repetitivos; no imprimir secreto |
| 429 / agotamiento de cuota | Detener consultas y esperar; volver a consultar saldo de cuenta prudentemente; no ejecutar bucles de reintentos |
| Fallo de Account API antes de una tanda | Suspender los GET de vuelos de esa tanda, conservar resultados previos rotulados y mostrar saldo oficial sin verificar; reintentar prudentemente, sin continuar solo con el contador local |
| Error 5xx | Dejar último resultado previo marcado como antiguo, reintento limitado |
| JSON inválido / campos renombrados | `NO_VERIFICADO`; no inventar ni sumar valores |
| Solo salida sin vuelta | `NO_VERIFICADO`; sin notificación |
| Tramo con 1+ escala | **Checkbox marcado:** descartar; **desmarcado:** aceptar tras validar segmentos, fechas y precio total de ida y vuelta |
| Aerolínea conocida sin resultados | Mostrar «Sin tarifa devuelta por esta fuente»; nunca afirmar que no opera o que fue revisada directamente |
| Aerolínea nueva/desconocida devuelta por el proveedor | Incluir en el resultado sin listas blancas; mostrar nombre/código como fue recibido |
| Solo tres candidatos consultados | Mostrar cobertura parcial, nunca afirmar revisión exhaustiva de aerolíneas |
| Moneda distinta a COP | No notificar ni convertir mediante tasa supuesta |
| API devuelve precio pero no puede vincularse a combinación real | No notificar |
| Mismo vuelo, fecha y precio previamente notificados | No repetir notificación |
| Tarifa repetida en otra revisión | Volver a notificar si es la menor oferta verificada bajo el máximo actual |
| Varias ofertas elegibles en una revisión | Enviar como máximo un aviso por alerta: el más barato entre los elegibles; mostrar las demás verificadas en el detalle, sin marcarlas como notificadas |
| Aeropuerto de origen o destino fuera de Colombia | Rechazar la configuración por estar fuera del alcance nacional de V1 |
| Android retrasa el Worker | Mostrar última ejecución real; no prometer puntualidad |
| App detenida forzosamente por el usuario, sin energía o desconectada | No prometer vigilancia continua; al volver a abrir, reorganizar trabajos y explicar revisión perdida |
| Saldo real de SerpApi menor que el contador local permite | Bloquear nuevos GET de vuelos y reflejar saldo oficial; no sobregastar |
| Permiso de notificaciones denegado | Guardar evento válido opcionalmente como pendiente/registrado, pero indicar que no se pudo notificar; no afirmar envío |
| Fecha de ida vencida | Marcar alerta vencida, detener consumo |
| Varias alertas con presupuesto insuficiente | Prioridad justa, aviso de cuota y ejecuciones pendientes; no sobrepasar tope |

No tomar la falta de resultados de una consulta parcial como evidencia de que no existen vuelos o mejores precios en todo el mercado.

---

## 12. Plan de desarrollo para Codex: implementación por fases

### Fase 0 — Inspección y diseño

- Inspeccionar el proyecto existente o crear uno si está vacío; no sobrescribir código existente sin aprobación.
- Confirmar versiones Android Studio/Gradle/Kotlin/Compose y librerías estables compatibles.
- Presentar arquitectura breve, estructura propuesta, riesgos y cobertura de pruebas. **Solicitar autorización antes de cambios amplios.**

### Fase 1 — Prueba de concepto SerpApi (prioritaria)

- Implementar cliente `GET` y DTO mínimos con seguridad de credenciales. Añadir **Account API gratuita** para validar clave y conocer saldo oficial sin consumir búsquedas de vuelos.
- Construir prueba manual de CTG–CLO, 25/28 de marzo de 2027, un adulto, con checkbox marcado (`stops=1`) y desmarcado (`stops=0`) — usando fixtures primero; solo efectuar llamadas reales expresamente autorizadas.
- Ejecutar primero una validación gratuita de Account API y, solo con autorización para gastar cuota, realizar 1 GET de salidas + 1 GET con `departure_token` para un candidato en la prueba inicial. Después validar la estrategia **adaptativa de hasta 3 candidatos** con fixtures y, si el usuario aprueba su costo, con consultas reales adicionales.
- Inspeccionar **JSON real** y documentar la semántica efectiva del precio entre las dos etapas. Si sigue ambiguo, **bloquear avisos**, documentar incertidumbre y pedir decisión; nunca simular confirmación.
- Mostrar respuesta mínima: ¿hay vuelos ida/vuelta válidos según el checkbox?, ¿qué precio total devuelve la API?, ¿cuántas aerolíneas encontró en las respuestas?, ¿qué enlaces ofrece?, ¿cuántos GET consumió la prueba? Evaluar `show_hidden`/`deep_search` sin asumir que resuelven toda la cobertura.
- Comparar, solo orientativamente, con el precio Avianca obtenido anteriormente por el usuario en capturas: $713.710 COP ida y vuelta en la tarifa indicada. **No asumir igualdad entre Google Flights y Avianca.** Ese precio no es resultado de SerpApi.
- **Puerta de salida:** no implementar motor de avisos productivo hasta tener una interpretación del JSON demostrada con fixture de prueba y verificación real, o hasta que quede claramente indicada y aprobada una limitación bloqueante.

### Fase 2 — Datos, formulario e interfaz

- Implementar creación/edición/pausa/eliminación de alertas múltiples nacionales de Colombia, checkbox `Solo vuelos directos` persistido, invalidación por cambios según sección 5.1, lista dinámica de aerolíneas de la última búsqueda y catálogo informativo de operadores nacionales actualizado por versiones de APK, con fecha de revisión y fuentes.
- Crear tarjetas de inicio, detalles, estados vacíos, configuración de clave y registro de notificaciones.
- Room para último resultado y retención de avisos, sin historial de precios.
- Tema claro y localización es-CO.

### Fase 3 — Motor de validación y cuota

- Validar cada dirección, fecha, conexiones según el checkbox, precios y moneda; identificar operadores sin filtros de exclusión y mostrar claramente la cobertura parcial.
- Controlar presupuesto global **contrastado con Account API antes de cada tanda**, bloqueo si falla esa comprobación, exclusión de los GET de cuenta del límite local de vuelos, exploración adaptativa hasta 3 salidas, distribución justa y tratamiento de errores.
- Persistir un ID de despacho para recuperar idempotentemente el mismo evento, elegir como máximo un aviso por alerta y revisión por la tarifa verificada más baja bajo el máximo y gestionar concurrencia entre manual y WorkManager.

### Fase 4 — WorkManager y notificaciones

- Implementar coordinador periódico y frecuencias por alerta.
- Solicitar permiso Android 13+, crear canal y notificación local, probar apertura de detalle.
- Manejar retrasos de Android y reinicios sin promesas de puntualidad; instrumentar diagnóstico y **demostrar ejecución real** con interfaz cerrada, pantalla apagada/bloqueada y, si hay dispositivo disponible, tras reinicio. No dar por probada la funcionalidad por el mero hecho de crear un Worker.

### Fase 5 — QA, seguridad y entrega

- Ejecutar pruebas, análisis de logs, verificación de secretos y compilación de **APK debug y APK release firmado con clave propia estable**. El debug también está firmado, con un certificado de depuración; no sustituye el artefacto release para uso habitual.
- Entregar README reproducible, instrucciones de prueba y ambos APK en carpeta `outputs` (o ubicación precisa) cuando el entorno permita generarlos; documentar cómo conservar de forma segura la clave de firma release y sus credenciales, y cómo instalar futuras actualizaciones con la misma identidad y firma preservando datos. No incluir claves ni contraseñas en Git, informes ni chats. Declarar explícitamente cualquier bloqueo de compilación o firma; no sustituir la entrega del release por una guía sin señalarlo como pendiente.
- Registrar limitaciones reales encontradas y recomendaciones de mantenimiento.

---

## 13. Pruebas automatizadas mínimas

No gastar llamadas reales para pruebas unitarias: usar fixtures JSON anónimos y respuestas MockWebServer/fakes.

**Parser / integración:**

1. Ida y vuelta directas válidas, precio total confirmado: devuelve resultado verificado.
2. Ida directa / regreso con escala: **descarta si `directOnly=true`; acepta si `directOnly=false` y el resto de reglas se cumplen**.
3. Ida con escala / regreso directa: **descarta si `directOnly=true`; acepta si `directOnly=false` y el resto de reglas se cumplen**.
4. Vuelta ausente o token ausente: no envía alerta.
5. La respuesta incluye un `price` de ida y vuelta y otro en vuelta: prueba que **no los suma ciegamente**.
6. Respuesta sin secciones `best_flights`, `other_flights`, con `error` o `search_metadata.status != Success`: estado correcto.
7. Moneda USD cuando se pidió COP: rechazo, sin conversión especulativa.
8. Fechas o códigos de aeropuertos distintos: rechazo.
9. Búsqueda limitada a un candidato: indicador de cobertura parcial.
9a. Checkbox alterna `stops=1` y `stops=0`/omisión en primera y segunda etapa; validar localmente conexiones para ambas configuraciones.
9b. Compañías retornadas de forma dinámica: incorporar una aerolínea no presente en el catálogo inicial; no aplicar lista blanca.
9c. Una aerolínea no listada por SerpApi aparece «Sin tarifa devuelta» / «Sin cobertura comprobada», nunca como inexistente ni con precio inventado.
9d. `show_hidden` y `deep_search` no pueden considerarse garantía de cobertura total; verificar límites de uso/cupo antes de nuevas llamadas.
9e. Cambiar origen, destino, fechas o casilla de directos invalida la compatibilidad de la cotización anterior hasta realizar una nueva búsqueda; mostrar el dato previo con sus criterios originales y no notificar con él.
9f. Itinerario con varias aerolíneas entre ambos sentidos: mostrar transportadores correspondientes sin inventar un único operador.
9g. Aerolíneas reportadas sin vuelta verificable: mostrarlas como detectadas sin precio de ida/vuelta confirmado y sin aviso.

**Dominio / reglas:**

10. Precio igual al umbral: **sí** es elegible; mayor que el umbral: **no**.
11. Toda oferta verificada bajo el máximo es elegible aunque ese itinerario o precio se haya notificado en otra revisión.
12. Misma oferta al mismo precio en 10 revisiones válidas: **un aviso por revisión (10 avisos)**.
13. Variación al alza, a la baja o repetición del mismo precio: en cada revisión notificar la tarifa verificada más baja bajo el máximo.
13a. Varias ofertas bajo el máximo en una revisión: como máximo un aviso por alerta, por la tarifa más baja, tanto en búsqueda manual como automática.
13b. Varias ofertas bajo el máximo: seleccionar la más barata; las opciones no seleccionadas no generan aviso.
14. Alerta pausada/vencida/sin permiso: manejo correcto.
15. Fechas regresivas, origen=destino, IATA inválidos o aeropuertos fuera de Colombia, valor cero, campos vacíos: validación impide guardar configuraciones inválidas.
15a. Cambiar nombre o frecuencia no invalida una cotización compatible; cambiar máximo no notifica al guardar y exige nueva consulta antes de avisar por el nuevo umbral.
16. Dos alertas comparten cupo; bloquear al llegar al techo.
17. Una revisión de ida y vuelta no cuenta artificialmente como un único GET.
18. Manual y Worker simultáneos no duplican cobros ni avisos.
19. Retención del último resultado tras cierre, retención visible de notificaciones con máximo 100 y borrado explícito; tras limpiar, la siguiente revisión válida vuelve a avisar la tarifa más baja.
20. La clave nunca aparece en logs ni queda en repositorio, fixture o APK como cadena literal.

**Pruebas de Android (instrumentadas cuando sea posible):**

21. Crear y pausar alerta en UI; comprobar persistencia.
22. Conceder/denegar permiso de notificaciones; manejo visible.
23. WorkManager al reiniciar app y reglas de reintento prudente; comprobar **ejecución real** en segundo plano con app fuera de pantalla, pantalla apagada, desde recientes, reinicio y restricciones de batería cuando haya dispositivo/emulador, con evidencias de hora y ejecución.
24. Accesibilidad básica: tamaño de fuente, contraste y navegación táctil.
25. Instalación del APK en Android 13+ y funcionamiento después de cerrar/reabrir la app; documentar limitaciones de ejecución bajo Doze.
26. Account API devuelve saldo real y validación gratuita de credencial; evitar registro de `api_key` y `account_email` presentes en JSON.
26a. Cada tanda manual o programada comprueba primero el saldo oficial; si falla, no se inicia ningún GET de vuelos, aunque haya clave validada, saldo antiguo o cupo local. Tras recuperar comprobación válida y ambos presupuestos suficientes, puede reanudarse.
26b. Los GET gratuitos de Account API no reservan unidades del límite local de búsquedas; cada GET de vuelos sí lo hace y reduce la capacidad conservadora restante de la tanda, incluso ante fallos de conexión.
27. Saldo real insuficiente bloquea llamadas aunque el contador local indique disponibilidad; diferencias se muestran claramente.
28. Un primer candidato con regreso no válido o precio sobre el máximo no impide examinar candidatos siguientes cuando queda presupuesto; el cierre anticipado exige una oferta válida bajo el máximo.
29. Desglose de precio por trayecto ausente: mostrar “No disponible”, no inventar cifras ni sumar dos totales.
30. Una oferta bajo el límite cuando las notificaciones están deshabilitadas no se marca falsamente como “enviada”; al habilitarlas, puede enviarse si sigue vigente.
31. Borrar el registro visual de notificaciones no provoca alertas duplicadas antiguas.
32. Simular dispositivo sin conexión, app forzada a detener, reinicio, batería restringida y recuperación sin prometer ejecuciones exactas.
33. Verificar generación y firma del APK release con clave propia estable y entrega adicional del APK debug; instalar y lanzar el release cuando haya dispositivo/emulador. Comprobar actualización release sobre release con la misma identidad y firma, conservando alertas y configuración; no declarar pruebas no ejecutadas.
34. El ejemplo se crea una vez en una instalación nueva y queda pausado; eliminarlo y comprobar que no reaparece al abrir, reiniciar o actualizar. Una instalación limpia puede crearlo nuevamente.
35. Catálogo informativo con fecha de revisión y fuentes, distribuido con la APK sin actualización remota automática; una nueva versión actualiza ese catálogo conservando las alertas. Un operador desconocido devuelto por SerpApi aparece en los resultados sin esperar esa actualización.

**Criterio de aceptación V1.4:** app compilable, permite múltiples alertas nacionales de Colombia configurables, checkbox `Solo vuelos directos` marcado por defecto y modificable, consulta SerpApi sin excluir aerolíneas, muestra operadores dinámicos y catálogo informativo actualizado por versiones de APK, distingue tarifas verificadas de las incompletas, invalida cotizaciones incompatibles tras editar criterios, valida escalas de ida y regreso, guarda último resultado sin histórico, comprueba saldo oficial antes de cada tanda y bloquea si falla, respeta cuota compartida sin descontar Account API del límite local de vuelos, mantiene trabajo periódico dentro de las restricciones de Android y envía como máximo un aviso por alerta y revisión entre ofertas elegibles, sin repetir ni avisar subidas del mismo itinerario. Entrega release firmado y debug con evidencia o bloqueo explícito, y no recrea un ejemplo eliminado durante la instalación vigente.

---

## 14. Instrucciones obligatorias para Codex

1. Leer **todo este archivo** antes de escribir código. No cambiar decisiones vinculantes.
2. No afirmar haber probado la API real, compilado la APK ni verificado vuelos sin evidencia de ejecución efectiva.
3. No suponer que la etapa de ida y la de regreso tienen precios separables: validar en documentación y JSON real; **nunca sumarlos sin demostrarlo**.
4. No introducir funcionalidades fuera de alcance sin autorización; en particular, no agregar gráficos, historial de precios, sesiones de usuario, backend o páginas de aerolíneas en V1. El catálogo nacional es informativo y debe distinguirse de disponibilidad obtenida con SerpApi.
5. Verificar que `stops=1` significa solo directos y `stops=0` permite cualquier número de escalas en SerpApi; respaldar el checkbox con validación local de segmentos para ida y regreso.
6. No usar claves hardcodeadas; no pedir que el usuario pegue su clave en conversaciones, capturas o commits.
7. Priorizar consumo gratuito responsable y explicar explícitamente el costo de los GET reales. **Consultar saldo oficial mediante Account API gratuita; no inventar el presupuesto del proveedor solo a partir del contador local.**
8. Pedir autorización para hacer la prueba real con la API y, por separado, para acciones destructivas del proyecto.
9. Entregar incrementos pequeños, pruebas y instrucciones reproducibles. No esconder errores ni reemplazarlos por respuestas simuladas como si fueran reales.
10. Si alguna restricción de SerpApi/Android impide cumplir requisitos, informarlo **antes** de relajar reglas o construir una alternativa.

---

## 15. Preguntas esenciales restantes

**No quedan preguntas funcionales pendientes de la revisión V1.4.** Las decisiones y recomendaciones aprobadas por el propietario están incorporadas en las secciones correspondientes, incluidas rutas nacionales, elegibilidad y cantidad de avisos, bloqueo por fallo de saldo, actualización del catálogo, firma de entrega, efectos de edición y ciclo de vida del ejemplo. La autorización para editar esta especificación no implica por sí sola autorización para iniciar la programación ni consumir cuota; se mantienen las puertas de autorización de la sección 0.

**Única dependencia obligatoria para la prueba contra el servicio real:** el propietario debe disponer de **una cuenta y API key de SerpApi** y **introducir la clave directamente en la app o entorno seguro local**, además de autorizar el consumo de unas pocas consultas de prueba. **No enviar la clave a Codex por chat ni introducirla en el documento MD.** Sin la clave se puede desarrollar y probar la interfaz y el motor usando JSON simulado, pero no certificar la integración real.

**Decisiones técnicas que Codex debe documentar y verificar, sin bloquear el inicio:** versiones concretas de dependencias, disponibilidad actual del plan gratuito, semántica real de precios en el JSON de cada etapa, capacidad de enlaces profundos y restricciones del teléfono sobre ejecuciones en segundo plano. Estos puntos requieren evidencia técnica, **no nuevas preferencias del propietario**.

---

## 16. Fuentes oficiales de referencia (comprobadas el 19-oct-2026)

- **SerpApi — Google Flights API:** https://serpapi.com/google-flights-api  
  Parámetros `type=1`, `stops=0/1`, `departure_token`, `show_hidden`, `deep_search`, `currency`, `adults`, `include_airlines` (no usar como lista blanca predeterminada), estructura JSON, etc.
- **Google Travel — Cobertura:** https://support.google.com/travel/answer/2475306?hl=es  
  Google Flights explica que no necesariamente incluye todas las aerolíneas o vuelos.
- **Aerocivil — Rutas y frecuencias 2026:** https://www.aerocivil.gov.co/documentos/1649/rutas-y-frecuencias-2026/  
  Referencia para contrastar y mantener el catálogo informativo de aerolíneas nacionales; comprobar fecha/alcance en cada actualización.
- **SerpApi — Precios y cómputo de consultas:** https://serpapi.com/pricing  
  Plan Free de 250 búsquedas al mes a la fecha de esta especificación; consultar condiciones vigentes.
- **SerpApi — Account API:** https://serpapi.com/account-api  
  Consulta gratuita para saldo real y consumo de cuota, independiente del contador local.
- **SerpApi — Opciones de reserva:** https://serpapi.com/google-flights-booking-options  
  Fuente opcional de información cuando existe `booking_token`; no garantizar reserva ni introducirla como dependencia obligatoria sin presupuesto y prueba previa.
- **Android — WorkManager (definir trabajo periódico):** https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work  
  Periodicidad mínima técnica de 15 minutos, **sin puntualidad garantizada**.
- **Android — Tareas persistentes y reinicio:** https://developer.android.com/develop/background-work/background-tasks/persistent  
  WorkManager admite tareas persistentes y periódicas después de cerrar/reiniciar, sujeto a límites del sistema.
- **Android — Doze y App Standby:** https://developer.android.com/training/monitoring-device-state/doze-standby  
  Restricciones de batería y aplazamiento de trabajos en segundo plano.
- **Android — Permiso de notificaciones Android 13+:** https://developer.android.com/develop/ui/compose/notifications/notification-permission
- **Android — Keystore:** https://developer.android.com/privacy-and-security/keystore
- **Android — Firma de aplicaciones:** https://developer.android.com/studio/publish/app-signing  
  Diferencias entre firma debug y release, conservación de claves y firma de actualizaciones.

**Nota final:** documento **consolidado V1.4** para implementar las decisiones aprobadas de la V1; la comprobación en vivo de SerpApi es una **puerta técnica de validación**, no un requisito funcional todavía sin respuesta. **No existe una cotización SerpApi verificada aportada por esta especificación** y **no se ha creado una tarea de recordatorio/alerta dentro de ChatGPT**.

---

## 17. Checklist de entrega final y definición de «terminado» (auditoría definitiva)

El estado actual de cada criterio se registra en [docs/INFORME_IMPLEMENTACION_V1.md](docs/INFORME_IMPLEMENTACION_V1.md). Ese informe distingue implementación, pruebas aprobadas, cobertura parcial y pruebas en teléfono físico todavía no ejecutadas; esta especificación sigue definiendo los criterios vinculantes.

En esta lista, `[x]` significa que el criterio tiene evidencia suficiente conforme a su alcance; `[ ]` significa que está parcial o que falta una prueba de aceptación. El informe enlazado explica cada caso y no permite interpretar un `[ ]` como función ausente automáticamente.

- [ ] El proyecto abre en Android Studio y **Gradle compila limpiamente** con un JDK/SDK documentados.
- [x] Los **APK debug y release firmado con clave propia estable se generan realmente**, con ubicación y nombre exactos entregados. Si existe dispositivo/emulador compatible, se instala y lanza el release y se comprueba una actualización release sobre release con la misma identidad y firma preservando datos; si no existe, reportar esas pruebas como **no ejecutadas**. Documentar custodia segura de la clave de firma y sus credenciales; cualquier artefacto faltante queda pendiente o bloqueado explícitamente.
- [ ] La app acepta una API key ingresada por UI, la cifra con Keystore, comprueba el saldo por Account API y **no registra secretos**, ni en errores HTTP ni en la respuesta de cuenta.
- [x] Se validó con la app V1.0.2 y respuestas reales **un itinerario** de ida y vuelta usando `departure_token`, tras la consulta manual del propietario: dos GET HTTP 200, regreso asociado válido y total Round trip coincidente. Evidencia y límites: `docs/INFORME_IMPLEMENTACION_V1.md`; no se conserva el JSON crudo y no prueba la cobertura completa.
- [ ] El checkbox `Solo vuelos directos` aparece por alerta, inicialmente marcado. Marcado filtra ida y regreso sin escalas; desmarcado admite conexiones, valida las rutas y fechas exactas y notifica únicamente por **precio total observado**, en COP, por adulto, con `precio <= umbral`.
- [ ] V1 admite únicamente rutas entre aeropuertos de Colombia y valida su identidad y país, además del formato IATA; las rutas internacionales se rechazan claramente.
- [ ] La consulta por defecto no filtra aerolíneas y la UI muestra operadores encontrados, incluidos desconocidos en el catálogo, combinaciones mixtas y un catálogo nacional informativo actualizado mediante versiones de APK, con fecha de revisión y fuentes. No incorpora actualización automática remota ni declara cobertura total. Aerolíneas ausentes no se presentan como inexistentes o sin vuelos confirmados.
- [x] Se explica al propietario que el resultado no garantiza disponibilidad al pagar, ocho cupos, impuestos/equipaje no acreditados o un precio de cada trayecto cuando SerpApi no lo desglosa.
- [ ] Múltiples alertas: crear, editar, pausar, reanudar, eliminar, consultar manual y programar frecuencia; impedir consumo descontrolado y advertir sobre cobertura parcial.
- [ ] Cambiar ruta, fechas o filtro de escalas invalida la compatibilidad de la cotización anterior y exige nueva verificación. Nombre o frecuencia no invalidan cotizaciones compatibles; cambiar el máximo exige una nueva consulta antes de avisar por esa modificación.
- [ ] Contador local **y** saldo real de SerpApi independientes; saldo oficial comprobado antes de cada tanda manual o programada, bloqueo si falla la comprobación y reanudación prudente al recuperarse con ambos presupuestos suficientes. Cada GET de vuelos reserva una unidad local y reduce la capacidad conservadora de la tanda; los GET gratuitos de Account API quedan excluidos de ese límite local.
- [ ] Máximo **un aviso por alerta y revisión**, siempre por la tarifa verificada más baja bajo el máximo aunque se haya avisado el mismo precio antes. La notificación indica “Tarifa más baja verificada” y no atribuye cobertura exhaustiva.
- [ ] Último precio, estado y hora persistidos sin historial de precios; registro visible de hasta 100 avisos. Limpiar el registro no impide notificar la tarifa más baja en la próxima revisión.
- [ ] Alerta de ejemplo creada una sola vez en instalación nueva, pausada y sin recreación tras eliminarla, abrir, reiniciar o actualizar; una instalación limpia puede volver a crearla.
- [ ] WorkManager se prueba **realmente** ejecutando una revisión programada con app cerrada normalmente y pantalla bloqueada/apagada; se valida reprogramación tras reinicio del dispositivo cuando es posible; se documentan detención forzada, Doze, red desconectada y ahorro de batería, con evidencia y clasificación aprobado/fallido/no ejecutado.
- [x] Se realizan pruebas automáticas offline de parser, precio, fecha, directos, cuota, concurrencia, idempotencia del despacho, errores y UI, y se informa **aprobadas/fallidas/no ejecutadas**. Evidencia del 20-10-2026: 45 pruebas JVM y 24 instrumentadas aprobadas (0 fallos/errores), más `lintDebug` y `lintRelease` aprobados; la suite no realizó GET reales a SerpApi. La regresión de notificación cubre repetir la tarifa en una revisión nueva e idempotencia al reintentar el mismo evento.
- [x] Entrega de `README.md` con requisitos, pasos de compilación, dónde instalar la APK, permisos, explicación de cuota, observaciones de seguridad y limitaciones conocidas.
- [x] Reporte final conciso: **implementado / pendiente / bloqueado**, evidencia de compilación y pruebas, número exacto de búsquedas reales consumidas y pasos manuales restantes.

**Regla de bloqueo:** si los resultados reales de SerpApi no permiten demostrar una tarifa única de ida y regreso válida según la configuración de escalas asociada a las fechas elegidas, el módulo de **notificaciones de ofertas** no se activa con valores supuestos. Codex documenta el bloqueo y pide decisión al propietario, sin sustituir automáticamente el proveedor aprobado.

---

## 18. Criterios operativos de seguridad y calidad que no se deben pasar por alto

### 18.1. Modo de inicio seguro

- Al instalar la APK por primera vez, **ninguna búsqueda de vuelos se ejecuta por sí sola**.
- El usuario introduce su clave en la interfaz; el botón “Probar conexión” consulta la **Account API** y muestra su saldo, sin gastar búsquedas de vuelos.
- La alerta de ejemplo se crea **pausada, una sola vez en una instalación nueva**. Eliminarla no provoca su recreación al abrir, reiniciar o actualizar. Solo una acción explícita del propietario puede activar el monitoreo.
- Una simulación o fixture JSON nunca se presenta como precio real; colocar etiqueta visible “DATOS DE PRUEBA” en entornos de desarrollo si se muestran en pantalla.

### 18.2. Reglas temporales y de fiabilidad

- Con conexiones permitidas (`directOnly=false`), usar la fecha **local de salida de cada trayecto** para validar el día elegido; las escalas pueden cruzar medianoche. No descartar ni aceptar sin verificar la secuencia de segmentos, aeropuerto final y fecha de salida local.
- Guardar las fechas del itinerario como **fechas de calendario del aeropuerto**, no reinterpretarlas como fecha UTC cuando se cambia la zona horaria del celular.
- Guardar el momento de consulta/observación como instante UTC y mostrarlo convertido a hora local del dispositivo.
- Diferenciar `fecha programada`, `momento real de ejecución` y `momento de observación del precio`; WorkManager no garantiza periodicidad exacta ni ejecutar después de forzar detención.
- Cuando falta conexión, no se presentan como recientes tarifas de búsquedas anteriores. Indicar explícitamente “Último precio observado hace X”.

### 18.3. Precio y notificaciones

- Una comparación de precio se refiere al **total observado de ida y vuelta para un adulto** en COP, no al precio de salida, al mínimo mensual ni al precio medio de la ruta.
- El API puede devolver tarifas que cambien al pulsar el enlace o añadir equipaje. El mensaje debe decir “precio observado; verifica condiciones antes de comprar”, sin afirmar disponibilidad para ocho personas.
- Conservar el ID técnico de cada despacho pendiente para recuperar idempotentemente ese mismo evento; no usarlo ni usar mínimos previos para impedir avisos en revisiones nuevas. No almacenar una serie histórica de cotizaciones.
- Emitir como máximo un aviso por alerta y revisión, por la tarifa verificada más baja bajo el máximo, incluso si el mismo itinerario/precio se avisó antes; mantener visibles las demás opciones verificadas.
- Si una revisión parcial comprueba tres candidatos, no afirmar que se han agotado todas las alternativas o que no hay ofertas en el mercado.

### 18.4. Operaciones seguras para Codex

- Antes de comandos que borren archivos, migren datos de forma destructiva, reemplacen proyectos, publiquen repositorios o generen costos, pedir autorización.
- Mantener secretos fuera de VCS e informes. Añadir `.gitignore` seguro y documentación de instalación/configuración; no incluir claves en capturas de los resultados.
- No incrustar resultados externos de ejemplo como producción. En simulaciones de cuota se deben mockear ambos endpoints.
- No cambiar los requisitos con el argumento de “hacer la app más bonita”: el diseño Material 3 solo claro es obligatorio; funciones V2 requieren consentimiento.

### 18.5. Qué significa entregar el proyecto

La entrega incluye el **código Kotlin íntegro**, recursos y Gradle Wrapper, `README.md`, pruebas automatizadas con fixtures sanitizados, documentación mínima de configuración, evidencia de build o bloqueo, y **APK debug y APK release firmado generados realmente** si el entorno permite compilar y firmar. El release es el artefacto para uso habitual, firmado con una clave propia estable cuya custodia y uso en futuras actualizaciones se documentan; el debug se destina a pruebas. Si no puede generar alguno, declararlo pendiente o bloqueado, dejar instrucciones exactas para `assembleDebug`, `assembleRelease` con configuración segura de firma y un procedimiento de instalación/sideload, sin fingir que existen binarios ni sustituir silenciosamente la entrega requerida.

---

## 19. Resumen de aprobación y advertencias no bloqueantes

**Decisiones ya aprobadas por el propietario:** Android 13+, Kotlin, APK privada release firmada con clave propia estable y debug para pruebas, múltiples alertas de rutas nacionales de Colombia, frecuencia configurable, checkbox `Solo vuelos directos` (por defecto marcado, desmarcable), operadores dinámicos hasta donde SerpApi los devuelve y catálogo informativo actualizado por versiones de APK con fecha de revisión y fuentes, ejecución comprobable en segundo plano dentro de límites Android, SerpApi como única fuente de tarifas, máximo un aviso por alerta y revisión por la tarifa verificada más baja dentro del máximo, incluso si se avisó el mismo itinerario o importe anteriormente, un adulto, último precio + avisos, tema claro. Comprobar saldo oficial antes de cada tanda y suspender búsquedas si falla, sin continuar solo con el contador local; Account API gratuita queda fuera del límite local de vuelos. Aplicar los efectos de edición de la sección 5.1 y no recrear el ejemplo eliminado dentro de la instalación vigente.

**Configuración inicial propuesta en este documento:** casilla `Solo vuelos directos` marcada, modo `Todas las aerolíneas`, frecuencia de 24 h modificable a 6/8/12/24 h, tope local conservador de **200 GET de vuelos iniciados / 30 días**, sin descontar Account API, 3 candidatos de ida como máximo por ronda si la cuota lo permite, y alerta Cartagena–Cali de ejemplo pausada, creada una sola vez en una instalación nueva. Estos son valores técnicos de partida, no datos observados en vuelos.

**Limitaciones que deben explicarse:** tarifas observadas ≠ reserva; búsquedas de un adulto ≠ disponibilidad para ocho pasajeros; datos cacheados pueden estar desactualizados; Android no garantiza revisar con puntualidad; la casilla define si se aceptan escalas; solo se vigilan combinaciones exploradas dentro de la cuota; Google Flights no asegura cobertura universal de operadores colombianos.

**Validación real existente:** el propietario aportó una prueba manual ejecutada con la app V1.0.2 para CTG–CLO, 2027-01-25/2027-02-03: dos GET de vuelos HTTP 200, salida y regreso asociados marcados `Round trip`, mismo precio y `VERIFICADO` por COP 599.350. Coincide con la opción JetSMART de igual importe de la captura manual de Google Flights. El presupuesto local pasó de 2/200 a 4/200; la cuenta mostraba uso 6/250, por lo que dos búsquedas mensuales no figuran en el contador local de la app. Esta prueba confirma solo una salida candidata y no valida ejecución automática de WorkManager, precio al comprar ni cobertura del mercado. La app no presenta fixtures como resultados reales.

**Alcance de esta aprobación V1.4:** incorpora las decisiones funcionales aprobadas y se mantiene como contrato del producto. La autorización posterior del propietario permitió implementar la app; la evidencia actual, los límites y las verificaciones aún pendientes están en el informe V1 enlazado en la sección 17.
