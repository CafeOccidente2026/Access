/**
 * Salto de foco de los formularios de compra: el cursor pasa solo al campo que le toca llenar al usuario,
 * sin que tenga que buscarlo. Los campos se ubican por su data-field-key.
 */
export function focusField(host: HTMLElement, key: string): void {
  setTimeout(() => {
    const el = host.querySelector(`[data-field-key="${key}"]`) as HTMLElement | null;
    el?.focus();
    el?.classList.add('field-flash');
    setTimeout(() => el?.classList.remove('field-flash'), 1000);
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
