package pe.factura.adapters.signing;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import pe.factura.application.port.out.FirmaResultado;
import pe.factura.domain.DomainException;
import pe.factura.domain.tenant.CertificadoDigital;

import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XmlDsigSignerTest {
    static final String XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                 xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
                 xmlns:ext="urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2">
          <ext:UBLExtensions><ext:UBLExtension><ext:ExtensionContent/></ext:UBLExtension></ext:UBLExtensions>
          <cbc:ID>F001-1</cbc:ID>
        </Invoice>
        """;

    static CertificadoDigital cert() throws Exception {
        byte[] p12 = XmlDsigSignerTest.class.getResourceAsStream("/test-cert.p12").readAllBytes();
        return new CertificadoDigital(p12, "test1234", LocalDate.of(2036, 1, 1));
    }

    @Test void firmaDentroDeExtensionContentYDevuelveHash() throws Exception {
        FirmaResultado r = new XmlDsigSigner().firmar(XML, cert());
        assertThat(r.xmlFirmado()).contains("<ext:ExtensionContent><ds:Signature").contains("Id=\"signatureFACTURA\"")
                .contains("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256").contains("<ds:X509Certificate>");
        assertThat(r.hash()).isNotBlank().hasSizeGreaterThan(20);
        assertThat(r.xmlFirmado()).contains("<ds:DigestValue>" + r.hash() + "</ds:DigestValue>");
    }

    @Test void laFirmaEsVerificable() throws Exception {
        FirmaResultado r = new XmlDsigSigner().firmar(XML, cert());
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance(); dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(r.xmlFirmado().getBytes(StandardCharsets.UTF_8)));
        NodeList nl = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        KeyStore ks = KeyStore.getInstance("PKCS12"); ks.load(new ByteArrayInputStream(cert().pkcs12()), "test1234".toCharArray());
        DOMValidateContext ctx = new DOMValidateContext(ks.getCertificate(ks.aliases().nextElement()).getPublicKey(), nl.item(0));
        assertThat(XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(ctx).validate(ctx)).isTrue();
    }

    @Test void alterarElDocumentoInvalidaLaFirma() throws Exception {
        FirmaResultado r = new XmlDsigSigner().firmar(XML, cert());
        String alterado = r.xmlFirmado().replace("F001-1", "F001-2");
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance(); dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(alterado.getBytes(StandardCharsets.UTF_8)));
        NodeList nl = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        KeyStore ks = KeyStore.getInstance("PKCS12"); ks.load(new ByteArrayInputStream(cert().pkcs12()), "test1234".toCharArray());
        DOMValidateContext ctx = new DOMValidateContext(ks.getCertificate(ks.aliases().nextElement()).getPublicKey(), nl.item(0));
        assertThat(XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(ctx).validate(ctx)).isFalse();
    }

    @Test void passwordIncorrectaLanzaConCausaOriginal() throws Exception {
        CertificadoDigital certConMalaClave = new CertificadoDigital(cert().pkcs12(), "mala", LocalDate.of(2036, 1, 1));
        assertThatThrownBy(() -> new XmlDsigSigner().firmar(XML, certConMalaClave))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).codigo()).isEqualTo("FIRMA_FALLIDA"))
                .satisfies(e -> assertThat(e.getCause()).isNotNull());
    }
}
