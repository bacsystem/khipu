package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase.PlanDeCuenta;
import pe.factura.application.port.in.ConsultarConsumoUseCase;
import pe.factura.application.port.in.ConsultarConsumoUseCase.ConsumoDeCuenta;
import pe.factura.application.port.out.CuentaRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.cuenta.Cuenta;
import pe.factura.domain.plan.*;
import pe.factura.domain.plataforma.ActorAdmin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/** C1/C7: lo que el cliente ve de su cuenta: cómo se llama, su plan con su estado de pago y lo que consumió este mes. */
class ConsultarMiCuentaServiceTest {
    final UUID cuentaId = UUID.randomUUID();
    final Map<UUID, Cuenta> cuentasMap = new HashMap<>();
    final CuentaRepository cuentas = new CuentaRepository() {
        public void guardar(Cuenta c) { cuentasMap.put(c.id(), c); }
        public Optional<Cuenta> buscar(UUID id) { return Optional.ofNullable(cuentasMap.get(id)); }
        public Optional<Cuenta> buscarPorEmail(String e) { return Optional.empty(); }
    };

    final Plan emprende = new Plan(UUID.randomUUID(), "Emprende", new BigDecimal("29.00"),
            new Limites(Limite.de(300), 1, Limite.de(1), Limite.de(2), 5), EstadoPlan.ACTIVO, false);
    final Suscripcion suscripcion = new Suscripcion(UUID.randomUUID(), cuentaId, emprende.id(), Instant.parse("2026-10-08T17:00:00Z"),
            Instant.parse("2027-01-09T05:00:00Z"), 5, null);
    final PlanDeCuenta planDeCuenta = new PlanDeCuenta(cuentaId, emprende, suscripcion, EstadoSuscripcion.VIGENTE, suscripcion.hastaCuandoCubre(), null);

    final CambiarPlanDeCuentaUseCase planes = new CambiarPlanDeCuentaUseCase() {
        public PlanDeCuenta plan(UUID id) {
            if (!id.equals(cuentaId)) throw new DomainException("NO_ENCONTRADO", "Cuenta no encontrada");
            return planDeCuenta;
        }
        public Previsualizacion previsualizar(UUID c, UUID p) { throw new AssertionError("el cliente solo mira"); }
        public PlanDeCuenta cambiar(ActorAdmin a, UUID c, UUID p, Instant v, Integer g) { throw new AssertionError("el cliente solo mira"); }
    };
    final ConsultarConsumoUseCase consumo = new ConsultarConsumoUseCase() {
        public YearMonth mesActual() { return YearMonth.of(2026, 10); }
        public ConsumoDeEmpresa deEmpresa(UUID t, YearMonth m) { throw new AssertionError("se mira la cuenta entera"); }
        public ConsumoDeCuenta deCuenta(UUID c, YearMonth m) {
            assertThat(m).as("el mes en curso").isNull();
            return new ConsumoDeCuenta(c, YearMonth.of(2026, 10), 12, List.of());
        }
    };
    final ConsultarMiCuentaService service = new ConsultarMiCuentaService(cuentas, planes, consumo);

    @Test void juntaElNombreElPlanYElConsumoDelMes() {
        cuentas.guardar(new Cuenta(cuentaId, "Ferretería Torres", "ana@negocio.pe", "987654321"));

        var mi = service.deLaCuenta(cuentaId);

        assertThat(mi.nombre()).isEqualTo("Ferretería Torres");
        assertThat(mi.plan()).isSameAs(planDeCuenta);
        assertThat(mi.consumo().documentos()).isEqualTo(12);
        assertThat(mi.consumo().mes()).isEqualTo(YearMonth.of(2026, 10));
    }

    @Test void unaCuentaQueNoExisteEsNoEncontrado() {
        assertThatThrownBy(() -> service.deLaCuenta(UUID.randomUUID())).extracting("codigo").isEqualTo("NO_ENCONTRADO");
    }
}
