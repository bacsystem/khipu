package pe.factura.adapters.sunatrest;

import com.fasterxml.jackson.databind.JsonNode;
import pe.factura.application.port.out.SunatSireGateway;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.DomainException;
import pe.factura.domain.sire.PeriodoSire;
import pe.factura.domain.tenant.CredencialesApiSunat;
import pe.factura.domain.tenant.Tenant;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * La propuesta del RVIE del SIRE por la API REST de SUNAT (#322), según el «Manual del servicio API del Registro de Ventas e Información Electrónica» v31
 * (cpe.sunat.gob.pe, 2026-09):
 * <ul>
 *   <li>Token (§1 y 5.1): {@code POST <seguridad>/v1/clientessol/<client_id>/oauth2/token/}, grant {@code password}, scope {@value #SCOPE}, {@code username} = RUC +
 *       usuario SOL, {@code password} = clave SOL. La aplicación se registra en SOL como «MIGE RCE y RVIE - SIRE».</li>
 *   <li>Pedir la propuesta (5.18): {@code GET <api>/v1/contribuyente/migeigv/libros/rvie/propuesta/web/propuesta/{AAAAMM}/exportapropuesta?codTipoArchivo=0} (0 es
 *       texto). Responde {@code numTicket}.</li>
 *   <li>Consultar el ticket (5.16): {@code GET <api>/v1/contribuyente/migeigv/libros/rvierce/gestionprocesosmasivos/web/masivo/consultaestadotickets}. Su
 *       {@code registros[]} trae {@code codEstadoProceso} (Anexo III: 01 cargado, 02 validando, 03 procesado con errores, 04 procesado sin errores, 05 en proceso, 06
 *       terminado) y, ya terminado, {@code archivoReporte[]} con el nombre de cada archivo.</li>
 *   <li>Descargar un archivo (5.17): {@code GET …/masivo/archivoreporte} con los datos que dio la consulta del ticket. Responde el ZIP.</li>
 * </ul>
 * Lo que el manual deja ambiguo y aquí se supone, sin haberse probado contra SUNAT (no hay credenciales API de prueba; ver deploy/README.md §15 y la corrida
 * controlada {@code :adapters:out-sunat-rest:sireReal}): que {@code codLibro=140000} y {@code codOrigenEnvio=2} van también en la consulta del ticket (la tabla de
 * parámetros los pide, el ejemplo no los trae); que 04 y 06 son los estados en que ya se baja el archivo; y el nombre {@code codTipoAchivoReporte} (con esa errata,
 * como lo escribe el manual) o {@code codTipoArchivoReporte} para el tipo.
 *
 * <p>SUNAT no publica una instancia de pruebas del SIRE y todo esto lee datos reales, así que la API de beta es la misma que la de producción (configurable). Está
 * apagado por defecto ({@code SUNAT_API_SIRE_HABILITADA}) hasta que el dueño haga la corrida controlada.
 */
public class RestSireGateway implements SunatSireGateway {
    static final String SCOPE = "https://api-sire.sunat.gob.pe";
    static final String COD_LIBRO_RVIE = "140000";
    /** Código de origen de envío «servicio web» (manual, 5.16). */
    static final String ORIGEN_SERVICIO_WEB = "2";
    private static final String RUTA = "/v1/contribuyente/migeigv/libros/";
    /** Los tickets caben en una página: se consulta uno por su número. */
    private static final int POR_PAGINA = 20;

    private final boolean habilitada;
    private final UrlPorEntorno seguridad;
    private final UrlPorEntorno api;
    private final ClienteApiSunat cliente;

    public RestSireGateway(boolean habilitada, UrlPorEntorno seguridad, UrlPorEntorno api, ClienteApiSunat cliente) {
        this.habilitada = habilitada; this.seguridad = seguridad; this.api = api; this.cliente = cliente;
    }

    @Override public boolean disponible() { return habilitada; }

    /** El token del grant password: credenciales API SUNAT más el usuario (RUC + usuario) y la clave SOL. */
    private PedidoDeToken token(Tenant t) {
        if (!habilitada)
            throw new DomainException("SIRE_NO_DISPONIBLE", "La conexión con el SIRE está apagada en este servidor (SUNAT_API_SIRE_HABILITADA): el contrato con SUNAT aún no se verificó. "
                    + "Descargue la propuesta del SIRE y use la conciliación con el archivo");
        CredencialesApiSunat c = t.apiSunat();
        if (c == null) throw new DomainException("CREDENCIALES_API_SUNAT_NO_CARGADAS", "La empresa no tiene credenciales API SUNAT (client_id y client_secret): el SIRE las necesita");
        if (t.sol() == null) throw new DomainException("CREDENCIALES_SOL_NO_CARGADAS", "La empresa no tiene credenciales SOL: el SIRE las necesita junto con las credenciales API SUNAT");
        return PedidoDeToken.password(seguridad.para(t.entorno()) + "/v1/clientessol/" + c.clientId() + "/oauth2/token/", SCOPE, c.clientId(), c.clientSecret(),
                t.sol().usernameToken(t.ruc()), t.sol().clave());
    }

    @Override public String solicitarPropuesta(Tenant t, PeriodoSire periodo) {
        PedidoDeToken p = token(t);
        String url = api.para(t.entorno()) + RUTA + "rvie/propuesta/web/propuesta/" + periodo.aaaamm() + "/exportapropuesta?codTipoArchivo=0";
        JsonNode r = cliente.getJson(p, url, ClienteApiSunat.CodigoDeRechazo.DEL_DETALLE);
        String ticket = ClienteApiSunat.texto(r, "numTicket");
        if (ticket.isBlank()) throw new SunatTransientException("0000", "SUNAT aceptó el pedido de la propuesta " + periodo + " pero no devolvió numTicket");
        return ticket;
    }

    @Override public EstadoDelTicket consultar(Tenant t, PeriodoSire periodo, String ticket) {
        exigirTicket(ticket);
        PedidoDeToken p = token(t);
        String url = api.para(t.entorno()) + RUTA + "rvierce/gestionprocesosmasivos/web/masivo/consultaestadotickets?perIni=" + periodo.aaaamm() + "&perFin=" + periodo.aaaamm()
                + "&page=1&perPage=" + POR_PAGINA + "&numTicket=" + enc(ticket) + "&codLibro=" + COD_LIBRO_RVIE + "&codOrigenEnvio=" + ORIGEN_SERVICIO_WEB;
        JsonNode r = cliente.getJson(p, url, ClienteApiSunat.CodigoDeRechazo.DEL_DETALLE);
        JsonNode registro = null;
        for (JsonNode x : r.path("registros")) {
            if (ticket.equals(ClienteApiSunat.texto(x, "numTicket"))) { registro = x; break; }
        }
        if (registro == null)
            throw new DomainException("NO_ENCONTRADO", "SUNAT no tiene el ticket " + ticket + " en el periodo " + periodo + ": revise el número y el periodo");
        String codigo = ClienteApiSunat.texto(registro, "codEstadoProceso");
        String descripcion = p.sinSecretos(ClienteApiSunat.texto(registro, "desEstadoProceso"));
        Fase fase = switch (codigo) {
            case "01", "02", "05" -> Fase.EN_PROCESO;
            case "03" -> Fase.CON_ERRORES;
            case "04", "06" -> Fase.TERMINADO;
            default -> throw new SunatTransientException("0000", "SUNAT informó para el ticket " + ticket + " un estado que no conozco (" + (codigo.isBlank() ? "vacío" : codigo) + ")");
        };
        List<ArchivoDelTicket> archivos = new ArrayList<>();
        if (fase == Fase.TERMINADO) {
            String proceso = ClienteApiSunat.texto(registro, "codProceso");
            String per = ClienteApiSunat.texto(registro, "perTributario");
            for (JsonNode a : registro.path("archivoReporte")) {
                String nombre = ClienteApiSunat.texto(a, "nomArchivoReporte");
                if (nombre.isBlank()) continue;
                String tipo = ClienteApiSunat.texto(a, "codTipoAchivoReporte", "codTipoArchivoReporte");
                archivos.add(new ArchivoDelTicket(ticket, nombre, tipo.isBlank() ? null : tipo, proceso, per.isBlank() ? periodo.aaaamm() : per));
            }
        }
        return new EstadoDelTicket(ticket, fase, codigo, descripcion, archivos);
    }

    @Override public byte[] descargar(Tenant t, ArchivoDelTicket a, long maximoBytes) {
        exigirTicket(a.ticket());
        PedidoDeToken p = token(t);
        String url = api.para(t.entorno()) + RUTA + "rvierce/gestionprocesosmasivos/web/masivo/archivoreporte?nomArchivoReporte=" + enc(a.nombre())
                // Si SUNAT mandó el tipo nulo, el manual pide mandarlo nulo: el texto «null».
                + "&codTipoArchivoReporte=" + (a.tipo() == null ? "null" : enc(a.tipo())) + "&perTributario=" + enc(a.periodo()) + "&codProceso=" + enc(a.proceso())
                + "&numTicket=" + enc(a.ticket()) + "&codLibro=" + COD_LIBRO_RVIE;
        try {
            return cliente.descargar(p, url, maximoBytes);
        } catch (TransporteHttp.ArchivoDemasiadoGrande e) {
            throw new DomainException("RVIE_PROPUESTA_DEMASIADO_GRANDE", "Un archivo de la propuesta de SUNAT pasa de los " + maximoBytes / (1024 * 1024) + " MB que khipu concilia");
        }
    }

    /** El ticket va en la query y en lo que SUNAT devuelve (un número como 202600000123): solo letras, dígitos y guiones, para que un valor raro no cambie la consulta. */
    private static void exigirTicket(String ticket) {
        if (ticket == null || !ticket.matches("[0-9A-Za-z-]{1,40}")) throw new DomainException("PARAMETRO_INVALIDO", "ticket: debe tener solo letras, dígitos y guiones (hasta 40)");
    }

    private static String enc(String v) { return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8); }
}
