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
    private final EstablecimientoRepository establecimientos;


    public TenantCreado crearTenant(String ruc, String razonSocial, Entorno entorno) {
        Ruc.exigirValido(ruc, "RUC_INVALIDO", "Empresa");
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

    public Tenant actualizarDatosFiscales(UUID tenantId, Domicilio domicilio, String cuentaDetracciones, String nombreComercial, boolean padronTasaEspecialIgv) {
        Tenant t = obtener(tenantId).conDatosFiscales(domicilio, cuentaDetracciones, nombreComercial, padronTasaEspecialIgv);
        uow.ejecutar(() -> tenants.guardar(t));
        return t;
    }

    /**
     * Rechaza el certificado que todavía no entró en vigencia, no solo el vencido. Una CA suele emitir la renovación
     * con {@code notBefore} en la fecha en que expira el anterior, así que un certificado que llega hoy puede recién
     * servir el mes que viene. Firmar antes de esa fecha hace que SUNAT devuelva 2327 («El certificado usado no se
     * encuentra vigente») con el correlativo ya consumido: la numeración se asigna y se bloquea antes de firmar.
     */
    public void cargarCertificado(UUID tenantId, byte[] pkcs12, String clave) {
        Tenant t = obtener(tenantId);
        LocalDate vigencia;
        Instant desde;
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(pkcs12), clave.toCharArray());
            String alias = aliasConClavePrivada(ks);
            X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
            if (ks.getKey(alias, clave.toCharArray()) == null) throw new IllegalStateException("sin clave privada");
            String subject = cert.getSubjectX500Principal().getName();
            if (!ouContieneRuc(subject, t.ruc())) throw new DomainException("CERTIFICADO_INVALIDO", "El RUC " + t.ruc() + " no figura en el campo OU del certificado");
            vigencia = cert.getNotAfter().toInstant().atZone(ZoneId.of("America/Lima")).toLocalDate();
            desde = cert.getNotBefore().toInstant();
        } catch (DomainException e) { throw e;
        } catch (Exception e) { throw new DomainException("CERTIFICADO_INVALIDO", "No se pudo abrir el PKCS#12: " + e.getMessage(), e); }
        if (vigencia.isBefore(LocalDate.now(clock))) throw new DomainException("CERTIFICADO_VENCIDO", "El certificado venció el " + vigencia);
        // Se compara el instante, no la fecha: un certificado que arranca hoy a las 15:00 no sirve para firmar a las 08:00.
        if (desde.isAfter(clock.instant()))
            throw new DomainException("CERTIFICADO_NO_VIGENTE",
                    "2327 - El certificado recién entra en vigencia el " + desde.atZone(ZoneId.of("America/Lima")).toLocalDate()
                            + "; SUNAT rechaza todo lo que se firme antes de esa fecha");
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

    /** La validación y la normalización del usuario viven en {@link CredencialesSol}: así valen para cualquier camino. */
    public void cargarCredencialesSol(UUID tenantId, String usuario, String clave) {
        Tenant t = obtener(tenantId);
        CredencialesSol sol = new CredencialesSol(usuario, clave);
        uow.ejecutar(() -> tenants.guardar(t.conCredencialesSol(sol)));
    }

    public void crearSerie(UUID tenantId, TipoDocumento tipo, String codigo, long correlativoInicial) {
        crearSerie(tenantId, tipo, codigo, correlativoInicial, null);
    }

    public void crearSerie(UUID tenantId, TipoDocumento tipo, String codigo, long correlativoInicial, String establecimiento) {
        obtener(tenantId);
        if (!tipo.serieValida(codigo)) throw new DomainException("SERIE_INVALIDA", "Serie " + codigo + " no válida para " + tipo);
        Serie s = new Serie(tenantId, tipo, codigo, correlativoInicial, true, establecimiento);
        uow.ejecutar(() -> {
            // Bloquea la fila del anexo para que no se pueda dar de baja entre este chequeo y el INSERT (desactivarEstablecimiento hace el mismo bloqueo).
            if (!s.enDomicilioFiscal()) {
                Establecimiento e = establecimientos.buscarConBloqueo(tenantId, s.establecimiento())
                        .orElseThrow(() -> new DomainException("ESTABLECIMIENTO_INVALIDO", "El establecimiento " + s.establecimiento() + " no existe en la empresa: regístrelo antes de asignarle una serie"));
                if (!e.activo()) throw new DomainException("ESTABLECIMIENTO_INVALIDO", "El establecimiento " + s.establecimiento() + " (" + e.nombre() + ") está dado de baja");
            }
            series.crear(s);
        });
    }

    public List<Serie> listarSeries(UUID tenantId) { return series.listar(tenantId); }

    public List<Establecimiento> listarEstablecimientos(UUID tenantId) { obtener(tenantId); return establecimientos.listar(tenantId); }

    public Establecimiento guardarEstablecimiento(UUID tenantId, String codigo, String nombre, Domicilio domicilio) {
        obtener(tenantId);
        boolean activo = establecimientos.buscar(tenantId, codigo == null ? "" : codigo.strip()).map(Establecimiento::activo).orElse(true);
        Establecimiento e = new Establecimiento(tenantId, codigo, nombre, domicilio, activo);
        uow.ejecutar(() -> establecimientos.guardar(e));
        return e;
    }

    public Establecimiento desactivarEstablecimiento(UUID tenantId, String codigo) {
        obtener(tenantId);
        // Mismo bloqueo de fila que crearSerie: si una serie se está creando contra este anexo, esta baja espera a que termine esa transacción.
        return uow.ejecutar(() -> {
            Establecimiento e = establecimientos.buscarConBloqueo(tenantId, codigo).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Establecimiento " + codigo + " no encontrado"));
            List<String> enUso = series.listar(tenantId).stream().filter(s -> s.activa() && s.establecimiento().equals(e.codigo())).map(Serie::codigo).toList();
            if (!enUso.isEmpty())
                throw new DomainException("ESTABLECIMIENTO_EN_USO", "El establecimiento " + codigo + " tiene series activas (" + String.join(", ", enUso) + "): reasígnelas antes de darlo de baja");
            Establecimiento baja = e.desactivar();
            establecimientos.guardar(baja);
            return baja;
        });
    }

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
