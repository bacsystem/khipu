package pe.factura.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Listado de cuentas de clientes para el backoffice del administrador (#180). Lectura pura: no se audita (la bitácora
 * de #178 registra acciones, no consultas). El estado y el plan de cada cuenta llegan con #182 y #189, cuando existan.
 */
public interface ListarCuentasAdminUseCase {
    List<CuentaResumen> listar(Filtro filtro, int pagina, int porPagina);
    long contar(Filtro filtro);

    /**
     * Una fila del listado. {@code ultimoAcceso} es la sesión más reciente de los usuarios de la cuenta (inicio de sesión o
     * refresco de token en el portal); el uso por API key no cuenta. Nulo si la cuenta nunca inició sesión. {@code suspendidaEn} es nulo si
     * la cuenta está activa (#182); una cuenta suspendida sigue en el listado, para poder reactivarla.
     */
    record CuentaResumen(UUID id, String nombre, String email, String telefono, Instant creadaEn, int empresas, Instant ultimoAcceso, Instant suspendidaEn,
                         Instant bajaEn) {}

    /**
     * {@code q}: búsqueda libre sobre el correo y el nombre de la cuenta, y sobre el RUC y la razón social de sus empresas.
     * Nula si viene vacía o en blanco. {@code bajas}: qué hacer con las cuentas dadas de baja (#201); por defecto, ocultarlas.
     */
    record Filtro(String q, VisibilidadDeBajas bajas) {
        public static final Filtro NINGUNO = new Filtro(null);
        public Filtro {
            q = q == null || q.isBlank() ? null : q.strip();
            bajas = bajas == null ? VisibilidadDeBajas.OCULTAS : bajas;
        }
        public Filtro(String q) { this(q, VisibilidadDeBajas.OCULTAS); }
    }
}
