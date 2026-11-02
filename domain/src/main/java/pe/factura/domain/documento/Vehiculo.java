package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

/** Vehículo del traslado por su placa. El primero de la guía es el principal y los siguientes los secundarios (hasta dos, regla 4389). */
public record Vehiculo(String placa) {
    public Vehiculo { placa = TextoDeGuia.compacto(placa); }

    void exigirValido() {
        if (placa == null) throw new DomainException("TRANSPORTE_INVALIDO", "2566 - Falta el número de placa del vehículo");
        if (!placa.matches("[A-Z0-9]{6,8}"))
            throw new DomainException("TRANSPORTE_INVALIDO", "2567 - La placa del vehículo son de 6 a 8 letras o dígitos (sin guiones); recibido " + placa);
    }
}
