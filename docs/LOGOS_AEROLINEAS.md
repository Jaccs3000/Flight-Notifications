# Logos de aerolíneas

Los PNG se incluyen en `app/src/main/res/drawable-nodpi/` para que las alertas guardadas puedan mostrar su logo sin conexión. Se descargaron el 21 de octubre de 2026 del CDN de Google Flights, el mismo formato de URL que documenta [SerpApi para sus resultados de vuelos](https://serpapi.com/google-flights-api).

Se conservan la proporción y los colores de cada imagen. La interfaz usa `ContentScale.Fit`, sin tinte ni recorte del logo. El nombre de la aerolínea acompaña siempre a la imagen y cada logo tiene una descripción accesible.

| Aerolínea | Recurso | Fuente |
|---|---|---|
| Avianca | `airline_av.png` | https://www.gstatic.com/flights/airline_logos/70px/AV.png |
| JetSMART | `airline_ja.png` | https://www.gstatic.com/flights/airline_logos/70px/JA.png |
| LATAM | `airline_la.png` | https://www.gstatic.com/flights/airline_logos/70px/LA.png |
| Wingo | `airline_p5.png` | https://www.gstatic.com/flights/airline_logos/70px/P5.png |
| SATENA | `airline_9r.png` | https://www.gstatic.com/flights/airline_logos/70px/9R.png |
| Clic / Easyfly | `airline_ve.png` | https://www.gstatic.com/flights/airline_logos/70px/VE.png |
| Copa | `airline_cm.png` | https://www.gstatic.com/flights/airline_logos/70px/CM.png |
| American Airlines | `airline_aa.png` | https://www.gstatic.com/flights/airline_logos/70px/AA.png |
| Delta | `airline_dl.png` | https://www.gstatic.com/flights/airline_logos/70px/DL.png |
| United | `airline_ua.png` | https://www.gstatic.com/flights/airline_logos/70px/UA.png |
| Spirit | `airline_nk.png` | https://www.gstatic.com/flights/airline_logos/70px/NK.png |
| Iberia | `airline_ib.png` | https://www.gstatic.com/flights/airline_logos/70px/IB.png |

Las combinaciones de aerolíneas muestran un logo por marca, sin repetir la misma compañía. Los nombres no incluidos en este conjunto siguen apareciendo con su nombre completo y un icono genérico de avión. Este cambio no filtra compañías ni modifica consultas, precios, cuotas o datos guardados.

Comprobación: `AirlineLogoTest` renderiza una alerta verificada de JetSMART, los doce recursos incluidos, una compañía desconocida y una combinación JetSMART/Avianca. Utiliza datos de prueba locales, sin consultas de vuelos.
