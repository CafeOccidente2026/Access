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

/** El campo que sigue a afterKey en el orden del formulario; nada si es el último o no está en el orden. Celular no está
 *  en el orden (se pide solo si TERCEROS no lo tiene): después de él se sigue con el primer campo pendiente. */
export function advanceFocus(host: HTMLElement, order: readonly string[], afterKey: string): void {
  if (afterKey === 'cellphone') {
    focusPending(host, order);
    return;
  }
  const idx = order.indexOf(afterKey);
  if (idx === -1 || idx === order.length - 1) {
    return;
  }
  focusField(host, order[idx + 1]);
}

/** El primer campo del orden que todavía se puede digitar (los confirmados y los que no tocan quedan de solo lectura). */
function focusPending(host: HTMLElement, order: readonly string[]): void {
  setTimeout(() => {
    const pending = order.find((key) => {
      const el = host.querySelector<HTMLInputElement>(`[data-field-key="${key}"]`);
      return el && !el.readOnly && !el.disabled;
    });
    if (pending) {
      focusField(host, pending);
    }
  });
}
