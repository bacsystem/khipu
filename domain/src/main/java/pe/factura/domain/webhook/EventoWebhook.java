package pe.factura.domain.webhook;

import pe.factura.domain.DomainException;
import pe.factura.domain.documento.EstadoDocumento;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Los eventos a los que un integrador puede suscribir un webhook (#19): cada cambio de estado de un comprobante que le importa a quien integra. Los estados de paso
 * (RECIBIDO, FIRMADO, ENVIADO…) no se publican: el integrador recibe lo que cierra o detiene el trámite.
 */
public final class EventoWebhook {
    private EventoWebhook() {}

    /** El evento que khipu manda a pedido para probar el endpoint; no se puede suscribir. */
    public static final String PRUEBA = "webhook.prueba";
    /** Suscribirse a todos los eventos. */
    public static final String TODOS = "*";

    private static final Map<EstadoDocumento, String> POR_ESTADO = new LinkedHashMap<>();
    static {
        POR_ESTADO.put(EstadoDocumento.ACEPTADO, "comprobante.aceptado");
        POR_ESTADO.put(EstadoDocumento.ACEPTADO_CON_OBS, "comprobante.aceptado_con_obs");
        POR_ESTADO.put(EstadoDocumento.RECHAZADO, "comprobante.rechazado");
        POR_ESTADO.put(EstadoDocumento.ANULADO, "comprobante.anulado");
        POR_ESTADO.put(EstadoDocumento.ERROR_ENVIO, "comprobante.error_envio");
        POR_ESTADO.put(EstadoDocumento.FUERA_DE_PLAZO, "comprobante.fuera_de_plazo");
        POR_ESTADO.put(EstadoDocumento.DESCARTADO, "comprobante.descartado");
    }

    /** El evento que corresponde a haber entrado en ese estado; vacío si el estado no se publica. */
    public static Optional<String> deEstado(EstadoDocumento estado) { return Optional.ofNullable(POR_ESTADO.get(estado)); }

    /** Todos los eventos suscribibles, en un orden estable. */
    public static List<String> todos() { return List.copyOf(POR_ESTADO.values()); }

    /**
     * Los eventos pedidos por el integrador, validados y sin repetidos. {@code *} solo, equivale a todos. Una lista vacía o un evento que no existe lanza
     * {@code WEBHOOK_INVALIDO}.
     */
    public static List<String> normalizar(List<String> pedidos) {
        if (pedidos == null || pedidos.isEmpty()) throw new DomainException("WEBHOOK_INVALIDO", "Elija al menos un evento al que suscribirse");
        if (pedidos.size() == 1 && TODOS.equals(pedidos.get(0).strip())) return todos();
        List<String> resultado = new ArrayList<>();
        for (String p : pedidos) {
            String evento = p == null ? "" : p.strip();
            if (!POR_ESTADO.containsValue(evento))
                throw new DomainException("WEBHOOK_INVALIDO", "El evento «" + evento + "» no existe; los válidos son " + String.join(", ", todos()) + " o * para todos");
            if (!resultado.contains(evento)) resultado.add(evento);
        }
        return List.copyOf(resultado);
    }
}
