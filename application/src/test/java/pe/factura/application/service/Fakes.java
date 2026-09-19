package pe.factura.application.service;

import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;
import pe.factura.domain.tenant.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

final class Fakes {
    static final class Comprobantes implements ComprobanteRepository {
        final Map<UUID, Comprobante> datos = new HashMap<>();
        public void guardar(Comprobante c) { datos.put(c.id(), c); }
        public Optional<Comprobante> buscar(UUID t, UUID id) { return Optional.ofNullable(datos.get(id)).filter(c -> c.tenantId().equals(t)); }
        public Optional<Comprobante> bloquear(UUID t, UUID id) { return buscar(t, id); }
        public BigDecimal montoRegularizado(UUID t, String serie, long numero) {
            return datos.values().stream().filter(c -> c.tenantId().equals(t) && c.estado() != EstadoDocumento.RECHAZADO && c.estado() != EstadoDocumento.INVALIDO)
                    .flatMap(c -> c.anticipos().stream()).filter(a -> a.serie().equals(serie) && a.numero() == numero)
                    .map(Anticipo::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        public Optional<Comprobante> bloquearPorNumero(UUID t, TipoDocumento tipo, String serie, long numero) { return buscarPorNumero(t, tipo, serie, numero); }
        public Optional<Comprobante> buscarPorNumero(UUID t, TipoDocumento tipo, String serie, long numero) {
            return datos.values().stream().filter(c -> c.tenantId().equals(t) && c.tipo() == tipo && c.serie().equals(serie) && Long.valueOf(numero).equals(c.numero())).findFirst();
        }
        public List<Comprobante> notasDe(UUID t, String serie, long numero) {
            return datos.values().stream().filter(c -> c.tenantId().equals(t) && c.esNota() && c.nota().serieAfectada().equals(serie) && c.nota().numeroAfectado() == numero).toList();
        }
        public List<Comprobante> pendientesDeEnvioEmitidosHasta(java.time.LocalDate fecha) {
            return datos.values().stream().filter(c -> c.estado().esEnviable() && !c.fechaEmision().isAfter(fecha)).toList();
        }
        public List<Comprobante> listar(UUID t, EstadoDocumento e, int p, int pp) { return datos.values().stream().filter(c -> c.tenantId().equals(t)).toList(); }
        public long contar(UUID t, EstadoDocumento e) { return listar(t, e, 1, Integer.MAX_VALUE).size(); }
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
        final Map<String, Serie> series = new HashMap<>();
        public void crear(Serie s) { ultimo.put(s.tenantId() + s.tipo().codigo() + s.codigo(), s.ultimoNumero()); series.put(s.tenantId() + s.tipo().codigo() + s.codigo(), s); }
        public java.util.Optional<Serie> buscar(UUID t, TipoDocumento tipo, String serie) { return java.util.Optional.ofNullable(series.get(t + tipo.codigo() + serie)); }
        public List<Serie> listar(UUID t) { return series.values().stream().filter(s -> s.tenantId().equals(t)).toList(); }
    }
    static final class Establecimientos implements pe.factura.application.port.out.EstablecimientoRepository {
        final Map<String, pe.factura.domain.tenant.Establecimiento> datos = new HashMap<>();
        public void guardar(pe.factura.domain.tenant.Establecimiento e) { datos.put(e.tenantId() + e.codigo(), e); }
        public java.util.Optional<pe.factura.domain.tenant.Establecimiento> buscar(UUID t, String codigo) { return java.util.Optional.ofNullable(datos.get(t + codigo)); }
        public List<pe.factura.domain.tenant.Establecimiento> listar(UUID t) { return datos.values().stream().filter(e -> e.tenantId().equals(t)).sorted(java.util.Comparator.comparing(pe.factura.domain.tenant.Establecimiento::codigo)).toList(); }
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
        public boolean existe(String k) { return datos.containsKey(k); }
        public void borrar(String k) { datos.remove(k); }
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
        /** sendSummary/getStatus: ticket fijo y estado configurable (por defecto procesado con el mismo ZIP de respuesta). */
        RuntimeException fallaResumen; RuntimeException fallaStatus; String ticket = "T-1"; String statusCode = "0"; int consultas;
        public byte[] sendBill(Tenant t, String nombre, byte[] xml) { ultimoNombre = nombre; if (falla != null) throw falla; return respuesta; }
        public String sendSummary(Tenant t, String nombre, byte[] xml) { ultimoNombre = nombre; if (fallaResumen != null) throw fallaResumen; return ticket; }
        public EstadoTicket getStatus(Tenant t, String tk) { consultas++; if (fallaStatus != null) throw fallaStatus; return new EstadoTicket(statusCode, "98".equals(statusCode) ? null : respuesta); }
    }
    static final class Bajas implements BajaRepository {
        final Map<UUID, ComunicacionBaja> datos = new LinkedHashMap<>();
        public void guardar(ComunicacionBaja b) { datos.put(b.id(), b); }
        public Optional<ComunicacionBaja> buscar(UUID t, UUID id) { return Optional.ofNullable(datos.get(id)).filter(b -> b.tenantId().equals(t)); }
        public List<ComunicacionBaja> deComprobante(UUID t, UUID c) { return datos.values().stream().filter(b -> b.tenantId().equals(t) && b.comprobanteId().equals(c)).toList(); }
        public int siguienteCorrelativo(UUID t, LocalDate f) { return (int) datos.values().stream().filter(b -> b.tenantId().equals(t) && b.fechaGeneracion().equals(f)).count() + 1; }
    }
    /** UblGenerator de prueba: devuelve un XML mínimo con la raíz según el tipo. */
    static final class Ubl implements UblGenerator {
        /** Emisor con el que se generó el último XML: permite comprobar el domicilio del establecimiento de la serie (#80). */
        Tenant ultimoEmisor;
        public String generar(Comprobante c, Tenant t) { ultimoEmisor = t; return "<" + c.tipo() + ">" + c.nombreArchivo() + "</" + c.tipo() + ">"; }
        public String generarBaja(ComunicacionBaja b, Tenant t) { return "<VoidedDocuments>" + b.identificador() + "</VoidedDocuments>"; }
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
                new Receptor("6", "20601234565", "CLIENTE SAC", null),
                List.of(new Item("P1", "Prod", "NIU", java.math.BigDecimal.ONE, new java.math.BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)), CLOCK);
        c.asignarNumero(1, "20100066603");
        String key = "k/" + c.nombreArchivo() + ".xml";
        storage.guardar(key, "<xml/>".getBytes());
        c.firmar("hash", key);
        return c;
    }
}
