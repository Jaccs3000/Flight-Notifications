# Plan para aplicar los bocetos a la aplicación Android

Fecha: 20 de octubre de 2026. Estado: implementación aplicada en V1.0.9; falta la comprobación visual en emulador.
Objetivo: reproducir las seis imágenes finales en composición, colores, tipografía, iconos, espaciados y comportamiento de los controles, usando los datos reales de la aplicación.

## Estado de ejecución V1.0.9

- Aplicados colores con contraste reforzado y estados visuales diferenciados para éxito, advertencia y error.
- Unificados los iconos vectoriales, las tarjetas, las etiquetas de estado y los botones de acción en alertas, edición, detalle, actividad, ajustes y diagnóstico.
- Reorganizadas las acciones de alertas para separar revisar, ver resultados, editar y eliminar; las acciones menos frecuentes quedan en un menú.
- En resultados, separadas las aerolíneas detectadas de las tarifas verificadas y explicado cuándo una exploración parcial pudo dejar opciones sin revisar.
- El autocompletado filtra dentro del formulario mientras se conserva el foco; el campo de máximo agrupa cifras COP sin decimales.
- Añadidos un selector de hora inicial y controles para revisar cada 6, 8, 12 o 24 horas; la primera revisión usa la hora seleccionada y las siguientes parten del intento anterior.
- Añadidos el prefijo `$` con el código COP, el botón flotante redondo con `+`, la flecha circular sólida de volver y nombres completos de aeropuertos en las alertas.
- Aplicados colores e insignias de iniciales para identificar aerolíneas; reforzado el fondo y los estados seleccionados.
- APK debug y release compilados; `lintVitalRelease` pasó. `apksigner` verificó la firma de release. No se ejecutaron pruebas automatizadas ni la comprobación visual en emulador.

## 1. Referencias y límites

- La referencia visual son los PNG finales de la [propuesta v1](../outputs/diseno-propuesta-v1/README.md). Los PNG prevalecen sobre diferencias menores entre el dibujo y su prompt.
- Los requisitos funcionales aprobados y las instrucciones posteriores del propietario siguen gobernando precios, consultas, cuota, avisos, datos y seguridad.
- Base técnica: V1.0.5 / Room v5, commit `56473df`, actualmente en `C:\Flight-Notifications-main-push-staging`. Al iniciar, confirmar que se desarrolla sobre esa fuente validada o sobre una posterior que conserve sus correcciones.
- Las imágenes están en `C:\Flight-Notifications-main\outputs\diseno-propuesta-v1`. Incorporar las referencias al checkout de desarrollo con rutas relativas antes de implementar.
- El rediseño se realiza en Kotlin y Jetpack Compose, aprovechando los repositorios y ViewModels existentes. La reorganización visual no necesita nuevos datos de negocio persistentes.
- Los saldos, precios, fechas, cantidades de combinaciones y horas de las imágenes son ejemplos; en producción se sustituyen por el estado real. No fijar esos valores en las pantallas.
- Las barras de estado y navegación del sistema las dibuja Android. Dentro del área de la app se reproduce el diseño de referencia, respetando los márgenes del sistema y del teclado.
- El catálogo se mantiene como vista informativa secundaria desde Ajustes. Los seis bocetos no requieren añadir una séptima sección principal.
- No añadir campos de pasajeros, compra de tiquetes, filtros de aerolínea, mapas, gráficas, login ni servicios externos.

## 2. Mapa de navegación

| Tipo | Pantalla | Referencia |
|---|---|---|
| Principal | Alertas | [01-alertas.png](../outputs/diseno-propuesta-v1/01-alertas.png) |
| Secundaria | Crear / editar alerta | [02-crear-editar-alerta.png](../outputs/diseno-propuesta-v1/02-crear-editar-alerta.png) |
| Secundaria | Detalle y resultados | [03-detalle-resultados.png](../outputs/diseno-propuesta-v1/03-detalle-resultados.png) |
| Principal | Ajustes | [04-ajustes.png](../outputs/diseno-propuesta-v1/04-ajustes.png) |
| Secundaria | Diagnóstico | [05-diagnostico.png](../outputs/diseno-propuesta-v1/05-diagnostico.png) |
| Principal | Actividad | [06-actividad.png](../outputs/diseno-propuesta-v1/06-actividad.png) |

La barra inferior contiene Alertas, Actividad y Ajustes, con los mismos iconos, tamaños y selección en las tres pantallas. Formulario, detalle y diagnóstico muestran una acción de volver y ocultan esa barra.

Conservar la selección de sección y posición de lectura. Volver desde una pantalla secundaria regresa al origen. Una notificación abre el detalle de su alerta por ID; si esa alerta se eliminó, mostrar una explicación y permitir volver a Alertas. Evitar destinos repetidos al recibir otra notificación con la app abierta.

## 3. Orden de implementación

### Paso 1. Fijar una especificación visual comprobable

1. Conservar los seis PNG finales con nombre y huella verificable.
2. Medir márgenes, proporciones, tamaños de texto, alturas de controles, radios, bordes y alineaciones a partir de las imágenes.
3. Tomar como referencia inicial un viewport de 390 × 867 dp, fuente al 100 % y tema claro; registrar los insets y la configuración del emulador. La proporción corresponde a los PNG.
4. Fijar familia tipográfica, pesos y recursos de iconos para que el aspecto sea consistente entre pantallas.
5. Crear una tabla de tokens reutilizables y ajustar sus medidas mediante capturas.
6. Preparar datos simulados que permitan representar las seis imágenes sin introducir una API key ni usar SerpApi real.

**Salida:** especificación de medidas, colores, tipografías, iconos y estados de referencia.

### Paso 2. Construir el tema y los componentes compartidos

- Fondo gris claro, superficies blancas, texto azul oscuro y acciones teal, conforme a las referencias.
- Verde reservado para estados correctos/verificados; errores y bloqueos tienen texto y estilo diferenciados.
- Crear componentes compartidos para tarjetas, botones, chips, cabeceras, precio COP, campos, filas de ajustes y estados vacíos.
- Unificar iconos de navegación: campana, historial y engranaje. Usar recursos vectoriales.
- Centralizar formato de moneda, fechas y tiempos de observación. Mostrar COP y total de ida y vuelta; omitir horarios de los vuelos.
- Mantener objetivos táctiles de al menos 48 dp y etiquetas accesibles.

**Salida:** un sistema visual único que evita diferencias entre pantallas.

### Paso 3. Separar la pantalla actual y conectar la navegación

- Sustituir la composición de una sola pantalla larga de `ProbeScreen` y `FlightAlertsSection` por destinos independientes.
- Conectar cada destino a los estados y acciones existentes, sin repetir cargas ni crear una nueva instancia de la lógica de consultas al cambiar de pestaña.
- Conservar borradores y estado de lectura durante rotación/recreación.
- Integrar botón Atrás, apertura desde notificaciones y retorno después de editar/eliminar.
- Mostrar mensajes breves en el contexto de la operación, evitando mensajes de otra pantalla.

**Salida:** navegación funcional de las seis pantallas y apertura correcta desde notificaciones.

### Paso 4. Implementar Alertas y el formulario

**Alertas**
- Título, contador, filtros locales Todas/Activas/Pausadas y botón Nueva alerta como en la imagen.
- Filtrar únicamente datos locales; cambiar un filtro no inicia consultas.
- Tarjetas con ruta, fechas, estado y frecuencia. Dar protagonismo al último precio realmente verificado y compatible con la alerta.
- Mostrar explícitamente resultados anteriores cuando el último intento falló o cambió el criterio; evitar una etiqueta verde de verificación vigente en ese caso.
- Las tarjetas sin resultados o pausadas tienen el aspecto compacto del boceto.
- Acceder al detalle tocando la tarjeta o Ver resultados.
- Mantener un acceso contextual a ajustes de notificaciones cuando estén bloqueadas.

**Crear / editar**
- Mismos grupos de ruta/fechas y precio/frecuencia, con validación junto al campo.
- Seleccionar aeropuertos del catálogo colombiano y fechas con las restricciones actuales.
- Formatear COP al introducirlo sin perder el valor numérico ni aceptar importes fuera del rango permitido.
- Mantener un adulto y clase económica como contexto fijo.
- Frecuencias 6/8/12/24 h, directos inicialmente activado y estimación calculada, sin fijar siempre 60–120.
- Una alerta nueva se guarda pausada. Al editar, conservar el estado de activación y aplicar las reglas aprobadas de edición.
- En edición cambiar título y acción a Editar alerta / Guardar cambios.
- Guardar no inicia búsquedas ni avisos. Considerar confirmación al salir si hay cambios sin guardar.
- Con teclado o fuente ampliada, permitir desplazamiento hasta el botón final sin tapar campos.

**Salida:** listado y CRUD con el aspecto de 01 y 02, y validaciones completas.

### Paso 5. Implementar Detalle y resultados

- Encabezado, tarifa destacada, ida/regreso, cobertura, otras tarifas y acciones como en 03.
- Mostrar el total de ida/vuelta una sola vez; no sumar etapas ni poner importes engañosos por trayecto.
- Calcular Dentro de tu máximo o Supera tu máximo con datos reales.
- Mostrar Exploración parcial sólo cuando el resultado lo indique.
- Dar acceso a todas las combinaciones guardadas mediante el desplegable de la imagen. La cifra procede de los datos reales.
- Evitar filas visualmente repetidas en el resumen, manteniendo acceso a las combinaciones originales y su identidad interna. No fusionar sus precios ni perder ofertas.
- Las aerolíneas desconocidas y mixtas continúan siendo visibles.
- Abrir Google Flights con el enlace validado existente y la advertencia de precio observado.
- Revisar ahora conserva confirmación de consumo, límite actual de hasta 4 GET por alerta y posibilidad de cancelar.
- Menú contextual para editar/eliminar, con confirmación al borrar.
- Distinguir sin resultados, buscando, error, sin tarifa verificable y dato previo.

**Salida:** detalle fiel a 03 y correctamente conectado al motor ya probado.

### Paso 6. Implementar Actividad, Ajustes y Diagnóstico

**Actividad**
- Agrupar los avisos por fecha y reproducir las tarjetas de 06.
- Mostrar el importe de cada aviso guardado, no sustituirlo por el precio actual de la alerta.
- Conservar avisos del mismo precio en revisiones distintas.
- Tocar un aviso abre su alerta y permite reconocer que el aviso es histórico.
- Limpiar solicita confirmación y borra el registro visible según la política actual, sin tocar el presupuesto.
- Incluir estado vacío y lista desplazable para la retención vigente.

**Ajustes**
- Reproducir 04: clave protegida, estado de cuenta, saldo del proveedor, presupuesto local y accesos secundarios.
- Permitir guardar/reemplazar/eliminar la clave con un diálogo de gestión. No revelar el valor almacenado.
- Distinguir cuenta no comprobada, activa, inactiva, error y saldo comprobado anteriormente.
- Conservar los detalles de cuenta necesarios en un desplegable o diálogo secundario.
- Reducir límite conserva las restricciones actuales y muestra errores junto al campo; no permite subirlo ni reinicia el consumo.
- Reflejar los permisos/canal reales al volver desde Ajustes de Android.
- Versión tomada de los metadatos del APK.
- Mantener el catálogo con fuentes/fecha y su advertencia informativa.

**Diagnóstico**
- Reproducir 05: programación, prueba manual plegada y pestañas de registro.
- Comprobar programación no hace llamadas API.
- Mantener la prueba CTG–CLO opcional, fechas/directos editables, confirmación y hasta 2 GET; diferenciarla de la revisión de una alerta.
- Conexión y Vuelos usan sus registros independientes. Copiar/Limpiar actúan sólo sobre la pestaña seleccionada.
- Mantener logs persistidos y saneados, sin keys, tokens o cuerpos de respuesta.
- Abrir esta pantalla o una pestaña no inicia búsquedas de vuelos.

**Salida:** las tres pantallas restantes reproducidas y todas sus acciones existentes accesibles.

### Paso 7. Completar estados y accesibilidad

- Dibujar vacío, carga, búsqueda en progreso, guardado, error, sin red, cuota agotada, cuenta/clave inválida y permiso/canal bloqueado usando los mismos componentes.
- Tratar alertas vencidas y resultados históricos sin aparentar precios vigentes.
- Mantener accesibles las acciones incluso con nombres largos, cantidades grandes o fechas que crucen meses/años.
- Verificar fuente ampliada, TalkBack, contraste, foco, teclado, desplazamiento y botón Atrás.
- Evitar que pulsaciones repetidas dupliquen acciones. Conservar la serialización/coalescencia y la recuperación de avisos.
- No iniciar búsquedas por navegar, guardar un formulario, rotar o recomponer una pantalla.

**Salida:** pantallas completas para uso real, además del estado de ejemplo de las imágenes.

### Paso 8. Comparar capturas y corregir diferencias

1. Renderizar cada pantalla con los datos simulados y el viewport de referencia.
2. Capturar los seis destinos reales del emulador.
3. Comparar cada captura con su PNG mediante superposición y diferencia visual.
4. Corregir orden, alineación, espaciado, tamaños de texto, colores, iconos y dimensiones de controles.
5. Excluir de la comparación sólo los indicadores variables del sistema y las pequeñas diferencias de rasterización de fuentes; registrar toda desviación necesaria.
6. Repetir las capturas después de corregir, hasta cerrar las diferencias materiales.
7. Verificar después en pantalla pequeña, el emulador habitual y fuente ampliada: conservar jerarquía y accesibilidad mediante desplazamiento/adaptación.

**Salida:** seis capturas reales comparables con los bocetos y una lista cerrada de ajustes visuales.

### Paso 9. Verificar comportamiento y ausencia de regresiones

- Actualizar las pruebas de UI afectadas por la navegación y añadir sólo los casos necesarios de las nuevas interacciones.
- Probar CRUD, filtros locales, selector de fechas, validaciones, retorno, rotación y apertura por notificación.
- Probar revisión/confirmación/cancelación, datos previos, permisos, copia/limpieza por registro y gestión de clave/límite.
- Ejecutar las pruebas existentes pertinentes, instrumentación y Lint debug/release.
- Verificar que la presentación no alteró selección de tarifa mínima, avisos repetidos entre revisiones, límite de un aviso por revisión, presupuesto, turnos ni recuperación.
- Usar clientes simulados en desarrollo; registrar cero GET reales para las comprobaciones visuales y automáticas.
- La comprobación física final del propietario seguirá cubriendo actualización y WorkManager con app cerrada, bloqueo y reinicio.

**Salida:** informe de validación visual y funcional, con cualquier escenario pendiente declarado.

### Paso 10. Generar y entregar el APK

- Incrementar la versión respecto de V1.0.5 y `versionCode=7`.
- Compilar mediante el script de entrega y conservar paquete y certificado release.
- Verificar firma, versión y hashes SHA-256; preservar la base y credencial existentes.
- Copiar cada APK generado a `G:\Mi unidad\APK android car\` y conservar copias descargables.
- Entregar APK release, capturas reales de las seis pantallas, SHA-256 e informe de comprobaciones.
- Registrar el código y documentación resultantes en el repositorio autorizado.

**Salida:** APK listo para instalar como actualización y compararlo en el teléfono.

## 4. Criterios de cierre

El rediseño queda terminado cuando:

1. Las seis capturas de la aplicación coinciden visualmente con los PNG de referencia en el viewport fijado, sin diferencias materiales pendientes.
2. Las tres secciones principales y las tres pantallas secundarias navegan correctamente.
3. Todos los controles del boceto realizan la acción prevista, con estados de carga/error y mensajes en su contexto.
4. Los datos reales sustituyen los ejemplos y no se muestra una tarifa antigua como recién verificada.
5. La aplicación sigue notificando la tarifa elegible más baja de cada revisión, aunque repita un precio de otra revisión.
6. Ninguna navegación o filtro local añade gasto de vuelos; las confirmaciones y topes de búsqueda se conservan.
7. La fuente ampliada, el teclado y las pantallas pequeñas no bloquean controles.
8. Las pruebas requeridas y Lint pasan; el APK conserva identidad/firma y está disponible en la ruta de entrega.

## 5. Primera tarea al ejecutar

Fijar las referencias y medidas (paso 1), crear el tema/componentes (paso 2) y conectar la navegación (paso 3). Después implementar y comparar Alertas y Crear/editar antes de extender los mismos componentes al resto. Así se resuelven las decisiones visuales comunes una sola vez.

