-- S6 (seguridad): version de las sesiones del usuario. El token lleva la version con que se emitio; al cambiarle la
-- contrasena, el rol o la agencia, o al desactivarlo, sube y los tokens anteriores dejan de valer.
ALTER TABLE usuarios ADD COLUMN version_token integer NOT NULL DEFAULT 0;
