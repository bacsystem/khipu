package pe.factura.domain.plan;

/** Cuánto lleva ocupado una cuenta de lo que su plan limita además de los documentos (#498). Es la única definición de «ocupado»: lo que rechaza y lo que el portal muestra. */
public record UsoDeRecursos(long apiKeys, long usuarios, long rucs) {
    public long de(RecursoDelPlan recurso) {
        return switch (recurso) {
            case API_KEYS -> apiKeys;
            case USUARIOS -> usuarios;
            case RUCS -> rucs;
        };
    }
}
