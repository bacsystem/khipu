package pe.factura.application.port.in;

import pe.factura.application.port.out.PadronRucRepository.CargaRegistrada;

/** Trae el padrón reducido de SUNAT a la base (#361). */
public interface CargarPadronRucUseCase {
    /** Descarga el archivo y deja el padrón igual a él. Falla (y deja el padrón anterior) si el archivo no se puede leer, viene cortado o ya hay otra carga corriendo. */
    CargaRegistrada cargar();
}
