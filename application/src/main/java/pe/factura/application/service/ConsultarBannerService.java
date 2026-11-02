package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.ConsultarBannerUseCase;
import pe.factura.application.port.out.BannerRepository;
import pe.factura.domain.plataforma.BannerDeMantenimiento;

import java.time.Clock;
import java.util.Optional;

@RequiredArgsConstructor
public class ConsultarBannerService implements ConsultarBannerUseCase {
    private final BannerRepository banners;
    private final Clock clock;

    @Override public Optional<BannerDeMantenimiento> vigente() {
        return banners.buscar().map(BannerRepository.Guardado::banner).filter(b -> b.vigenteEn(clock.instant()));
    }
}
