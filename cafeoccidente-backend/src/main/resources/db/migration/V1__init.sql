-- Primera migracion: esqueleto de tablas (una por entidad, solo id).
-- Se completara con la estructura real tomada de Aurora en migraciones siguientes.

CREATE TABLE control_record (id BIGSERIAL PRIMARY KEY);

CREATE TABLE driver (id BIGSERIAL PRIMARY KEY);
CREATE TABLE inventory_movement (id BIGSERIAL PRIMARY KEY);
CREATE TABLE remission (id BIGSERIAL PRIMARY KEY);

CREATE TABLE dry_coffee_purchase (id BIGSERIAL PRIMARY KEY);
CREATE TABLE green_coffee_purchase (id BIGSERIAL PRIMARY KEY);
CREATE TABLE other_coffee_purchase (id BIGSERIAL PRIMARY KEY);
CREATE TABLE husk_purchase (id BIGSERIAL PRIMARY KEY);

CREATE TABLE announcement (id BIGSERIAL PRIMARY KEY);
CREATE TABLE announcement_quota (id BIGSERIAL PRIMARY KEY);
CREATE TABLE future_purchase (id BIGSERIAL PRIMARY KEY);

CREATE TABLE agency (id BIGSERIAL PRIMARY KEY);
CREATE TABLE fund (id BIGSERIAL PRIMARY KEY);
CREATE TABLE product_code (id BIGSERIAL PRIMARY KEY);

CREATE TABLE users (id BIGSERIAL PRIMARY KEY);
CREATE TABLE role (id BIGSERIAL PRIMARY KEY);
CREATE TABLE permission (id BIGSERIAL PRIMARY KEY);
