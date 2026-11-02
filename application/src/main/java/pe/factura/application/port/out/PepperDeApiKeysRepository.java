package pe.factura.application.port.out;

import java.util.UUID;

/**
 * Lo que hace falta para rotar API_KEY_PEPPER sin invalidar las API keys ya emitidas (S2). Cada key guarda una huella del pepper con que se calculó
 * su hash (no el pepper): así se sabe cuántas siguen con el anterior y cuándo es seguro quitarlo.
 */
public interface PepperDeApiKeysRepository {
    /**
     * Reemplaza el hash de una key por el calculado con el pepper vigente, y le pone la huella vigente. Solo si sigue teniendo {@code hashAnterior}: dos
     * peticiones simultáneas con la misma key no se pisan. {@code false} si ya no lo tenía.
     */
    boolean rehashear(UUID id, String hashAnterior, String hashNuevo);

    /** Pone {@code huella} a las keys que no tienen ninguna (las de antes de este cambio). Devuelve cuántas. */
    int completarHuellas(String huella);

    /** Cuántas keys activas tienen una huella distinta de {@code huellaVigente}: las que todavía dependen del pepper anterior. */
    int activasConOtraHuella(String huellaVigente);
}
