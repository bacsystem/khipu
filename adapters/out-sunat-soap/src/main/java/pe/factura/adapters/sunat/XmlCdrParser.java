package pe.factura.adapters.sunat;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import pe.factura.application.port.out.CdrParser;
import pe.factura.domain.documento.Cdr;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

public class XmlCdrParser implements CdrParser {
    private static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";

    @Override public Cdr parsear(byte[] cdrZip) {
        byte[] xml = ZipUtil.extraerPrimero(cdrZip, ".xml");
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml));

            NodeList responses = doc.getElementsByTagNameNS(CAC, "Response");
            if (responses.getLength() == 0) throw new IllegalStateException("CDR sin cac:Response");
            org.w3c.dom.Element resp = (org.w3c.dom.Element) responses.item(0);
            String codigo = texto(resp, "ResponseCode");
            String descripcion = texto(resp, "Description");

            List<String> notas = new ArrayList<>();
            NodeList notes = doc.getDocumentElement().getElementsByTagNameNS(CBC, "Note");
            for (int i = 0; i < notes.getLength(); i++)
                if (notes.item(i).getParentNode() == doc.getDocumentElement()) notas.add(notes.item(i).getTextContent().trim());

            return new Cdr(codigo, descripcion, List.copyOf(notas));
        } catch (IllegalStateException e) { throw e;
        } catch (Exception e) { throw new IllegalStateException("CDR ilegible: " + e.getMessage(), e); }
    }

    private static String texto(org.w3c.dom.Element padre, String local) {
        NodeList nl = padre.getElementsByTagNameNS(CBC, local);
        return nl.getLength() == 0 ? "" : nl.item(0).getTextContent().trim();
    }
}
