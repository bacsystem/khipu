package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.out.PlantillasRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.PlantillaDeCorreo;
import pe.factura.domain.plataforma.PlantillaDeCorreo.Texto;

import java.util.Map;

/**
 * El texto con que sale un correo de la plataforma (#199): el que un administrador guardó, o el de fábrica. **Un correo nunca deja de salir por su texto**: si el guardado ya no se
 * puede validar (una variable que una versión nueva dejó de tener), sale el de fábrica en vez de fallar —un correo de acceso roto dejaría a alguien sin poder entrar—.
 */
@RequiredArgsConstructor
public class PlantillasDeCorreo {
    private final PlantillasRepository guardadas;

    /** El correo {@code tipo} con sus variables reemplazadas por {@code valores}. */
    public Texto de(PlantillaDeCorreo tipo, Map<String, String> valores) {
        return tipo.renderizar(vigente(tipo), valores);
    }

    private Texto vigente(PlantillaDeCorreo tipo) {
        return guardadas.buscar(tipo).map(g -> {
            try {
                return tipo.validar(g.texto().asunto(), g.texto().cuerpo());
            } catch (DomainException e) {
                return tipo.defecto();
            }
        }).orElse(tipo.defecto());
    }
}
