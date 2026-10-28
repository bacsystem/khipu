package pe.factura.application.service;

import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;
import pe.factura.domain.plataforma.RegistroAuditoria;
import pe.factura.domain.tenant.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

final class Fakes {
    /** Como {@code JdbcAceptacionDeTerminosRepository} (#342): una fila por usuario y versión, y aceptar otra vez conserva la primera fecha. */
    static final class Aceptaciones implements AceptacionDeTerminosRepository {
        final Map<String, pe.factura.domain.cuenta.AceptacionDeTerminos> filas = new HashMap<>();

        public synchronized void registrar(pe.factura.domain.cuenta.AceptacionDeTerminos a) { filas.putIfAbsent(a.usuarioId() + "|" + a.version(), a); }
        public synchronized Optional<pe.factura.domain.cuenta.AceptacionDeTerminos> buscar(UUID usuarioId, String version) {
            return Optional.ofNullable(filas.get(usuarioId + "|" + version));
        }
    }

    /** Mismas reglas que la sentencia de {@code JdbcIntentosDeAccesoRepository}, en memoria y sincronizadas (#261). */
    static final class Intentos implements IntentosDeAccesoRepository {
        record Fila(int intentos, Instant ventanaDesde, Instant bloqueadoHasta) {}
        final Map<String, Fila> filas = new HashMap<>();

        public synchronized boolean reservar(String clave, int max, Instant ahora, Duration ventana, Duration bloqueo) {
            Fila f = filas.get(clave);
            if (f != null && f.bloqueadoHasta() != null && f.bloqueadoHasta().isAfter(ahora)) return false;
            boolean nueva = f == null || !f.ventanaDesde().plus(ventana).isAfter(ahora) || f.bloqueadoHasta() != null;
            int n = nueva ? 1 : f.intentos() + 1;
            filas.put(clave, new Fila(n, nueva ? ahora : f.ventanaDesde(), n >= max ? ahora.plus(bloqueo) : null));
            return true;
        }
        public synchronized void devolver(String clave) {
            filas.computeIfPresent(clave, (k, f) -> new Fila(Math.max(0, f.intentos() - 1), f.ventanaDesde(), null));
        }
        public synchronized void reiniciar(String clave) { filas.remove(clave); }
        public synchronized int purgar(Instant antesDe) {
            int antes = filas.size();
            filas.values().removeIf(f -> f.ventanaDesde().isBefore(antesDe) && (f.bloqueadoHasta() == null || f.bloqueadoHasta().isBefore(antesDe)));
            return antes - filas.size();
        }
    }

    /** Las empresas con credenciales SOL rechazadas (#107), y cuántas veces se levantó la marca. */
    static final class RechazosDeSol implements RechazoDeSolRepository {
        final Map<UUID, Rechazo> filas = new HashMap<>();
        int levantados;
        public void marcar(UUID t, String motivo, Instant en) { filas.merge(t, new Rechazo(en, motivo), (viejo, nuevo) -> new Rechazo(viejo.en(), nuevo.motivo())); }
        public Optional<Rechazo> buscar(UUID t) { return Optional.ofNullable(filas.get(t)); }
        public boolean levantar(UUID t, Instant ahora) { levantados++; return filas.remove(t) != null; }
    }

    static class Comprobantes implements ComprobanteRepository {
        final Map<UUID, Comprobante> datos = new HashMap<>();
        /** Reservas de envío vivas (LOG-01), como la columna enviando_desde: desde cuándo está reservado cada comprobante. */
        final Map<UUID, Instant> reservas = new HashMap<>();
        /**
         * Como el repositorio real (LOG-01): la actualización es condicional al estado desde el que salió la transición en memoria (o al actual si no hay transición), o al
         * mismo estado destino (dos réplicas con el mismo resultado, #325-H2); una copia leída antes de que otro guardara otro resultado no lo pisa. Libera la reserva del envío.
         */
        public void guardar(Comprobante c) {
            Comprobante enBase = datos.get(c.id());
            if (enBase != null && enBase != c) {
                EstadoDocumento origen = c.eventosPendientes().isEmpty() ? c.estado() : c.eventosPendientes().get(0).estadoAnterior();
                if (origen != null && enBase.estado() != origen && enBase.estado() != c.estado())
                    throw new DomainException("ESTADO_CONFLICTO", "El comprobante cambió de estado en otra transacción: está " + enBase.estado() + " y se esperaba " + origen);
            }
            reservas.remove(c.id());
            datos.put(c.id(), c);
            for (EventoDocumento e : c.eventosPendientes()) eventos.computeIfAbsent(c.id(), k -> new ArrayList<>()).add(new EventoDocumento(e.estadoAnterior(), e.estadoNuevo(), e.detalle(), Instant.now()));
            c.eventosGuardados();
        }
        public Optional<Comprobante> buscar(UUID t, UUID id) { return Optional.ofNullable(datos.get(id)).filter(c -> c.tenantId().equals(t)); }
        /** Cuántas veces se pidió una fila con bloqueo: quien serializa a los administradores (#196) tiene que pedirla así. */
        int bloqueos;
        public Optional<Comprobante> bloquear(UUID t, UUID id) { bloqueos++; return buscar(t, id); }
        public synchronized boolean reservarEnvio(UUID t, UUID id, Instant ahora, Instant reservaVencidaAntesDe) {
            Comprobante c = datos.get(id);
            if (c == null || !c.tenantId().equals(t) || !c.estado().esEnviable()) return false;
            Instant desde = reservas.get(id);
            if (desde != null && !desde.isBefore(reservaVencidaAntesDe)) return false;
            reservas.put(id, ahora);
            return true;
        }
        public BigDecimal montoRegularizado(UUID t, String serie, long numero) {
            return datos.values().stream().filter(c -> c.tenantId().equals(t) && c.estado() != EstadoDocumento.RECHAZADO && c.estado() != EstadoDocumento.INVALIDO)
                    .flatMap(c -> c.anticipos().stream()).filter(a -> a.serie().equals(serie) && a.numero() == numero)
                    .map(Anticipo::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        /**
         * Las bajas contra las que excluir lo que ya tiene un resumen en curso o que se aísla de a uno ({@code ComunicacionBaja.aislaSuComprobante}), como el NOT EXISTS de la
         * consulta real (que prueba {@code JdbcResumenDiarioPorLotesTest}). Sin ellas no se excluye nada.
         */
        Bajas bajasParaAgrupar;
        public List<Comprobante> bloquearPorInformarDeLaFecha(UUID t, java.time.LocalDate fecha, UUID excluido, int limite) {
            return datos.values().stream().filter(c -> c.tenantId().equals(t) && !c.id().equals(excluido) && c.fechaEmision().equals(fecha) && c.estado().esEnviable() && c.esDeBoleta())
                    .filter(c -> bajasParaAgrupar == null || bajasParaAgrupar.deComprobante(t, c.id()).stream().noneMatch(b -> b.pendiente() || b.aislaSuComprobante()))
                    .sorted(Comparator.comparing(Comprobante::serie).thenComparing(Comprobante::numero)).limit(limite).toList();
        }
        public Optional<Comprobante> bloquearPorNumero(UUID t, TipoDocumento tipo, String serie, long numero) { return buscarPorNumero(t, tipo, serie, numero); }
        public Optional<Comprobante> buscarPorNumero(UUID t, TipoDocumento tipo, String serie, long numero) {
            return datos.values().stream().filter(c -> c.tenantId().equals(t) && c.tipo() == tipo && c.serie().equals(serie) && Long.valueOf(numero).equals(c.numero())).findFirst();
        }
        public List<Comprobante> notasDe(UUID t, String serie, long numero) {
            return datos.values().stream().filter(c -> c.tenantId().equals(t) && c.esNota() && c.nota().serieAfectada().equals(serie) && c.nota().numeroAfectado() == numero).toList();
        }
        public List<Comprobante> pendientesQueVencenEmitidosHasta(java.time.LocalDate fecha) {
            return datos.values().stream().filter(c -> c.estado().esEnviable() && !c.fechaEmision().isAfter(fecha)
                    && c.tipo() != TipoDocumento.BOLETA && !(c.esNota() && c.nota().deBoleta() && c.nota().vaEnResumenDiario(c.tipo()))).toList();
        }
        final Map<UUID, List<EventoDocumento>> eventos = new HashMap<>();
        public List<EventoDocumento> eventosDe(UUID t, UUID id) { return eventos.getOrDefault(id, List.of()); }
        public List<Comprobante> firmadosEmitidosEntre(java.time.LocalDate desde, java.time.LocalDate hasta) { return datos.values().stream().filter(c -> c.xmlKey() != null && !c.fechaEmision().isBefore(desde) && !c.fechaEmision().isAfter(hasta)).toList(); }
        /**
         * Por fecha de emisión y número, como la consulta real. No excluye lo de boleta a propósito: ese filtro es de {@code JdbcComprobanteRepository} (con su prueba) y el
         * servicio tiene que defenderse solo si algún día le llega (#357).
         */
        public List<Comprobante> pendientesDeCdr() {
            return datos.values().stream().filter(c -> c.xmlKey() != null && c.cdrKey() == null && c.estado() != EstadoDocumento.FIRMADO && c.estado() != EstadoDocumento.FUERA_DE_PLAZO && c.estado() != EstadoDocumento.INVALIDO && c.estado() != EstadoDocumento.RECIBIDO)
                    .sorted(Comparator.comparing(Comprobante::fechaEmision).thenComparing(Comprobante::numero)).toList();
        }
        public List<Comprobante> listar(UUID t, pe.factura.application.port.in.ConsultarComprobanteUseCase.Filtro f, int p, int pp) { return datos.values().stream().filter(c -> c.tenantId().equals(t)).toList(); }
        public long contar(UUID t, pe.factura.application.port.in.ConsultarComprobanteUseCase.Filtro f) { return listar(t, f, 1, Integer.MAX_VALUE).size(); }
        /** Sin orden por creación: la exportación por llave la prueban el adaptador REST y la persistencia. */
        public pe.factura.application.port.in.ConsultarComprobanteUseCase.PaginaPorLlave listarPorLlave(UUID t, pe.factura.application.port.in.ConsultarComprobanteUseCase.Filtro f,
                Set<EstadoDocumento> excluidos, Set<TipoDocumento> tipos, pe.factura.application.port.in.ConsultarComprobanteUseCase.Llave despues, int limite) {
            List<Comprobante> filas = despues != null ? List.of() : listar(t, f, 1, limite).stream().filter(c -> !excluidos.contains(c.estado()) && (tipos.isEmpty() || tipos.contains(c.tipo()))).toList();
            return new pe.factura.application.port.in.ConsultarComprobanteUseCase.PaginaPorLlave(filas, filas.isEmpty() ? null : new pe.factura.application.port.in.ConsultarComprobanteUseCase.Llave(Instant.EPOCH, filas.get(filas.size() - 1).id()));
        }
        public long contar(UUID t, pe.factura.application.port.in.ConsultarComprobanteUseCase.Filtro f, Set<EstadoDocumento> excluidos, Set<TipoDocumento> tipos) {
            return listar(t, f, 1, Integer.MAX_VALUE).stream().filter(c -> !excluidos.contains(c.estado()) && (tipos.isEmpty() || tipos.contains(c.tipo()))).count();
        }
    }
    static class Series implements SerieRepository {
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
    /** {@code series}: de dónde sale el establecimiento asignado a una serie (mismo JOIN que hace la consulta real en Postgres). */
    static final class Establecimientos implements EstablecimientoRepository, EmisorDeSerieRepository {
        final Map<String, pe.factura.domain.tenant.Establecimiento> datos = new HashMap<>();
        private final Series series;
        Establecimientos(Series series) { this.series = series; }
        public void guardar(pe.factura.domain.tenant.Establecimiento e) { datos.put(e.tenantId() + e.codigo(), e); }
        public java.util.Optional<pe.factura.domain.tenant.Establecimiento> buscar(UUID t, String codigo) { return java.util.Optional.ofNullable(datos.get(t + codigo)); }
        public java.util.Optional<pe.factura.domain.tenant.Establecimiento> buscarConBloqueo(UUID t, String codigo) { return buscar(t, codigo); }
        public List<pe.factura.domain.tenant.Establecimiento> listar(UUID t) { return datos.values().stream().filter(e -> e.tenantId().equals(t)).sorted(java.util.Comparator.comparing(pe.factura.domain.tenant.Establecimiento::codigo)).toList(); }
        public java.util.Optional<Asignacion> buscarAsignacionDeSerie(UUID t, TipoDocumento tipo, String serie) {
            return series.buscar(t, tipo, serie).filter(s -> !s.enDomicilioFiscal())
                    .map(s -> new Asignacion(s.establecimiento(), datos.get(t + s.establecimiento())));
        }
    }
    static class Tenants implements TenantRepository {
        final Map<UUID, Tenant> datos = new HashMap<>();
        /**
         * Corre una sola vez, justo después de la próxima lectura y antes de devolverla: «otro proceso» escribe entre la lectura del servicio y su guardado (LOG-05). El
         * servicio recibe lo que leyó antes de ese escrito, como una transacción de verdad.
         */
        Runnable alBuscar;
        public void guardar(Tenant t) { datos.put(t.id(), t); }
        public Optional<Tenant> buscar(UUID id) {
            Optional<Tenant> leido = Optional.ofNullable(datos.get(id));
            if (alBuscar != null) { Runnable r = alBuscar; alBuscar = null; r.run(); }
            return leido;
        }
        public Optional<Tenant> buscarPorRuc(String ruc) { return datos.values().stream().filter(t -> t.ruc().equals(ruc)).findFirst(); }
        final Map<UUID, UUID> cuentas = new HashMap<>();
        public List<Tenant> listarPorCuenta(UUID c) { return datos.values().stream().filter(t -> c.equals(cuentas.get(t.id()))).toList(); }
        public void asignarCuenta(UUID t, UUID c) { cuentas.put(t, c); }
        public Optional<UUID> cuentaDe(UUID t) { return Optional.ofNullable(cuentas.get(t)); }
    }
    static class Storage implements DocumentStorage {
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
        public void completarPorAgregado(UUID agregadoId, String accion) { filas.removeIf(f -> f.agregadoId().equals(agregadoId) && f.accion().equals(accion)); }
    }
    static final class Gateway implements SunatBillingGateway {
        RuntimeException falla; byte[] respuesta = "cdr".getBytes(); String ultimoNombre; int enviados;
        /** sendSummary/getStatus: ticket fijo y estado configurable (por defecto procesado con el mismo ZIP de respuesta). */
        RuntimeException fallaResumen; RuntimeException fallaStatus; String ticket = "T-1"; String statusCode = "0"; int consultas;
        /** Cuántos sendSummary llegaron a SUNAT (con o sin falla): un resumen de varias líneas es uno solo (#301). */
        int resumenes;
        /** Corre una sola vez, al entrar al próximo sendBill y antes de responder: sirve para que «otro proceso» intente enviar mientras SUNAT todavía no contestó a este. */
        Runnable alEnviar;
        public byte[] sendBill(Tenant t, String nombre, byte[] xml) {
            enviados++; ultimoNombre = nombre;
            if (alEnviar != null) { Runnable r = alEnviar; alEnviar = null; r.run(); }
            if (falla != null) throw falla; return respuesta;
        }
        public String sendSummary(Tenant t, String nombre, byte[] xml) { ultimoNombre = nombre; resumenes++; if (fallaResumen != null) throw fallaResumen; return ticket; }
        /** Corre una sola vez al empezar la próxima consulta del ticket: sirve para que "otro proceso" avance mientras este espera la respuesta de SUNAT. */
        Runnable alConsultar;
        public EstadoTicket getStatus(Tenant t, String tk) {
            consultas++;
            if (alConsultar != null) { Runnable r = alConsultar; alConsultar = null; r.run(); }
            if (fallaStatus != null) throw fallaStatus; return new EstadoTicket(statusCode, "98".equals(statusCode) ? null : respuesta); }
        /** Con qué nombre de resumen se consultó cada ticket (#40: el adaptador elige el servicio por él). */
        final List<String> consultadosCon = new ArrayList<>();
        @Override public EstadoTicket getStatus(Tenant t, String nombreArchivo, String tk) { consultadosCon.add(nombreArchivo + "#" + tk); return getStatus(t, tk); }
    }
    /** Guarda y devuelve copias, como una base de datos: dos procesos que leen la misma fila no comparten el objeto (el avance de uno no cambia lo que el otro ya leyó). */
    static final class Bajas implements BajaRepository {
        final Map<UUID, ComunicacionBaja> datos = new LinkedHashMap<>();
        static ComunicacionBaja copia(ComunicacionBaja b) {
            return ComunicacionBaja.rehidratar(b.id(), b.tenantId(), b.fechaGeneracion(), b.correlativo(), b.comprobanteId(), b.tipoComprobante(), b.deBoleta(), b.serie(), b.numero(),
                    b.fechaReferencia(), b.motivo(), b.condicion(), b.estado(), b.ticket(), b.xmlKey(), b.cdrKey(), b.cdr(), b.intentos(), b.ultimoError(), b.linea(), b.lineas());
        }
        public void guardar(ComunicacionBaja b) { datos.put(b.id(), copia(b)); }
        public Optional<ComunicacionBaja> buscar(UUID t, UUID id) { return Optional.ofNullable(datos.get(id)).filter(b -> b.tenantId().equals(t)).map(Bajas::copia); }
        public List<ComunicacionBaja> deComprobante(UUID t, UUID c) { return datos.values().stream().filter(b -> b.tenantId().equals(t) && b.comprobanteId().equals(c)).map(Bajas::copia).toList(); }
        /** El máximo del día más uno, como el adaptador: las filas de un resumen de varias líneas (#301) comparten correlativo. */
        public int siguienteCorrelativo(UUID t, LocalDate f) { return datos.values().stream().filter(b -> b.tenantId().equals(t) && b.fechaGeneracion().equals(f)).mapToInt(ComunicacionBaja::correlativo).max().orElse(0) + 1; }
        public List<ComunicacionBaja> delResumen(UUID t, LocalDate f, int correlativo) {
            return datos.values().stream().filter(b -> b.tenantId().equals(t) && b.fechaGeneracion().equals(f) && b.correlativo() == correlativo).sorted(Comparator.comparingInt(ComunicacionBaja::linea)).map(Bajas::copia).toList();
        }
    }
    /** UblGenerator de prueba: devuelve un XML mínimo con la raíz según el tipo. */
    static final class Ubl implements UblGenerator {
        /** Emisor con el que se generó el último XML: permite comprobar el domicilio del establecimiento de la serie (#80). */
        Tenant ultimoEmisor;
        public String generar(Comprobante c, Tenant t) { ultimoEmisor = t; return "<" + c.tipo() + ">" + c.nombreArchivo() + "</" + c.tipo() + ">"; }
        public String generarBaja(ComunicacionBaja b, Comprobante c, Tenant t) { String raiz = b.resumenDiario() ? "SummaryDocuments" : "VoidedDocuments"; return "<" + raiz + ">" + b.identificador() + "</" + raiz + ">"; }
        /** Cuántas líneas llevó cada resumen generado, en orden: lo que un test de agrupación (#301) quiere ver. */
        final List<Integer> lineasPorResumen = new ArrayList<>();
        public String generarResumen(List<ComunicacionBaja> filas, List<Comprobante> comprobantes, Tenant t) {
            lineasPorResumen.add(filas.size());
            return "<SummaryDocuments>" + filas.get(0).identificador() + "</SummaryDocuments>";
        }
    }
    static final class Consultas implements pe.factura.application.port.out.SunatConsultaGateway {
        Consulta respuesta = new Consulta("0001", "El comprobante existe y está aceptado.", "cdr".getBytes());
        RuntimeException falla; int llamadas; String ultimaOperacion; Object[] ultimosCriterios;
        /** Falla solo la consulta de ese número de comprobante (getStatusCdr): lo que {@code falla} hace con todas. */
        final Map<Long, RuntimeException> fallaPorNumero = new HashMap<>();
        public Consulta getStatusCdr(Tenant t, String ruc, String tipo, String serie, long numero) {
            llamadas++; ultimaOperacion = "getStatusCdr"; ultimosCriterios = new Object[]{ruc, tipo, serie, numero};
            if (fallaPorNumero.containsKey(numero)) throw fallaPorNumero.get(numero);
            if (falla != null) throw falla;
            return respuesta;
        }
        public Consulta getStatus(Tenant t, String ruc, String tipo, String serie, long numero) { llamadas++; ultimaOperacion = "getStatus"; if (falla != null) throw falla; return respuesta; }
        public Consulta validar(Tenant t, String ruc, String tipo, String serie, long numero, String td, String nd, java.time.LocalDate f, java.math.BigDecimal m) { llamadas++; ultimaOperacion = "validar"; ultimosCriterios = new Object[]{ruc, tipo, serie, numero, td, nd, f, m}; if (falla != null) throw falla; return respuesta; }
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

    /** Sin padrón (#378): ni rechaza ni avisa, como una instalación que no cargó el padrón. */
    static final pe.factura.application.port.in.ValidarCompradorUseCase SIN_COMPRADOR = new pe.factura.application.port.in.ValidarCompradorUseCase() {
        public void exigirQueExista(pe.factura.domain.documento.Receptor receptor) { }
        public List<Aviso> avisosDe(pe.factura.domain.documento.Receptor receptor) { return List.of(); }
    };

    /** Sabe si el trabajo corre dentro de {@code ejecutar}: lo que separa «dentro de la transacción» de «después de ella». */
    static final class UowTransaccional implements UnitOfWork {
        boolean dentro;
        public <T> T ejecutar(Supplier<T> w) { dentro = true; try { return w.get(); } finally { dentro = false; } }
        public void ejecutar(Runnable w) { ejecutar(() -> { w.run(); return null; }); }
    }

    /** El tope del plan (#18): sin tope salvo que se marque {@code lleno}. Registra cada control y si corrió dentro de la transacción. */
    static final class Tope implements pe.factura.application.service.TopeDelPlan {
        boolean lleno;
        UowTransaccional uow;
        final List<Boolean> controladoDentro = new ArrayList<>();
        public void exigirDisponible(UUID tenantId, LocalDate fechaEmision) {
            controladoDentro.add(uow != null && uow.dentro);
            if (lleno) throw new DomainException("LIMITE_PLAN", "Llegaste al tope de documentos del mes de tu plan");
        }
    }

    /** Claves de idempotencia en memoria (#115). Registra si cada llamada corrió dentro de la transacción. */
    static class Idempotencias implements IdempotenciaRepository {
        final Map<String, Registro> filas = new HashMap<>();
        final List<Boolean> reservadoDentro = new ArrayList<>();
        UowTransaccional uow;
        /** Cuándo se reserva cada clave, como el {@code DEFAULT now()} de la columna {@code creado_at}. */
        Clock reloj = CLOCK;
        public Optional<Registro> buscar(String alcance, String clave) {
            return Optional.ofNullable(filas.get(alcance + "|" + clave));
        }
        public Optional<Registro> reservar(String alcance, String clave, String huella) {
            reservadoDentro.add(uow != null && uow.dentro);
            // Lee `filas` directo y no `buscar`: como el INSERT ... ON CONFLICT real, la reserva ve lo confirmado aunque la búsqueda previa no.
            Registro r = filas.get(alcance + "|" + clave);
            if (r != null) return Optional.of(r);
            filas.put(alcance + "|" + clave, new Registro(huella, null, null, Instant.now(reloj)));
            return Optional.empty();
        }
        public void completar(String alcance, String clave, UUID recursoId, byte[] respuesta) {
            completadoDentro.add(uow != null && uow.dentro);
            filas.computeIfPresent(alcance + "|" + clave, (k, r) -> new Registro(r.huella(), recursoId, respuesta, r.creadoAt()));
        }
        final List<Boolean> completadoDentro = new ArrayList<>();
        public int borrarAnterioresA(Instant limite) { return 0; }
        /** Simula que pasó la hora de la respuesta: olvida todas las respuestas guardadas. */
        public int olvidarRespuestasAnterioresA(Instant limite) {
            filas.replaceAll((k, r) -> new Registro(r.huella(), r.recursoId(), null, r.creadoAt()));
            return filas.size();
        }
    }

    /** Bitácora en memoria; {@code falla} simula que la tabla de auditoría rechaza la escritura. */
    static class Auditoria implements AuditoriaAdminRepository {
        final List<RegistroAuditoria> registros = new ArrayList<>();
        final List<Boolean> dentroAlRegistrar = new ArrayList<>();
        UowTransaccional uow;
        RuntimeException falla;
        public void registrar(RegistroAuditoria r) {
            if (falla != null) throw falla;
            dentroAlRegistrar.add(uow != null && uow.dentro);
            registros.add(r);
        }
    }

    static Tenant tenantListo(UUID id) {
        return new Tenant(id, "20100066603", "EMPRESA SAC", Entorno.BETA, new CredencialesSol("MODDATOS", "moddatos"),
                new CertificadoDigital(new byte[]{1}, "clave", LocalDate.of(2030, 1, 1)));
    }
    static Comprobante facturaFirmada(UUID tenantId, Storage storage) { return facturaFirmada(tenantId, storage, 1); }
    static Comprobante facturaFirmada(UUID tenantId, Storage storage, long numero) {
        Comprobante c = Comprobante.factura(tenantId, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", null), List.of(new Item("P1", "Prod", "NIU", java.math.BigDecimal.ONE, new java.math.BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).crear(CLOCK);
        return firmado(c, numero, storage);
    }
    /** Una boleta B001 a consumidor final, numerada y firmada. */
    static Comprobante boletaFirmada(UUID tenantId, Storage storage, long numero) {
        Comprobante c = Comprobante.boleta(tenantId, "B001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("-", "-", "CLIENTES VARIOS", null),
                List.of(new Item("P1", "Prod", "NIU", java.math.BigDecimal.ONE, new java.math.BigDecimal("50.00"), TipoAfectacionIgv.GRAVADO))).crear(CLOCK);
        return firmado(c, numero, storage);
    }
    /** Una nota de crédito BC01 (devolución) sobre la boleta B001-1, numerada y firmada. */
    static Comprobante notaDeBoletaFirmada(UUID tenantId, Storage storage, long numero) {
        Comprobante c = Comprobante.nota(tenantId, TipoDocumento.NOTA_CREDITO, "BC01", LocalDate.of(2026, 9, 13), new Nota(TipoDocumento.BOLETA, "B001", 1, "07", "Devolución"),
                new Receptor("-", "-", "CLIENTES VARIOS", null), List.of(new Item("P1", "Prod", "NIU", java.math.BigDecimal.ONE, new java.math.BigDecimal("50.00"), TipoAfectacionIgv.GRAVADO))).crear(CLOCK);
        return firmado(c, numero, storage);
    }
    /** Una liquidación de compra L001 (#327) a un vendedor con DNI, numerada y firmada. */
    static Comprobante liquidacionFirmada(UUID tenantId, Storage storage, long numero) {
        Comprobante c = Comprobante.liquidacionCompra(tenantId, "L001", LocalDate.of(2026, 9, 13), "PEN", null, new Receptor("1", "45678912", "JUAN QUISPE", "Caserío Santa Rosa s/n"),
                new LiquidacionCompra("060101", "02", new LiquidacionCompra.Ubicacion("Fundo Santa Rosa", "060101", "02"), null),
                List.of(new Item("C1", "Café", "KGM", java.math.BigDecimal.ONE, new java.math.BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO, null, null, false, List.of(), CodigoProductoSunat.de("50201706"), null)))
                .crear(CLOCK);
        return firmado(c, numero, storage);
    }
    /** Una retención R001 (#40) a un proveedor con RUC, numerada y firmada. */
    static Comprobante retencionFirmada(UUID tenantId, Storage storage, long numero) {
        Comprobante c = Comprobante.deAgente(tenantId, "R001", LocalDate.of(2026, 9, 13), new Receptor("6", "20131312955", "PROVEEDOR SAC", null),
                new ComprobanteDeAgente(TipoDocumento.RETENCION, "01", null, List.of(new ComprobanteDeAgente.Documento("01", "F001-1", LocalDate.of(2026, 9, 1),
                        new java.math.BigDecimal("100.00"), "PEN", 1, LocalDate.of(2026, 9, 13), new java.math.BigDecimal("100.00"), null, null)))).crear("20100066603", CLOCK);
        return firmado(c, numero, storage);
    }
    private static Comprobante firmado(Comprobante c, long numero, Storage storage) {
        c.asignarNumero(numero, "20100066603");
        String key = "k/" + c.nombreArchivo() + ".xml";
        storage.guardar(key, "<xml/>".getBytes());
        c.firmar("hash", key);
        return c;
    }
}
