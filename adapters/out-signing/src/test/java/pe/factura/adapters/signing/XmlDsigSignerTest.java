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

    /** El PKCS#12 se abre una vez por certificado (issue #9): las firmas siguientes reutilizan la clave ya extraída. */
    @Test void elCertificadoSeAbreUnaVezYLasFirmasSiguientesLoReutilizan() throws Exception {
        XmlDsigSigner signer = new XmlDsigSigner();
        FirmaResultado primera = signer.firmar(XML, cert());
        FirmaResultado segunda = signer.firmar(XML, cert());   // otra instancia de CertificadoDigital, mismo contenido
        assertThat(segunda.hash()).isEqualTo(primera.hash());
        assertThat(signer.estadisticasCache().loadCount()).isEqualTo(1);
        assertThat(signer.estadisticasCache().hitCount()).isEqualTo(1);
    }

    @Test void unaClaveDistintaOUnPkcs12DistintoNoCompartenLaEntradaDelCache() throws Exception {
        XmlDsigSigner signer = new XmlDsigSigner();
        signer.firmar(XML, cert());
        // Clave mala: falla y no queda cacheada; la clave buena con el mismo archivo sigue siendo un hit.
        CertificadoDigital malaClave = new CertificadoDigital(cert().pkcs12(), "mala", LocalDate.of(2036, 1, 1));
        assertThatThrownBy(() -> signer.firmar(XML, malaClave)).isInstanceOf(DomainException.class);
        signer.firmar(XML, cert());
        assertThat(signer.estadisticasCache().loadSuccessCount()).isEqualTo(1);
        assertThat(signer.estadisticasCache().loadFailureCount()).isEqualTo(1);
        // Otro PKCS#12 (mismas claves, distinto orden de entradas → bytes distintos) es otra entrada.
        signer.firmar(XML, certConEntradaDeCaPrimero());
        assertThat(signer.estadisticasCache().loadSuccessCount()).isEqualTo(2);
    }

    /**
     * PKCS#12 con una entrada de solo certificado (confiable) ANTES de la entrada con clave privada.
     * <p>
     * El proveedor PKCS12 del JDK siempre escribe las bolsas de claves antes que las de certificados, así que
     * un store/load con la API KeyStore devuelve la clave primero. Para reproducir el orden que producen otras
     * herramientas (OpenSSL, exportaciones de Windows) se intercambian los dos ContentInfo del AuthenticatedSafe
     * y se descarta el MacData (opcional para el JDK). El resultado se carga con KeyStore sin errores.
     */
    static CertificadoDigital certConEntradaDeCaPrimero() throws Exception {
        KeyStore origen = KeyStore.getInstance("PKCS12");
        origen.load(new ByteArrayInputStream(cert().pkcs12()), "test1234".toCharArray());
        String alias = origen.aliases().nextElement();
        java.security.Key key = origen.getKey(alias, "test1234".toCharArray());
        java.security.cert.Certificate[] chain = origen.getCertificateChain(alias);

        KeyStore nuevo = KeyStore.getInstance("PKCS12");
        nuevo.load(null, null);
        nuevo.setCertificateEntry("ca", chain[0]);
        nuevo.setKeyEntry("factura", key, "test1234".toCharArray(), chain);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        nuevo.store(out, "test1234".toCharArray());
        return new CertificadoDigital(Der.certificadosPrimero(out.toByteArray()), "test1234", LocalDate.of(2036, 1, 1));
    }

    /** Lector/escritor DER mínimo para reordenar el AuthenticatedSafe de un PFX. */
    static final class Der {
        /** PFX ::= SEQUENCE { version, authSafe ContentInfo { OID, [0] OCTET STRING { SEQUENCE OF ContentInfo } }, macData OPTIONAL }. */
        static byte[] certificadosPrimero(byte[] pfx) {
            java.util.List<byte[]> pfxHijos = hijos(pfx);                       // [version, authSafe, macData]
            java.util.List<byte[]> authSafe = hijos(pfxHijos.get(1));           // [OID, [0]]
            byte[] octet = hijos(authSafe.get(1)).get(0);                       // OCTET STRING
            java.util.List<byte[]> contentInfos = hijos(contenido(octet));      // [claves, certificados] según el JDK
            if (contentInfos.size() != 2) throw new IllegalStateException("se esperaban 2 ContentInfo, hay " + contentInfos.size());
            byte[] reordenado = tlv(0x30, concat(contentInfos.get(1), contentInfos.get(0)));
            byte[] nuevoAuthSafe = tlv(0x30, concat(authSafe.get(0), tlv(0xA0, tlv(0x04, reordenado))));
            return tlv(0x30, concat(pfxHijos.get(0), nuevoAuthSafe));         // sin MacData
        }
        static java.util.List<byte[]> hijos(byte[] der) {
            byte[] c = contenido(der);
            java.util.List<byte[]> out = new java.util.ArrayList<>();
            int i = 0;
            while (i < c.length) { int fin = finDe(c, i); out.add(java.util.Arrays.copyOfRange(c, i, fin)); i = fin; }
            return out;
        }
        static byte[] contenido(byte[] der) { int[] h = cabecera(der, 0); return java.util.Arrays.copyOfRange(der, h[0], h[0] + h[1]); }
        static int finDe(byte[] b, int desde) { int[] h = cabecera(b, desde); return h[0] + h[1]; }
        /** Devuelve {offset del contenido, longitud del contenido}. */
        static int[] cabecera(byte[] b, int desde) {
            int i = desde + 1;
            int l = b[i++] & 0xFF;
            if (l >= 0x80) { int n = l & 0x7F; l = 0; for (int k = 0; k < n; k++) l = (l << 8) | (b[i++] & 0xFF); }
            return new int[]{i, l};
        }
        static byte[] tlv(int tag, byte[] contenido) {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            o.write(tag);
            int l = contenido.length;
            if (l < 0x80) o.write(l);
            else { byte[] lb = java.math.BigInteger.valueOf(l).toByteArray(); if (lb[0] == 0) lb = java.util.Arrays.copyOfRange(lb, 1, lb.length); o.write(0x80 | lb.length); o.writeBytes(lb); }
            o.writeBytes(contenido);
            return o.toByteArray();
        }
        static byte[] concat(byte[] a, byte[] b) { byte[] r = java.util.Arrays.copyOf(a, a.length + b.length); System.arraycopy(b, 0, r, a.length, b.length); return r; }
    }

    @Test void eligeElAliasConClavePrivadaAunqueNoSeaElPrimero() throws Exception {
        CertificadoDigital c = certConEntradaDeCaPrimero();
        KeyStore ks = KeyStore.getInstance("PKCS12"); ks.load(new ByteArrayInputStream(c.pkcs12()), "test1234".toCharArray());
        String primero = ks.aliases().nextElement();
        assertThat(ks.isKeyEntry(primero)).as("el primer alias (" + primero + ") debe ser de solo certificado para que la prueba sea significativa").isFalse();
        assertThat(ks.isKeyEntry("factura")).isTrue();

        FirmaResultado r = new XmlDsigSigner().firmar(XML, c);
        assertThat(r.xmlFirmado()).contains("<ds:Signature").contains("<ds:X509Certificate>");
        assertThat(r.hash()).isNotBlank();
    }

    @Test void pkcs12SinClavePrivadaLanzaCertificadoInvalido() throws Exception {
        KeyStore origen = KeyStore.getInstance("PKCS12");
        origen.load(new ByteArrayInputStream(cert().pkcs12()), "test1234".toCharArray());
        KeyStore soloCert = KeyStore.getInstance("PKCS12");
        soloCert.load(null, null);
        soloCert.setCertificateEntry("ca", origen.getCertificate(origen.aliases().nextElement()));
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        soloCert.store(out, "test1234".toCharArray());

        assertThatThrownBy(() -> new XmlDsigSigner().firmar(XML, new CertificadoDigital(out.toByteArray(), "test1234", LocalDate.of(2036, 1, 1))))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("CERTIFICADO_INVALIDO");
    }
}
