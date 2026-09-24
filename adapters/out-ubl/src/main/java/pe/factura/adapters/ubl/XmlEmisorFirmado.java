package pe.factura.adapters.ubl;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import pe.factura.application.port.out.EmisorFirmado;
import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.EmisorImpreso;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.Optional;

/**
 * Lee el emisor de {@code cac:AccountingSupplierParty} en el XML ya firmado.
 *
 * <p>Es la fuente correcta por dos razones. Es el documento que SUNAT recibió, así que la representación impresa dice
 * exactamente lo que se declaró; y existe para <em>todos</em> los comprobantes, también los emitidos antes de que esto
 * se arreglara, que una copia guardada de ahora en adelante habría dejado sin corregir.
 */
public class XmlEmisorFirmado implements EmisorFirmado {

    private static final String CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";

    @Override
    public Optional<EmisorImpreso> leer(byte[] xmlFirmado) {
        if (xmlFirmado == null || xmlFirmado.length == 0) return Optional.empty();
        try {
            Element emisor = primero(parsear(xmlFirmado).getDocumentElement(), CAC, "AccountingSupplierParty");
            if (emisor == null) return Optional.empty();
            Element parte = primero(emisor, CAC, "Party");
            if (parte == null) return Optional.empty();

            Element legal = primero(parte, CAC, "PartyLegalEntity");
            String razonSocial = texto(legal, CBC, "RegistrationName");
            String ruc = texto(primero(parte, CAC, "PartyIdentification"), CBC, "ID");
            if (razonSocial == null || ruc == null) return Optional.empty();

            return Optional.of(new EmisorImpreso(ruc, razonSocial, texto(primero(parte, CAC, "PartyName"), CBC, "Name"),
                    domicilio(primero(legal, CAC, "RegistrationAddress"))));
        } catch (Exception e) {
            // Un XML que no se puede leer no debe impedir imprimir: quien llama cae al emisor actual.
            return Optional.empty();
        }
    }

    /**
     * El domicilio solo se reconstruye si el XML trae el ubigeo: sin él {@link Domicilio} no se puede construir, y es
     * el caso de los comprobantes emitidos sin domicilio fiscal configurado, que llevan solo el AddressTypeCode.
     */
    private static Domicilio domicilio(Element dir) {
        if (dir == null) return null;
        String ubigeo = texto(dir, CBC, "ID");
        Element linea = primero(dir, CAC, "AddressLine");
        String direccion = texto(linea, CBC, "Line");
        if (ubigeo == null || direccion == null) return null;
        return new Domicilio(ubigeo, direccion, texto(dir, CBC, "CitySubdivisionName"), texto(dir, CBC, "District"),
                texto(dir, CBC, "CityName"), texto(dir, CBC, "CountrySubentity"), texto(dir, CBC, "AddressTypeCode"));
    }

    /** Sin DTD ni entidades externas: el XML viene de storage, pero un parser permisivo es un vector igual. */
    private static Document parsear(byte[] xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    /**
     * El primer hijo con ese nombre, buscando en profundidad pero **sin cruzar** a otra parte: el receptor tiene la
     * misma estructura, así que un {@code getElementsByTagNameNS} sobre el documento entero traería sus datos.
     */
    private static Element primero(Node desde, String ns, String nombre) {
        if (desde == null) return null;
        NodeList hijos = desde.getChildNodes();
        for (int i = 0; i < hijos.getLength(); i++) {
            Node h = hijos.item(i);
            if (h.getNodeType() != Node.ELEMENT_NODE) continue;
            if (ns.equals(h.getNamespaceURI()) && nombre.equals(h.getLocalName())) return (Element) h;
            Element anidado = primero(h, ns, nombre);
            if (anidado != null) return anidado;
        }
        return null;
    }

    private static String texto(Node desde, String ns, String nombre) {
        Element e = primero(desde, ns, nombre);
        if (e == null) return null;
        String v = e.getTextContent();
        return v == null || v.isBlank() ? null : v.strip();
    }
}
