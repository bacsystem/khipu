package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.AccionesDeEmpresaUseCase;
import pe.factura.application.port.out.AccionesDeEmpresaRepository;
import pe.factura.application.port.out.AccionesDeEmpresaRepository.ApiKeyDeEmpresa;
import pe.factura.application.port.out.AuditoriaAdminRepository;
import pe.factura.application.port.out.SunatBillingGateway;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.RegistroAuditoria;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class AccionesDeEmpresaService implements AccionesDeEmpresaUseCase {
    /** Un ticket que SUNAT no tiene: la consulta es de solo lectura, no envía ni cambia nada. */
    static final String TICKET_DE_PRUEBA = "0";

    private final TenantRepository tenants;
    private final AccionesDeEmpresaRepository acciones;
    private final SunatBillingGateway sunat;
    private final AuditoriaAdminRepository auditoria;
    private final UnitOfWork uow;
    private final Clock clock;

    @Override public CambioDeEntorno cambiarEntorno(ActorAdmin actor, UUID empresaId, Entorno hacia) {
        Entorno actual = exigirQueExista(empresaId);
        if (hacia == null) throw new DomainException("ENTORNO_INVALIDO", "El entorno es obligatorio");
        if (actual == hacia) throw sinCambios(hacia);
        // Lo que se emitió en un entorno no debe enviarse al otro: el outbox reintenta contra el entorno que la empresa tenga EN ESE MOMENTO.
        if (acciones.enviosPendientes(empresaId) > 0)
            throw new DomainException("EMPRESA_CON_ENVIOS_PENDIENTES", "La empresa tiene envíos pendientes a SUNAT: espera a que terminen antes de cambiar el entorno");
        Instant ahora = clock.instant();
        // Condicional (solo si seguía en el entorno que se vio) y en la misma transacción que la bitácora.
        uow.ejecutar(() -> {
            if (!acciones.cambiarEntorno(empresaId, actual, hacia)) throw sinCambios(hacia);
            auditoria.registrar(RegistroAuditoria.de(actor, AccionAdmin.CAMBIAR_ENTORNO_EMPRESA, cuentaDe(empresaId), empresaId, "desde=" + actual + " hacia=" + hacia, ahora));
        });
        return new CambioDeEntorno(empresaId, actual, hacia);
    }

    @Override public ApiKeyRevocada revocarApiKey(ActorAdmin actor, UUID empresaId, UUID apiKeyId) {
        exigirQueExista(empresaId);
        ApiKeyDeEmpresa key = apiKeyId == null ? null : acciones.apiKey(empresaId, apiKeyId).orElse(null);
        if (key == null) throw new DomainException("NO_ENCONTRADO", "La API key no existe");
        if (!key.activa()) throw yaRevocada();
        Instant ahora = clock.instant();
        uow.ejecutar(() -> {
            if (!acciones.revocarApiKey(empresaId, apiKeyId, ahora)) throw yaRevocada();
            auditoria.registrar(RegistroAuditoria.de(actor, AccionAdmin.REVOCAR_API_KEY_EMPRESA, cuentaDe(empresaId), empresaId, "prefijo=" + key.prefijo(), ahora));
        });
        return new ApiKeyRevocada(apiKeyId, ahora);
    }

    @Override public ResultadoDeConexion probarConexion(ActorAdmin actor, UUID empresaId) {
        Tenant t = empresaId == null ? null : tenants.buscar(empresaId).orElse(null);
        if (t == null) throw new DomainException("NO_ENCONTRADO", "La empresa no existe");
        if (t.sol() == null) throw new DomainException("SOL_NO_CARGADAS", "La empresa no tiene credenciales SOL cargadas");
        // Fuera de la transacción: la llamada a SUNAT es lenta y externa.
        ResultadoDeConexion r = consultar(t);
        String detalle = "entorno=" + r.entorno() + " resultado=" + r.resultado() + (r.codigo() == null ? "" : " codigo=" + r.codigo());
        uow.ejecutar(() -> auditoria.registrar(RegistroAuditoria.de(actor, AccionAdmin.PROBAR_CONEXION_EMPRESA, cuentaDe(empresaId), empresaId, detalle, clock.instant())));
        return r;
    }

    private ResultadoDeConexion consultar(Tenant t) {
        try {
            sunat.getStatus(t, TICKET_DE_PRUEBA);
            return new ResultadoDeConexion(Resultado.CONECTADO, t.entorno(), null, null);
        } catch (SunatRechazoException e) {
            return new ResultadoDeConexion(Resultado.RECHAZADO, t.entorno(), e.codigo(), e.descripcion());
        } catch (SunatTransientException e) {
            return new ResultadoDeConexion(Resultado.SIN_RESPUESTA, t.entorno(), e.codigo(), e.getMessage());
        }
    }

    private Entorno exigirQueExista(UUID empresaId) {
        if (empresaId == null) throw new DomainException("NO_ENCONTRADO", "La empresa no existe");
        return acciones.entornoDe(empresaId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "La empresa no existe"));
    }

    /** La cuenta dueña, para que la acción aparezca también en la bitácora de la cuenta; vacía en las empresas de integración. */
    private UUID cuentaDe(UUID empresaId) { return tenants.cuentaDe(empresaId).orElse(null); }

    private static DomainException sinCambios(Entorno entorno) { return new DomainException("ENTORNO_SIN_CAMBIOS", "La empresa ya está en " + entorno); }

    private static DomainException yaRevocada() { return new DomainException("API_KEY_YA_REVOCADA", "La API key ya estaba revocada"); }
}
