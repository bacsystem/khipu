package pe.factura.adapters.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import org.xhtmlrenderer.pdf.ITextRenderer;
import pe.factura.application.port.out.PdfGenerator;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.MontoEnLetras;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.LogoPdf;
import pe.factura.domain.tenant.Tenant;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Representación impresa: una plantilla XHTML por tipo de comprobante (Freemarker, {@code pdf/*.ftl}) renderizada a PDF con
 * Flying Saucer. El QR se genera con ZXing y se incrusta como imagen base64; el hash impreso es el {@code DigestValue} de la firma.
 */
public class FlyingSaucerPdfGenerator implements PdfGenerator {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final int QR_PX = 220;
    private final Configuration cfg;

    public FlyingSaucerPdfGenerator() {
        cfg = new Configuration(Configuration.VERSION_2_3_33);
        cfg.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "pdf");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setLocale(Locale.ROOT);
        cfg.setNumberFormat("computer");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
    }

    @Override public byte[] generar(Comprobante c, Tenant t, String contenidoQr, byte[] logo) {
        String xhtml = xhtml(c, t, contenidoQr, logo);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(xhtml);
            renderer.layout();
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("Error generando el PDF de " + c.nombreArchivo(), e); }
    }

    /** XHTML intermedio (lo que ve la plantilla); expuesto para que los tests comprueben el contenido sin parsear el PDF. */
    String xhtml(Comprobante c, Tenant t, String contenidoQr, byte[] logo) {
        try {
            Map<String, Object> modelo = new HashMap<>();
            modelo.put("c", c);
            modelo.put("t", t);
            // Diseño de la empresa: la plantilla es una clase CSS de comun.ftl, el color una variable y el logo una imagen incrustada.
            modelo.put("p", t.personalizacionPdf());
            modelo.put("plantilla", t.personalizacionPdf().plantilla().nombre());
            if (logo != null) {
                modelo.put("logo", "data:" + LogoPdf.tipoContenido(LogoPdf.extension(logo)) + ";base64," + Base64.getEncoder().encodeToString(logo));
                modelo.put("logoEstilo", tamañoLogo(logo));
            }
            modelo.put("tot", c.totales());
            modelo.put("fechaEmision", c.fechaEmision().format(FECHA));
            modelo.put("fechaVencimiento", c.fechaVencimiento() == null ? null : c.fechaVencimiento().format(FECHA));
            modelo.put("montoEnLetras", MontoEnLetras.de(c.totales().total(), c.moneda()));
            modelo.put("qr", "data:image/png;base64," + Base64.getEncoder().encodeToString(qrPng(contenidoQr)));
            modelo.put("statics", ((freemarker.ext.beans.BeansWrapper) cfg.getObjectWrapper()).getStaticModels());
            // Las dos notas comparten plantilla; solo cambian el título del recuadro y el nombre en la leyenda del pie.
            if (c.tipo() == TipoDocumento.NOTA_CREDITO) modelo.put("n", Map.of("titulo", "NOTA DE CRÉDITO ELECTRÓNICA", "nombre", "nota de crédito"));
            if (c.tipo() == TipoDocumento.NOTA_DEBITO) modelo.put("n", Map.of("titulo", "NOTA DE DÉBITO ELECTRÓNICA", "nombre", "nota de débito"));
            StringWriter out = new StringWriter();
            cfg.getTemplate(switch (c.tipo()) {
                case FACTURA -> "factura.ftl";
                case BOLETA -> "boleta.ftl";
                case NOTA_CREDITO, NOTA_DEBITO -> "nota.ftl";
            }).process(modelo, out);
            // El parser XML de Flying Saucer exige la declaración <?xml?> en el primer byte.
            return out.toString().strip();
        } catch (Exception e) { throw new IllegalStateException("Error generando la plantilla del PDF de " + c.nombreArchivo(), e); }
    }

    static final double LOGO_ANCHO_MAX_MM = 55, LOGO_ALTO_MAX_MM = 18;

    /**
     * Flying Saucer omite una imagen incrustada (data:) si la hoja de estilos no le da un tamaño explícito, y no respeta max-width/max-height:
     * el tamaño se calcula aquí para que el logo quepa en 55×18 mm conservando su proporción.
     */
    static String tamañoLogo(byte[] logo) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(logo));
            if (img == null) throw new IllegalStateException("No se pudo decodificar el logo");
            double escala = Math.min(LOGO_ANCHO_MAX_MM / img.getWidth(), LOGO_ALTO_MAX_MM / img.getHeight());
            return String.format(Locale.ROOT, "width: %.1fmm; height: %.1fmm;", img.getWidth() * escala, img.getHeight() * escala);
        } catch (IOException e) { throw new IllegalStateException("No se pudo leer el logo", e); }
    }

    static byte[] qrPng(String contenido) {
        try {
            BitMatrix m = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, QR_PX, QR_PX,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1, EncodeHintType.CHARACTER_SET, "UTF-8"));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(m, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("No se pudo generar el QR", e); }
    }
}
