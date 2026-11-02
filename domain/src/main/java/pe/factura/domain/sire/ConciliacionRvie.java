package pe.factura.domain.sire;

import pe.factura.domain.DomainException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Concilia el registro de ventas de khipu con la propuesta del RVIE de SUNAT (#322), comprobante por comprobante (tipo, serie y número): los que SUNAT tiene y
 * khipu no, los que khipu tiene y SUNAT no, y los que están en los dos con algún campo distinto ({@link ImportesRvie#comparables()}). Lo de khipu se carga entero
 * ({@link #deKhipu}); lo de SUNAT se va comparando fila por fila ({@link #deSunat}), sin guardarlo. El detalle de cada lista se corta en {@link #MAX_DETALLE}; los
 * totales cuentan todo.
 */
public final class ConciliacionRvie {
    public static final int MAX_DETALLE = 500;

    public record Diferencia(String comprobante, String campo, String khipu, String sunat) {}

    /** {@code totalDifieren} cuenta comprobantes; {@code totalDiferencias}, campos (un comprobante puede diferir en varios). */
    public record Resultado(long registrosKhipu, long registrosSunat, long coinciden, long totalSoloEnSunat, long totalSoloEnKhipu, long totalDifieren, long totalDiferencias,
                            List<String> soloEnSunat, List<String> soloEnKhipu, List<Diferencia> difieren) {
        /** Si los dos registros dicen lo mismo: nada falta, nada sobra y nada difiere. */
        public boolean cuadra() { return totalSoloEnSunat == 0 && totalSoloEnKhipu == 0 && totalDifieren == 0; }
    }

    private final Map<String, ImportesRvie> khipu = new LinkedHashMap<>();
    private final Set<String> vistosEnSunat = new HashSet<>();
    private final List<String> soloEnSunat = new ArrayList<>();
    private final List<Diferencia> difieren = new ArrayList<>();
    private long registrosKhipu, registrosSunat, coinciden, totalSoloEnSunat, totalDifieren, totalDiferencias;

    public void deKhipu(ImportesRvie i) {
        if (khipu.putIfAbsent(i.clave(), i) != null) throw new IllegalStateException("El registro de khipu repite " + i.clave());
        registrosKhipu++;
    }

    public void deSunat(ImportesRvie s) {
        String clave = s.clave();
        if (!vistosEnSunat.add(clave))
            throw new DomainException("RVIE_ARCHIVO_INVALIDO", "La propuesta del RVIE repite el comprobante " + clave + ": un comprobante va una sola vez en el registro");
        registrosSunat++;
        ImportesRvie k = khipu.remove(clave);
        if (k == null) {
            totalSoloEnSunat++;
            if (soloEnSunat.size() < MAX_DETALLE) soloEnSunat.add(clave);
            return;
        }
        Map<String, String> deK = k.comparables(), deS = s.comparables();
        boolean distinto = false;
        for (Map.Entry<String, String> e : deK.entrySet()) {
            String enSunat = deS.get(e.getKey());
            if (e.getValue().equals(enSunat)) continue;
            // Una propuesta sin el campo 35 no dice el estado: no se lo cuenta como diferencia.
            if ("estado".equals(e.getKey()) && (enSunat == null || enSunat.isEmpty())) continue;
            distinto = true;
            totalDiferencias++;
            if (difieren.size() < MAX_DETALLE) difieren.add(new Diferencia(clave, e.getKey(), e.getValue(), enSunat));
        }
        if (distinto) totalDifieren++;
        else coinciden++;
    }

    /** El resultado: lo que quedó de khipu sin pareja en SUNAT es lo que SUNAT no tiene. */
    public Resultado resultado() {
        List<String> soloEnKhipu = khipu.keySet().stream().limit(MAX_DETALLE).toList();
        return new Resultado(registrosKhipu, registrosSunat, coinciden, totalSoloEnSunat, khipu.size(), totalDifieren, totalDiferencias, List.copyOf(soloEnSunat), soloEnKhipu,
                List.copyOf(difieren));
    }
}
