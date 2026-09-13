package pe.factura.adapters.signing;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import pe.factura.application.port.out.FirmaResultado;
import pe.factura.application.port.out.XmlSigner;
import pe.factura.domain.DomainException;
import pe.factura.domain.tenant.CertificadoDigital;

import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;

public class XmlDsigSigner implements XmlSigner {
    private static final String NS_EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    private final boolean sha1;

    public XmlDsigSigner() { this(false); }
    /** sha1=true replica los ejemplos históricos de SUNAT (rsa-sha1); por defecto rsa-sha256. */
    public XmlDsigSigner(boolean sha1) { this.sha1 = sha1; }

    @Override public FirmaResultado firmar(String xml, CertificadoDigital cert) {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(cert.pkcs12()), cert.clave().toCharArray());
            String alias = ks.aliases().nextElement();
            PrivateKey key = (PrivateKey) ks.getKey(alias, cert.clave().toCharArray());
            X509Certificate x509 = (X509Certificate) ks.getCertificate(alias);

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            NodeList contents = doc.getElementsByTagNameNS(NS_EXT, "ExtensionContent");
            if (contents.getLength() == 0) throw new DomainException("XML_SIN_EXTENSION", "El XML no tiene ext:ExtensionContent para la firma");
            Element contenedor = (Element) contents.item(contents.getLength() - 1);

            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
            String digestAlg = sha1 ? DigestMethod.SHA1 : DigestMethod.SHA256;
            String signAlg = sha1 ? SignatureMethod.RSA_SHA1 : SignatureMethod.RSA_SHA256;
            Reference ref = fac.newReference("", fac.newDigestMethod(digestAlg, null),
                    List.of(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null)), null, null);
            SignedInfo si = fac.newSignedInfo(
                    fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                    fac.newSignatureMethod(signAlg, null), List.of(ref));
            KeyInfoFactory kif = fac.getKeyInfoFactory();
            KeyInfo ki = kif.newKeyInfo(List.of(kif.newX509Data(List.of(x509))));

            DOMSignContext ctx = new DOMSignContext(key, contenedor);
            ctx.setDefaultNamespacePrefix("ds");
            XMLSignature firma = fac.newXMLSignature(si, ki, null, "signatureFACTURA", null);
            firma.sign(ctx);

            String hash = Base64.getEncoder().encodeToString(ref.getDigestValue());
            return new FirmaResultado(serializar(doc), hash);
        } catch (DomainException e) { throw e;
        } catch (Exception e) { throw new DomainException("FIRMA_FALLIDA", "No se pudo firmar el XML: " + e.getMessage()); }
    }

    private static String serializar(Document doc) throws Exception {
        var tf = TransformerFactory.newInstance().newTransformer();
        tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        tf.setOutputProperty(OutputKeys.INDENT, "no");
        StringWriter out = new StringWriter();
        tf.transform(new DOMSource(doc), new StreamResult(out));
        return out.toString();
    }
}
