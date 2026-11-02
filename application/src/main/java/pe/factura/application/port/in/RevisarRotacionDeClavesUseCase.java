package pe.factura.application.port.in;

/**
 * Lo que queda de una rotación de MASTER_KEY o de API_KEY_PEPPER (S2). Se corre al arrancar el backend: termina lo que se puede terminar sin
 * intervención (volver a cifrar con la clave vigente) y dice lo que falta, para saber cuándo es seguro quitar la clave o el pepper anteriores.
 */
public interface RevisarRotacionDeClavesUseCase {
    /**
     * @param secretosPendientes      valores que no abren con la MASTER_KEY vigente después de recifrar (debería ser 0). Sin rotación, mayor que 0 es
     *                                 que hay valores ilegibles: típicamente, escritos con la clave anterior después de quitarla
     * @param apiKeysConPepperAnterior keys activas que todavía dependen del pepper anterior: se re-hashean solas la próxima vez que se usan. Si es mayor
     *                                 que 0 y no se está rotando, esas keys ya no autentican (se quitó el pepper anterior antes de tiempo)
     */
    record Informe(int secretosRecifrados, int secretosPendientes, int apiKeysConPepperAnterior, boolean rotandoPepper) {}

    Informe revisar();
}
