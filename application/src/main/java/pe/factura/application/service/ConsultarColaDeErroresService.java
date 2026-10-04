package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.ConsultarColaDeErroresUseCase;
import pe.factura.application.port.out.ColaDeErroresRepository;
import pe.factura.application.port.out.ColaDeErroresRepository.Fila;
import pe.factura.domain.documento.ClaseDeError;
import pe.factura.domain.documento.FaultSunat;

/**
 * La cola global de errores del backoffice (#196). Solo lectura. El repositorio trae las filas crudas; acá se decide la clase de cada una con {@link ClaseDeError} y su fault
 * con {@link FaultSunat}, una sola vez.
 */
@RequiredArgsConstructor
public class ConsultarColaDeErroresService implements ConsultarColaDeErroresUseCase {
    private final ColaDeErroresRepository cola;

    @Override public Pagina listar(Filtro filtro, int pagina, int porPagina) {
        Filtro f = new Filtro(filtro.clase(), filtro.empresaId(), filtro.texto() == null || filtro.texto().isBlank() ? null : filtro.texto().strip());
        return new Pagina(cola.listar(f, pagina, porPagina).stream().map(ConsultarColaDeErroresService::aError).toList(), cola.contar(f));
    }

    private static ErrorDeEmision aError(Fila f) {
        ClaseDeError clase = ClaseDeError.de(f.estado(), f.cdrCodigo())
                .orElseThrow(() -> new IllegalStateException("El comprobante " + f.nombreArchivo() + " (" + f.estado() + ") no es de la cola de errores"));
        return new ErrorDeEmision(f.comprobanteId(), f.tenantId(), f.ruc(), f.razonSocial(), f.cuentaId(), f.cuentaNombre(), f.nombreArchivo(), f.tipo(), f.serie(), f.numero(),
                f.fechaEmision(), f.estado(), clase, f.intentos(), FaultSunat.de(f.ultimoError(), f.cdrCodigo(), f.cdrDescripcion()), f.siguienteIntento(), f.actualizadoEn(),
                clase.accionable());
    }
}
