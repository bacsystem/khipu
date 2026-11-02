package pe.factura.application.port.in;

import pe.factura.domain.plataforma.BannerDeMantenimiento;

import java.util.Optional;

/** El aviso de mantenimiento que se muestra ahora a todos los clientes (#199). Es público: lo lee el portal antes de que nadie haya iniciado sesión. */
public interface ConsultarBannerUseCase {
    /** El aviso, solo si su vigencia incluye el instante actual; uno programado para después o ya vencido no se muestra. */
    Optional<BannerDeMantenimiento> vigente();
}
