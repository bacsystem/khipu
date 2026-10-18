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
import java.util.HashMap;
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
        String plantilla = b.resumenDiario() ? "summary-documents.ftl" : "voided-documents.ftl";
        try {
            Map<String, Object> modelo = new HashMap<>();
            modelo.put("b", b);
            modelo.put("c", c);
            modelo.put("tot", c.totales());
            modelo.put("t", t);
            if (b.resumenDiario()) modelo.put("nombreComprador", nombreDelCompradorEnElResumen(c.receptor().razonSocial()));
            StringWriter out = new StringWriter();
            cfg.getTemplate(plantilla).process(modelo, out);
            return out.toString();
        } catch (Exception e) { throw new IllegalStateException("Error generando " + (b.resumenDiario() ? "SummaryDocuments" : "VoidedDocuments"), e); }
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
            Template tpl = cfg.getTemplate(c.esNota() ? "nota.ftl" : "invoice.ftl");
            Map<String, Object> modelo = new HashMap<>();
            // Lo único que distingue el CreditNote del DebitNote: raíz, catálogo del motivo y los nombres de total, línea y cantidad.
            if (c.tipo() == TipoDocumento.NOTA_CREDITO)
                modelo.put("n", Map.of("raiz", "CreditNote", "catalogo", Nota.catalogoMotivo(c.tipo()), "listName", "Tipo de nota de credito", "total", "LegalMonetaryTotal", "linea", "CreditNoteLine", "cantidad", "CreditedQuantity"));
            else if (c.tipo() == TipoDocumento.NOTA_DEBITO)
                modelo.put("n", Map.of("raiz", "DebitNote", "catalogo", Nota.catalogoMotivo(c.tipo()), "listName", "Tipo de nota de debito", "total", "RequestedMonetaryTotal", "linea", "DebitNoteLine", "cantidad", "DebitedQuantity"));
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
