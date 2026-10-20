# Propuesta visual de Alertas de vuelos

Bocetos de alta fidelidad para revisar la organización de la aplicación Android. Son una propuesta de diseño con datos ilustrativos; esta entrega contiene imágenes, sin cambios en la aplicación ni nuevas consultas a SerpApi.

## Archivos

- [Alertas](01-alertas.png)
- [Crear / editar alerta](02-crear-editar-alerta.png)
- [Detalle y resultados](03-detalle-resultados.png)
- [Ajustes](04-ajustes.png)
- [Diagnóstico](05-diagnostico.png)
- [Actividad](06-actividad.png)

Las cinco pantallas solicitadas son Alertas, Crear/editar, Detalle/resultados, Ajustes y Diagnóstico. Actividad completa la navegación con los avisos enviados que la aplicación ya guarda. El catálogo informativo se abre desde Ajustes como vista secundaria.

## Criterios de experiencia

- Mostrar primero la tarifa más baja observada y verificada, junto con su ruta y fechas.
- Mostrar el total de ida y vuelta una sola vez, en COP; no sumar importes por etapa.
- Omitir horarios de vuelos. Las horas visibles identifican revisiones, avisos o el estado de Android.
- Navegación principal de Alertas, Actividad y Ajustes; formulario, detalle y diagnóstico se abren como pantallas secundarias con acción de volver.
- Una acción principal por contexto y campos agrupados por tarea.
- Crear alertas pausadas y mostrar el consumo estimado antes de activarlas.
- Conservar el aviso de exploración parcial y la advertencia sobre precio observado en el detalle.
- Avisos repetidos del mismo precio se presentan en Actividad como revisiones diferentes.
- Mantener cuenta y presupuesto local claramente diferenciados.
- Concentrar las pruebas y registros en Diagnóstico, con acceso a copiar y limpiar.
- Usar textos breves, buen contraste, espaciado consistente e iconos con etiquetas.

## Lenguaje visual

Tema claro, superficies blancas, fondo gris muy claro, texto azul oscuro y acciones teal. El verde se reserva para estados correctos. Controles con objetivos táctiles de al menos 48 dp en la implementación; márgenes y tipografías deberán adaptarse al tamaño de pantalla y al escalado de fuente de Android.

La variante de creación representa también el formulario de edición mediante el título «Editar alerta» y la acción «Guardar cambios». Los diálogos de fecha, confirmación, clave y límite completan los flujos existentes.

Los prompts utilizados se conservan en [PROMPTS.md](PROMPTS.md). Se empleó la herramienta integrada de generación de imágenes.
