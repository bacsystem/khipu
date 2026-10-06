package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.AltaAsistidaUseCase;
import pe.factura.application.port.in.Idempotencia;
import pe.factura.application.port.out.*;
import pe.factura.application.port.out.SesionRepository.TokenRecuperacion;
import pe.factura.domain.DomainException;
import pe.factura.domain.cuenta.Cuenta;
import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.cuenta.Usuario;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.RegistroAuditoria;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Ruc;
import pe.factura.domain.tenant.Serie;
import pe.factura.domain.tenant.Tenant;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public class AltaAsistidaService implements AltaAsistidaUseCase {
    /** Más larga que la de recuperar contraseña (1 hora): quien recibe una invitación no la abre necesariamente hoy. */
    static final Duration VIDA_INVITACION = Duration.ofDays(7);

    private final CuentaRepository cuentas;
    private final UsuarioRepository usuarios;
    private final SesionRepository sesiones;
    private final TenantRepository tenants;
    private final SerieRepository series;
    private final ApiKeyRepository apiKeys;
    private final PasswordHasher hasher;
    private final CorreoSender correo;
    private final UnitOfWork uow;
    private final AuditoriaAdminRepository auditoria;
    private final String pepper;
    private final Clock clock;
    private final IdempotenciaRepository idempotencia;
    private final SecretCipher cifrador;

    /** Las altas no son de ninguna empresa: un solo alcance para todas las claves. */
    static final String ALCANCE = "alta-cuenta";
    /** Separador de la respuesta guardada: ningún campo lo puede contener (la razón social no admite caracteres de control, 4338). */
    private static final String SEP = "\u001f";

    @Override
    public Resultado alta(ActorAdmin actor, Solicitud s, String urlPortal, Idempotencia clave) {
        // Un reintento se reconoce antes de validar: si no, el correo ya registrado lo convertiría en un 409 DUPLICADO.
        if (clave != null) {
            Optional<IdempotenciaRepository.Registro> previo = idempotencia.buscar(ALCANCE, clave.clave());
            if (previo.isPresent()) return repetida(previo.get(), clave);
        }
        // Todo se valida antes de escribir nada: una solicitud inválida no deja ni un rastro.
        Cuenta cuenta = new Cuenta(UUID.randomUUID(), s.nombre(), s.email(), s.telefono());
        Ruc.exigirValido(s.ruc(), "RUC_INVALIDO", "Empresa");
        Entorno entorno = s.entorno() == null ? Entorno.BETA : s.entorno();
        Tenant tenant = new Tenant(UUID.randomUUID(), s.ruc(), s.razonSocial(), entorno, null, null);
        TipoDocumento tipo = s.tipoSerie();
        if (tipo == null) throw new DomainException("SERIE_INVALIDA", "El tipo de la primera serie es obligatorio");
        if (!tipo.serieValida(s.serie())) throw new DomainException("SERIE_INVALIDA", "Serie " + s.serie() + " no válida para " + tipo);
        if (cuentas.buscarPorEmail(cuenta.email()).isPresent() || usuarios.buscarPorEmail(cuenta.email()).isPresent())
            throw new DomainException("DUPLICADO", "Ya existe una cuenta con ese correo");
        if (tenants.buscarPorRuc(s.ruc()).isPresent()) throw new DomainException("DUPLICADO", "Ya existe una empresa con RUC " + s.ruc());

        // El administrador no elige la contraseña: queda una que nadie conoce, y el cliente fija la suya con la invitación.
        Usuario usuario = new Usuario(UUID.randomUUID(), cuenta.id(), cuenta.email(), hasher.hash(TokenOpaco.generar()), Rol.ADMIN, true);
        String apiKey = ApiKeyGenerator.generar();
        Instant ahora = Instant.now(clock);
        String invitacion = TokenOpaco.generar();

        Optional<IdempotenciaRepository.Registro> simultaneo = uow.ejecutar(() -> {
            // Lo primero de la transacción: un pedido simultáneo con la misma clave espera aquí a que el primero termine y devuelve su
            // respuesta, en vez de chocar con el correo ya registrado.
            if (clave != null) {
                Optional<IdempotenciaRepository.Registro> previo = idempotencia.reservar(ALCANCE, clave.clave(), clave.huella());
                if (previo.isPresent()) return previo;
            }
            cuentas.guardar(cuenta);
            usuarios.guardar(usuario);
            tenants.guardar(tenant);
            tenants.asignarCuenta(tenant.id(), cuenta.id());
            apiKeys.guardar(ApiKeyGenerator.nueva(tenant.id(), apiKey, pepper, ahora));
            series.crear(new Serie(tenant.id(), tipo, s.serie(), 0, true, null));
            sesiones.crearRecuperacion(new TokenRecuperacion(TokenOpaco.hash(invitacion), usuario.id(), ahora.plus(VIDA_INVITACION), false));
            // En la misma transacción: si la bitácora falla, el alta tampoco queda. El detalle no lleva la API key, el token ni el correo.
            auditoria.registrar(RegistroAuditoria.de(actor, AccionAdmin.CREAR_CUENTA, cuenta.id(), tenant.id(),
                    "ruc=" + s.ruc() + " serie=" + s.serie() + " entorno=" + entorno, ahora));
            // La respuesta, con la API key, se guarda junto con el alta: si la respuesta se pierde, el reintento la recupera.
            if (clave != null) idempotencia.completar(ALCANCE, clave.clave(), cuenta.id(), cifrada(new AltaCreada(cuenta.id(), tenant, apiKey, tipo, s.serie(), false)));
            return Optional.<IdempotenciaRepository.Registro>empty();
        });
        if (simultaneo.isPresent()) return repetida(simultaneo.get(), clave);

        AltaCreada creada = new AltaCreada(cuenta.id(), tenant, apiKey, tipo, s.serie(), enviarInvitacion(usuario.email(), tenant, urlPortal, invitacion));
        // Con la invitación ya enviada, la respuesta guardada lo refleja: el reintento muestra lo mismo que vio el administrador.
        if (clave != null && creada.invitacionEnviada()) idempotencia.completar(ALCANCE, clave.clave(), cuenta.id(), cifrada(creada));
        return new Resultado(creada, false);
    }

    /** La misma clave con otro pedido es un error del cliente; pasada la hora, la API key ya no se guarda y no se puede devolver. */
    private Resultado repetida(IdempotenciaRepository.Registro previo, Idempotencia clave) {
        if (!previo.huella().equals(clave.huella()))
            throw new DomainException("IDEMPOTENCIA_INVALIDA", "La clave de idempotencia " + clave.clave()
                    + " ya se usó con otro contenido: genere una clave nueva para otra alta");
        if (previo.respuestaCifrada() == null || vencida(previo))
            throw new DomainException("IDEMPOTENCIA_VENCIDA", "Esta alta ya se hizo y su API key inicial ya no se puede volver a mostrar: "
                    + "el cliente puede crear otra API key desde su portal");
        return new Resultado(desdeCifrada(previo.respuestaCifrada()), true);
    }

    /**
     * La hora de la API key se cumple aquí, al leer: la limpieza (#115) corre cada hora y solo olvida lo que ya tiene más de una, así
     * que la respuesta podía seguir guardada hasta unas dos horas y salir en un reintento. Con la misma vigencia que usa la limpieza.
     */
    private boolean vencida(IdempotenciaRepository.Registro previo) {
        return previo.creadoAt() != null && !Instant.now(clock).isBefore(previo.creadoAt().plus(LimpiarIdempotenciaService.VIGENCIA_RESPUESTA));
    }

    private byte[] cifrada(AltaCreada a) {
        Tenant t = a.tenant();
        String texto = String.join(SEP, a.cuentaId().toString(), t.id().toString(), t.ruc(), t.razonSocial(), t.entorno().name(),
                a.apiKeyEnClaro(), a.tipoSerie().name(), a.serie(), String.valueOf(a.invitacionEnviada()));
        return cifrador.cifrar(texto.getBytes(StandardCharsets.UTF_8));
    }

    private AltaCreada desdeCifrada(byte[] cifrada) {
        String[] c = new String(cifrador.descifrar(cifrada), StandardCharsets.UTF_8).split(SEP, -1);
        Tenant tenant = new Tenant(UUID.fromString(c[1]), c[2], c[3], Entorno.valueOf(c[4]), null, null);
        return new AltaCreada(UUID.fromString(c[0]), tenant, c[5], TipoDocumento.valueOf(c[6]), c[7], Boolean.parseBoolean(c[8]));
    }

    /**
     * Fuera de la transacción: un SMTP caído no debe impedir el alta, y el cliente puede pedir otro enlace con «olvidé mi contraseña».
     * Devuelve si el correo salió de verdad: sin SMTP el adaptador lo escribe en el log y no lanza, y eso no es una entrega (sigue
     * «enviándose» para que en desarrollo el enlace se lea en el log).
     */
    private boolean enviarInvitacion(String email, Tenant tenant, String urlPortal, String token) {
        try {
            correo.enviar(email, "Te damos la bienvenida a khipu",
                    "Dimos de alta a " + tenant.razonSocial() + " (RUC " + tenant.ruc() + ") en khipu.\n"
                            + "Para entrar, crea tu contraseña en este enlace (válido 7 días, de un solo uso):\n"
                            + urlPortal + "/restablecer/" + token + "?invitacion=1");
            return correo.entregaDeVerdad();
        } catch (RuntimeException e) {
            // La causa la registra el adaptador de correo, que es quien la conoce (este módulo no tiene logger). Aquí solo importa
            // que el alta quedó hecha y la invitación no salió.
            return false;
        }
    }
}
