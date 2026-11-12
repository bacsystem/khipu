package pe.factura.application.port.in;

/** Borra las sesiones vencidas hace más de la retención configurada (#611): sin esto cada refresh agrega una fila y la tabla solo crece. */
public interface PurgarSesionesUseCase {
    /** Devuelve cuántas borró. */
    int purgar();
}
