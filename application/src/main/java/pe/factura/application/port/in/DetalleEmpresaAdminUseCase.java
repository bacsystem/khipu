package pe.factura.application.port.in;

import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EstadoCertificado;
import pe.factura.domain.tenant.Entorno;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Detalle de una empresa para el backoffice (#186): lo mismo que ve su dueño, para diagnosticar sin riesgo de cambiarle nada. Lectura pura,
 * sin acciones (llegan en su propio issue) y sin secretos: del certificado y de la clave SOL solo se dice si están y hasta cuándo vale el
 * certificado; de las API keys, solo su prefijo; del logo, solo si hay uno. No se audita (la bitácora de #178 registra acciones, no
 * consultas). «Hoy» es el de Lima, como en el listado (#185).
 */
public interface DetalleEmpresaAdminUseCase {
    /** {@code NO_ENCONTRADO} si la empresa no existe. */
    EmpresaDetalle detalle(UUID empresaId);

    /**
     * {@code cuentaId} y {@code cuentaNombre} son nulos en las empresas dadas de alta por una integración. El estado del certificado es el
     * mismo del listado: una sola regla. {@code domicilio} es nulo si la empresa todavía no declaró el suyo.
     */
    record EmpresaDetalle(UUID id, String ruc, String razonSocial, String nombreComercial, Entorno entorno, Instant creadaEn,
                          UUID cuentaId, String cuentaNombre,
                          EstadoCertificado certificado, LocalDate certificadoVigenteHasta, Integer certificadoDiasRestantes, boolean tieneCredencialesSol,
                          DomicilioDeEmpresa domicilio, String cuentaDetracciones, boolean padronTasaEspecialIgv, PdfDeEmpresa pdf,
                          List<SerieDeEmpresa> series, List<EstablecimientoDeEmpresa> establecimientos, List<ApiKeyDeEmpresa> apiKeys,
                          List<ComprobanteReciente> comprobantes, List<EventoDeComprobante> eventos, Outbox outbox) {}

    /** El domicilio fiscal (con su código de establecimiento, normalmente 0000) o el de un establecimiento anexo (con su código). */
    record DomicilioDeEmpresa(String ubigeo, String direccion, String urbanizacion, String distrito, String provincia, String departamento,
                              String codigoEstablecimiento) {}

    /** La personalización del PDF; {@code tieneLogo} dice si hay uno cargado, sin exponer dónde está guardado. */
    record PdfDeEmpresa(String plantilla, String colorPrimario, boolean tieneLogo, String pieDePagina, String observacionesPorDefecto) {}

    record SerieDeEmpresa(String tipo, String codigo, long ultimoNumero, boolean activa, String establecimiento) {}

    record EstablecimientoDeEmpresa(String nombre, DomicilioDeEmpresa domicilio, boolean activo) {}

    /** Una API key sin su secreto ni su hash: solo el prefijo con el que el dueño la reconoce. {@code revocadaEn} es nulo si sigue vigente. */
    record ApiKeyDeEmpresa(UUID id, String prefijo, boolean activa, Instant creadaEn, Instant revocadaEn) {}

    /** El CDR de SUNAT de un comprobante: código, descripción y observaciones (puede no haber ninguna). */
    record Cdr(String codigo, String descripcion, List<String> observaciones) {}

    /** {@code cdr} es nulo si SUNAT todavía no respondió; {@code ultimoError}, si el último intento no falló. */
    record ComprobanteReciente(UUID id, String tipo, String serie, long numero, LocalDate fechaEmision, String estado, String moneda, BigDecimal total,
                               int intentos, String ultimoError, Cdr cdr) {}

    /** Un cambio de estado de uno de los comprobantes recientes, del más reciente al más antiguo. */
    record EventoDeComprobante(String serie, long numero, String estadoAnterior, String estadoNuevo, String detalle, Instant ocurridoEn) {}

    /** Lo que el outbox todavía tiene pendiente de esta empresa: el total y las próximas tareas. */
    record Outbox(long total, List<TareaPendiente> proximas) {}

    record TareaPendiente(String agregado, UUID agregadoId, String accion, int intentos, Instant siguienteIntento, String ultimoError) {}
}
