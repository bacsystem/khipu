package pe.factura.adapters.persistence;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.factura.application.port.out.UnitOfWork;
import java.util.function.Supplier;

public class JdbcUnitOfWork implements UnitOfWork {
    private final TransactionTemplate tx;
    public JdbcUnitOfWork(PlatformTransactionManager tm) { this.tx = new TransactionTemplate(tm); }
    @Override public <T> T ejecutar(Supplier<T> trabajo) { return tx.execute(s -> trabajo.get()); }
    @Override public void ejecutar(Runnable trabajo) { tx.executeWithoutResult(s -> trabajo.run()); }
}
