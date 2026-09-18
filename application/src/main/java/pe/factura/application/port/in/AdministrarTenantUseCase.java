package pe.factura.application.port.in;

import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.ApiKey;
import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Serie;
import pe.factura.domain.tenant.Tenant;

import java.util.List;
import java.util.UUID;

public interface AdministrarTenantUseCase {
    record TenantCreado(Tenant tenant, String apiKeyEnClaro) {}
    TenantCreado crearTenant(String ruc, String razonSocial, Entorno entorno);
    Tenant obtener(UUID tenantId);
    /** Domicilio fiscal (RegistrationAddress del XML) y cuenta de detracciones por defecto; cualquiera puede ir en null para borrarlo. */
    Tenant actualizarDatosFiscales(UUID tenantId, Domicilio domicilio, String cuentaDetracciones, String nombreComercial);
    void cargarCertificado(UUID tenantId, byte[] pkcs12, String clave);   // valida abriendo el KeyStore (RUC en OU y vigencia)
    void cargarCredencialesSol(UUID tenantId, String usuario, String clave);
    void crearSerie(UUID tenantId, TipoDocumento tipo, String codigo, long correlativoInicial);
    List<Serie> listarSeries(UUID tenantId);
    String crearApiKey(UUID tenantId);                                   // devuelve la key en claro una sola vez
    List<ApiKey> listarApiKeys(UUID tenantId);                           // nunca el secreto: solo prefijo, estado y fechas
    void revocarApiKey(UUID tenantId, UUID apiKeyId);                    // NO_ENCONTRADO si no existe o es de otro tenant
}
