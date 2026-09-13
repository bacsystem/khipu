package pe.factura.application.port.out;

import java.util.function.Supplier;

public interface UnitOfWork {
    <T> T ejecutar(Supplier<T> trabajo);
    void ejecutar(Runnable trabajo);
}
