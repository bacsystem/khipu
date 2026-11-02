package pe.factura.adapters.ubl;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import pe.factura.application.port.out.UblGenerator;
import pe.factura.domain.documento.ComunicacionBaja;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.MontoEnLetras;
import pe.factura.domain.documento.Nota;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.Tenant;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FreemarkerUblGenerator implements UblGenerator {
    private final Configuration cfg;

    public FreemarkerUblGenerator() {
        cfg = new Configuration(Configuration.VERSION_2_3_33);
        cfg.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
    }

    @Override public String generarBaja(ComunicacionBaja b, Comprobante c, Tenant t) {
        if (b.resumenDiario()) return generarResumen(List.of(b), List.of(c), t);
        try {
            Map<String, Object> modelo = new HashMap<>();
            modelo.put("b", b);
            modelo.put("c", c);
            modelo.put("tot", c.totales());
            modelo.put("t", t);
            StringWriter out = new StringWriter();
            cfg.getTemplate("voided-documents.ftl").process(modelo, out);
            return out.toString();
        } catch (Exception e) { throw new IllegalStateException("Error generando VoidedDocuments", e); }
    }

    @Override public String generarResumen(List<ComunicacionBaja> filas, List<Comprobante> comprobantes, Tenant t) {
        if (filas.isEmpty() || filas.size() != comprobantes.size()) throw new IllegalArgumentException("Un resumen diario lleva una fila por comprobante y al menos una");
        try {
            List<Map<String, Object>> lineas = new ArrayList<>(filas.size());
            for (int i = 0; i < filas.size(); i++) {
                Comprobante c = comprobantes.get(i);
                if (filas.get(i).linea() != i + 1) throw new IllegalArgumentException("La fila " + i + " del resumen es la línea " + filas.get(i).linea() + ": el LineID sale de la fila y deben ir en orden");
                lineas.add(Map.of("b", filas.get(i), "c", c, "nombreComprador", nombreDelCompradorEnElResumen(c.receptor().razonSocial())));
            }
            Map<String, Object> modelo = new HashMap<>();
            modelo.put("b", filas.get(0));
            modelo.put("c", comprobantes.get(0));
            modelo.put("tot", comprobantes.get(0).totales());
            modelo.put("t", t);
            modelo.put("lineas", lineas);
            StringWriter out = new StringWriter();
            cfg.getTemplate("summary-documents.ftl").process(modelo, out);
            return out.toString();
        } catch (Exception e) { throw new IllegalStateException("Error generando SummaryDocuments", e); }
    }

    /** Tope del campo 10.3 del resumen (regla 2022): la boleta admite 1500, el resumen 250. */
    static final int MAX_NOMBRE_COMPRADOR_EN_RESUMEN = 250;

    /**
     * Nombre del comprador para el resumen diario: sin espacios en los extremos y hasta 250 caracteres (2022). El corte no parte un par de sustitutos (un emoji en el límite):
     * un sustituto suelto deja el XML inválido y el resumen no se podría generar.
     */
    static String nombreDelCompradorEnElResumen(String razonSocial) {
        String nombre = razonSocial == null ? "" : razonSocial.strip();
        if (nombre.length() <= MAX_NOMBRE_COMPRADOR_EN_RESUMEN) return nombre;
        int fin = MAX_NOMBRE_COMPRADOR_EN_RESUMEN;
        if (Character.isHighSurrogate(nombre.charAt(fin - 1))) fin--;
        return nombre.substring(0, fin);
    }

    @Override public String generar(Comprobante c, Tenant t) {
        try {
            // #327: la liquidación de compra es un SelfBilledInvoice, con las partes invertidas; la factura y la boleta, un Invoice.
            // #40: la retención y la percepción son UBL 2.0 (Retention / Perception), con la misma forma y otros nombres.
            Template tpl = cfg.getTemplate(c.esNota() ? "nota.ftl" : c.esLiquidacionCompra() ? "liquidacion.ftl" : c.esDeAgente() ? "agente.ftl" : "invoice.ftl");
            Map<String, Object> modelo = new HashMap<>();
            // Lo único que distingue el CreditNote del DebitNote: raíz, catálogo del motivo y los nombres de total, línea y cantidad.
            if (c.tipo() == TipoDocumento.NOTA_CREDITO)
                modelo.put("n", Map.of("raiz", "CreditNote", "catalogo", Nota.catalogoMotivo(c.tipo()), "listName", "Tipo de nota de credito", "total", "LegalMonetaryTotal", "linea", "CreditNoteLine", "cantidad", "CreditedQuantity"));
            else if (c.tipo() == TipoDocumento.NOTA_DEBITO)
                modelo.put("n", Map.of("raiz", "DebitNote", "catalogo", Nota.catalogoMotivo(c.tipo()), "listName", "Tipo de nota de debito", "total", "RequestedMonetaryTotal", "linea", "DebitNoteLine", "cantidad", "DebitedQuantity"));
            if (c.tipo() == TipoDocumento.RETENCION) modelo.put("n", Map.of("raiz", "Retention", "sistema", "Retention", "total", "Paid", "neto", "Paid"));
            else if (c.tipo() == TipoDocumento.PERCEPCION) modelo.put("n", Map.of("raiz", "Perception", "sistema", "Perception", "total", "Cashed", "neto", "Cashed"));
            modelo.put("c", c);
            modelo.put("t", t);
            modelo.put("tot", c.totales());
            modelo.put("fechaEmision", c.fechaEmision().toString());
            modelo.put("horaEmision", c.horaEmision() == null ? null : c.horaEmision().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
            modelo.put("montoEnLetras", MontoEnLetras.de(c.totales().total(), c.moneda()));
            // Enums del dominio (Tributo.ISC/ICBPER) accesibles desde la plantilla para los subtotales de línea.
            modelo.put("statics", ((freemarker.ext.beans.BeansWrapper) cfg.getObjectWrapper()).getStaticModels());
            StringWriter out = new StringWriter();
            tpl.process(modelo, out);
            return out.toString();
        } catch (Exception e) { throw new IllegalStateException("Error generando UBL", e); }
    }
}
