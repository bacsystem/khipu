package pe.factura.adapters.rest;

import pe.factura.application.port.out.AdministradorTokenEmisor;

import java.util.Optional;
import java.util.UUID;

/** Base de los fakes del emisor de tokens del administrador: cada test sobrescribe solo lo que usa, el resto no se espera. */
abstract class TokensDeAdminFalsos implements AdministradorTokenEmisor {
    public String emitir(Claims c) { throw new UnsupportedOperationException(); }
    public Optional<Claims> verificar(String token) { return Optional.empty(); }
    public long vidaSesionSegundos() { throw new UnsupportedOperationException(); }
    public String emitirDesafio(UUID administradorId) { throw new UnsupportedOperationException(); }
    public Optional<UUID> verificarDesafio(String token) { return Optional.empty(); }
}
