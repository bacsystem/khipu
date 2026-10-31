package pe.factura.adapters.sunatrest;

import com.fasterxml.jackson.databind.JsonNode;
import pe.factura.application.port.out.SunatApiValidezGateway.Validez;
import pe.factura.application.port.out.SunatCredencialesException;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.tenant.CredencialesApiSunat;
import pe.factura.domain.tenant.CredencialesSol;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La corrida controlada de la consulta de validez por la API REST de SUNAT (#318): lo que el manual de SUNAT dice, preguntado a SUNAT de verdad con las credenciales
 * de una empresa. Es lo único que el issue tenía pendiente (las pruebas automáticas usan un SUNAT simulado), y se hace con un solo comando, sin backend, base de datos ni
 * Docker: {@code ./gradlew :adapters:out-sunat-rest:validezReal} (ver {@code deploy/README.md}, sección 13).
 *
 * <p>La lógica vive aquí y no en la prueba etiquetada para poder probarla contra un SUNAT simulado ({@code CorridaDeValidezTest}) sin salir a Internet: así la corrida
 * real no estrena código el día que se hace. Nunca escribe ni imprime el secreto, el token ni el cuerpo entero de una respuesta; el «Id» de la aplicación sale
 * enmascarado.
 */
final class CorridaDeValidez {
    private CorridaDeValidez() {}

    static final String ID_INVENTADO = "00000000-0000-4000-8000-000000000000";
    /** Un correlativo que ninguna empresa llega a emitir: SUNAT debe decir que no existe. */
    static final long NUMERO_INEXISTENTE = 99_999_999L;

    /** Lo que hace falta para preguntar. Sin {@code clientId}/{@code clientSecret} solo corre el paso de credenciales inventadas. */
    record Datos(String urlSeguridad, String urlApi, String ruc, String clientId, String clientSecret,
                 String rucEmisor, String tipo, String serie, long numero, LocalDate fecha, BigDecimal monto) {
        boolean conCredenciales() { return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank(); }
        boolean conComprobante() { return serie != null && !serie.isBlank() && numero > 0 && fecha != null; }
    }

    /**
     * @param informativo si el paso solo registra lo que SUNAT contestó (para decidir cosas del contrato) y no hace fallar la corrida
     * @param omitido     si no se pudo preguntar por falta de datos
     */
    record Paso(String nombre, String esperado, boolean cumple, boolean informativo, boolean omitido, String obtenido) {}

    /** Las variables de entorno de la corrida real. Lo que falte deja al paso correspondiente omitido, no inventa un valor. */
    static Datos deEntorno(Map<String, String> env) {
        String ruc = env.getOrDefault("VALIDEZ_RUC", "").strip();
        String numero = env.getOrDefault("VALIDEZ_NUMERO", "").strip();
        String fecha = env.getOrDefault("VALIDEZ_FECHA", "").strip();
        String monto = env.getOrDefault("VALIDEZ_MONTO", "").strip();
        return new Datos(
                env.getOrDefault("VALIDEZ_URL_SEGURIDAD", "https://api-seguridad.sunat.gob.pe").strip(),
                env.getOrDefault("VALIDEZ_URL_API", "https://api.sunat.gob.pe").strip(),
                ruc.isEmpty() ? "20100066603" : ruc,
                env.getOrDefault("VALIDEZ_CLIENT_ID", "").strip(),
                env.getOrDefault("VALIDEZ_CLIENT_SECRET", "").strip(),
                env.getOrDefault("VALIDEZ_RUC_EMISOR", ruc).strip(),
                env.getOrDefault("VALIDEZ_TIPO", "01").strip(),
                env.getOrDefault("VALIDEZ_SERIE", "").strip(),
                numero.isEmpty() ? 0 : Long.parseLong(numero),
                fecha.isEmpty() ? null : LocalDate.parse(fecha),
                monto.isEmpty() ? null : new BigDecimal(monto));
    }

    static List<Paso> correr(Datos d) {
        List<Paso> pasos = new ArrayList<>();
        pasos.add(credencialesInventadas(d));
        if (!d.conCredenciales()) {
            pasos.add(omitido("el comprobante existente", "faltan VALIDEZ_CLIENT_ID y VALIDEZ_CLIENT_SECRET"));
            return pasos;
        }
        if (!d.conComprobante()) {
            pasos.add(omitido("el comprobante existente", "faltan VALIDEZ_SERIE, VALIDEZ_NUMERO y VALIDEZ_FECHA (un comprobante tuyo ya aceptado)"));
            return pasos;
        }
        Tenant empresa = empresa(d, d.clientId(), d.clientSecret());
        RestValidezGateway g = gateway(d);
        pasos.add(consulta("el comprobante existente", "estadoCp 1 (aceptado)", true, g, empresa, d, d.serie(), d.numero(), d.fecha(), d.monto(), "1"));
        pasos.add(consulta("la fecha de emisión equivocada (un día antes)", "SUNAT contesta algo sobre el comprobante, sin error de credenciales", false, g, empresa, d, d.serie(), d.numero(), d.fecha().minusDays(1), d.monto(), null));
        pasos.add(consulta("un correlativo que no existe", "estadoCp 0 (no existe)", true, g, empresa, d, d.serie(), NUMERO_INEXISTENTE, d.fecha(), d.monto(), "0"));
        pasos.add(consulta("sin monto", "SUNAT contesta algo sobre el comprobante, sin error de credenciales", false, g, empresa, d, d.serie(), d.numero(), d.fecha(), null, null));
        pasos.add(numerosComoNumeros(d, empresa));
        return pasos;
    }

    private static Paso omitido(String nombre, String motivo) { return new Paso(nombre, "-", true, true, true, "Omitido: " + motivo); }

    // ---- el paso que no necesita credenciales ----------------------------------------------------------------------------------------------------------------------

    /** SUNAT contesta 400 `unauthorized_client` a un cliente inventado: la URL del token existe y el rechazo es de credenciales, no un fallo pasajero que se reintenta. */
    private static Paso credencialesInventadas(Datos d) {
        String nombre = "credenciales inventadas";
        String esperado = "SUNAT las rechaza como credenciales (HTTP 400/401/403), no como un fallo pasajero";
        try {
            gateway(d).validar(empresa(d, ID_INVENTADO, "secreto-inventado-que-sunat-no-conoce"), d.rucEmisor(), "01", "F001", 1, LocalDate.of(2026, 1, 2), null);
            return new Paso(nombre, esperado, false, false, false, "SUNAT las aceptó: no debería");
        } catch (SunatCredencialesException e) {
            return new Paso(nombre, esperado, true, false, false, "SunatCredencialesException " + e.codigo() + ": " + acotar(e.getMessage()));
        } catch (RuntimeException e) {
            return new Paso(nombre, esperado, false, false, false, describir(e));
        }
    }

    // ---- las consultas con credenciales ------------------------------------------------------------------------------------------------------------------------------

    private static Paso consulta(String nombre, String esperado, boolean exigeEstado, RestValidezGateway g, Tenant empresa, Datos d,
                                 String serie, long numero, LocalDate fecha, BigDecimal monto, String estadoEsperado) {
        try {
            Validez v = g.validar(empresa, d.rucEmisor(), d.tipo(), serie, numero, fecha, monto);
            String obtenido = "estadoCp=" + v.estadoCp() + " estadoRuc=" + v.estadoRuc() + " condDomiRuc=" + v.condicionDomicilio() + " observaciones=" + v.observaciones().size()
                    + (v.observaciones().isEmpty() ? "" : " (" + acotar(String.join(" | ", v.observaciones())) + ")");
            boolean cumple = estadoEsperado == null || estadoEsperado.equals(v.estadoCp());
            return new Paso(nombre, esperado, cumple, !exigeEstado, false, obtenido);
        } catch (SunatRechazoException e) {
            // SUNAT entendió el pedido y lo rechaza por sus datos: en el paso informativo es una respuesta válida, en el que exige un estado es el contrato roto.
            return new Paso(nombre, esperado, !exigeEstado, !exigeEstado, false, "SunatRechazoException " + e.codigo() + ": " + acotar(e.getMessage()));
        } catch (RuntimeException e) {
            return new Paso(nombre, esperado, false, false, false, describir(e));
        }
    }

    /**
     * El manual muestra {@code "numero": "749"} y {@code "monto": "236.00"} como texto, y sus tablas los dan como entero y decimal. El gateway los manda como texto;
     * este paso manda los mismos como números JSON para saber, por SUNAT y no por el manual, cuál de los dos formatos acepta. Solo informa.
     */
    private static Paso numerosComoNumeros(Datos d, Tenant empresa) {
        String nombre = "numero y monto como números JSON (no como texto)";
        String esperado = "informativo: dice si SUNAT acepta también ese formato";
        try {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("numRuc", d.rucEmisor());
            cuerpo.put("codComp", d.tipo());
            cuerpo.put("numeroSerie", d.serie());
            cuerpo.put("numero", d.numero());
            cuerpo.put("fechaEmision", d.fecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            if (d.monto() != null) cuerpo.put("monto", d.monto());
            PedidoDeToken token = PedidoDeToken.clientCredentials(d.urlSeguridad() + "/v1/clientesextranet/" + d.clientId() + "/oauth2/token/", RestValidezGateway.SCOPE, d.clientId(), d.clientSecret());
            JsonNode r = cliente().postJson(token, d.urlApi() + "/v1/contribuyente/contribuyentes/" + empresa.ruc() + "/validarcomprobante", cuerpo);
            return new Paso(nombre, esperado, true, true, false, "SUNAT lo acepta: success=" + r.path("success").asBoolean(false) + " estadoCp=" + r.path("data").path("estadoCp").asText(""));
        } catch (SunatRechazoException e) {
            return new Paso(nombre, esperado, true, true, false, "SUNAT lo rechaza (" + e.codigo() + "): el formato correcto es el texto del manual");
        } catch (RuntimeException e) {
            return new Paso(nombre, esperado, true, true, false, describir(e));
        }
    }

    // ---- armado ---------------------------------------------------------------------------------------------------------------------------------------------------------

    private static ClienteApiSunat cliente() { return new ClienteApiSunat(Duration.ofSeconds(10), Duration.ofSeconds(20), Clock.systemUTC()); }

    private static RestValidezGateway gateway(Datos d) {
        return new RestValidezGateway(new UrlPorEntorno(d.urlSeguridad(), d.urlSeguridad()), new UrlPorEntorno(d.urlApi(), d.urlApi()), cliente());
    }

    private static Tenant empresa(Datos d, String clientId, String clientSecret) {
        return new Tenant(UUID.randomUUID(), d.ruc(), "EMPRESA DE LA CORRIDA", Entorno.PRODUCCION, new CredencialesSol("USUARIO1", "clave-sol"), null)
                .conCredencialesApiSunat(new CredencialesApiSunat(clientId, clientSecret));
    }

    private static String describir(RuntimeException e) {
        // SunatCredencialesException hereda de SunatTransientException: se pregunta primero por ella, o unas credenciales rechazadas se describirían como un fallo pasajero.
        String que = e instanceof SunatCredencialesException ? "SunatCredencialesException (SUNAT rechaza las credenciales de la empresa)"
                : e instanceof SunatTransientException ? "SunatTransientException (fallo pasajero: SUNAT no respondió bien, no es un dato sobre el comprobante)" : e.getClass().getSimpleName();
        return que + ": " + acotar(e.getMessage());
    }

    private static String acotar(String s) {
        if (s == null) return "";
        String una = s.replaceAll("\\s+", " ").strip();
        return una.length() > 300 ? una.substring(0, 300) + "…" : una;
    }

    /** El «Id» de la aplicación sale enmascarado en la evidencia: solo sus primeros 8 caracteres. */
    static String enmascarado(String id) {
        return id == null || id.isBlank() ? "(sin credenciales)" : id.substring(0, Math.min(8, id.length())) + "…";
    }

    /** La evidencia de la corrida: qué se preguntó, qué se esperaba y qué contestó SUNAT. Sin secretos ni tokens. */
    static String resumen(Datos d, List<Paso> pasos, Instant cuando) {
        StringBuilder sb = new StringBuilder("# Corrida controlada de la consulta de validez (#318)\n\n");
        sb.append("Fecha: ").append(cuando).append("\n\n");
        sb.append("- Servicio de token: ").append(d.urlSeguridad()).append("\n");
        sb.append("- API: ").append(d.urlApi()).append("\n");
        sb.append("- Empresa que consulta (RUC): ").append(d.ruc()).append("\n");
        sb.append("- Aplicación (Id): ").append(enmascarado(d.clientId())).append("\n");
        if (d.conComprobante()) {
            sb.append("- Comprobante preguntado: ").append(d.rucEmisor()).append(" ").append(d.tipo()).append(" ").append(d.serie()).append("-").append(d.numero())
                    .append(" del ").append(d.fecha()).append(d.monto() == null ? "" : " por " + d.monto().toPlainString()).append("\n");
        }
        sb.append("\n| Paso | Esperado | Resultado | Lo que contestó SUNAT |\n|---|---|---|---|\n");
        for (Paso p : pasos) {
            String estado = p.omitido() ? "omitido" : p.cumple() ? (p.informativo() ? "informa" : "cumple") : "NO CUMPLE";
            sb.append("| ").append(p.nombre()).append(" | ").append(p.esperado()).append(" | ").append(estado).append(" | ").append(p.obtenido().replace("|", "/")).append(" |\n");
        }
        long falla = pasos.stream().filter(p -> !p.cumple() && !p.informativo()).count();
        sb.append("\n").append(falla == 0 ? "**La corrida cumple.**" : "**" + falla + " paso(s) no cumplen.**").append("\n");
        return sb.toString();
    }
}
