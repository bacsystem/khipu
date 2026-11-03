-- El plan que la cuenta eligió en la página de precios al registrarse (#581). Es un dato para el operador, no una suscripción: la cuenta nace en el plan por
-- defecto y el plan pagado lo activa el backoffice al registrar el pago. Opcional (quien se registra desde «Empezar gratis» o sin pasar por la portada no
-- elige ninguno). Si el plan se borra, el dato se pierde con él: la cuenta no deja de existir por eso.
ALTER TABLE cuenta ADD COLUMN plan_solicitado_id UUID REFERENCES plan (id) ON DELETE SET NULL;
