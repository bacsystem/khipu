package pe.factura.domain.documento;

import pe.factura.domain.DomainException;
import pe.factura.domain.catalogo.CatalogoSunat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Guía de remisión electrónica (#39): remitente (09, serie {@code T###}) o transportista (31, serie {@code V###}), UBL 2.1 {@code DespatchAdvice}, enviada
 * por la API REST de SUNAT. El tipo sale de {@link DatosDeGuia} y de ningún otro lado.
 *
 * <p>Las reglas se validan al crearla ({@link #crear}), antes de numerar: lo que SUNAT rechazaría con la guía ya numerada se rechaza aquí, citando el
 * código de la hoja «CódigosRetorno» de las reglas de validación (2026-08-26). El repositorio no tiene la hoja de validaciones de la GRE ni su guía de
 * elaboración del XML: cada código se eligió por su descripción en «CódigosRetorno»; donde ninguno corresponde, el mensaje no cita ninguno.
 *
 * <p>Ciclo: {@code RECIBIDO → FIRMADO → ENVIADO_PENDIENTE_TICKET → ACEPTADO | ACEPTADO_CON_OBS | RECHAZADO}, con {@code ERROR_ENVIO} cuando el envío falla.
 * {@link #reintentoAutomatico()} dice si khipu sigue solo (reenviando o consultando el ticket) o si hace falta una acción.
 */
public class GuiaRemision {
    /** Motivos del catálogo 20 que khipu emite. Importación, exportación (DAM, puertos, depósitos temporales) y emisor itinerante (sin punto de llegada) no. */
    static final Set<String> MOTIVOS_ADMITIDOS = Set.of("01", "02", "03", "04", "05", "06", "07", "13", "14", "17");
    /** Motivo 04: traslado entre establecimientos de la misma empresa. */
    static final String ENTRE_ESTABLECIMIENTOS = "04";
    /** Motivo 13: otros (la descripción es obligatoria). */
    static final String OTROS = "13";
    /** Motivos en que el destinatario es otro que el remitente (2555): las ventas y la consignación. */
    static final Set<String> DESTINATARIO_DISTINTO = Set.of("01", "03", "05", "14");
    /** Motivos en que el destinatario se identifica con RUC (3417). */
    static final Set<String> DESTINATARIO_CON_RUC = Set.of("06", "17");
    static final int MAXIMO_ITEMS = 9999;

    private final UUID id;
    private final UUID tenantId;
    private final String serie;
    private Long numero;
    private final LocalDate fechaEmision;
    private final LocalTime horaEmision;
    private final ParteDeGuia destinatario;
    private final DatosDeGuia datos;
    private final Traslado traslado;
    private final List<ItemDeGuia> items;
    private final List<DocumentoDeTraslado> documentos;
    private final String observaciones;
    private EstadoGuia estado;
    private String nombreArchivo, hash, xmlKey, cdrKey, ticket, ultimoError, enlaceConsulta;
    private LocalDateTime fechaRecepcion;
    private Cdr cdr;
    private int intentos, consultasTicket;
    private boolean reintentoAutomatico;

    private GuiaRemision(UUID id, UUID tenantId, String serie, Long numero, LocalDate fechaEmision, LocalTime horaEmision, ParteDeGuia destinatario, DatosDeGuia datos,
                         Traslado traslado, List<ItemDeGuia> items, List<DocumentoDeTraslado> documentos, String observaciones, EstadoGuia estado) {
        this.id = id; this.tenantId = tenantId; this.serie = serie; this.numero = numero; this.fechaEmision = fechaEmision; this.horaEmision = horaEmision;
        this.destinatario = destinatario; this.datos = datos; this.traslado = traslado; this.items = List.copyOf(items); this.documentos = List.copyOf(documentos);
        this.observaciones = observaciones; this.estado = estado;
    }

    /**
     * Arma y valida una guía nueva. {@code rucEmisor}: el de la empresa que emite (el remitente en la 09, el transportista en la 31), que las reglas
     * 2554, 2555, 2560 y 3414 comparan con el destinatario, el remitente y los establecimientos. {@code fechaEmision} nula es hoy.
     */
    public static GuiaRemision crear(UUID tenantId, String rucEmisor, String serie, LocalDate fechaEmision, ParteDeGuia destinatario, DatosDeGuia datos, Traslado traslado,
                                     List<ItemDeGuia> items, List<DocumentoDeTraslado> documentos, String observaciones, Clock clock) {
        if (datos == null) throw new DomainException("GUIA_INVALIDA", "Faltan los datos de la guía: los del remitente (09) o los del transportista (31)");
        TipoDocumento tipo = datos.tipo();
        String s = serie == null ? null : serie.strip().toUpperCase(java.util.Locale.ROOT);
        if (!tipo.serieValida(s))
            throw new DomainException("SERIE_INVALIDA", "502 - La serie de una guía " + (tipo == TipoDocumento.GUIA_REMITENTE ? "remitente es T### (T001)" : "transportista es V### (V001)") + "; recibida " + serie);
        LocalDate hoy = LocalDate.now(clock);
        LocalDate fecha = fechaEmision == null ? hoy : fechaEmision;
        if (fecha.isAfter(hoy)) throw new DomainException("FECHA_INVALIDA", "La fecha de emisión de la guía (" + fecha + ") no puede ser posterior a hoy (" + hoy + ")");
        if (destinatario == null) throw new DomainException("DESTINATARIO_INVALIDO", "Falta el destinatario de los bienes");
        destinatario.exigirValida("destinatario", "DESTINATARIO_INVALIDO", null, null, null);
        if (traslado == null) throw new DomainException("TRASLADO_INVALIDO", "Faltan los datos del traslado (fecha de inicio, peso, partida y llegada)");
        List<ItemDeGuia> lineas = items == null ? List.of() : items;
        if (lineas.isEmpty()) throw new DomainException("ITEM_INVALIDO", "La guía lleva al menos un bien");
        if (lineas.size() > MAXIMO_ITEMS) throw new DomainException("ITEM_INVALIDO", "La guía admite hasta " + MAXIMO_ITEMS + " bienes");
        for (int i = 0; i < lineas.size(); i++) lineas.get(i).exigirValido(i + 1);
        List<DocumentoDeTraslado> docs = documentos == null ? List.of() : documentos;
        docs.forEach(d -> d.exigirValido(tipo));
        for (int i = 0; i < docs.size(); i++)
            for (int j = i + 1; j < docs.size(); j++)
                if (docs.get(i).mismoQue(docs.get(j))) throw new DomainException("DOCUMENTO_RELACIONADO_INVALIDO", "3340 - El documento relacionado " + docs.get(j).numero() + " está repetido");
        String nota = TextoDeGuia.limpio(observaciones);
        if (nota != null && !TextoDeGuia.unaLinea(nota, 1, 250)) throw new DomainException("OBSERVACIONES_INVALIDAS", "Las observaciones de la guía tienen hasta 250 caracteres, sin saltos de línea");

        Traslado t;
        DatosDeGuia d;
        if (datos instanceof DatosDeGuia.DeRemitente r) {
            d = validarRemitente(r, rucEmisor, destinatario, traslado);
            // El establecimiento sin RUC es del propio remitente (3410 exige el RUC; 3414, que sea el suyo en el traslado entre establecimientos).
            t = traslado.conPuntos(con(traslado.partida(), rucEmisor), con(traslado.llegada(), rucEmisor));
            t.exigirValido(fecha, tipo);
            validarTransporteDelRemitente((DatosDeGuia.DeRemitente) d, rucEmisor, destinatario, t);
        } else {
            DatosDeGuia.DeTransportista tr = (DatosDeGuia.DeTransportista) datos;
            d = validarTransportista(tr, rucEmisor);
            String rucRemitente = tr.remitente().esRuc() ? tr.remitente().numDoc() : null;
            t = traslado.conPuntos(con(traslado.partida(), rucRemitente), con(traslado.llegada(), rucRemitente));
            t.exigirValido(fecha, tipo);
            // La transportista siempre lleva su vehículo y su conductor: es quien hace el traslado.
            if (!t.conVehiculos()) throw new DomainException("TRANSPORTE_INVALIDO", "2566 - La guía transportista lleva el vehículo principal (su placa)");
            if (!t.conConductores()) throw new DomainException("TRANSPORTE_INVALIDO", "3357 - La guía transportista lleva al conductor principal");
        }
        LocalTime hora = LocalTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        return new GuiaRemision(UUID.randomUUID(), tenantId, s, null, fecha, hora, destinatario, d, t, lineas, docs, nota, EstadoGuia.RECIBIDO);
    }

    private static PuntoDeTraslado con(PuntoDeTraslado p, String ruc) { return p == null ? null : p.conRucPorDefecto(ruc); }

    private static DatosDeGuia.DeRemitente validarRemitente(DatosDeGuia.DeRemitente r, String rucEmisor, ParteDeGuia destinatario, Traslado traslado) {
        if (r.motivo() == null) throw new DomainException("TRASLADO_INVALIDO", "3404 - Falta el motivo de traslado (catálogo 20)");
        Optional<String> descripcionDelCatalogo = CatalogoSunat.descripcion("20", r.motivo());
        if (descripcionDelCatalogo.isEmpty()) throw new DomainException("TRASLADO_INVALIDO", "3405 - El motivo de traslado " + r.motivo() + " no existe en el catálogo 20");
        if (!MOTIVOS_ADMITIDOS.contains(r.motivo()))
            throw new DomainException("TRASLADO_INVALIDO", "khipu todavía no emite guías con motivo " + r.motivo() + " (" + descripcionDelCatalogo.get()
                    + "): la importación y la exportación piden la DAM y sus reglas aduaneras, y el emisor itinerante no tiene punto de llegada");
        String descripcion = r.descripcionMotivo();
        if (descripcion == null && OTROS.equals(r.motivo())) throw new DomainException("TRASLADO_INVALIDO", "3457 - Con el motivo 13 (otros) describa el motivo del traslado");
        if (descripcion == null) descripcion = descripcionDelCatalogo.get();
        if (!TextoDeGuia.unaLinea(descripcion, 3, 100)) throw new DomainException("TRASLADO_INVALIDO", "4190 - La descripción del motivo de traslado tiene de 3 a 100 caracteres, sin saltos de línea");
        if (r.modalidad() == null) throw new DomainException("TRASLADO_INVALIDO", "Falta la modalidad de traslado: 01 transporte público o 02 transporte privado (catálogo 18)");

        if (ENTRE_ESTABLECIMIENTOS.equals(r.motivo()) && !destinatario.esElRuc(rucEmisor))
            throw new DomainException("DESTINATARIO_INVALIDO", "2554 - En un traslado entre establecimientos de la misma empresa (motivo 04) el destinatario es el propio remitente (RUC " + rucEmisor + ")");
        if (DESTINATARIO_DISTINTO.contains(r.motivo()) && destinatario.esElRuc(rucEmisor))
            throw new DomainException("DESTINATARIO_INVALIDO", "2555 - Con el motivo " + r.motivo() + " (" + descripcionDelCatalogo.get() + ") el destinatario no puede ser el propio remitente");
        if (DESTINATARIO_CON_RUC.contains(r.motivo()) && !destinatario.esRuc())
            throw new DomainException("DESTINATARIO_INVALIDO", "3417 - Con el motivo " + r.motivo() + " (" + descripcionDelCatalogo.get() + ") el destinatario se identifica con RUC (tipo_doc 6)");
        if (ENTRE_ESTABLECIMIENTOS.equals(r.motivo())) {
            if (traslado.partida() != null && !traslado.partida().esEstablecimiento())
                throw new DomainException("TRASLADO_INVALIDO", "3365 - En un traslado entre establecimientos (motivo 04) el punto de partida es un establecimiento de la empresa: indique su código");
            if (traslado.llegada() != null && !traslado.llegada().esEstablecimiento())
                throw new DomainException("TRASLADO_INVALIDO", "3369 - En un traslado entre establecimientos (motivo 04) el punto de llegada es un establecimiento de la empresa: indique su código");
            for (PuntoDeTraslado p : new PuntoDeTraslado[]{traslado.partida(), traslado.llegada()})
                if (p != null && p.rucEstablecimiento() != null && !p.rucEstablecimiento().equals(rucEmisor))
                    throw new DomainException("TRASLADO_INVALIDO", "3414 - En un traslado entre establecimientos (motivo 04) los establecimientos son del remitente (RUC " + rucEmisor + ")");
            if (traslado.partida() != null && traslado.llegada() != null && java.util.Objects.equals(traslado.partida().codigoEstablecimiento(), traslado.llegada().codigoEstablecimiento()))
                throw new DomainException("TRASLADO_INVALIDO", "En un traslado entre establecimientos (motivo 04) la partida y la llegada son establecimientos distintos");
        }
        return new DatosDeGuia.DeRemitente(r.motivo(), descripcion, r.modalidad(), r.transportista());
    }

    private static void validarTransporteDelRemitente(DatosDeGuia.DeRemitente r, String rucEmisor, ParteDeGuia destinatario, Traslado t) {
        boolean deLaTransportista = t.tiene(IndicadorDeTraslado.VEHICULOS_CONDUCTORES_TRANSPORTISTA);
        if (r.modalidad() == ModalidadTraslado.PUBLICO) {
            if (r.transportista() == null) throw new DomainException("TRANSPORTE_INVALIDO", "2534 - En transporte público (01) indique la empresa de transporte: RUC y razón social");
            r.transportista().exigirValido();
            if (r.transportista().ruc().equals(rucEmisor)) throw new DomainException("TRANSPORTE_INVALIDO", "2560 - El transportista no puede ser el propio remitente: eso es transporte privado (02)");
            if (deLaTransportista && t.tiene(IndicadorDeTraslado.VEHICULO_M1_O_L))
                throw new DomainException("TRASLADO_INVALIDO", "3451 - En transporte público no van juntos los indicadores de vehículos y conductores del transportista y de vehículos M1 o L");
            if (deLaTransportista) {
                if (!t.conVehiculos()) throw new DomainException("TRANSPORTE_INVALIDO", "2566 - Con el indicador de vehículos y conductores del transportista indique el vehículo principal");
                if (!t.conConductores()) throw new DomainException("TRANSPORTE_INVALIDO", "3357 - Con el indicador de vehículos y conductores del transportista indique al conductor principal");
            } else if (t.conVehiculos() || t.conConductores()) {
                throw new DomainException("TRANSPORTE_INVALIDO", "2774 - En transporte público los vehículos y conductores son del transportista: no se informan (salvo con el indicador vehiculos_conductores_transportista)");
            }
        } else {
            if (r.transportista() != null) throw new DomainException("TRANSPORTE_INVALIDO", "3347 - En transporte privado (02) no se informa una empresa de transporte");
            if (deLaTransportista) throw new DomainException("TRASLADO_INVALIDO", "3450 - En transporte privado no va el indicador de vehículos y conductores del transportista");
            if (!t.tiene(IndicadorDeTraslado.VEHICULO_M1_O_L)) {
                if (!t.conVehiculos()) throw new DomainException("TRANSPORTE_INVALIDO", "2533 - En transporte privado indique el vehículo (placa), salvo con el indicador vehiculo_m1_o_l");
                if (!t.conConductores()) throw new DomainException("TRANSPORTE_INVALIDO", "2533 - En transporte privado indique al conductor (documento y licencia), salvo con el indicador vehiculo_m1_o_l");
            }
        }
    }

    private static DatosDeGuia.DeTransportista validarTransportista(DatosDeGuia.DeTransportista tr, String rucEmisor) {
        if (tr.remitente() == null) throw new DomainException("REMITENTE_INVALIDO", "3383 - Falta el remitente de los bienes");
        tr.remitente().exigirValida("remitente", "REMITENTE_INVALIDO", "3383", "3384", "3387");
        if (tr.remitente().esElRuc(rucEmisor)) throw new DomainException("REMITENTE_INVALIDO", "2560 - El remitente no puede ser el propio transportista: el remitente emite una guía remitente");
        TransportistaContratado.exigirRegistroMtc(tr.registroMtc(), true);
        return tr;
    }

    /** Para persistencia: reconstruye sin validar (los datos ya se validaron al emitir). */
    public static GuiaRemision rehidratar(UUID id, UUID tenantId, String serie, Long numero, LocalDate fechaEmision, LocalTime horaEmision, ParteDeGuia destinatario, DatosDeGuia datos,
                                         Traslado traslado, List<ItemDeGuia> items, List<DocumentoDeTraslado> documentos, String observaciones, EstadoGuia estado, Envio envio) {
        GuiaRemision g = new GuiaRemision(id, tenantId, serie, numero, fechaEmision, horaEmision, destinatario, datos, traslado, items, documentos, observaciones, estado);
        g.nombreArchivo = envio.nombreArchivo(); g.hash = envio.hash(); g.xmlKey = envio.xmlKey(); g.cdrKey = envio.cdrKey(); g.cdr = envio.cdr();
        g.ticket = envio.ticket(); g.fechaRecepcion = envio.fechaRecepcion(); g.intentos = envio.intentos(); g.consultasTicket = envio.consultasTicket();
        g.ultimoError = envio.ultimoError(); g.reintentoAutomatico = envio.reintentoAutomatico(); g.enlaceConsulta = envio.enlaceConsulta();
        return g;
    }

    /** Lo que cambia al firmar y al enviar: lo que la persistencia guarda aparte de los datos de la guía. */
    public record Envio(String nombreArchivo, String hash, String xmlKey, String cdrKey, Cdr cdr, String ticket, LocalDateTime fechaRecepcion, int intentos, int consultasTicket,
                        String ultimoError, boolean reintentoAutomatico, String enlaceConsulta) {}

    public Envio envio() {
        return new Envio(nombreArchivo, hash, xmlKey, cdrKey, cdr, ticket, fechaRecepcion, intentos, consultasTicket, ultimoError, reintentoAutomatico, enlaceConsulta);
    }

    public void asignarNumero(long numero, String rucEmisor) {
        if (this.numero != null) throw new DomainException("NUMERO_YA_ASIGNADO", "La guía ya tiene número");
        this.numero = numero;
        this.nombreArchivo = NombreArchivo.de(rucEmisor, tipo(), serie, numero);
    }

    public void firmar(String hash, String xmlKey) {
        if (numero == null) throw new DomainException("TRANSICION_INVALIDA", "La guía se numera antes de firmarse");
        transitar(EstadoGuia.FIRMADO);
        this.hash = hash; this.xmlKey = xmlKey;
    }

    /** SUNAT recibió el archivo y dio el ticket: khipu lo consulta solo. */
    public void marcarEnviada(String ticket, LocalDateTime fechaRecepcion) {
        if (ticket == null || ticket.isBlank()) throw new DomainException("TICKET_INVALIDO", "SUNAT no devolvió el número de ticket");
        transitar(EstadoGuia.ENVIADO_PENDIENTE_TICKET);
        this.ticket = ticket.strip(); this.fechaRecepcion = fechaRecepcion;
        this.intentos++; this.consultasTicket = 0; this.ultimoError = null; this.reintentoAutomatico = true;
    }

    /** El envío quedó reservado en la base (estado ENVIANDO): lo que sigue es la respuesta de SUNAT, o el error. */
    public void iniciarEnvio() {
        if (!estado.esEnviable() && estado != EstadoGuia.ENVIANDO) throw new DomainException("ESTADO_NO_ENVIABLE", "La guía está " + estado + ": no se envía");
        transitar(EstadoGuia.ENVIANDO);
    }

    /** El outbox agotó sus intentos: la guía sigue en error de envío, pero khipu ya no la reintenta sola. */
    public void agotarReintentos(String motivo) {
        if (estado != EstadoGuia.ERROR_ENVIO) throw new DomainException("TRANSICION_INVALIDA", "Solo se agotan los reintentos de una guía en ERROR_ENVIO; esta está " + estado);
        this.ultimoError = motivo; this.reintentoAutomatico = false;
    }

    /**
     * Códigos de «CódigosRetorno» que dicen que ese número ya está en SUNAT: 1032 (ya informado, anulado o rechazado), 1033 (registrado con otros datos), 2223
     * (el archivo ya fue presentado) y 4000 (el documento ya fue presentado). Reenviarlo da el mismo error para siempre.
     */
    static final Set<String> YA_EN_SUNAT = FaultsSunat.YA_EN_SUNAT;

    public static boolean yaEnSunat(String codigo) { return FaultsSunat.yaEnSunat(codigo); }

    /** El envío falló. {@code reintentoAutomatico}: si khipu lo reintenta solo (SUNAT no respondió) o hace falta corregir algo y reenviar a mano. */
    public void marcarErrorEnvio(String motivo, boolean reintentoAutomatico) {
        if (!estado.esEnviable() && estado != EstadoGuia.ENVIANDO) throw new DomainException("TRANSICION_INVALIDA", "No se registra un error de envío en una guía " + estado);
        transitar(EstadoGuia.ERROR_ENVIO);
        this.intentos++; this.ultimoError = motivo; this.reintentoAutomatico = reintentoAutomatico;
    }

    /** SUNAT respondió que el ticket sigue en proceso (98). */
    public void ticketEnProceso() {
        exigirEsperandoTicket();
        this.consultasTicket++; this.ultimoError = null;
    }

    /** La consulta del ticket no dio respuesta (SUNAT caída, credenciales) o se llegó al tope: {@code seguirConsultando} dice si khipu sigue solo. */
    public void ticketSinRespuesta(String motivo, boolean seguirConsultando) {
        exigirEsperandoTicket();
        this.consultasTicket++; this.ultimoError = motivo; this.reintentoAutomatico = seguirConsultando;
    }

    /** Se llegó al tope de consultas automáticas del ticket: la guía lo sigue esperando, pero khipu deja de consultarlo solo (la consulta ya se contó). */
    public void dejarDeConsultar(String motivo) {
        exigirEsperandoTicket();
        this.ultimoError = motivo; this.reintentoAutomatico = false;
    }

    /** Vuelve a consultar sola un ticket que había dejado de consultar (se pidió a mano y SUNAT sigue en 98). */
    public void reanudarConsultas() { exigirEsperandoTicket(); this.reintentoAutomatico = true; this.consultasTicket = 0; }

    /** El CDR del ticket (0 o 99 con CDR): aceptada, aceptada con observaciones o rechazada según su código. {@code enlace}: la URL de consulta del QR, si la trae. */
    public void aplicarCdr(Cdr cdr, String cdrKey, String enlace) {
        EstadoGuia destino = cdr.esRechazo() ? EstadoGuia.RECHAZADO : cdr.esObservado() ? EstadoGuia.ACEPTADO_CON_OBS : EstadoGuia.ACEPTADO;
        transitar(destino);
        this.cdr = cdr; this.cdrKey = cdrKey; this.enlaceConsulta = enlace; this.ultimoError = null; this.reintentoAutomatico = false;
    }

    /** SUNAT procesó el ticket con error y sin CDR (99): un rechazo (2000–3999) deja la guía rechazada; cualquier otro código, en error de envío y sin reintento. */
    public void errorDelTicket(String codigo, String descripcion) {
        exigirEsperandoTicket();
        Cdr r = new Cdr(codigo == null || codigo.isBlank() ? "99" : codigo.strip(), descripcion == null ? "" : descripcion.strip(), List.of());
        if (yaEnSunat(r.codigo())) {
            // El número ya está en SUNAT (otro envío llegó antes, o se registró con otros datos): reenviar daría el mismo error para siempre.
            transitar(EstadoGuia.ERROR_ENVIO);
            this.ultimoError = "La guía ya está registrada en SUNAT (" + r.codigo() + " - " + r.descripcion() + "): revísela en SOL (consulta de guías de remisión) en vez de reenviarla";
        } else if (r.codigo().matches("\\d+") && r.esRechazo()) {
            transitar(EstadoGuia.RECHAZADO);
            this.cdr = r; this.ultimoError = null;
        } else {
            transitar(EstadoGuia.ERROR_ENVIO);
            this.ultimoError = "SUNAT no procesó la guía (" + r.codigo() + " - " + r.descripcion() + "): corrija lo que indica y reenvíela";
        }
        this.reintentoAutomatico = false;
    }

    private void exigirEsperandoTicket() {
        if (!estado.esperaTicket()) throw new DomainException("TRANSICION_INVALIDA", "La guía no está esperando un ticket de SUNAT (estado " + estado + ")");
    }

    private void transitar(EstadoGuia destino) {
        if (!estado.puedeTransitarA(destino)) throw new DomainException("TRANSICION_INVALIDA", "No se puede pasar de " + estado + " a " + destino);
        estado = destino;
    }

    public TipoDocumento tipo() { return datos.tipo(); }
    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public String serie() { return serie; }
    public Long numero() { return numero; }
    public LocalDate fechaEmision() { return fechaEmision; }
    public LocalTime horaEmision() { return horaEmision; }
    public ParteDeGuia destinatario() { return destinatario; }
    public DatosDeGuia datos() { return datos; }
    public Traslado traslado() { return traslado; }
    public List<ItemDeGuia> items() { return items; }
    public List<DocumentoDeTraslado> documentos() { return documentos; }
    public String observaciones() { return observaciones; }
    public EstadoGuia estado() { return estado; }
    public String nombreArchivo() { return nombreArchivo; }
    public String hash() { return hash; }
    public String xmlKey() { return xmlKey; }
    public String cdrKey() { return cdrKey; }
    public Cdr cdr() { return cdr; }
    public String ticket() { return ticket; }
    public LocalDateTime fechaRecepcion() { return fechaRecepcion; }
    public int intentos() { return intentos; }
    public int consultasTicket() { return consultasTicket; }
    public String ultimoError() { return ultimoError; }
    public boolean reintentoAutomatico() { return reintentoAutomatico; }
    public String enlaceConsulta() { return enlaceConsulta; }

    /** La guía remitente, o vacío en una transportista. */
    public Optional<DatosDeGuia.DeRemitente> remitente() { return datos instanceof DatosDeGuia.DeRemitente r ? Optional.of(r) : Optional.empty(); }
    /** La guía transportista, o vacío en una remitente. */
    public Optional<DatosDeGuia.DeTransportista> transportista() { return datos instanceof DatosDeGuia.DeTransportista t ? Optional.of(t) : Optional.empty(); }
}
