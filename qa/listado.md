# Flujo 8 · Listado de comprobantes, filtros y paginación

Auditoría independiente (contexto limpio, worktree `wt-listado`, `E2E_PORT=3290`, `API_BASE_URL` a puerto muerto).
19 mutaciones dirigidas, arnés validado con un letal conocido y un no-op en cada lado. Suites: Vitest 150/150 · `:adapters:out-persistence` 50 · `:adapters:in-rest` 120 · `:application` 99, verdes.
**Veredicto de la auditoría #1: NO CERTIFICADO — 1 bloqueante, 5 importantes.**

Aclaración de alcance: **no existe buscador**. Los filtros son estado, rango de fechas y serie; las pestañas por tipo de comprobante están deshabilitadas.

Leyenda: 🔧 corregido, a la espera de recert · ✅ verificado por una recert posterior · ⬜ abierto, en [pendientes.md](pendientes.md).

## Bloqueantes

| Estado | Hallazgo | Dónde | Arreglo |
|---|---|---|---|
| 🔧 | **Al cambiar de empresa, el listado sigue mostrando los comprobantes de la anterior, e indefinidamente si el refetch falla.** La clave de caché de react-query no incluye la empresa. El cambio de empresa es una navegación blanda: la tabla no se desmonta, y como ya hay datos en caché para esa clave, los datos correctos que acaba de renderizar el servidor se descartan en favor de las filas de la otra empresa. Reproducido: con el refetch abortado, a los 15 segundos la barra dice «EMPRESA C SAC · RUC 20512333797» y la tabla muestra las 14 facturas de otra empresa con sus receptores e importes, sin ningún aviso. Con red sana la ventana es de unos 213 ms, pero existe. No cruza cuentas —el backend corta con 403— pero sí cruza empresas dentro de la cuenta, y atribuye información fiscal a un RUC emisor que no la emitió | `comprobantes-table.tsx` | la empresa va primero en la clave, y la clave se extrajo a `claveComprobantes` para poder atarla con un test. El e2e del cambio de empresa quedó pendiente: el selector vive en la barra lateral y no se abre en el viewport de la suite |

## Importantes

| Estado | Hallazgo | Dónde | Arreglo |
|---|---|---|---|
| ⬜ | El refresco del listado falla en silencio: el componente nunca lee el estado de error. Con la red cortada, tras 20 segundos y 7 intentos la tabla sigue con las filas viejas, sin ninguna alerta, y el icono de refrescar **gira para siempre** porque entre el reintento y el sondeo cada 10 segundos nunca baja. Un 500 con mensaje del backend no se muestra en ninguna parte. La página que sondea comprobantes en estado no final puede quedar congelada mostrando «Enviado» de hace horas mientras SUNAT ya los rechazó | `comprobantes-table.tsx` | leer `isError` y avisar; el render del servidor sí avisa, el refresco del cliente no |
| ⬜ | Un `hasta` anterior al `desde` **ensancha** la lista en vez de acotarla: el cliente borra el `hasta` en silencio, el campo queda en blanco y el resultado crece. La API devuelve 400 `RANGO_INVALIDO` para ese rango y el mock también; el portal es el único que lo tapa. Queda una URL compartible que dice `hasta=2026-09-01` y devuelve resultados posteriores | `lib/api/facturas.ts` | propagar el 400 en vez de descartar el campo |
| ⬜ | El número de página llega sin validar desde la URL: `?pagina=-5` muestra «Mostrando -59–-50 de 14»; `?pagina=99` dice **«Todavía no emitiste ningún comprobante»** habiendo 14, y sin enlace para quitar filtros, así que el usuario queda sin salida; `?pagina=2.5` contra el backend real es 400, o sea que en producción la página entera cae en la pantalla de error | `(privado)/comprobantes/page.tsx` | el mismo saneado que ya tiene `por_pagina` |
| ⬜ | Paridad del mock: acepta seis clases de parámetro que el backend rechaza con 400 (página fraccionaria o enorme, estado inexistente, fecha con otro formato, serie mal formada, `por_pagina` sin tope). Tampoco verifica que la empresa pertenezca a la cuenta, así que **ningún e2e detectaría una regresión del 403 por empresa ajena** | `src/mocks/handlers.ts` | lo único que lo cubre hoy es un test de bootstrap |
| ⬜ | Los importes que muestra el listado son un recálculo, sin contraste contra lo firmado: el `SELECT` del listado no trae las cinco columnas de totales, que se **escriben** en el INSERT y **nunca se leen** en ningún SQL del proyecto. El test del repositorio compara el objeto en memoria contra el releído, los dos calculados con el mismo código. Cualquier cambio en el cálculo hace que el listado muestre un importe distinto del que viajó en el XML, y nada lo denuncia | `JdbcComprobanteRepository` | leer los totales persistidos, que ya existen y están muertos |

## Menores

En [pendientes.md](pendientes.md): «IGV S/ 0.00» en un comprobante IVAP que sí lleva impuesto dentro del total (el total está bien, el subrenglón contradice su composición; igual quedan invisibles el ISC y el ICBPER), tres estados del filtro que ningún camino del backend puede producir, el botón de PDF deshabilitado que es código muerto contra la API real, el conteo que no hace el mismo JOIN que el listado, que `GET /v1/facturas` devuelva también notas sin forma de separarlas, que un comprobante con baja en curso se vea «Aceptado» a secas, y el orden sin desempate.

## Mutaciones

12 mueren de 19. Los dos controles se comportan como deben.

**El hueco más grave es de cobertura del dinero**: cambiar la columna «Importe Total» para que muestre la base gravada en vez del total deja **150 tests de Vitest y 49 e2e en verde**. Ningún test afirma sobre el importe que muestra el listado. Tampoco sobre el color ni la etiqueta de la insignia de estado: pintar un aceptado con el estilo de rechazo, o renombrar «Rechazado» a «Aceptado», sobreviven las dos.

Del lado del backend sobreviven exactamente los dos huecos de paginación y orden: un offset que **repite una fila** entre páginas, y listar de la más vieja a la más nueva. No hay ningún test que pagine ni que fije el orden, que es contrato documentado y en lo que se apoya el portal.

## Lo que sí resiste

- **Aislamiento entre cuentas: sólido.** El filtro valida que la empresa pertenezca a la cuenta del JWT, y el proxy del portal **construye las cabeceras de cero** desde las cookies, así que una empresa puesta por el navegador nunca se reenvía. Cubierto de punta a punta, y la mutación que abre la fuga muere.
- **Sin inyección SQL.** Todo el `WHERE` se arma con fragmentos fijos y parámetros; el estado es un enum, las fechas son fechas, las páginas enteros y la serie pasa por una expresión regular. Una serie con `' OR '1'='1` da 400.
- **Paginación consistente en el camino normal**, con el total correcto a través del proxy y reflejando los filtros; los bordes del rango de fechas son inclusive y el conteo usa el mismo `WHERE`.
- **El estado que devuelve la API no miente**, y el enlace al CDR aparece solo cuando hay constancia.
- El saneado de los demás parámetros sí tiene tests unitarios, y el render del servidor surfacea los errores de la API con un botón de reintento.
