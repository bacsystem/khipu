package pe.factura.application.service;

import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;
import pe.factura.domain.tenant.*;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;

final class Fakes {
    static final class Comprobantes implements ComprobanteRepository {
        final Map<UUID, Comprobante> datos = new HashMap<>();
        public void guardar(Comprobante c) { datos.put(c.id(), c); }
        public Optional<Comprobante> buscar(UUID t, UUID id) { return Optional.ofNullable(datos.get(id)).filter(c -> c.tenantId().equals(t)); }
        public boolean existe(UUID t, TipoDocumento tipo, String serie, long numero) {
            return datos.values().stream().anyMatch(c -> c.tenantId().equals(t) && c.tipo() == tipo && c.serie().equals(serie) && Long.valueOf(numero).equals(c.numero()));
        }
        public List<Comprobante> listar(UUID t, EstadoDocumento e, int p, int pp) { return datos.values().stream().filter(c -> c.tenantId().equals(t)).toList(); }
    }
    static final class Series implements SerieRepository {
        final Map<String, Long> ultimo = new HashMap<>();
        public long siguienteNumero(UUID t, TipoDocumento tipo, String serie) {
            String k = t + tipo.codigo() + serie;
            if (!ultimo.containsKey(k)) throw new DomainException("SERIE_NO_CONFIGURADA", "Serie no configurada: " + serie);
            return ultimo.merge(k, 1L, Long::sum);
        }
        public void avanzarHasta(UUID t, TipoDocumento tipo, String serie, long numero) {
            String k = t + tipo.codigo() + serie;
            if (!ultimo.containsKey(k)) throw new DomainException("SERIE_NO_CONFIGURADA", "Serie no configurada: " + serie);
            ultimo.merge(k, numero, Math::max);
        }
        public void crear(Serie s) { ultimo.put(s.tenantId() + s.tipo().codigo() + s.codigo(), s.ultimoNumero()); }
        public List<Serie> listar(UUID t) { return List.of(); }
    }
    static final class Tenants implements TenantRepository {
        final Map<UUID, Tenant> datos = new HashMap<>();
        public void guardar(Tenant t) { datos.put(t.id(), t); }
        public Optional<Tenant> buscar(UUID id) { return Optional.ofNullable(datos.get(id)); }
        public Optional<Tenant> buscarPorRuc(String ruc) { return datos.values().stream().filter(t -> t.ruc().equals(ruc)).findFirst(); }
        final Map<UUID, UUID> cuentas = new HashMap<>();
        public List<Tenant> listarPorCuenta(UUID c) { return datos.values().stream().filter(t -> c.equals(cuentas.get(t.id()))).toList(); }
        public void asignarCuenta(UUID t, UUID c) { cuentas.put(t, c); }
        public Optional<UUID> cuentaDe(UUID t) { return Optional.ofNullable(cuentas.get(t)); }
    }
    static final class Storage implements DocumentStorage {
        final Map<String, byte[]> datos = new HashMap<>();
        public void guardar(String k, byte[] c) { datos.put(k, c); }
        public byte[] leer(String k) { byte[] b = datos.get(k); if (b == null) throw new IllegalStateException("no existe " + k); return b; }
    }
    static final class Outbox implements OutboxRepository {
        record Fila(UUID tenantId, String accion, UUID agregadoId, Instant cuando) {}
        final List<Fila> filas = new ArrayList<>();
        /** Imita el ON CONFLICT (agregado_id, accion) DO NOTHING del adaptador JDBC. */
        public void programar(UUID t, String accion, UUID id, Instant cuando) {
            if (filas.stream().anyMatch(f -> f.agregadoId().equals(id) && f.accion().equals(accion))) return;
            filas.add(new Fila(t, accion, id, cuando));
        }
        public List<OutboxItem> tomarVencidas(int l, Duration d) { return List.of(); }
        public void reprogramar(UUID id, Instant c, String e) {}
        public void completar(UUID id) {}
    }
    static final class Gateway implements SunatBillingGateway {
        RuntimeException falla; byte[] respuesta = "cdr".getBytes(); String ultimoNombre;
        public byte[] sendBill(Tenant t, String nombre, byte[] xml) { ultimoNombre = nombre; if (falla != null) throw falla; return respuesta; }
    }
    static final class Cdrs implements CdrParser {
        Cdr cdr = new Cdr("0", "aceptada", List.of());
        public Cdr parsear(byte[] zip) { return cdr; }
    }
    static final UnitOfWork UOW = new UnitOfWork() {
        public <T> T ejecutar(Supplier<T> w) { return w.get(); }
        public void ejecutar(Runnable w) { w.run(); }
    };
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));

    static Tenant tenantListo(UUID id) {
        return new Tenant(id, "20100066603", "EMPRESA SAC", Entorno.BETA, new CredencialesSol("MODDATOS", "moddatos"),
                new CertificadoDigital(new byte[]{1}, "clave", LocalDate.of(2030, 1, 1)));
    }
    static Comprobante facturaFirmada(UUID tenantId, Storage storage) {
        Comprobante c = Comprobante.crearFactura(tenantId, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", null),
                List.of(new Item("P1", "Prod", "NIU", java.math.BigDecimal.ONE, new java.math.BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)), CLOCK);
        c.asignarNumero(1, "20100066603");
        String key = "k/" + c.nombreArchivo() + ".xml";
        storage.guardar(key, "<xml/>".getBytes());
        c.firmar("hash", key);
        return c;
    }
}
