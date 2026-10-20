package pe.factura.application.service;

import pe.factura.application.port.out.GuiaRemisionRepository;
import pe.factura.application.port.out.SunatGreGateway;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;
import pe.factura.domain.tenant.CertificadoDigital;
import pe.factura.domain.tenant.CredencialesApiSunat;
import pe.factura.domain.tenant.CredencialesSol;
import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Dobles de prueba de la guía de remisión (#39). */
final class FakesDeGuia {
    private FakesDeGuia() {}

    static final String EMISOR = "20100066603";
    static final LocalDate HOY = LocalDate.of(2026, 9, 13);

    /** Guarda copias, como la base: dos lecturas de la misma guía no comparten el objeto; y no pisa una guía resuelta, como el adaptador JDBC. */
    static final class Guias implements GuiaRemisionRepository {
        final Map<UUID, GuiaRemision> datos = new LinkedHashMap<>();
        int guardadas;
        public void guardar(GuiaRemision g) {
            GuiaRemision previa = datos.get(g.id());
            if (previa != null && previa.estado().esFinal() && !g.estado().esFinal()) throw new DomainException("ESTADO_CONFLICTO", "ya resuelta");
            guardadas++;
            datos.put(g.id(), copia(g));
        }
        public Optional<GuiaRemision> buscar(UUID t, UUID id) { return Optional.ofNullable(datos.get(id)).filter(g -> g.tenantId().equals(t)).map(FakesDeGuia::copia); }
        public Optional<GuiaRemision> bloquear(UUID t, UUID id) { return buscar(t, id); }
        public List<GuiaRemision> listar(UUID t, Filtro f, int p, int pp) { return datos.values().stream().filter(g -> g.tenantId().equals(t)).map(FakesDeGuia::copia).toList(); }
        public long contar(UUID t, Filtro f) { return listar(t, f, 1, Integer.MAX_VALUE).size(); }
    }

    static GuiaRemision copia(GuiaRemision g) {
        return GuiaRemision.rehidratar(g.id(), g.tenantId(), g.serie(), g.numero(), g.fechaEmision(), g.horaEmision(), g.destinatario(), g.datos(), g.traslado(), g.items(),
                g.documentos(), g.observaciones(), g.estado(), g.envio());
    }

    /** SUNAT simulado: el envío devuelve {@code ticket} (o lanza {@code fallaEnvio}); cada consulta devuelve el siguiente de {@code respuestas} (o lanza {@code fallaTicket}). */
    static final class Gre implements SunatGreGateway {
        boolean disponible = true;
        RuntimeException fallaEnvio, fallaTicket;
        String ticket = "7f7b00ab-4fee-4ae2-9e04-fbd16ca18677";
        final Deque<EstadoDelTicket> respuestas = new ArrayDeque<>();
        int envios, consultas;
        String ultimoNombre; byte[] ultimoXml;
        /** Corre al empezar la próxima consulta: «otro proceso» que avanza mientras esta espera a SUNAT. */
        Runnable alConsultar;
        public boolean disponible(Entorno e) { return disponible; }
        public Recepcion enviar(Tenant t, String nombre, byte[] xml) {
            envios++; ultimoNombre = nombre; ultimoXml = xml;
            if (fallaEnvio != null) throw fallaEnvio;
            return new Recepcion(ticket, LocalDateTime.of(2026, 9, 13, 10, 0, 1));
        }
        public EstadoDelTicket consultar(Tenant t, String tk) {
            consultas++;
            if (alConsultar != null) { Runnable r = alConsultar; alConsultar = null; r.run(); }
            if (fallaTicket != null) throw fallaTicket;
            return respuestas.isEmpty() ? enProceso() : respuestas.poll();
        }
    }

    static SunatGreGateway.EstadoDelTicket enProceso() { return new SunatGreGateway.EstadoDelTicket("98", null, null, null, null); }
    static SunatGreGateway.EstadoDelTicket aceptada() { return new SunatGreGateway.EstadoDelTicket("0", "cdr-zip".getBytes(), null, null, "https://e-factura.sunat.gob.pe/qr?x=1"); }
    static SunatGreGateway.EstadoDelTicket conError(String cod, String des) { return new SunatGreGateway.EstadoDelTicket("99", null, cod, des, null); }

    static Tenant tenantGre(UUID id) {
        return new Tenant(id, EMISOR, "EMPRESA SAC", Entorno.PRODUCCION, new CredencialesSol("MODDATOS", "moddatos"), new CertificadoDigital(new byte[]{1}, "clave", LocalDate.of(2030, 1, 1)),
                Domicilio.de("150101", "Av. Lima 123, Lima"), null).conCredencialesApiSunat(new CredencialesApiSunat("cliente-id", "secreto"));
    }

    static Traslado privado(PuntoDeTraslado partida, PuntoDeTraslado llegada) {
        return new Traslado(HOY, new BigDecimal("25.5"), "KGM", null, partida, llegada, List.of(new Vehiculo("ABC123")),
                List.of(new Conductor("1", "45678912", "JUAN", "QUISPE", "Q45678912")), Set.of());
    }
    static Traslado privado() { return privado(PuntoDeTraslado.de("150101", "Av. Argentina 1234"), PuntoDeTraslado.de("040101", "Calle Mercaderes 200")); }

    static ParteDeGuia cliente() { return new ParteDeGuia("6", "20601234565", "COMERCIAL ANDINA S.A.C."); }
    static List<ItemDeGuia> items() { return List.of(new ItemDeGuia("P001", "Laptop", "NIU", new BigDecimal("2"), null)); }
    static DatosDeGuia.DeRemitente venta() { return new DatosDeGuia.DeRemitente("01", null, ModalidadTraslado.PRIVADO, null); }

    /** Una guía remitente numerada y firmada, con su XML en el storage. */
    static GuiaRemision firmada(UUID tenantId, Fakes.Storage storage, Guias guias) {
        GuiaRemision g = GuiaRemision.crear(tenantId, EMISOR, "T001", HOY, cliente(), venta(), privado(), items(), List.of(), null, Fakes.CLOCK);
        g.asignarNumero(1, EMISOR);
        String key = tenantId + "/2026/09/guias/" + g.nombreArchivo() + ".xml";
        storage.guardar(key, "<DespatchAdvice/>".getBytes());
        g.firmar("HASH", key);
        guias.guardar(g);
        return g;
    }
}
