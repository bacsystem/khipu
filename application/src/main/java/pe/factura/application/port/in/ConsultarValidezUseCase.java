package pe.factura.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ConsultarValidezUseCase {
    record Criterios(String rucEmisor, String tipo, String serie, long numero, String tipoDocReceptor, String numDocReceptor, LocalDate fechaEmision, BigDecimal importeTotal) {}

    /** Por dónde respondió SUNAT (#318): la API REST con las credenciales API SUNAT, o {@code billValidService} (SOAP) con la clave SOL. */
    enum Fuente { API_REST, SOAP }

    /**
     * Estado que SUNAT informa de un comprobante (propio o de un tercero) por sus datos. {@code codigo} es el de la fuente: {@code statusCode} de
     * billValidService (0001…) o {@code estadoCp} de la API REST (0…4). {@code estadoRuc}, {@code condicionDomicilio} y {@code observaciones} solo los da
     * la API REST (nulos y vacía por SOAP). {@code aviso}: por qué se respondió por SOAP teniendo credenciales API (la API no respondió); nulo si no.
     */
    record Validez(String estado, String codigo, String mensaje, Fuente fuente, String estadoRuc, String condicionDomicilio, List<String> observaciones, String aviso) {
        public Validez { observaciones = observaciones == null ? List.of() : List.copyOf(observaciones); }
        public Validez(String estado, String codigo, String mensaje) { this(estado, codigo, mensaje, Fuente.SOAP, null, null, List.of(), null); }
    }
    Validez consultar(UUID tenantId, Criterios criterios);
}
