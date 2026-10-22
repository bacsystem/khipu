package pe.factura.adapters.ubl;

import org.xml.sax.SAXParseException;
import pe.factura.application.port.out.XsdValidator;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.ComunicacionBaja;
import pe.factura.domain.documento.TipoDocumento;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.StringReader;
import java.net.URL;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class JaxpXsdValidator implements XsdValidator {
    private final Map<TipoDocumento, Schema> esquemas = new EnumMap<>(TipoDocumento.class);
    private final Schema baja;
    private final Schema resumenDiario;
    /**
     * Los esquemas compilados, por JVM y no por validador: un {@code Schema} es inmutable y seguro entre hilos, y compilar los UBL cuesta segundos y
     * mucha memoria (los tipos, elementos y anotaciones de Xerces de cada esquema). Con uno por contexto de Spring, las pruebas E2E —que dejan sus contextos en caché toda la
     * corrida— agotaban el heap de 1 GB aunque ningún test fallara.
     */
    private static final Map<String, Schema> COMPILADOS = new ConcurrentHashMap<>();
    private static final AtomicInteger CARGAS = new AtomicInteger();

    /** Cuántos XSD se han compilado en esta JVM (para las pruebas). */
    static int cargas() { return CARGAS.get(); }

    public JaxpXsdValidator() {
        baja = cargar("xsd/2.0/maindoc/UBLPE-VoidedDocuments-1.0.xsd");
        resumenDiario = cargar("xsd/2.0/maindoc/UBLPE-SummaryDocuments-1.0.xsd");
        esquemas.put(TipoDocumento.FACTURA, cargar("xsd/2.1/maindoc/UBL-Invoice-2.1.xsd"));
        esquemas.put(TipoDocumento.BOLETA, esquemas.get(TipoDocumento.FACTURA));
        esquemas.put(TipoDocumento.LIQUIDACION_COMPRA, cargar("xsd/2.1/maindoc/UBL-SelfBilledInvoice-2.1.xsd"));
        esquemas.put(TipoDocumento.NOTA_CREDITO, cargar("xsd/2.1/maindoc/UBL-CreditNote-2.1.xsd"));
        esquemas.put(TipoDocumento.NOTA_DEBITO, cargar("xsd/2.1/maindoc/UBL-DebitNote-2.1.xsd"));
        // #40: UBL 2.0 con los componentes sac: del paquete de SUNAT, los mismos de la baja y el resumen diario.
        esquemas.put(TipoDocumento.RETENCION, cargar("xsd/2.0/maindoc/UBLPE-Retention-1.0.xsd"));
        esquemas.put(TipoDocumento.PERCEPCION, cargar("xsd/2.0/maindoc/UBLPE-Perception-1.0.xsd"));
    }

    private Schema cargar(String recurso) { return COMPILADOS.computeIfAbsent(recurso, this::compilar); }

    private Schema compilar(String recurso) {
        try {
            URL url = getClass().getClassLoader().getResource(recurso);
            if (url == null) throw new IllegalStateException("No se encontró " + recurso);
            SchemaFactory f = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            f.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            CARGAS.incrementAndGet();
            return f.newSchema(url);   // los imports relativos (../common/*.xsd) se resuelven contra la URL
        } catch (Exception e) { throw new IllegalStateException("No se pudo cargar el XSD " + recurso, e); }
    }

    @Override public void validar(String xml, TipoDocumento tipo) {
        Schema s = esquemas.get(tipo);
        if (s == null) throw new IllegalArgumentException("Sin XSD para " + tipo);
        validar(xml, s);
    }

    // La baja decide si va en un resumen diario (275-H4): la regla «boleta, resumen» vive en un solo lugar.
    @Override public void validarBaja(String xml, ComunicacionBaja b) { validar(xml, b.resumenDiario() ? resumenDiario : baja); }

    private static void validar(String xml, Schema s) {
        try {
            Validator v = s.newValidator();
            v.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            v.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            v.validate(new StreamSource(new StringReader(xml)));
        } catch (SAXParseException e) {
            throw new DomainException("XSD_INVALIDO", "XML inválido (línea " + e.getLineNumber() + "): " + e.getMessage(), e);
        } catch (Exception e) {
            throw new DomainException("XSD_INVALIDO", "XML inválido: " + e.getMessage(), e);
        }
    }
}
