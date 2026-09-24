/**
 * Regla global de captura secuencial: dentro de un FOCUS_ORDER (el orden real en que el cajero
 * llena el formulario), un campo solo se habilita cuando le toca su turno - todos los que vienen
 * despues quedan bloqueados (readonly) hasta que el anterior se confirme. Evita que el cajero salte
 * campos (ej. escribir Sacos antes que la Cedula).
 */
export function isSequentialFieldEnabled(
  key: string,
  focusOrder: readonly string[],
  locked: ReadonlySet<string>,
): boolean {
  const idx = focusOrder.indexOf(key);
  if (idx === -1) {
    return true;
  }
  if (locked.has(key)) {
    return true;
  }
  const currentIdx = focusOrder.findIndex((k) => !locked.has(k));
  return currentIdx === -1 || idx === currentIdx;
}
