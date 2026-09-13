package pe.factura.adapters.ubl;

import org.xml.sax.SAXParseException;
import pe.factura.application.port.out.XsdValidator;
import pe.factura.domain.DomainException;
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

public class JaxpXsdValidator implements XsdValidator {
    private final Map<TipoDocumento, Schema> esquemas = new EnumMap<>(TipoDocumento.class);

    public JaxpXsdValidator() {
        esquemas.put(TipoDocumento.FACTURA, cargar("xsd/2.1/maindoc/UBL-Invoice-2.1.xsd"));
        esquemas.put(TipoDocumento.BOLETA, esquemas.get(TipoDocumento.FACTURA));
    }

    private Schema cargar(String recurso) {
        try {
            URL url = getClass().getClassLoader().getResource(recurso);
            if (url == null) throw new IllegalStateException("No se encontró " + recurso);
            SchemaFactory f = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            f.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            return f.newSchema(url);   // los imports relativos (../common/*.xsd) se resuelven contra la URL
        } catch (Exception e) { throw new IllegalStateException("No se pudo cargar el XSD " + recurso, e); }
    }

    @Override public void validar(String xml, TipoDocumento tipo) {
        Schema s = esquemas.get(tipo);
        if (s == null) throw new IllegalArgumentException("Sin XSD para " + tipo);
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
