package pe.factura.adapters.pdf;

import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import org.xhtmlrenderer.pdf.ITextRenderer;
import pe.factura.application.port.out.GuiaPdfGenerator;
import pe.factura.domain.documento.GuiaRemision;
import pe.factura.domain.documento.IndicadorDeTraslado;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.LogoPdf;
import pe.factura.domain.tenant.Tenant;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Representación impresa de la guía de remisión (#39): {@code pdf/guia.ftl} con la página y el diseño de la empresa de {@code pdf/comun.ftl}. El QR codifica la
 * URL de consulta que SUNAT pone en el CDR (la de la GRE: no es el QR de RUC|tipo|serie… de los comprobantes de pago); sin CDR, el PDF sale sin QR.
 */
public class FlyingSaucerGuiaPdfGenerator implements GuiaPdfGenerator {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final Configuration cfg;

    public FlyingSaucerGuiaPdfGenerator() {
        cfg = new Configuration(Configuration.VERSION_2_3_33);
        cfg.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "pdf");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setLocale(Locale.ROOT);
        cfg.setNumberFormat("computer");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
    }

    @Override public byte[] generar(GuiaRemision g, Tenant t, String enlaceQr, byte[] logo) {
        String xhtml = xhtml(g, t, enlaceQr, logo);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(xhtml);
            renderer.layout();
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("Error generando el PDF de " + g.nombreArchivo(), e); }
    }

    /** XHTML intermedio, para que las pruebas lean el contenido sin parsear el PDF. */
    String xhtml(GuiaRemision g, Tenant t, String enlaceQr, byte[] logo) {
        try {
            Map<String, Object> modelo = new HashMap<>();
            modelo.put("c", g);
            modelo.put("t", t);
            modelo.put("p", t.personalizacionPdf());
            modelo.put("plantilla", t.personalizacionPdf().plantilla().nombre());
            if (logo != null) {
                modelo.put("logo", "data:" + LogoPdf.tipoContenido(LogoPdf.extension(logo)) + ";base64," + Base64.getEncoder().encodeToString(logo));
                modelo.put("logoEstilo", FlyingSaucerPdfGenerator.tamañoLogo(logo));
            }
            modelo.put("tr", g.traslado());
            g.remitente().ifPresent(r -> modelo.put("remitente", r));
            g.transportista().ifPresent(r -> modelo.put("transportista", r));
            modelo.put("indicadores", g.traslado().indicadores().stream().sorted(Comparator.comparing(IndicadorDeTraslado::ordinal)).map(IndicadorDeTraslado::codigo).toList());
            modelo.put("titulo", g.tipo() == TipoDocumento.GUIA_REMITENTE ? "GUÍA DE REMISIÓN ELECTRÓNICA REMITENTE" : "GUÍA DE REMISIÓN ELECTRÓNICA TRANSPORTISTA");
            modelo.put("fechaEmision", g.fechaEmision().format(FECHA));
            modelo.put("fechaInicio", g.traslado().fechaInicio().format(FECHA));
            if (enlaceQr != null && !enlaceQr.isBlank()) modelo.put("qr", "data:image/png;base64," + Base64.getEncoder().encodeToString(FlyingSaucerPdfGenerator.qrPng(enlaceQr)));
            modelo.put("statics", ((freemarker.ext.beans.BeansWrapper) cfg.getObjectWrapper()).getStaticModels());
            StringWriter out = new StringWriter();
            cfg.getTemplate("guia.ftl").process(modelo, out);
            return out.toString().strip();
        } catch (Exception e) { throw new IllegalStateException("Error generando la plantilla del PDF de " + g.nombreArchivo(), e); }
    }
}
