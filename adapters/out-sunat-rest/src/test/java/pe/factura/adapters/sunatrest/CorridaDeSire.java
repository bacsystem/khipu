package pe.factura.adapters.sunatrest;

import pe.factura.adapters.sunatrest.CorridaDeValidez.Paso;
import pe.factura.application.port.out.SunatCredencialesException;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatSireGateway.ArchivoDelTicket;
import pe.factura.application.port.out.SunatSireGateway.EstadoDelTicket;
import pe.factura.application.port.out.SunatSireGateway.Fase;
import pe.factura.domain.DomainException;
import pe.factura.domain.sire.PeriodoSire;
import pe.factura.domain.sire.PropuestaRvie;
import pe.factura.domain.tenant.CredencialesApiSunat;
import pe.factura.domain.tenant.CredencialesSol;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * La corrida controlada de la propuesta del RVIE por la API del SIRE (#322): lo que el manual de SUNAT dice, preguntado a SUNAT de verdad con las credenciales de una
 * empresa. Es lo único que la fase 2 del issue deja pendiente (las pruebas automáticas usan un SIRE simulado), y se hace con un solo comando, sin backend, base de datos
 * ni Docker: {@code ./gradlew :adapters:out-sunat-rest:sireReal} (ver {@code deploy/README.md}, sección 15).
 *
 * <p>Solo lee: pide la propuesta (SUNAT arma un archivo y da un ticket), sigue el ticket y baja el archivo. No reemplaza ni acepta nada en el SIRE. La lógica vive aquí y
 * no en la prueba etiquetada para poder probarla contra un SIRE simulado ({@code CorridaDeSireTest}) sin salir a Internet. Nunca escribe ni imprime el secreto, la
 * clave SOL, el token ni una fila de la propuesta (son datos de los clientes de la empresa): de lo que baja solo cuenta filas y campos y nombra el primer error de
 * lectura con su línea y su campo.
 */
final class CorridaDeSire {
    private CorridaDeSire() {}

    static final String ID_INVENTADO = "00000000-0000-4000-8000-000000000000";

    /** Lo que hace falta para preguntar. Sin las credenciales solo corre el paso de las inventadas. */
    record Datos(String urlSeguridad, String urlApi, String ruc, String usuarioSol, String claveSol, String clientId, String clientSecret, String periodo,
                 Duration espera, Duration intervalo) {
        boolean conCredenciales() { return !vacio(clientId) && !vacio(clientSecret) && !vacio(usuarioSol) && !vacio(claveSol); }
        boolean conRuc() { return !vacio(ruc); }
        private static boolean vacio(String s) { return s == null || s.isBlank(); }
    }

    static Datos deEntorno(Map<String, String> env) {
        String espera = env.getOrDefault("SIRE_ESPERA_SEGUNDOS", "").strip();
        long segundos;
        try {
            segundos = espera.isEmpty() ? 180 : Long.parseLong(espera);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("SIRE_ESPERA_SEGUNDOS debe ser un entero (cuántos segundos esperar a que SUNAT termine la propuesta), no «" + espera + "»", e);
        }
        return new Datos(
                env.getOrDefault("SIRE_URL_SEGURIDAD", "https://api-seguridad.sunat.gob.pe").strip(),
                env.getOrDefault("SIRE_URL_API", "https://api-sire.sunat.gob.pe").strip(),
                env.getOrDefault("SIRE_RUC", "").strip(),
                env.getOrDefault("SIRE_USUARIO_SOL", "").strip(),
                env.getOrDefault("SIRE_CLAVE_SOL", "").strip(),
                env.getOrDefault("SIRE_CLIENT_ID", "").strip(),
                env.getOrDefault("SIRE_CLIENT_SECRET", "").strip(),
                env.getOrDefault("SIRE_PERIODO", "").strip(),
                Duration.ofSeconds(segundos), Duration.ofSeconds(5));
    }

    static List<Paso> correr(Datos d) {
        List<Paso> pasos = new ArrayList<>();
        pasos.add(credencialesInventadas(d));
        if (!d.conCredenciales() || !d.conRuc() || d.periodo().isBlank()) {
            pasos.add(omitido("la propuesta del periodo", "faltan SIRE_RUC, SIRE_USUARIO_SOL, SIRE_CLAVE_SOL, SIRE_CLIENT_ID, SIRE_CLIENT_SECRET y SIRE_PERIODO (un mes con ventas, AAAAMM)"));
            return pasos;
        }
        PeriodoSire periodo;
        try {
            periodo = PeriodoSire.de(d.periodo());
        } catch (DomainException e) {
            throw new IllegalArgumentException("SIRE_PERIODO: " + e.getMessage(), e);
        }
        Tenant empresa = empresa(d, d.clientId(), d.clientSecret());
        RestSireGateway g = gateway(d);

        String ticket = pedir(pasos, g, empresa, periodo);
        if (ticket == null) return pasos;
        pasos.add(consultaSinLosParametrosExtra(d, empresa, periodo, ticket));
        EstadoDelTicket fin = seguir(pasos, g, empresa, periodo, ticket, d);
        if (fin == null) return pasos;
        List<byte[]> archivos = bajar(pasos, g, empresa, fin);
        if (archivos == null) return pasos;
        pasos.add(leer(archivos, d, periodo));
        return pasos;
    }

    private static Paso omitido(String nombre, String motivo) { return new Paso(nombre, "-", true, true, true, "Omitido: " + motivo); }

    // ---- el paso que no necesita credenciales ----------------------------------------------------------------------------------------------------------------------

    /** SUNAT contesta 400 `unauthorized_client` a un cliente inventado: la URL del token existe y el rechazo es de credenciales, no un fallo pasajero que se reintenta. */
    private static Paso credencialesInventadas(Datos d) {
        String nombre = "credenciales inventadas";
        String esperado = "SUNAT las rechaza como credenciales (HTTP 400/401/403), no como un fallo pasajero";
        try {
            Tenant t = empresa(d, ID_INVENTADO, "secreto-inventado-que-sunat-no-conoce");
            gateway(d).solicitarPropuesta(t, PeriodoSire.de("202601"));
            return new Paso(nombre, esperado, false, false, false, "SUNAT las aceptó: no debería");
        } catch (SunatCredencialesException e) {
            return new Paso(nombre, esperado, true, false, false, "SunatCredencialesException " + e.codigo() + ": " + CorridaDeValidez.acotar(e.getMessage()));
        } catch (RuntimeException e) {
            return new Paso(nombre, esperado, false, false, false, CorridaDeValidez.describir(e));
        }
    }

    // ---- los pasos con credenciales ---------------------------------------------------------------------------------------------------------------------------------

    /** El ticket, o nulo si no se pudo pedir (el paso ya quedó anotado y los demás no tienen sentido). */
    private static String pedir(List<Paso> pasos, RestSireGateway g, Tenant empresa, PeriodoSire periodo) {
        String nombre = "pedir la propuesta del periodo " + periodo;
        String esperado = "SUNAT acepta el pedido y da un número de ticket";
        try {
            String ticket = g.solicitarPropuesta(empresa, periodo);
            pasos.add(new Paso(nombre, esperado, true, false, false, "ticket de " + ticket.length() + " caracteres (" + ticket.substring(0, Math.min(4, ticket.length())) + "…)"));
            return ticket;
        } catch (SunatRechazoException e) {
            // 1070: SUNAT no tiene comprobantes en el periodo. No rompe el contrato, pero no deja seguir: se pide otro mes.
            boolean sinDatos = "1070".equals(e.codigo());
            pasos.add(new Paso(nombre, esperado, sinDatos, sinDatos, false, "SunatRechazoException " + e.codigo() + ": " + CorridaDeValidez.acotar(e.getMessage())
                    + (sinDatos ? " (el periodo no tiene comprobantes en el SIRE: repita con un mes que sí)" : "")));
            return null;
        } catch (RuntimeException e) {
            pasos.add(new Paso(nombre, esperado, false, false, false, CorridaDeValidez.describir(e)));
            return null;
        }
    }

    /**
     * El manual lista {@code codLibro} y {@code codOrigenEnvio} como obligatorios de la consulta del ticket pero su ejemplo no los trae; el gateway los manda. Esta
     * consulta no los manda, para saber por SUNAT y no por el manual cuál de las dos formas acepta. Solo informa.
     */
    private static Paso consultaSinLosParametrosExtra(Datos d, Tenant empresa, PeriodoSire periodo, String ticket) {
        String nombre = "consultar el ticket sin codLibro ni codOrigenEnvio (como el ejemplo del manual)";
        String esperado = "informativo: dice si SUNAT acepta también esa forma";
        try {
            PedidoDeToken token = PedidoDeToken.password(d.urlSeguridad() + "/v1/clientessol/" + d.clientId() + "/oauth2/token/", RestSireGateway.SCOPE, d.clientId(), d.clientSecret(),
                    empresa.sol().usernameToken(empresa.ruc()), empresa.sol().clave());
            String url = d.urlApi() + "/v1/contribuyente/migeigv/libros/rvierce/gestionprocesosmasivos/web/masivo/consultaestadotickets?perIni=" + periodo.aaaamm() + "&perFin="
                    + periodo.aaaamm() + "&page=1&perPage=20&numTicket=" + ticket;
            var r = cliente().getJson(token, url, ClienteApiSunat.CodigoDeRechazo.DEL_DETALLE);
            return new Paso(nombre, esperado, true, true, false, "SUNAT la acepta: " + r.path("registros").size() + " registro(s)");
        } catch (SunatRechazoException e) {
            return new Paso(nombre, esperado, true, true, false, "SUNAT la rechaza (" + e.codigo() + "): hacen falta los dos parámetros, como los manda khipu");
        } catch (RuntimeException e) {
            return new Paso(nombre, esperado, true, true, false, CorridaDeValidez.describir(e));
        }
    }

    /** El estado final del ticket, o nulo si no llegó a {@code TERMINADO} (error o tiempo agotado). */
    private static EstadoDelTicket seguir(List<Paso> pasos, RestSireGateway g, Tenant empresa, PeriodoSire periodo, String ticket, Datos d) {
        String nombre = "seguir el ticket hasta que SUNAT termine";
        String esperado = "TERMINADO en menos de " + d.espera().toSeconds() + " s, con al menos un archivo";
        Instant limite = Instant.now().plus(d.espera());
        List<String> estados = new ArrayList<>();
        try {
            while (true) {
                EstadoDelTicket e = g.consultar(empresa, periodo, ticket);
                String visto = e.codigoSunat() + " " + e.descripcionSunat();
                if (estados.isEmpty() || !estados.get(estados.size() - 1).equals(visto)) estados.add(visto);
                if (e.fase() == Fase.TERMINADO) {
                    boolean conArchivos = !e.archivos().isEmpty();
                    pasos.add(new Paso(nombre, esperado, conArchivos, false, false, "estados vistos: " + String.join(" -> ", estados) + "; archivos: " + e.archivos().size()
                            + e.archivos().stream().map(a -> " [" + a.nombre() + ", tipo " + a.tipo() + ", proceso " + a.proceso() + "]").reduce("", String::concat)));
                    return conArchivos ? e : null;
                }
                if (e.fase() == Fase.CON_ERRORES) {
                    pasos.add(new Paso(nombre, esperado, false, false, false, "SUNAT terminó con errores. Estados vistos: " + String.join(" -> ", estados)));
                    return null;
                }
                if (Instant.now().isAfter(limite)) {
                    pasos.add(new Paso(nombre, esperado, false, false, false, "SUNAT no terminó en " + d.espera().toSeconds() + " s. Estados vistos: " + String.join(" -> ", estados)
                            + ". Suba SIRE_ESPERA_SEGUNDOS si el periodo es grande"));
                    return null;
                }
                try {
                    Thread.sleep(d.intervalo().toMillis());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    pasos.add(new Paso(nombre, esperado, false, false, false, "interrumpida"));
                    return null;
                }
            }
        } catch (RuntimeException e) {
            pasos.add(new Paso(nombre, esperado, false, false, false, CorridaDeValidez.describir(e) + ". Estados vistos: " + String.join(" -> ", estados)));
            return null;
        }
    }

    /** Los bytes de cada archivo, o nulo si alguno no se pudo bajar. */
    private static List<byte[]> bajar(List<Paso> pasos, RestSireGateway g, Tenant empresa, EstadoDelTicket fin) {
        String nombre = "bajar cada archivo de la propuesta";
        String esperado = "cada archivo llega completo y es un ZIP con un .txt";
        List<byte[]> bytes = new ArrayList<>();
        StringBuilder detalle = new StringBuilder();
        try {
            for (ArchivoDelTicket a : fin.archivos()) {
                byte[] b = g.descargar(empresa, a, 20L * 1024 * 1024);
                bytes.add(b);
                String contenido = contenidoDelZip(b);
                detalle.append(detalle.isEmpty() ? "" : "; ").append(a.nombre()).append(": ").append(b.length).append(" bytes, ").append(contenido);
            }
            boolean zips = bytes.stream().allMatch(CorridaDeSire::esZip);
            pasos.add(new Paso(nombre, esperado, zips, false, false, detalle.toString()));
            return zips ? bytes : null;
        } catch (RuntimeException e) {
            pasos.add(new Paso(nombre, esperado, false, false, false, CorridaDeValidez.describir(e)));
            return null;
        }
    }

    /**
     * Lee lo bajado con el mismo lector que usa la conciliación ({@link PropuestaRvie}) y dice cuántas filas leyó, si la primera línea era de títulos y cuántos campos
     * traen las filas. No imprime ninguna fila: son los datos de los clientes de la empresa. Es el paso que confirma o desmiente el supuesto de las 40 columnas.
     */
    private static Paso leer(List<byte[]> archivos, Datos d, PeriodoSire periodo) {
        String nombre = "leer la propuesta con el lector de la conciliación de khipu";
        String esperado = "todas las filas se leen (RUC, periodo, tipo, serie, número, fecha, importes), con 40 campos";
        PropuestaRvie lector = new PropuestaRvie(d.ruc(), periodo);
        long filas = 0, lineas = 0;
        Integer camposDeLaPrimeraFila = null;
        boolean titulos = false;
        try {
            for (byte[] b : archivos) {
                String texto = texto(b);
                long n = 0;
                for (String linea : texto.split("\n", -1)) {
                    n++;
                    lineas++;
                    var fila = lector.leer(linea, n);
                    if (fila != null) {
                        filas++;
                        if (camposDeLaPrimeraFila == null) camposDeLaPrimeraFila = linea.split("\\|", -1).length;
                    } else if (n == 1 && !linea.isBlank() && !linea.strip().matches("\\d{11}\\|.*")) {
                        titulos = true;
                    }
                }
            }
            boolean cumple = filas > 0 || lector.consolidadas() > 0;
            return new Paso(nombre, esperado, cumple, false, false, filas + " fila(s) leída(s) de " + lineas + " línea(s); consolidadas (campo 10) " + lector.consolidadas()
                    + "; fila de títulos: " + (titulos ? "sí" : "no") + "; campos separados por | en la primera fila: " + camposDeLaPrimeraFila + " (khipu escribe 40 y un | final)");
        } catch (DomainException e) {
            return new Paso(nombre, esperado, false, false, false, "no se pudo leer: " + CorridaDeValidez.acotar(e.getMessage()).replaceAll("«[^»]*»", "«…»")
                    + " (el formato real difiere del supuesto: corrija PropuestaRvie y FormatoRvie)");
        } catch (IOException | RuntimeException e) {
            return new Paso(nombre, esperado, false, false, false, CorridaDeValidez.describir(e instanceof RuntimeException r ? r : new IllegalStateException(e)));
        }
    }

    // ---- armado ---------------------------------------------------------------------------------------------------------------------------------------------------------

    private static boolean esZip(byte[] b) { return b != null && b.length >= 4 && b[0] == 'P' && b[1] == 'K' && b[2] == 3 && b[3] == 4; }

    /** Los nombres de lo que trae el ZIP, sin su contenido. */
    private static String contenidoDelZip(byte[] b) {
        if (!esZip(b)) return "no es un ZIP";
        List<String> nombres = new ArrayList<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(b), StandardCharsets.UTF_8)) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) nombres.add(e.getName());
        } catch (IOException e) {
            return "ZIP dañado";
        }
        return "ZIP con " + nombres;
    }

    /** El primer {@code .txt} del ZIP, o el archivo mismo si es texto. */
    private static String texto(byte[] b) throws IOException {
        if (!esZip(b)) return new String(b, StandardCharsets.UTF_8);
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(b), StandardCharsets.UTF_8)) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; )
                if (!e.isDirectory() && e.getName().toLowerCase(Locale.ROOT).endsWith(".txt")) return new String(z.readAllBytes(), StandardCharsets.UTF_8).replace("\r", "");
        }
        throw new DomainException("RVIE_ARCHIVO_INVALIDO", "el ZIP no trae ningún .txt");
    }

    private static ClienteApiSunat cliente() { return new ClienteApiSunat(Duration.ofSeconds(10), Duration.ofSeconds(30), Clock.systemUTC()); }

    private static RestSireGateway gateway(Datos d) {
        UrlPorEntorno seguridad = new UrlPorEntorno(d.urlSeguridad(), d.urlSeguridad());
        return new RestSireGateway(true, seguridad, new UrlPorEntorno(d.urlApi(), d.urlApi()), cliente());
    }

    private static Tenant empresa(Datos d, String clientId, String clientSecret) {
        String ruc = d.conRuc() ? d.ruc() : "20100066603";
        String usuario = d.usuarioSol().isBlank() ? "USUARIO1" : d.usuarioSol();
        String clave = d.claveSol().isBlank() ? "clave-sol" : d.claveSol();
        return new Tenant(UUID.randomUUID(), ruc, "EMPRESA DE LA CORRIDA", Entorno.PRODUCCION, new CredencialesSol(usuario, clave), null)
                .conCredencialesApiSunat(new CredencialesApiSunat(clientId, clientSecret));
    }

    /** La evidencia de la corrida: qué se preguntó, qué se esperaba y qué contestó SUNAT. Sin secretos, tokens ni filas de la propuesta. */
    static String resumen(Datos d, List<Paso> pasos, Instant cuando) {
        StringBuilder sb = new StringBuilder("# Corrida controlada de la propuesta del RVIE por la API del SIRE (#322)\n\n");
        sb.append("Fecha: ").append(cuando).append("\n\n");
        sb.append("- Servicio de token: ").append(d.urlSeguridad()).append("\n");
        sb.append("- API del SIRE: ").append(d.urlApi()).append("\n");
        sb.append("- Empresa (RUC): ").append(d.conRuc() ? d.ruc() : "(sin indicar)").append("\n");
        sb.append("- Usuario SOL: ").append(d.usuarioSol().isBlank() ? "(sin indicar)" : "indicado").append("\n");
        sb.append("- Aplicación (Id): ").append(CorridaDeValidez.enmascarado(d.clientId())).append("\n");
        sb.append("- Periodo: ").append(d.periodo().isBlank() ? "(sin indicar)" : d.periodo()).append("\n");
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
