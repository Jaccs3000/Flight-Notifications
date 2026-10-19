# Semántica del precio de Google Flights en SerpApi

## Estado

**No verificada contra una respuesta real de la cuenta del propietario.** Los precios de los fixtures son ficticios y no sirven para inferir cómo queda representado el total de ida y vuelta en la ruta CTG–CLO.

La documentación de [Google Flights API de SerpApi](https://serpapi.com/google-flights-api) describe `price` como un entero en la moneda solicitada y `departure_token` como el mecanismo para pedir el regreso asociado a una salida de ida y vuelta. La documentación basta para construir la petición, pero no prueba por sí sola que el campo `price` de la segunda respuesta represente el total de una combinación concreta, ni que sea correcto sumar precios de ambas etapas.

## Comportamiento implementado

- La primera petición solicita `type=1`, fechas de ida y vuelta y `currency=COP`.
- Se escoge una salida compatible con la fecha/ruta y su `departure_token`; no se decodifica ni persiste.
- La segunda petición conserva los mismos criterios y añade ese token opaco.
- Se procesan todos los itinerarios recibidos, sin filtros por compañía.
- Si el campo `price` es un entero positivo y exacto, se presenta como importe devuelto para esa opción. No se suma entre etapas y no se etiqueta como total verificado.
- La interfaz muestra `Total de ida y vuelta: no verificado` cuando detecta una pareja de itinerarios.

## Evidencia necesaria para cambiar este estado

Después de una prueba real autorizada, comparar la consulta exacta (sin guardar `api_key` ni token), `search_parameters`, el tipo reflejado, moneda, pasajeros/clase si el proveedor los refleja, los segmentos de ida y la respuesta obtenida con el token. Determinar qué objeto y campo corresponden exactamente a la combinación seleccionada. Conservar solo un fixture mínimo sanitizado con el token sustituido de forma consistente. Si la respuesta no acredita vínculo inequívoco con la opción, conservar el resultado como no verificado.
