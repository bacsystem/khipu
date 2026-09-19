package pe.factura.bootstrap.homologacion;

import java.time.LocalDate;
import java.util.List;

/**
 * Casos de emisión contra e-beta: uno por bloque funcional implementado (los mismos que documenta la guía del portal), más
 * las notas de crédito/débito sobre facturas ya aceptadas en la misma corrida. El receptor es un RUC real y activo (SUNAT,
 * 20131312955) porque e-beta valida el padrón (regla 1083). Resumen diario y comunicación de baja se añadirán con #20 y #31.
 */
final class EscenariosFactura {
    private EscenariosFactura() {}

    static final String CLIENTE = """
        "cliente":{"tipo_doc":"6","num_doc":"20131312955","razon_social":"SUPERINTENDENCIA NACIONAL DE ADUANAS Y DE ADMINISTRACION TRIBUTARIA","direccion":"AV. GARCILASO DE LA VEGA 1472, LIMA"}""";

    /**
     * {@code tasaEspecial}: el tenant se marca en el padrón de tasa especial del IGV antes de emitir. {@code observacionEsperada}:
     * único código 4xxx tolerado aunque el XML sea válido (el RUC de prueba no está en el padrón → 4439); nulo = sin observaciones.
     */
    record Escenario(String id, String descripcion, String endpoint, String cuerpo, boolean tasaEspecial, String observacionEsperada) {
        Escenario(String id, String descripcion, String cuerpo) { this(id, descripcion, "/v1/facturas", cuerpo, false, null); }
        Escenario(String id, String descripcion, String endpoint, String cuerpo) { this(id, descripcion, endpoint, cuerpo, false, null); }
        /** Nombre del caso en los informes de JUnit/Gradle: sin el JSON del cuerpo, que ya queda en la evidencia. */
        @Override public String toString() { return id + " — " + descripcion; }
    }

    static final String NOTAS = "/v1/notas";

    /**
     * {@code serie} y {@code fecha} se inyectan por ejecución. Los marcadores {@code ${ANTICIPO}}, {@code ${GRAVADA}}, {@code ${MIXTA}}
     * y {@code ${CREDITO}} los rellena el test con los números que SUNAT ya aceptó en los escenarios 16, 01, 04 y 09.
     */
    static List<Escenario> todos(String serie, LocalDate fecha) { return todos(serie, serie, fecha); }

    /** {@code serieAnexo}: serie de factura asignada al establecimiento anexo 0002 (escenario 24). */
    static List<Escenario> todos(String serie, String serieAnexo, LocalDate fecha) {
        String cab = "\"serie\":\"" + serie + "\",\"fecha_emision\":\"" + fecha + "\"";
        String cabAnexo = "\"serie\":\"" + serieAnexo + "\",\"fecha_emision\":\"" + fecha + "\"";
        return List.of(
            new Escenario("01-gravada", "Venta interna gravada al contado",
                "{" + cab + ",\"moneda\":\"PEN\"," + CLIENTE + ",\"items\":[" +
                "{\"codigo\":\"SRV-001\",\"descripcion\":\"Servicio de consultoría\",\"unidad\":\"ZZ\",\"cantidad\":1,\"precio_unitario\":1180.00,\"tipo_afectacion_igv\":\"10\"}]}"),
            new Escenario("02-exonerada", "Venta exonerada (Apéndice I)",
                "{" + cab + ",\"moneda\":\"PEN\"," + CLIENTE + ",\"items\":[" +
                "{\"descripcion\":\"Libro impreso\",\"unidad\":\"NIU\",\"cantidad\":3,\"precio_unitario\":50.00,\"tipo_afectacion_igv\":\"20\"}]}"),
            new Escenario("03-inafecta", "Venta inafecta",
                "{" + cab + ",\"moneda\":\"PEN\"," + CLIENTE + ",\"items\":[" +
                "{\"descripcion\":\"Servicio inafecto\",\"unidad\":\"ZZ\",\"cantidad\":1,\"precio_unitario\":300.00,\"tipo_afectacion_igv\":\"30\"}]}"),
            new Escenario("04-mixta", "Gravada + exonerada + inafecta en la misma factura",
                "{" + cab + ",\"moneda\":\"PEN\"," + CLIENTE + ",\"items\":[" +
                "{\"descripcion\":\"Laptop\",\"unidad\":\"NIU\",\"cantidad\":2,\"precio_unitario\":2360.00,\"tipo_afectacion_igv\":\"10\"}," +
                "{\"descripcion\":\"Libro\",\"unidad\":\"NIU\",\"cantidad\":1,\"precio_unitario\":80.00,\"tipo_afectacion_igv\":\"20\"}," +
                "{\"descripcion\":\"Servicio inafecto\",\"unidad\":\"ZZ\",\"cantidad\":1,\"precio_unitario\":100.00,\"tipo_afectacion_igv\":\"30\"}]}"),
            new Escenario("05-gratuita", "Bonificación gratuita junto a una línea onerosa",
                "{" + cab + ",\"moneda\":\"PEN\"," + CLIENTE + ",\"items\":[" +
                "{\"descripcion\":\"Producto\",\"unidad\":\"NIU\",\"cantidad\":10,\"precio_unitario\":11.80,\"tipo_afectacion_igv\":\"10\"}," +
                "{\"descripcion\":\"Bonificación\",\"unidad\":\"NIU\",\"cantidad\":1,\"precio_unitario\":10.00,\"tipo_afectacion_igv\":\"15\"}]}"),
            new Escenario("06-descuentos", "Descuento de línea (00 y 01) y global (02)",
                "{" + cab + ",\"moneda\":\"PEN\"," + CLIENTE + ",\"items\":[" +
                "{\"descripcion\":\"Monitor 27\\\"\",\"unidad\":\"NIU\",\"cantidad\":2,\"precio_unitario\":1180.00,\"tipo_afectacion_igv\":\"10\",\"descuento\":{\"porcentaje\":10}}," +
                "{\"descripcion\":\"Cable HDMI\",\"unidad\":\"NIU\",\"cantidad\":1,\"precio_unitario\":59.00,\"tipo_afectacion_igv\":\"10\",\"descuento\":{\"monto\":5.00,\"afecta_base_igv\":false}}]," +
                "\"descuento_global\":{\"porcentaje\":2}}"),
            new Escenario("07-cargos", "Cargos de línea (47/48) y globales (49/46)",
                "{" + cab + ",\"moneda\":\"PEN\"," + CLIENTE + ",\"items\":[" +
                "{\"descripcion\":\"Refrigeradora 300 L\",\"unidad\":\"NIU\",\"cantidad\":1,\"precio_unitario\":1770.00,\"tipo_afectacion_igv\":\"10\"," +
                "\"cargos\":[{\"monto\":50.00},{\"monto\":20.00,\"afecta_base_igv\":false}]}]," +
                "\"cargos\":[{\"monto\":30.00},{\"porcentaje\":10,\"motivo\":\"recargo_consumo\"}]}"),
            new Escenario("08-usd", "Factura en dólares",
                "{" + cab + ",\"moneda\":\"USD\"," + CLIENTE + ",\"items\":[" +
                "{\"descripcion\":\"Licencia anual\",\"unidad\":\"ZZ\",\"cantidad\":1,\"precio_unitario\":590.00,\"tipo_afectacion_igv\":\"10\"}]}"),
            new Escenario("09-credito", "Al crédito con dos cuotas",
                "{" + cab + ",\"moneda\":\"PEN\"," + CLIENTE + ",\"items\":[" +
                "{\"descripcion\":\"Proyecto\",\"unidad\":\"ZZ\",\"cantidad\":1,\"precio_unitario\":1180.00,\"tipo_afectacion_igv\":\"10\"}]," +
                "\"forma_pago\":{\"tipo\":\"credito\",\"monto_pendiente\":1180.00,\"cuotas\":[" +
                "{\"monto\":590.00,\"vencimiento\":\"" + fecha.plusDays(30) + "\"},{\"monto\":590.00,\"vencimiento\":\"" + fecha.plusDays(60) + "\"}]}}"),
            new Escenario("10-detraccion", "Operación sujeta a detracción (1001)",
                "{" + cab + ",\"moneda\":\"PEN\",\"tipo_operacion\":\"1001\"," + CLIENTE + ",\"items\":[" +
                "{\"descripcion\":\"Servicio de mantenimiento\",\"unidad\":\"ZZ\",\"cantidad\":1,\"precio_unitario\":1180.00,\"tipo_afectacion_igv\":\"10\"}]," +
                "\"detraccion\":{\"codigo_bien_servicio\":\"022\",\"porcentaje\":12,\"cuenta_banco_nacion\":\"00-000-123456\"}}"),
            new Escenario("11-retencion", "Con retención del IGV informada (62)",
                "{" + cab + ",\"moneda\":\"PEN\"," + CLIENTE + ",\"items\":[" +
                "{\"descripcion\":\"Servicio\",\"unidad\":\"ZZ\",\"cantidad\":1,\"precio_unitario\":1180.00,\"tipo_afectacion_igv\":\"10\"}]," +
                "\"retencion_igv\":{\"porcentaje\":3}}"),
            new Escenario("12-percepcion", "Operación sujeta a percepción (2001, régimen 51)",
                "{" + cab + ",\"moneda\":\"PEN\",\"tipo_operacion\":\"2001\"," + CLIENTE + ",\"items\":[" +
                "{\"descripcion\":\"Mercadería\",\"unidad\":\"NIU\",\"cantidad\":10,\"precio_unitario\":118.00,\"tipo_afectacion_igv\":\"10\"}]," +
                "\"percepcion\":{\"regimen\":\"51\"}}"),
            new Escenario("13-isc-icbper", "ISC al valor e ICBPER",
                "{" + cab + ",\"moneda\":\"PEN\"," + CLIENTE + ",\"items\":[" +
                "{\"descripcion\":\"Cerveza\",\"unidad\":\"NIU\",\"cantidad\":12,\"precio_unitario\":5.50,\"tipo_afectacion_igv\":\"10\",\"isc\":{\"sistema\":\"01\",\"tasa\":35}}," +
                "{\"descripcion\":\"Bolsa plástica\",\"unidad\":\"NIU\",\"cantidad\":2,\"precio_unitario\":0.60,\"tipo_afectacion_igv\":\"10\",\"icbper\":true}]}"),
            new Escenario("14-referencias-opcionales", "Orden de compra, guía, documento relacionado, vencimiento, código SUNAT, GTIN y redondeo",
                "{" + cab + ",\"fecha_vencimiento\":\"" + fecha.plusDays(30) + "\",\"moneda\":\"PEN\"," + CLIENTE + "," +
                "\"orden_compra\":\"OC-2026-0457\",\"guias\":[{\"tipo\":\"09\",\"numero\":\"T001-123\"}],\"documentos_relacionados\":[{\"tipo\":\"05\",\"numero\":\"SCOP-8841203\"}]," +
                "\"items\":[{\"descripcion\":\"Combustible diésel B5\",\"unidad\":\"GLL\",\"cantidad\":7,\"precio_unitario\":16.91,\"tipo_afectacion_igv\":\"10\"," +
                "\"codigo_sunat\":\"15101505\",\"gtin\":{\"tipo\":\"GTIN-13\",\"codigo\":\"7750182000123\"}}],\"redondeo\":-0.37}"),
            new Escenario("15-muchos-items", "Cincuenta ítems",
                "{" + cab + ",\"moneda\":\"PEN\"," + CLIENTE + ",\"items\":[" + muchosItems(50) + "]}"),
            new Escenario("16-anticipo", "Factura de anticipo (paso 1 de 2)",
                "{" + cab + ",\"moneda\":\"PEN\"," + CLIENTE + ",\"items\":[" +
                "{\"descripcion\":\"Anticipo por obra\",\"unidad\":\"ZZ\",\"cantidad\":1,\"precio_unitario\":1180.00,\"tipo_afectacion_igv\":\"10\"}]}"),
            new Escenario("17-anticipo-final", "Factura final que regulariza el anticipo (paso 2 de 2)",
                "{" + cab + ",\"moneda\":\"PEN\"," + CLIENTE + ",\"items\":[" +
                "{\"descripcion\":\"Obra completa\",\"unidad\":\"ZZ\",\"cantidad\":1,\"precio_unitario\":3540.00,\"tipo_afectacion_igv\":\"10\"}]," +
                "\"anticipos\":[{\"serie\":\"" + serie + "\",\"numero\":${ANTICIPO},\"monto\":1000.00}]}"),
            new Escenario("18-nc-total", "Nota de crédito total (01) sobre la factura gravada", NOTAS,
                "{\"tipo\":\"07\"," + cab + ",\"documento_afectado\":{\"serie\":\"" + serie + "\",\"numero\":${GRAVADA}}," +
                "\"motivo\":\"01\",\"descripcion\":\"Anulación de la operación por error en el pedido\"}"),
            new Escenario("19-nc-parcial", "Nota de crédito parcial (07, devolución por ítem) sobre la factura mixta", NOTAS,
                "{\"tipo\":\"07\"," + cab + ",\"documento_afectado\":{\"serie\":\"" + serie + "\",\"numero\":${MIXTA}}," +
                "\"motivo\":\"07\",\"descripcion\":\"Devolución de una laptop\",\"items\":[" +
                "{\"descripcion\":\"Laptop\",\"unidad\":\"NIU\",\"cantidad\":1,\"precio_unitario\":2360.00,\"tipo_afectacion_igv\":\"10\"}]}"),
            new Escenario("20-nc-cuotas", "Nota de crédito 13 que reprograma las cuotas de la factura al crédito", NOTAS,
                "{\"tipo\":\"07\"," + cab + ",\"documento_afectado\":{\"serie\":\"" + serie + "\",\"numero\":${CREDITO}}," +
                "\"motivo\":\"13\",\"descripcion\":\"Reprogramación de cuotas\"," +
                "\"forma_pago\":{\"tipo\":\"credito\",\"monto_pendiente\":1180.00,\"cuotas\":[{\"monto\":1180.00,\"vencimiento\":\"" + fecha.plusDays(90) + "\"}]}}"),
            new Escenario("21-nd-interes", "Nota de débito por intereses de mora (01) sobre la factura gravada", NOTAS,
                "{\"tipo\":\"08\"," + cab + ",\"documento_afectado\":{\"serie\":\"" + serie + "\",\"numero\":${GRAVADA}}," +
                "\"motivo\":\"01\",\"descripcion\":\"Intereses por mora de 30 días\",\"items\":[" +
                "{\"descripcion\":\"Intereses por mora\",\"unidad\":\"ZZ\",\"cantidad\":1,\"precio_unitario\":59.00,\"tipo_afectacion_igv\":\"10\"}]}"),
            // Tasa reducida del padrón de restaurantes y hoteles (#84): el XML debe pasar 3279/3291/3462 con cbc:Percent 10.5.
            // El RUC de prueba no está en el padrón: producción observaría 4439; e-beta no cruza el padrón y acepta limpio (2026-09-19).
            new Escenario("23-tasa-reducida", "Venta gravada al 10.5 % (padrón de tasa especial del IGV, Ley 31556)", "/v1/facturas",
                "{" + cab + ",\"moneda\":\"PEN\"," + CLIENTE + ",\"items\":[" +
                "{\"codigo\":\"MENU-01\",\"descripcion\":\"Menú ejecutivo\",\"unidad\":\"NIU\",\"cantidad\":2,\"precio_unitario\":33.15,\"tipo_afectacion_igv\":\"10\"}]}",
                true, "4439"),
            // Serie asignada a un establecimiento anexo (#80): RegistrationAddress con AddressTypeCode 0002 y la dirección del anexo.
            new Escenario("24-anexo", "Factura desde una serie asignada al establecimiento anexo 0002",
                "{" + cabAnexo + ",\"moneda\":\"PEN\"," + CLIENTE + ",\"items\":[" +
                "{\"descripcion\":\"Venta en tienda Miraflores\",\"unidad\":\"NIU\",\"cantidad\":1,\"precio_unitario\":236.00,\"tipo_afectacion_igv\":\"10\"}]}"),
            // IVAP (#67): afectación 17, tributo 1016 al 4 %, leyenda 2007, TaxInclusiveAmount sin IGV (campo 55).
            new Escenario("25-ivap", "Venta de arroz pilado sujeta al IVAP (afectación 17, 4 %)",
                "{" + cab + ",\"moneda\":\"PEN\"," + CLIENTE + ",\"items\":[" +
                "{\"codigo\":\"ARZ-01\",\"descripcion\":\"Arroz pilado superior\",\"unidad\":\"KGM\",\"cantidad\":500,\"precio_unitario\":3.12,\"tipo_afectacion_igv\":\"17\"}]}"),
            // Exportación de bienes (#65): 0200 en USD a un cliente del exterior (tipo 0, país US), afectación 40 / tributo 9995 sin IGV, Incoterm FOB.
            new Escenario("26-exportacion", "Exportación de bienes (0200) a un cliente del exterior, afectación 40 e Incoterm FOB",
                "{" + cab + ",\"tipo_operacion\":\"0200\",\"moneda\":\"USD\"," +
                "\"cliente\":{\"tipo_doc\":\"0\",\"num_doc\":\"US123456789\",\"razon_social\":\"ACME IMPORTS LLC\",\"direccion\":\"1200 Main St, Miami FL\",\"pais\":\"US\"}," +
                "\"items\":[{\"codigo\":\"CAF-01\",\"descripcion\":\"Café verde en grano\",\"unidad\":\"KGM\",\"cantidad\":1000,\"precio_unitario\":4.50,\"tipo_afectacion_igv\":\"40\"}]," +
                "\"exportacion\":{\"incoterm\":\"FOB\"}}"),
            // Detracción 1002 (#69): recursos hidrobiológicos con los conceptos 3001–3006 del catálogo 55 por ítem (3063, 3130–3135) y código 004 (3129).
            new Escenario("27-hidrobiologicos", "Venta de recursos hidrobiológicos con detracción (1002) y datos de la embarcación por ítem",
                "{" + cab + ",\"moneda\":\"PEN\",\"tipo_operacion\":\"1002\"," + CLIENTE + ",\"items\":[" +
                "{\"codigo\":\"ANCH\",\"descripcion\":\"Anchoveta fresca\",\"unidad\":\"TNE\",\"cantidad\":12.5,\"precio_unitario\":1180.00,\"tipo_afectacion_igv\":\"10\"," +
                "\"hidrobiologico\":{\"matricula\":\"CO-12345-PM\",\"nombre_embarcacion\":\"DON JOSE II\",\"especie\":\"Anchoveta (Engraulis ringens)\",\"lugar_descarga\":\"Muelle de Chimbote\",\"fecha_descarga\":\"" + fecha + "\",\"cantidad\":12.5}}]," +
                "\"detraccion\":{\"codigo_bien_servicio\":\"004\",\"porcentaje\":4,\"cuenta_banco_nacion\":\"00-000-123456\"}}"),
            // Detracción 1004 (#69): transporte de carga con origen/destino, detalle del viaje, los tres valores referenciales (3116–3126) y un tramo con vehículo.
            new Escenario("28-transporte-carga", "Servicio de transporte de carga con detracción (1004): origen, destino, valores referenciales y tramo",
                "{" + cab + ",\"moneda\":\"PEN\",\"tipo_operacion\":\"1004\"," + CLIENTE + ",\"items\":[" +
                "{\"codigo\":\"FLT\",\"descripcion\":\"Flete Chimbote - Lima\",\"unidad\":\"ZZ\",\"cantidad\":1,\"precio_unitario\":2950.00,\"tipo_afectacion_igv\":\"10\"," +
                "\"transporte\":{\"origen\":{\"ubigeo\":\"021801\",\"direccion\":\"Av. Los Pescadores 450, Chimbote\"},\"destino\":{\"ubigeo\":\"150101\",\"direccion\":\"Jr. de la Union 100, Lima\"}," +
                "\"detalle_viaje\":\"Traslado de 20 t de harina de pescado en camion furgon\",\"valor_referencial\":{\"servicio\":2500,\"carga_efectiva\":2400,\"carga_util_nominal\":2600}," +
                "\"tramos\":[{\"origen_ubigeo\":\"021801\",\"destino_ubigeo\":\"150101\",\"descripcion\":\"Chimbote - Lima por Panamericana Norte\",\"valor_carga_efectiva\":2400,\"vehiculos\":[{\"configuracion\":\"T3S3\",\"carga_util_tm\":30,\"carga_efectiva_tm\":20}]}]}}]," +
                "\"detraccion\":{\"codigo_bien_servicio\":\"027\",\"porcentaje\":4,\"cuenta_banco_nacion\":\"00-000-123456\"}}")
        );
    }

    private static String muchosItems(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= n; i++) {
            if (i > 1) sb.append(',');
            sb.append("{\"codigo\":\"P").append(i).append("\",\"descripcion\":\"Producto ").append(i)
              .append("\",\"unidad\":\"NIU\",\"cantidad\":").append(i).append(",\"precio_unitario\":11.80,\"tipo_afectacion_igv\":\"10\"}");
        }
        return sb.toString();
    }
}
