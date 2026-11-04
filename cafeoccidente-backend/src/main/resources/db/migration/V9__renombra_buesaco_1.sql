-- La agencia 1 de la semilla (V2) es el punto BUESACO1 OCCIDENTE de Access. En Buesaco hay tres puntos
-- distintos: Buesaco 1, Buesaco 2 (lo crea migrate_eltambo.py desde Access) y Santa Maria (Buesaco).
UPDATE agencias SET nombre = 'Buesaco 1' WHERE id = 1 AND nombre = 'Buesaco';
