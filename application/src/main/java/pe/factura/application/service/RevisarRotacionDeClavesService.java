package pe.factura.application.service;

import pe.factura.application.port.in.RevisarRotacionDeClavesUseCase;
import pe.factura.application.port.out.PepperDeApiKeysRepository;
import pe.factura.application.port.out.SecretosCifradosRepository;

/** Ver {@link RevisarRotacionDeClavesUseCase}. */
public class RevisarRotacionDeClavesService implements RevisarRotacionDeClavesUseCase {
    private final SecretosCifradosRepository secretos;
    private final PepperDeApiKeysRepository pepper;
    private final String huellaVigente;
    private final String huellaAnterior;

    /** {@code pepperAnterior} vacío o nulo: no se está rotando el pepper. */
    public RevisarRotacionDeClavesService(SecretosCifradosRepository secretos, PepperDeApiKeysRepository pepper, String pepperVigente, String pepperAnterior) {
        this.secretos = secretos;
        this.pepper = pepper;
        this.huellaVigente = ApiKeyGenerator.huellaDePepper(pepperVigente);
        this.huellaAnterior = pepperAnterior == null || pepperAnterior.isBlank() ? null : ApiKeyGenerator.huellaDePepper(pepperAnterior);
    }

    @Override
    public Informe revisar() {
        int recifrados = secretos.recifrar();
        // Las keys de antes de que existiera la huella: si se está rotando, son de antes de rotar (pepper anterior); si no, del único pepper que hubo.
        pepper.completarHuellas(huellaAnterior != null ? huellaAnterior : huellaVigente);
        return new Informe(recifrados, secretos.pendientes(), pepper.activasConOtraHuella(huellaVigente), huellaAnterior != null);
    }
}
