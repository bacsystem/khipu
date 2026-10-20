package pe.factura.adapters.ubl;

import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import pe.factura.application.port.out.GuiaUblGenerator;
import pe.factura.domain.documento.GuiaRemision;
import pe.factura.domain.documento.IndicadorDeTraslado;
import pe.factura.domain.tenant.Tenant;

import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** La guía de remisión (#39) como UBL 2.1 {@code DespatchAdvice} ({@code templates/despatch-advice.ftl}), con la firma y las partes de {@code comun.ftl}. */
public class FreemarkerGuiaUblGenerator implements GuiaUblGenerator {
    private final Configuration cfg;

    public FreemarkerGuiaUblGenerator() {
        cfg = new Configuration(Configuration.VERSION_2_3_33);
        cfg.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
    }

    @Override public String generar(GuiaRemision g, Tenant t) {
        if (g.numero() == null) throw new IllegalStateException("La guía se numera antes de generar su XML");
        try {
            Map<String, Object> modelo = new HashMap<>();
            modelo.put("g", g);
            modelo.put("t", t);
            modelo.put("tr", g.traslado());
            g.remitente().ifPresent(r -> modelo.put("remitente", r));
            g.transportista().ifPresent(r -> modelo.put("transportista", r));
            // En el orden del enum: el XML de la misma guía sale siempre igual.
            modelo.put("indicadores", g.traslado().indicadores().stream().sorted(Comparator.comparing(IndicadorDeTraslado::ordinal)).toList());
            modelo.put("fechaEmision", g.fechaEmision().toString());
            modelo.put("horaEmision", g.horaEmision().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            modelo.put("statics", ((freemarker.ext.beans.BeansWrapper) cfg.getObjectWrapper()).getStaticModels());
            StringWriter out = new StringWriter();
            cfg.getTemplate("despatch-advice.ftl").process(modelo, out);
            return out.toString();
        } catch (Exception e) { throw new IllegalStateException("Error generando el DespatchAdvice de " + g.nombreArchivo(), e); }
    }
}
