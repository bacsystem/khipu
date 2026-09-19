package pe.factura.application.port.in;

import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.ApiKey;
import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Establecimiento;
import pe.factura.domain.tenant.Serie;
import pe.factura.domain.tenant.Tenant;

import java.util.List;
import java.util.UUID;

public interface AdministrarTenantUseCase {
    record TenantCreado(Tenant tenant, String apiKeyEnClaro) {}
    TenantCreado crearTenant(String ruc, String razonSocial, Entorno entorno);
    Tenant obtener(UUID tenantId);
    /** Domicilio fiscal (RegistrationAddress del XML) y cuenta de detracciones por defecto; cualquiera puede ir en null para borrarlo. */
    Tenant actualizarDatosFiscales(UUID tenantId, Domicilio domicilio, String cuentaDetracciones, String nombreComercial, boolean padronTasaEspecialIgv);
    void cargarCertificado(UUID tenantId, byte[] pkcs12, String clave);   // valida abriendo el KeyStore (RUC en OU y vigencia)
    void cargarCredencialesSol(UUID tenantId, String usuario, String clave);
    void crearSerie(UUID tenantId, TipoDocumento tipo, String codigo, long correlativoInicial);
    /** {@code establecimiento}: código del anexo desde el que emite la serie (nulo o 0000 = domicilio fiscal); debe existir y estar activo. */
    void crearSerie(UUID tenantId, TipoDocumento tipo, String codigo, long correlativoInicial, String establecimiento);
    List<Serie> listarSeries(UUID tenantId);
    /** Anexos de la empresa (activos e inactivos); el 0000 es el domicilio fiscal y no aparece aquí. */
    List<Establecimiento> listarEstablecimientos(UUID tenantId);
    /** Alta o edición por código; DUPLICADO no aplica: el mismo código reemplaza nombre y domicilio. */
    Establecimiento guardarEstablecimiento(UUID tenantId, String codigo, String nombre, Domicilio domicilio);
    /** Baja lógica; ESTABLECIMIENTO_EN_USO si alguna serie activa emite desde él. */
    Establecimiento desactivarEstablecimiento(UUID tenantId, String codigo);
    String crearApiKey(UUID tenantId);                                   // devuelve la key en claro una sola vez
    List<ApiKey> listarApiKeys(UUID tenantId);                           // nunca el secreto: solo prefijo, estado y fechas
    void revocarApiKey(UUID tenantId, UUID apiKeyId);                    // NO_ENCONTRADO si no existe o es de otro tenant
}
