package pe.factura.bootstrap;

import lombok.extern.slf4j.Slf4j;
import pe.factura.application.port.in.RevisarRotacionDeClavesUseCase;
import pe.factura.application.port.in.RevisarRotacionDeClavesUseCase.Informe;

/**
 * Lo que se registra al arrancar sobre la rotación de MASTER_KEY y API_KEY_PEPPER (S2): es lo que mira el operador para saber cuándo quitar la clave
 * o el pepper anteriores (deploy/README.md §9). Sin rotación en curso y sin nada raro, no dice nada.
 */
@Slf4j
final class RotacionDeClavesAlArrancar {
    private RotacionDeClavesAlArrancar() {}

    static Informe revisar(RevisarRotacionDeClavesUseCase rotacion, boolean rotandoMasterKey) {
        Informe i;
        try {
            i = rotacion.revisar();
        } catch (RuntimeException e) {
            log.error("Rotación de claves: no se pudo revisar al arrancar; lo pendiente se sigue leyendo con la clave anterior", e);
            return null;
        }
        if (rotandoMasterKey) {
            if (i.secretosPendientes() == 0)
                log.warn("Rotación de MASTER_KEY: {} valor(es) recifrado(s) con la clave vigente, 0 pendientes. Ya se puede quitar MASTER_KEY_ANTERIOR.", i.secretosRecifrados());
            else
                log.error("Rotación de MASTER_KEY: quedan {} valor(es) con la clave anterior que no se pudieron recifrar. NO quitar MASTER_KEY_ANTERIOR.", i.secretosPendientes());
        }
        if (i.rotandoPepper()) {
            if (i.apiKeysConPepperAnterior() == 0)
                log.warn("Rotación de API_KEY_PEPPER: 0 API keys activas dependen del pepper anterior. Ya se puede quitar API_KEY_PEPPER_ANTERIOR.");
            else
                log.warn("Rotación de API_KEY_PEPPER: {} API key(s) activa(s) todavía dependen del pepper anterior; cada una pasa al vigente la próxima vez que se usa. "
                        + "Si se quita API_KEY_PEPPER_ANTERIOR ahora, esas keys dejan de autenticar.", i.apiKeysConPepperAnterior());
        } else if (i.apiKeysConPepperAnterior() > 0) {
            log.error("API_KEY_PEPPER: {} API key(s) activa(s) tienen un pepper que ya no está configurado y no autentican. Volver a poner API_KEY_PEPPER_ANTERIOR "
                    + "o pedir a esos clientes que creen otra key.", i.apiKeysConPepperAnterior());
        }
        return i;
    }
}
