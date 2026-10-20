# Semántica del precio de Google Flights en SerpApi

> Esta nota conserva evidencia real aportada durante el PoC y una comprobación manual real posterior con V1.0.2, además de la regla conservadora implementada. Los alcances y límites están en [el informe de implementación](INFORME_IMPLEMENTACION_V1.md).

## Evidencia observada

En la prueba manual de CTG–CLO del 25–28 de marzo de 2027, la app y Google Flights mostraron la misma ida JetSMART JA 5532 (07:44–09:20) y el regreso JA 5531 (06:24–07:54). Google Flights mostró COP 493.440 como precio de ida y vuelta; SerpApi devolvió COP 493.440 tanto para el resultado `Round trip` de ida como para el regreso asociado al `departure_token`. No se deben sumar esos dos valores.

La documentación oficial describe `price` como el precio del billete en la moneda solicitada, `type` como el tipo de viaje y `departure_token` como el mecanismo para solicitar regresos asociados a una salida de ida y vuelta: [Google Flights API de SerpApi](https://serpapi.com/google-flights-api).

En una prueba manual real reportada el **20 de octubre de 2026** para CTG–CLO, 2027-01-25/2027-02-03, directos y COP, V1.0.2 inició dos GET HTTP 200. La ida devolvió tres opciones con token; la búsqueda asociada devolvió un regreso válido. La opción seleccionada de ida y el regreso asociado se marcaron `Round trip`, sus importes coincidieron y la app informó **COP 599.350 una sola vez**. Coincide con la opción JetSMART de igual importe de la captura de Google Flights compartida previamente para esas fechas. El log confirma que hubo tokens candidatos y una respuesta válida asociada, pero no se conserva el JSON crudo ni el valor del token. Solo se verificó una salida seleccionada, no todas las combinaciones ni la disponibilidad al pagar.

## Regla implementada

- La primera petición solicita ida y vuelta (`type=1`) en COP y escoge una salida válida con `departure_token`.
- La segunda petición usa ese token opaco y valida la ruta, fecha y secuencia del regreso.
- Se informa una tarifa total observada como `VERIFICADO` solo si la opción de ida y una opción de regreso asociada están marcadas `Round trip`, ambas tienen un precio positivo y exacto, y los dos importes coinciden.
- Se usa el importe coincidente una sola vez. Nunca se suman los importes de ida y regreso.
- Si falta el tipo/precio o los importes no coinciden, la pareja puede mostrarse como `NO_VERIFICADO`; el resultado no se presenta como una reserva ni garantiza que el precio siga disponible al comprar.

La regla se apoya en la respuesta real compartida para la prueba CTG–CLO y se comprueba con fixtures sintéticos. No se almacenan la API key, el token real ni cuerpos completos de respuestas.

## Límites

SerpApi indica que los resultados por defecto pueden diferir de Google Flights en navegador; `deep_search=true` ofrece resultados iguales a la página a costa de mayor tiempo de respuesta. La app no activa ese parámetro automáticamente.
