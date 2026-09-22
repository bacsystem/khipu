package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.VerificarIntegridadUseCase;
import pe.factura.application.port.out.ComprobanteRepository;
import pe.factura.application.port.out.DocumentStorage;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Integridad del storage (#38). Para cada comprobante firmado del rango: el XML existe y contiene el DigestValue con el
 * que se firmó (el {@code hash} que guardamos y que va impreso en el PDF y el QR: si el objeto se truncó o alguien lo
 * tocó, ese valor ya no aparece o el documento no cierra); y si SUNAT respondió, el CDR existe. Solo lee: no repara nada.
 */
@RequiredArgsConstructor
public class VerificarIntegridadService implements VerificarIntegridadUseCase {
    private final ComprobanteRepository comprobantes;
    private final DocumentStorage storage;

    @Override
    public Informe verificar(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || hasta.isBefore(desde))
            throw new DomainException("RANGO_INVALIDO", "El rango de fechas es obligatorio y desde ≤ hasta");
        List<Problema> problemas = new ArrayList<>();
        int verificados = 0;
        for (Comprobante c : comprobantes.firmadosEmitidosEntre(desde, hasta)) {
            verificados++;
            problemas.addAll(verificar(c));
        }
        return new Informe(desde, hasta, verificados, List.copyOf(problemas));
    }

    private List<Problema> verificar(Comprobante c) {
        List<Problema> p = new ArrayList<>();
        try {
            if (!storage.existe(c.xmlKey())) {
                p.add(problema(c, "XML_FALTANTE", c.xmlKey()));
            } else {
                String xml = new String(storage.leer(c.xmlKey()), StandardCharsets.UTF_8);
                if (c.hash() != null && !xml.contains(">" + c.hash() + "</"))
                    p.add(problema(c, "XML_CORRUPTO", "el DigestValue registrado (" + c.hash() + ") no está en " + c.xmlKey()));
                else if (!xml.stripTrailing().endsWith(">"))
                    p.add(problema(c, "XML_CORRUPTO", c.xmlKey() + " no termina en una etiqueta: truncado"));
            }
            if (c.cdrKey() != null && !storage.existe(c.cdrKey()))
                p.add(problema(c, "CDR_FALTANTE", c.cdrKey()));
        } catch (RuntimeException e) {
            p.add(problema(c, "STORAGE_INACCESIBLE", e.getMessage()));
        }
        return p;
    }

    private static Problema problema(Comprobante c, String tipo, String detalle) {
        return new Problema(c.id(), c.tenantId(), c.nombreArchivo(), tipo, detalle);
    }
}
