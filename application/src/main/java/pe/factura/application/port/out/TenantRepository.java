package pe.factura.application.port.out;

import pe.factura.domain.DomainException;
import pe.factura.domain.tenant.CertificadoDigital;
import pe.factura.domain.tenant.CredencialesApiSunat;
import pe.factura.domain.tenant.CredencialesSol;
import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.PersonalizacionPdf;
import pe.factura.domain.tenant.Tenant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * LOG-05: la empresa se crea entera ({@link #guardar}) y después se edita <b>por sección</b>, cada una con su método que toca solo sus columnas. Antes {@code guardar}
 * reescribía la fila completa con el objeto que el llamador había leído fuera de la transacción: unos datos fiscales guardados con un tenant leído antes de un
 * {@code cambiarEntorno} lo devolvían a BETA, y un certificado guardado con uno leído antes de que otro cargara las credenciales SOL las revertía. El entorno y la
 * designación de agente tienen sus propias escrituras condicionales ({@code AccionesDeEmpresaRepository}); {@code guardar} ya no los toca, ni a los secretos.
 */
public interface TenantRepository {
    /** Crea la empresa; sobre una existente actualiza solo la razón social, los datos fiscales y el diseño del PDF (nunca el entorno, los secretos ni la designación). */
    void guardar(Tenant t);
    /** Domicilio fiscal, cuenta de detracciones, nombre comercial y marca del padrón de tasa especial, sin tocar lo demás. {@code NO_ENCONTRADO} si no existe. */
    default void guardarDatosFiscales(UUID tenantId, Domicilio domicilio, String cuentaDetracciones, String nombreComercial, boolean padronTasaEspecialIgv) {
        actualizar(tenantId, t -> t.conDatosFiscales(domicilio, cuentaDetracciones, nombreComercial, padronTasaEspecialIgv));
    }
    default void guardarCertificado(UUID tenantId, CertificadoDigital certificado) { actualizar(tenantId, t -> t.conCertificado(certificado)); }
    default void guardarCredencialesSol(UUID tenantId, CredencialesSol sol) { actualizar(tenantId, t -> t.conCredencialesSol(sol)); }
    /** {@code null} las borra (#318). */
    default void guardarCredencialesApiSunat(UUID tenantId, CredencialesApiSunat apiSunat) { actualizar(tenantId, t -> t.conCredencialesApiSunat(apiSunat)); }
    default void guardarPersonalizacionPdf(UUID tenantId, PersonalizacionPdf personalizacion) { actualizar(tenantId, t -> t.conPersonalizacionPdf(personalizacion)); }
    /** Para las implementaciones en memoria (pruebas): leer en el momento de escribir y guardar entero. El adaptador JDBC escribe las columnas de la sección. */
    private void actualizar(UUID tenantId, UnaryOperator<Tenant> cambio) {
        guardar(buscar(tenantId).map(cambio).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado")));
    }
    Optional<Tenant> buscar(UUID id);
    Optional<Tenant> buscarPorRuc(String ruc);
    /** Empresas que pertenecen a una cuenta (portal). */
    List<Tenant> listarPorCuenta(UUID cuentaId);
    void asignarCuenta(UUID tenantId, UUID cuentaId);
    Optional<UUID> cuentaDe(UUID tenantId);
}
