package pe.factura.adapters.sunatrest;

import com.fasterxml.jackson.databind.JsonNode;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import pe.factura.application.port.out.SunatGreGateway;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.DomainException;
import pe.factura.domain.tenant.CredencialesApiSunat;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Guía de remisión electrónica por la API REST de SUNAT (#39), según el «Manual de servicios web plataforma nueva GRE» (§1.2, token) y el anexo
 * «manual_url_gre_rest» (docs/sunat/ref/, hojas REST1 y REST2):
 * <ul>
 *   <li>Token: {@code POST <seguridad>/v1/clientessol/<client_id>/oauth2/token/}, grant {@code password}, scope {@value #SCOPE}, {@code username} = RUC +
 *       usuario SOL, {@code password} = clave SOL.</li>
 *   <li>Envío: {@code POST <api>/v1/contribuyente/gem/comprobantes/{RUC}-{tipo}-{serie}-{número}} con {@code {"archivo": {"nomArchivo", "arcGreZip",
 *       "hashZip"}}}: el nombre del ZIP ({@code RUC-TT-SSSS-N.zip}, sin ceros a la izquierda, como el ejemplo {@code 20480072872-31-VA28-407.zip}), el ZIP en
 *       base64 con un único XML del mismo nombre (errores 157–161) y su SHA-256. Responde {@code numTicket} y {@code fecRecepcion}.</li>
 *   <li>Ticket: {@code GET <api>/v1/contribuyente/gem/comprobantes/envios/{numTicket}}: {@code codRespuesta} 98/0/99, {@code arcCdr} (base64) si
 *       {@code indCdrGenerado} es 1, y {@code error.numError}/{@code error.desError} con el 99.</li>
 * </ul>
 * Lo que el manual no fija y aquí se supone, sin haberse probado contra SUNAT (no hay credenciales API de prueba; ver deploy/README.md §14): el hash es el
 * SHA-256 en hexadecimal minúsculo (el manual dice «SHA-256» y su ejemplo no es un hash real); {@code arcCdr} es un ZIP con el {@code ApplicationResponse},
 * como el CDR del SOAP; la URL del QR está en un {@code cbc:DocumentDescription} del CDR.
 *
 * <p>Encendido: el contrato no se verificó contra SUNAT, así que el envío está apagado salvo que el servidor lo encienda ({@code SUNAT_API_GRE_HABILITADA}); apagado,
 * {@link #disponible} es falso y {@link #enviar} no llama a nadie. La consulta de un ticket ya dado sigue (solo lee).
 *
 * <p>Beta: SUNAT no publica una API GRE de pruebas, y la de producción emite guías con validez tributaria. Sin una URL de beta, una empresa en BETA no envía
 * guías. Con una (una API de pruebas de terceros), lleva su propia URL de token: el bearer y las credenciales de la empresa van a ese tercero y no al
 * {@code api-seguridad} real. Al construirse rechaza (es decir, no deja arrancar) una beta que apunte a la API o al token reales de SUNAT.
 */
public class RestGreGateway implements SunatGreGateway {
    static final String SCOPE = "https://api-cpe.sunat.gob.pe";
    private static final String RUTA = "/v1/contribuyente/gem/comprobantes/";
    static final String HOST_API_REAL = "api-cpe.sunat.gob.pe", HOST_TOKEN_REAL = "api-seguridad.sunat.gob.pe";

    private final boolean habilitada;
    private final String seguridadProduccion, seguridadBeta;
    private final String apiProduccion, apiBeta;
    private final ClienteApiSunat cliente;

    /**
     * {@code apiBeta} vacía o nula: las empresas en BETA no envían guías. Con {@code apiBeta}, {@code seguridadBeta} es obligatoria y ninguna de las dos puede ser
     * la de producción ni un host real de SUNAT: {@link IllegalStateException}.
     */
    public RestGreGateway(boolean habilitada, String seguridadProduccion, String seguridadBeta, String apiProduccion, String apiBeta, ClienteApiSunat cliente) {
        this.habilitada = habilitada;
        this.seguridadProduccion = url(seguridadProduccion);
        this.apiProduccion = url(apiProduccion);
        this.apiBeta = vacia(apiBeta) ? null : url(apiBeta);
        if (this.apiBeta != null) {
            if (vacia(seguridadBeta))
                throw new IllegalStateException("SUNAT_API_GRE_BETA_URL está configurada pero SUNAT_API_GRE_SEGURIDAD_BETA_URL no: la beta de la GRE necesita su propio servicio de token");
            this.seguridadBeta = url(seguridadBeta);
            if (this.apiBeta.equalsIgnoreCase(this.apiProduccion) || host(this.apiBeta).equalsIgnoreCase(HOST_API_REAL))
                throw new IllegalStateException("SUNAT_API_GRE_BETA_URL apunta a la API GRE de producción (" + this.apiBeta + "): las empresas en BETA enviarían guías con validez tributaria");
            if (this.seguridadBeta.equalsIgnoreCase(this.seguridadProduccion) || host(this.seguridadBeta).equalsIgnoreCase(HOST_TOKEN_REAL))
                throw new IllegalStateException("SUNAT_API_GRE_SEGURIDAD_BETA_URL apunta al token real de SUNAT (" + this.seguridadBeta + "): el bearer real de la empresa iría a una API de pruebas");
        } else {
            this.seguridadBeta = null;
        }
        this.cliente = cliente;
    }

    private static boolean vacia(String u) { return u == null || u.isBlank(); }
    private static String url(String u) { return new UrlPorEntorno(u, u).produccion(); }
    private static String host(String u) { return java.net.URI.create(u).getHost(); }

    @Override public boolean disponible(Entorno entorno) { return habilitada && (entorno == Entorno.PRODUCCION || apiBeta != null); }

    private String api(Tenant t) {
        if (t.entorno() == Entorno.PRODUCCION) return apiProduccion;
        if (apiBeta == null)
            throw new DomainException("GRE_NO_DISPONIBLE", "SUNAT no publica una API de pruebas para la guía de remisión y khipu no manda guías de una empresa en BETA a la de producción: "
                    + "el servidor no configuró SUNAT_API_GRE_BETA_URL");
        return apiBeta;
    }

    private String seguridad(Tenant t) { return t.entorno() == Entorno.PRODUCCION ? seguridadProduccion : seguridadBeta; }

    /** El token del grant password: credenciales API SUNAT más el usuario (RUC + usuario) y la clave SOL. */
    private PedidoDeToken token(Tenant t) {
        CredencialesApiSunat c = t.apiSunat();
        if (c == null) throw new DomainException("CREDENCIALES_API_SUNAT_NO_CARGADAS", "La empresa no tiene credenciales API SUNAT (client_id y client_secret): la guía de remisión las necesita");
        if (t.sol() == null) throw new DomainException("CREDENCIALES_SOL_NO_CARGADAS", "La empresa no tiene credenciales SOL: la guía de remisión las necesita junto con las credenciales API SUNAT");
        return PedidoDeToken.password(seguridad(t) + "/v1/clientessol/" + c.clientId() + "/oauth2/token/", SCOPE, c.clientId(), c.clientSecret(),
                t.sol().usernameToken(t.ruc()), t.sol().clave());
    }

    @Override public Recepcion enviar(Tenant t, String nombreArchivo, byte[] xmlFirmado) {
        if (nombreArchivo == null || !nombreArchivo.matches("\\d{11}-(09|31)-[TV][A-Z0-9]{3}-\\d{1,8}"))
            throw new IllegalArgumentException("Nombre de archivo de guía inválido: " + nombreArchivo);
        if (!habilitada) throw new DomainException("GRE_NO_DISPONIBLE", "El envío de guías de remisión a SUNAT está apagado en este servidor (SUNAT_API_GRE_HABILITADA): "
                + "el contrato con SUNAT aún no se verificó. Emítala con enviar_automatico: false para revisar el XML");
        String url = api(t) + RUTA + nombreArchivo;
        PedidoDeToken p = token(t);
        byte[] zip = zip(nombreArchivo + ".xml", xmlFirmado);
        Map<String, Object> cuerpo = Map.of("archivo", Map.of("nomArchivo", nombreArchivo + ".zip", "arcGreZip", Base64.getEncoder().encodeToString(zip), "hashZip", sha256(zip)));
        JsonNode r = cliente.postJson(p, url, cuerpo, ClienteApiSunat.CodigoDeRechazo.DEL_DETALLE);
        String ticket = ClienteApiSunat.texto(r, "numTicket");
        if (ticket.isBlank()) throw new SunatTransientException("0000", "SUNAT recibió la guía " + nombreArchivo + " pero no devolvió numTicket");
        return new Recepcion(ticket, fecha(ClienteApiSunat.texto(r, "fecRecepcion")));
    }

    @Override public EstadoDelTicket consultar(Tenant t, String ticket) {
        // El ticket va en la ruta: solo lo que SUNAT da (un UUID, 8-4-4-4-12), para que un valor guardado raro no cambie de recurso.
        if (ticket == null || !ticket.matches("[0-9A-Za-z-]{1,64}")) throw new DomainException("TICKET_INVALIDO", "Ticket de la guía inválido: " + ticket);
        JsonNode r = cliente.getJson(token(t), api(t) + RUTA + "envios/" + ticket, ClienteApiSunat.CodigoDeRechazo.DEL_DETALLE);
        String codigo = ClienteApiSunat.texto(r, "codRespuesta");
        if (codigo.isBlank()) throw new SunatTransientException("0000", "SUNAT respondió la consulta del ticket " + ticket + " sin codRespuesta");
        byte[] cdr = null;
        String arc = ClienteApiSunat.texto(r, "arcCdr");
        if (!arc.isBlank() && !"0".equals(ClienteApiSunat.texto(r, "indCdrGenerado"))) {
            try {
                cdr = Base64.getMimeDecoder().decode(arc);
            } catch (IllegalArgumentException e) {
                throw new SunatTransientException("0000", "SUNAT devolvió un CDR que no es base64 para el ticket " + ticket);
            }
        }
        JsonNode error = r.path("error");
        return new EstadoDelTicket(codigo, cdr, vacio(ClienteApiSunat.texto(error, "numError")), vacio(ClienteApiSunat.texto(error, "desError")), cdr == null ? null : enlace(cdr));
    }

    private static String vacio(String s) { return s == null || s.isBlank() ? null : s; }

    /** «Formato: yyyy-mm-dd'T'hh:ii:ss» (hora de Lima, sin zona); lo que no se entiende queda sin fecha: no es motivo para perder el ticket. */
    static LocalDateTime fecha(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static byte[] zip(String entrada, byte[] contenido) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); ZipOutputStream z = new ZipOutputStream(bos)) {
            z.putNextEntry(new ZipEntry(entrada));
            z.write(contenido);
            z.closeEntry();
            z.finish();
            return bos.toByteArray();
        } catch (IOException e) { throw new IllegalStateException("No se pudo comprimir " + entrada, e); }
    }

    static String sha256(byte[] b) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    /**
     * La URL de consulta de la guía que SUNAT pone en el CDR (la que imprime el QR): el primer {@code cbc:DocumentDescription} que sea una URL https. Un CDR que
     * no se puede leer no impide aceptar la guía: el enlace queda vacío y lo que el CDR dice lo interpreta {@code CdrParser}.
     */
    static String enlace(byte[] cdrZip) {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(cdrZip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (e.isDirectory() || !e.getName().toLowerCase().endsWith(".xml")) continue;
                DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
                f.setNamespaceAware(true);
                f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                Document d = f.newDocumentBuilder().parse(new ByteArrayInputStream(in.readAllBytes()));
                NodeList l = d.getElementsByTagNameNS("urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2", "DocumentDescription");
                for (int i = 0; i < l.getLength(); i++) {
                    String v = l.item(i).getTextContent() == null ? "" : l.item(i).getTextContent().strip();
                    if (v.startsWith("https://") && v.length() <= 500) return v;
                }
                return null;
            }
        } catch (Exception ex) {
            return null;
        }
        return null;
    }
}
