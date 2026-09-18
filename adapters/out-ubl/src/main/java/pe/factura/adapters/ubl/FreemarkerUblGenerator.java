package pe.factura.adapters.ubl;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import pe.factura.application.port.out.UblGenerator;
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
