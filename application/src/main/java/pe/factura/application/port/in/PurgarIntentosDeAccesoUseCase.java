package pe.factura.application.port.in;

/** Borra los contadores de intentos (#261) sin actividad desde hace un día: sin esto, probar correos al azar haría crecer la tabla. */
public interface PurgarIntentosDeAccesoUseCase {
    /** Devuelve cuántos borró. */
    int purgar();
}
