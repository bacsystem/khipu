package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.ConsultarValidezUseCase;
import pe.factura.application.port.out.SunatConsultaGateway;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.tenant.Ruc;
import pe.factura.domain.tenant.Tenant;

import java.util.UUID;

/** Validez de un comprobante por sus datos (billValidService, #36): sirve para verificar lo que un tercero nos factura. */
@RequiredArgsConstructor
public class ConsultarValidezService implements ConsultarValidezUseCase {
    private final TenantRepository tenants;
    private final SunatConsultaGateway consultas;

    @Override
    public Validez consultar(UUID tenantId, Criterios q) {
        Tenant t = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
        t.exigirCredencialesSol();
        if (!Ruc.formatoValido(q.rucEmisor())) throw new DomainException("PARAMETRO_INVALIDO", "ruc: 11 dígitos que empiezan por 10, 15, 16, 17 o 20");
        if (q.tipo() == null || !q.tipo().matches("01|03|07|08")) throw new DomainException("PARAMETRO_INVALIDO", "tipo: 01, 03, 07 u 08");
        if (q.serie() == null || !q.serie().matches("[A-Z0-9]{4}")) throw new DomainException("PARAMETRO_INVALIDO", "serie: 4 alfanuméricos (p. ej. F001)");
        if (q.numero() <= 0) throw new DomainException("PARAMETRO_INVALIDO", "numero: mayor que cero");
        SunatConsultaGateway.Consulta r = consultas.validar(t, q.rucEmisor(), q.tipo(), q.serie(), q.numero(), q.tipoDocReceptor(), q.numDocReceptor(), q.fechaEmision(), q.importeTotal());
        return new Validez(estado(r.statusCode()), r.statusCode(), r.statusMessage());
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
}
