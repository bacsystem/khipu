package pe.factura.application.service;

import pe.factura.application.port.out.AvisosRepository;
import pe.factura.domain.plataforma.MotivoDeAviso;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Los avisos a los clientes en memoria (#197), con la misma regla del real: una reserva solo entra si no hay otra del mismo motivo para la empresa después de {@code desde}. */
final class AvisosFake implements AvisosRepository {
    List<FilaCertificado> certificados = new ArrayList<>();
    List<FilaSol> sol = new ArrayList<>();
    final Map<UUID, Situacion> situaciones = new HashMap<>();
    final List<AvisoRegistrado> avisos = new ArrayList<>();
    final List<Object[]> consultas = new ArrayList<>();
    final List<Boolean> reservadoDentro = new ArrayList<>();
    final List<UUID> anulados = new ArrayList<>();
    Fakes.UowTransaccional uow;
    LocalDate hoyVisto;

    public List<FilaCertificado> certificados(LocalDate hoy, int pagina, int porPagina) { hoyVisto = hoy; consultas.add(new Object[]{"certificados", pagina, porPagina}); return certificados; }
    public long contarCertificados(LocalDate hoy) { return certificados.size(); }
    public List<FilaSol> credencialesSol(int pagina, int porPagina) { consultas.add(new Object[]{"sol", pagina, porPagina}); return sol; }
    public long contarCredencialesSol() { return sol.size(); }
    public Optional<Situacion> situacionDe(UUID empresaId, LocalDate hoy) { hoyVisto = hoy; return Optional.ofNullable(situaciones.get(empresaId)); }

    public Map<UUID, AvisoRegistrado> ultimosAvisos(Collection<UUID> empresas, MotivoDeAviso motivo) {
        Map<UUID, AvisoRegistrado> r = new HashMap<>();
        for (AvisoRegistrado a : avisos)
            if (a.motivo() == motivo && empresas.contains(a.empresaId())) r.merge(a.empresaId(), a, (x, y) -> x.enviadoEn().isAfter(y.enviadoEn()) ? x : y);
        return r;
    }

    public Optional<AvisoRegistrado> reservar(AvisoRegistrado nuevo, Instant desde) {
        reservadoDentro.add(uow != null && uow.dentro);
        Optional<AvisoRegistrado> previo = avisos.stream().filter(a -> a.empresaId().equals(nuevo.empresaId()) && a.motivo() == nuevo.motivo() && a.enviadoEn().isAfter(desde)).findFirst();
        if (previo.isPresent()) return previo;
        avisos.add(nuevo);
        return Optional.empty();
    }

    public void anular(UUID avisoId) {
        anulados.add(avisoId);
        avisos.removeIf(a -> a.id().equals(avisoId));
    }
}
