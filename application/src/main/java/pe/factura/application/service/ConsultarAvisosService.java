package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.ConsultarAvisosUseCase;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EstadoCertificado;
import pe.factura.application.port.out.AvisosRepository;
import pe.factura.application.port.out.AvisosRepository.AvisoRegistrado;
import pe.factura.application.port.out.AvisosRepository.Destino;
import pe.factura.application.port.out.AvisosRepository.FilaCertificado;
import pe.factura.application.port.out.AvisosRepository.FilaSol;
import pe.factura.domain.plataforma.MotivoDeAviso;
import pe.factura.domain.plataforma.TipoDeAviso;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Las listas de avisos del backoffice (#197). Solo lectura. El repositorio trae las empresas con su cuenta; acá se decide el motivo de cada una, se le pega el último aviso **de ese
 * motivo** y se calcula si se puede avisar ahora: hay a quién escribirle y no se avisó lo mismo hace menos de {@link MotivoDeAviso#ENFRIAMIENTO}.
 */
@RequiredArgsConstructor
public class ConsultarAvisosService implements ConsultarAvisosUseCase {
    private final AvisosRepository avisos;
    private final Clock clock;

    @Override public PaginaDeCertificados certificados(int pagina, int porPagina) {
        Instant ahora = clock.instant();
        List<FilaCertificado> filas = avisos.certificados(LocalDate.now(clock), pagina, porPagina);
        Map<UUID, AvisoRegistrado> ultimos = new HashMap<>();
        for (MotivoDeAviso m : MotivoDeAviso.de(TipoDeAviso.CERTIFICADO)) {
            List<UUID> ids = filas.stream().filter(f -> motivoDe(f.estado()) == m).map(FilaCertificado::empresaId).toList();
            if (!ids.isEmpty()) ultimos.putAll(avisos.ultimosAvisos(ids, m));
        }
        List<CertificadoEnRiesgo> resultado = filas.stream().map(f -> {
            MotivoDeAviso motivo = motivoDe(f.estado());
            AvisoRegistrado ultimo = ultimos.get(f.empresaId());
            Instant desde = esperaHasta(motivo, ultimo, ahora);
            return new CertificadoEnRiesgo(f.empresaId(), f.ruc(), f.razonSocial(), cuenta(f.cuenta()), motivo, f.vigenteHasta(), f.diasRestantes(), ultimo(ultimo), desde,
                    puedeAvisar(f.cuenta(), desde));
        }).toList();
        return new PaginaDeCertificados(resultado, avisos.contarCertificados(LocalDate.now(clock)));
    }

    @Override public PaginaDeSol credencialesSol(int pagina, int porPagina) {
        Instant ahora = clock.instant();
        List<FilaSol> filas = avisos.credencialesSol(pagina, porPagina);
        Map<UUID, AvisoRegistrado> ultimos = filas.isEmpty() ? Map.of()
                : avisos.ultimosAvisos(filas.stream().map(FilaSol::empresaId).collect(Collectors.toList()), MotivoDeAviso.CREDENCIALES_SOL_INVALIDAS);
        List<SolFallando> resultado = filas.stream().map(f -> {
            AvisoRegistrado ultimo = ultimos.get(f.empresaId());
            Instant desde = esperaHasta(MotivoDeAviso.CREDENCIALES_SOL_INVALIDAS, ultimo, ahora);
            return new SolFallando(f.empresaId(), f.ruc(), f.razonSocial(), cuenta(f.cuenta()), f.comprobantesAfectados(), f.ultimoFallo(), f.ultimoError(), ultimo(ultimo), desde,
                    puedeAvisar(f.cuenta(), desde));
        }).toList();
        return new PaginaDeSol(resultado, avisos.contarCredencialesSol());
    }

    private static MotivoDeAviso motivoDe(EstadoCertificado estado) {
        return switch (estado) {
            case VENCIDO -> MotivoDeAviso.CERTIFICADO_VENCIDO;
            case POR_VENCER -> MotivoDeAviso.CERTIFICADO_POR_VENCER;
            default -> throw new IllegalStateException("Un certificado " + estado + " no está en riesgo");
        };
    }

    /** Desde cuándo se puede repetir el aviso; nulo si nunca se avisó o la espera ya pasó. */
    private static Instant esperaHasta(MotivoDeAviso motivo, AvisoRegistrado ultimo, Instant ahora) {
        if (ultimo == null) return null;
        Instant desde = motivo.avisarDesde(ultimo.enviadoEn());
        return desde.isAfter(ahora) ? desde : null;
    }

    private static boolean puedeAvisar(Destino destino, Instant esperaHasta) {
        return destino.email() != null && esperaHasta == null;
    }

    private static CuentaDelCliente cuenta(Destino d) { return d.cuentaId() == null ? null : new CuentaDelCliente(d.cuentaId(), d.nombre(), d.email()); }

    private static UltimoAviso ultimo(AvisoRegistrado a) { return a == null ? null : new UltimoAviso(a.enviadoEn(), a.destinatario()); }
}
