package pe.factura.application.service;

import lombok.extern.slf4j.Slf4j;
import pe.factura.application.port.in.ConsultarValidezUseCase;
import pe.factura.application.port.out.SunatApiValidezGateway;
import pe.factura.application.port.out.SunatConsultaGateway;
import pe.factura.application.port.out.SunatCredencialesException;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.tenant.Ruc;
import pe.factura.domain.tenant.Tenant;

import java.util.UUID;

/**
 * Validez de un comprobante por sus datos (#36): sirve para verificar lo que un tercero nos factura. Dos caminos (#318):
 * <ul>
 *   <li>Con credenciales API SUNAT, la consulta integrada por la API REST (OAuth2). No usa la clave SOL y no depende de que la empresa esté en producción.</li>
 *   <li>Con clave SOL, {@code billValidService} (SOAP), que SUNAT solo publica en producción.</li>
 * </ul>
 * Si la API REST no está disponible (sin respuesta, tiempo agotado, 5xx) o rechaza las credenciales API (401: no cambia nada preguntarle de nuevo, pero la
 * clave SOL sí puede servir) y hay clave SOL, se pregunta por SOAP y la respuesta lo dice en {@code aviso}. NO hay fallback cuando la API responde que el
 * pedido es inválido ({@link SunatRechazoException}: 400/422, {@code success: false}): es una respuesta de SUNAT sobre los datos de la consulta, y
 * preguntarlo por otro canal solo daría la misma respuesta con otro formato (o, peor, una distinta que confunda). Sin fecha de emisión la API no
 * consulta (es obligatoria allí): se va directo a SOAP si hay clave SOL.
 */
@Slf4j
public class ConsultarValidezService implements ConsultarValidezUseCase {
    private final TenantRepository tenants;
    private final SunatConsultaGateway soap;
    private final SunatApiValidezGateway api;

    public ConsultarValidezService(TenantRepository tenants, SunatConsultaGateway soap, SunatApiValidezGateway api) {
        this.tenants = tenants; this.soap = soap; this.api = api;
    }

    /** Solo SOAP: la forma de antes de #318. */
    public ConsultarValidezService(TenantRepository tenants, SunatConsultaGateway soap) { this(tenants, soap, null); }

    @Override
    public Validez consultar(UUID tenantId, Criterios q) {
        Tenant t = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
        boolean conApi = api != null && t.apiSunat() != null;
        if (!conApi && t.sol() == null)
            throw new DomainException("CREDENCIALES_SUNAT_NO_CARGADAS", "Para consultar la validez en SUNAT configure las credenciales API SUNAT (client_id y client_secret "
                    + "de «Credenciales de API SUNAT» en SOL) o las credenciales SOL de la empresa");
        if (!Ruc.formatoValido(q.rucEmisor())) throw new DomainException("PARAMETRO_INVALIDO", "ruc: 11 dígitos que empiezan por 10, 15, 16, 17 o 20");
        if (q.tipo() == null || !q.tipo().matches("01|03|07|08")) throw new DomainException("PARAMETRO_INVALIDO", "tipo: 01, 03, 07 u 08");
        if (q.serie() == null || !q.serie().matches("[A-Z0-9]{4}")) throw new DomainException("PARAMETRO_INVALIDO", "serie: 4 alfanuméricos (p. ej. F001)");
        if (q.numero() <= 0 || q.numero() > 99_999_999L) throw new DomainException("PARAMETRO_INVALIDO", "numero: entre 1 y 99999999");

        if (conApi) {
            if (q.fechaEmision() == null) {
                if (t.sol() == null) throw new DomainException("PARAMETRO_INVALIDO", "fecha: obligatoria para la consulta por la API de SUNAT (la empresa no tiene clave SOL para consultar sin ella)");
                return porSoap(t, q, "Sin fecha de emisión la API de SUNAT no consulta: se usó billValidService con la clave SOL");
            }
            try {
                return porApi(t, q);
            } catch (SunatRechazoException e) {
                throw new DomainException("CONSULTA_RECHAZADA", "SUNAT rechazó la consulta: " + e.getMessage());
            } catch (SunatTransientException e) {
                if (t.sol() == null) throw traducirApi(e);
                String aviso = (e instanceof SunatCredencialesException ? "SUNAT no aceptó las credenciales API SUNAT" : "La API de SUNAT no respondió")
                        + " (" + e.getMessage() + "): se usó billValidService con la clave SOL";
                log.warn("Validez de comprobante, empresa {}: {}", t.id(), aviso);
                try {
                    return porSoap(t, q, aviso);
                } catch (DomainException d) {
                    // En BETA billValidService no existe: el error útil es el de la API, no «no disponible en beta».
                    if ("NO_DISPONIBLE_EN_BETA".equals(d.codigo())) throw traducirApi(e);
                    throw d;
                }
            }
        }
        return porSoap(t, q, null);
    }

    private Validez porApi(Tenant t, Criterios q) {
        SunatApiValidezGateway.Validez r = api.validar(t, q.rucEmisor(), q.tipo(), q.serie(), q.numero(), q.fechaEmision(), q.importeTotal());
        return new Validez(estadoApi(r.estadoCp()), r.estadoCp(), mensajeApi(r.estadoCp()), Fuente.API_REST, r.estadoRuc(), r.condicionDomicilio(), r.observaciones(), null);
    }

    private Validez porSoap(Tenant t, Criterios q, String aviso) {
        SunatConsultaGateway.Consulta r;
        try {
            r = soap.validar(t, q.rucEmisor(), q.tipo(), q.serie(), q.numero(), q.tipoDocReceptor(), q.numDocReceptor(), q.fechaEmision(), q.importeTotal());
        } catch (SunatRechazoException e) {
            throw new DomainException("CONSULTA_RECHAZADA", "SUNAT rechazó la consulta: " + e.getMessage());
        } catch (SunatCredencialesException e) {
            throw new DomainException("CREDENCIALES_SOL_RECHAZADAS", "SUNAT no acepta las credenciales SOL de la empresa: " + e.getMessage());
        } catch (SunatTransientException e) {
            throw new DomainException("SUNAT_NO_DISPONIBLE", (aviso == null ? "" : aviso + "; ") + "billValidService no respondió (" + e.getMessage() + "): intente más tarde");
        }
        return new Validez(estado(r.statusCode()), r.statusCode(), r.statusMessage(), Fuente.SOAP, null, null, java.util.List.of(), aviso);
    }

    private static DomainException traducirApi(SunatTransientException e) {
        if (e instanceof SunatCredencialesException)
            return new DomainException("CREDENCIALES_API_SUNAT_RECHAZADAS", "SUNAT no acepta las credenciales API SUNAT de la empresa (" + e.getMessage()
                    + "): revise el client_id y el client_secret en SOL («Credenciales de API SUNAT») y vuelva a guardarlos");
        return new DomainException("SUNAT_NO_DISPONIBLE", "La API de SUNAT no respondió (" + e.getMessage() + "): intente más tarde");
    }

    /** Tabla de retorno de billConsultService/billValidService del Manual del Programador v2.1 (§ consulta): 0001–0003 éxito, resto error de consulta. */
    static String estado(String codigo) {
        return switch (codigo) {
            case "0001" -> "ACEPTADO";
            case "0002" -> "RECHAZADO";
            case "0003" -> "DE_BAJA";
            case "0011" -> "NO_EXISTE";
            case "0012" -> "AJENO";
            default -> "ERROR_CONSULTA";
        };
    }

    /**
     * {@code estadoCp} del anexo del «Manual de Consulta Integrada de Comprobante de Pago por Servicio Web» v2.0 de SUNAT. «Anulado» es el comprobante
     * comunicado en una baja: el mismo {@code DE_BAJA} que da billValidService.
     */
    static String estadoApi(String estadoCp) {
        return switch (estadoCp == null ? "" : estadoCp.strip()) {
            case "0" -> "NO_EXISTE";
            case "1" -> "ACEPTADO";
            case "2" -> "DE_BAJA";
            case "3" -> "AUTORIZADO";
            case "4" -> "NO_AUTORIZADO";
            default -> "ERROR_CONSULTA";
        };
    }

    private static String mensajeApi(String estadoCp) {
        return switch (estadoCp == null ? "" : estadoCp.strip()) {
            case "0" -> "No existe: SUNAT no tiene informado el comprobante";
            case "1" -> "Aceptado";
            case "2" -> "Anulado: comunicado en una baja";
            case "3" -> "Autorizado: comprobante físico con autorización de imprenta";
            case "4" -> "No autorizado por imprenta";
            default -> "SUNAT devolvió un estado desconocido (" + estadoCp + ")";
        };
    }
}
