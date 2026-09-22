package pe.factura.adapters.signing;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
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
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;

public class XmlDsigSigner implements XmlSigner {
    private static final String NS_EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    private final boolean sha1;

    /** Clave privada y certificado ya extraídos del PKCS#12; inmutables, se comparten entre hilos. */
    private record MaterialFirma(PrivateKey key, X509Certificate x509) {}

    // Abrir el PKCS#12 (PBKDF2 con miles de iteraciones) cuesta unos ms por firma y hoy corre dentro del lock de la
    // serie (issue #9). Se cachea por hash del contenido+clave: un certificado nuevo tiene otro hash y entra solo;
    // expireAfterAccess acota cuánto vive la clave privada en memoria tras rotar el certificado o dejar de emitir.
    private final Cache<String, MaterialFirma> material = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .recordStats()
            .build();

    public XmlDsigSigner() { this(false); }
    /** sha1=true replica los ejemplos históricos de SUNAT (rsa-sha1); por defecto rsa-sha256. */
    public XmlDsigSigner(boolean sha1) { this.sha1 = sha1; }

    @Override public FirmaResultado firmar(String xml, CertificadoDigital cert) {
        try {
            MaterialFirma m = material.get(huella(cert), k -> abrir(cert));
            PrivateKey key = m.key();
            X509Certificate x509 = m.x509();

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
        } catch (Exception e) { throw new DomainException("FIRMA_FALLIDA", "No se pudo firmar el XML: " + e.getMessage(), e); }
    }

    private static MaterialFirma abrir(CertificadoDigital cert) {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(cert.pkcs12()), cert.clave().toCharArray());
            String alias = aliasConClavePrivada(ks);
            return new MaterialFirma((PrivateKey) ks.getKey(alias, cert.clave().toCharArray()), (X509Certificate) ks.getCertificate(alias));
        } catch (DomainException e) { throw e;
        } catch (Exception e) { throw new DomainException("FIRMA_FALLIDA", "No se pudo abrir el certificado: " + e.getMessage(), e); }
    }

    /** SHA-256 de PKCS#12 + clave: dos tenants con el mismo archivo comparten entrada; una clave distinta no la reutiliza. */
    private static String huella(CertificadoDigital cert) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(cert.pkcs12());
            md.update((byte) 0);
            md.update(cert.clave().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    CacheStats estadisticasCache() { return material.stats(); }

    /** Un PKCS#12 puede traer entradas de solo certificado (CA) antes de la clave; se elige la primera con clave privada. */
    private static String aliasConClavePrivada(KeyStore ks) throws KeyStoreException {
        for (Enumeration<String> aliases = ks.aliases(); aliases.hasMoreElements(); ) {
            String alias = aliases.nextElement();
            if (ks.isKeyEntry(alias)) return alias;
        }
        throw new DomainException("CERTIFICADO_INVALIDO", "El PKCS#12 no contiene una clave privada");
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
