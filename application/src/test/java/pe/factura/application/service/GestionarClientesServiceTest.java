package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.GestionarClientesUseCase.Guardado;
import pe.factura.application.port.out.ClienteRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.ClienteGuardado;
import pe.factura.domain.documento.ClienteGuardado.Datos;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Clientes guardados (#319): alta con duplicado, guardado por documento idempotente, cambio de documento, páginas y aislamiento por empresa. */
class GestionarClientesServiceTest {
    static final Instant AHORA = Instant.parse("2026-10-19T15:00:00Z");
    static final UUID T = UUID.randomUUID(), OTRA = UUID.randomUUID();

    static class Repo implements ClienteRepository {
        final Map<UUID, ClienteGuardado> filas = new LinkedHashMap<>();
        private boolean hayOtroConEseDocumento(ClienteGuardado c) {
            return filas.values().stream().anyMatch(x -> !x.id().equals(c.id()) && x.tenantId().equals(c.tenantId()) && x.tipoDoc().equals(c.tipoDoc()) && x.numDoc().equals(c.numDoc()));
        }
        public boolean crear(ClienteGuardado c) { if (hayOtroConEseDocumento(c)) return false; filas.put(c.id(), c); return true; }
        public boolean actualizar(ClienteGuardado c) {
            if (!filas.containsKey(c.id()) || !filas.get(c.id()).tenantId().equals(c.tenantId()) || hayOtroConEseDocumento(c)) return false;
            filas.put(c.id(), c);
            return true;
        }
        public Optional<ClienteGuardado> buscar(UUID t, UUID id) { return Optional.ofNullable(filas.get(id)).filter(c -> c.tenantId().equals(t)); }
        public Optional<ClienteGuardado> buscarPorDocumento(UUID t, String tipo, String num) {
            return filas.values().stream().filter(c -> c.tenantId().equals(t) && c.tipoDoc().equals(tipo) && c.numDoc().equals(num)).findFirst();
        }
        List<ClienteGuardado> deLaEmpresa(UUID t, String texto) {
            return filas.values().stream().filter(c -> c.tenantId().equals(t) && (texto == null || c.razonSocial().toLowerCase().contains(texto.toLowerCase()))).toList();
        }
        public List<ClienteGuardado> listar(UUID t, String texto, int pagina, int por) {
            List<ClienteGuardado> todos = new ArrayList<>(deLaEmpresa(t, texto));
            return todos.subList(Math.min((pagina - 1) * por, todos.size()), Math.min(pagina * por, todos.size()));
        }
        public long contar(UUID t, String texto) { return deLaEmpresa(t, texto).size(); }
        public boolean eliminar(UUID t, UUID id) { return buscar(t, id).isPresent() && filas.remove(id) != null; }
    }

    static class Directo implements UnitOfWork {
        public <T> T ejecutar(Supplier<T> t) { return t.get(); }
        public void ejecutar(Runnable t) { t.run(); }
    }

    Repo repo = new Repo();
    GestionarClientesService servicio = new GestionarClientesService(repo, new Directo(), Clock.fixed(AHORA, ZoneOffset.UTC));

    static Datos datos(String num, String nombre) { return new Datos("1", num, nombre, null, null, null); }

    @Test void crearGuardaYUnDocumentoRepetidoEsDuplicadoSinEscribirNada() {
        ClienteGuardado c = servicio.crear(T, datos("12345678", "JUAN PEREZ"));

        assertThat(repo.filas).containsKey(c.id());
        assertThatThrownBy(() -> servicio.crear(T, datos("12345678", "OTRO"))).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("DUPLICADO");
        assertThat(repo.filas).hasSize(1);
        assertThat(servicio.crear(OTRA, datos("12345678", "JUAN PEREZ")).tenantId()).as("otra empresa puede tener el mismo documento").isEqualTo(OTRA);
    }

    @Test void unDatoInvalidoNoSeGuarda() {
        assertThatThrownBy(() -> servicio.crear(T, datos("123", "JUAN PEREZ"))).hasMessageContaining("4207");
        assertThat(repo.filas).isEmpty();
    }

    @Test void guardarPorDocumentoCreaLaPrimeraVezYActualizaLasSiguientesSinDuplicar() {
        Guardado primero = servicio.guardarPorDocumento(T, datos("12345678", "JUAN PEREZ"));
        Guardado segundo = servicio.guardarPorDocumento(T, new Datos("1", "12345678", "JUAN PEREZ GOMEZ", "Av. Lima 1", null, "j@p.pe"));
        Guardado tercero = servicio.guardarPorDocumento(T, new Datos("1", "12345678", "JUAN PEREZ GOMEZ", "Av. Lima 1", null, "j@p.pe"));

        assertThat(primero.creado()).isTrue();
        assertThat(segundo.creado()).isFalse();
        assertThat(tercero.creado()).isFalse();
        assertThat(repo.filas).hasSize(1);
        ClienteGuardado guardado = repo.filas.values().iterator().next();
        assertThat(guardado.id()).isEqualTo(primero.cliente().id());
        assertThat(guardado.razonSocial()).isEqualTo("JUAN PEREZ GOMEZ");
        assertThat(guardado.direccion()).isEqualTo("Av. Lima 1");
        assertThat(guardado.creadoEn()).isEqualTo(AHORA);
    }

    @Test void guardarPorDocumentoConservaLaDireccionElPaisYElCorreoQueNoLlegan() {
        servicio.crear(T, new Datos("1", "12345678", "JUAN PEREZ", "Av. Lima 1", "PE", "j@p.pe"));

        Guardado g = servicio.guardarPorDocumento(T, datos("12345678", "JUAN PEREZ GOMEZ"));

        assertThat(g.creado()).isFalse();
        assertThat(g.cliente().razonSocial()).as("el nombre sí se actualiza").isEqualTo("JUAN PEREZ GOMEZ");
        assertThat(g.cliente().direccion()).isEqualTo("Av. Lima 1");
        assertThat(g.cliente().pais()).isEqualTo("PE");
        assertThat(g.cliente().email()).isEqualTo("j@p.pe");
        Guardado conOtraDireccion = servicio.guardarPorDocumento(T, new Datos("1", "12345678", "JUAN PEREZ GOMEZ", "Av. Arequipa 9", null, null));
        assertThat(conOtraDireccion.cliente().direccion()).as("lo que sí llega reemplaza").isEqualTo("Av. Arequipa 9");
        assertThat(conOtraDireccion.cliente().email()).isEqualTo("j@p.pe");
    }

    /** Un repositorio al que otra sesión le borra el cliente justo antes de actualizarlo. */
    static class RepoConBajaConcurrente extends Repo {
        @Override public boolean actualizar(ClienteGuardado c) { filas.remove(c.id()); return super.actualizar(c); }
    }

    @Test void siElClienteSeBorraMientrasSeGuardaNoSeResponde200ConUnClienteInexistente() {
        RepoConBajaConcurrente repoConBaja = new RepoConBajaConcurrente();
        GestionarClientesService s = new GestionarClientesService(repoConBaja, new Directo(), Clock.fixed(AHORA, ZoneOffset.UTC));
        s.crear(T, datos("12345678", "JUAN PEREZ"));

        assertThatThrownBy(() -> s.guardarPorDocumento(T, datos("12345678", "JUAN PEREZ GOMEZ"))).extracting("codigo").isEqualTo("ESTADO_CONFLICTO");
        ClienteGuardado otro = s.crear(T, datos("87654321", "MARIA LOPEZ"));
        assertThatThrownBy(() -> s.actualizar(T, otro.id(), datos("87654321", "MARIA LOPEZ DIAZ"))).as("la fila ya no está: no es un duplicado").extracting("codigo").isEqualTo("NO_ENCONTRADO");
    }

    @Test void obtenerYEliminarSoloDeLaPropiaEmpresa() {
        ClienteGuardado c = servicio.crear(T, datos("12345678", "JUAN PEREZ"));

        assertThat(servicio.obtener(T, c.id())).isEqualTo(c);
        assertThatThrownBy(() -> servicio.obtener(OTRA, c.id())).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThatThrownBy(() -> servicio.eliminar(OTRA, c.id())).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        servicio.eliminar(T, c.id());
        assertThatThrownBy(() -> servicio.eliminar(T, c.id())).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThat(repo.filas).isEmpty();
    }

    @Test void actualizarPuedeCambiarElDocumentoPeroNoAUnoQueYaEsDeOtro() {
        ClienteGuardado a = servicio.crear(T, datos("12345678", "JUAN PEREZ"));
        servicio.crear(T, datos("87654321", "MARIA LOPEZ"));

        ClienteGuardado cambiado = servicio.actualizar(T, a.id(), datos("11111111", "JUAN PEREZ"));
        assertThat(cambiado.numDoc()).isEqualTo("11111111");
        assertThatThrownBy(() -> servicio.actualizar(T, a.id(), datos("87654321", "JUAN PEREZ"))).extracting("codigo").isEqualTo("DUPLICADO");
        assertThat(repo.filas.get(a.id()).numDoc()).isEqualTo("11111111");
        assertThatThrownBy(() -> servicio.actualizar(OTRA, a.id(), datos("22222222", "X NOMBRE"))).extracting("codigo").isEqualTo("NO_ENCONTRADO");
    }

    @Test void listarPaginaBuscaYValidaLaPaginacion() {
        for (int i = 0; i < 5; i++) servicio.crear(T, datos("1000000" + i, "CLIENTE " + i));
        servicio.crear(OTRA, datos("20000000", "CLIENTE AJENO"));

        var p1 = servicio.listar(T, "  ", 1, 2);
        var buscada = servicio.listar(T, " cliente 3 ", 1, 20);

        assertThat(p1.total()).isEqualTo(5);
        assertThat(p1.clientes()).hasSize(2);
        assertThat(servicio.listar(T, null, 3, 2).clientes()).hasSize(1);
        assertThat(buscada.clientes()).extracting(ClienteGuardado::razonSocial).containsExactly("CLIENTE 3");
        assertThatThrownBy(() -> servicio.listar(T, null, 0, 20)).extracting("codigo").isEqualTo("PAGINACION_INVALIDA");
        assertThatThrownBy(() -> servicio.listar(T, null, 1, 101)).extracting("codigo").isEqualTo("PAGINACION_INVALIDA");
    }
}
