/** Cuántas veces se vuelve a buscar un campo que todavía no está pintado (cada 50 ms, hasta ~1 s). */
const RETRIES = 20;
const RETRY_MS = 50;

/**
 * Salto de foco de los formularios de compra: el cursor pasa solo al campo que le toca llenar al usuario,
 * sin que tenga que buscarlo. Los campos se ubican por su data-field-key. Si el campo aún no está en pantalla
 * (Verde lo pinta cuando llega el anuncio, prueba del 10-nov), se espera un momento a que aparezca.
 */
export function focusField(host: HTMLElement, key: string, retries = RETRIES): void {
  setTimeout(() => {
    const el = host.querySelector(`[data-field-key="${key}"]`) as HTMLElement | null;
    if (!el) {
      if (retries > 0) {
        setTimeout(() => focusField(host, key, retries - 1), RETRY_MS);
      }
      return;
    }
    el.focus();
    el.classList.add('field-flash');
    setTimeout(() => el.classList.remove('field-flash'), 1000);
  });
}

/** El campo que sigue a afterKey en el orden del formulario; nada si es el último o no está en el orden. */
export function advanceFocus(host: HTMLElement, order: readonly string[], afterKey: string): void {
  const idx = order.indexOf(afterKey);
  if (idx === -1 || idx === order.length - 1) {
    return;
  }
  focusField(host, order[idx + 1]);
}

/** El primer campo del orden que todavía no se ha confirmado (a donde sigue el cajero después de un campo suelto). */
export function nextPending(order: readonly string[], locked: ReadonlySet<string>): string | undefined {
  return order.find((key) => !locked.has(key));
}
