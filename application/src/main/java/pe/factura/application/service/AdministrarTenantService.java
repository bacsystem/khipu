package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.AdministrarTenantUseCase;
import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.*;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class AdministrarTenantService implements AdministrarTenantUseCase {
    private final TenantRepository tenants;
    private final SerieRepository series;
    private final ApiKeyRepository apiKeys;
    private final UnitOfWork uow;
    private final String pepper;
    private final Clock clock;


    public TenantCreado crearTenant(String ruc, String razonSocial, Entorno entorno) {
        if (tenants.buscarPorRuc(ruc).isPresent()) throw new DomainException("DUPLICADO", "Ya existe un tenant con RUC " + ruc);
        Tenant t = new Tenant(UUID.randomUUID(), ruc, razonSocial, entorno, null, null);
        String key = ApiKeyGenerator.generar();
        uow.ejecutar(() -> {
            tenants.guardar(t);
            apiKeys.guardar(nuevaApiKey(t.id(), key));
        });
        return new TenantCreado(t, key);
    }

    public Tenant obtener(UUID tenantId) { return tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado")); }

    public Tenant actualizarDatosFiscales(UUID tenantId, Domicilio domicilio, String cuentaDetracciones, String nombreComercial) {
        Tenant t = obtener(tenantId).conDatosFiscales(domicilio, cuentaDetracciones, nombreComercial);
        uow.ejecutar(() -> tenants.guardar(t));
        return t;
    }

    public void cargarCertificado(UUID tenantId, byte[] pkcs12, String clave) {
        Tenant t = obtener(tenantId);
        LocalDate vigencia;
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(pkcs12), clave.toCharArray());
            String alias = aliasConClavePrivada(ks);
            X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
            if (ks.getKey(alias, clave.toCharArray()) == null) throw new IllegalStateException("sin clave privada");
            String subject = cert.getSubjectX500Principal().getName();
            if (!ouContieneRuc(subject, t.ruc())) throw new DomainException("CERTIFICADO_INVALIDO", "El RUC " + t.ruc() + " no figura en el campo OU del certificado");
            vigencia = cert.getNotAfter().toInstant().atZone(ZoneId.of("America/Lima")).toLocalDate();
        } catch (DomainException e) { throw e;
        } catch (Exception e) { throw new DomainException("CERTIFICADO_INVALIDO", "No se pudo abrir el PKCS#12: " + e.getMessage(), e); }
        if (vigencia.isBefore(LocalDate.now(clock))) throw new DomainException("CERTIFICADO_VENCIDO", "El certificado venció el " + vigencia);
        uow.ejecutar(() -> tenants.guardar(t.conCertificado(new CertificadoDigital(pkcs12, clave, vigencia))));
    }

    /** Un PKCS#12 puede traer entradas de solo certificado (CA) antes de la clave; se elige la primera con clave privada. */
    private static String aliasConClavePrivada(KeyStore ks) throws java.security.KeyStoreException {
        for (java.util.Enumeration<String> aliases = ks.aliases(); aliases.hasMoreElements(); ) {
            String alias = aliases.nextElement();
            if (ks.isKeyEntry(alias)) return alias;
        }
        throw new DomainException("CERTIFICADO_INVALIDO", "El PKCS#12 no contiene una clave privada");
    }

    public void cargarCredencialesSol(UUID tenantId, String usuario, String clave) {
        Tenant t = obtener(tenantId);
        if (usuario == null || usuario.isBlank() || clave == null || clave.isBlank()) throw new DomainException("CREDENCIALES_INVALIDAS", "Usuario y clave SOL son obligatorios");
        uow.ejecutar(() -> tenants.guardar(t.conCredencialesSol(new CredencialesSol(usuario, clave))));
    }

    public void crearSerie(UUID tenantId, TipoDocumento tipo, String codigo, long correlativoInicial) {
        obtener(tenantId);
        if (!tipo.serieValida(codigo)) throw new DomainException("SERIE_INVALIDA", "Serie " + codigo + " no válida para " + tipo);
        uow.ejecutar(() -> series.crear(new Serie(tenantId, tipo, codigo, correlativoInicial, true)));
    }

    public List<Serie> listarSeries(UUID tenantId) { return series.listar(tenantId); }

    public String crearApiKey(UUID tenantId) {
        obtener(tenantId);
        String key = ApiKeyGenerator.generar();
        uow.ejecutar(() -> apiKeys.guardar(nuevaApiKey(tenantId, key)));
        return key;
    }

    public List<ApiKey> listarApiKeys(UUID tenantId) { return apiKeys.listarPorTenant(tenantId); }

    public void revocarApiKey(UUID tenantId, UUID apiKeyId) {
        ApiKey k = apiKeys.buscar(apiKeyId).filter(x -> x.tenantId().equals(tenantId))
                .orElseThrow(() -> new DomainException("NO_ENCONTRADO", "API key no encontrada"));
        if (!k.activa()) return;
        uow.ejecutar(() -> apiKeys.guardar(k.revocar(Instant.now(clock))));
    }

    private ApiKey nuevaApiKey(UUID tenantId, String key) {
        return new ApiKey(UUID.randomUUID(), tenantId, ApiKeyGenerator.hash(key, pepper), ApiKeyGenerator.prefijo(key), true, Instant.now(clock), null);
    }

    static boolean ouContieneRuc(String subjectDn, String ruc) {
        try {
            LdapName dn = new LdapName(subjectDn);
            for (Rdn rdn : dn.getRdns()) {
                if (rdn.getType().equalsIgnoreCase("OU") && String.valueOf(rdn.getValue()).trim().equals(ruc)) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
