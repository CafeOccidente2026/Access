/**
 * Decision del usuario (10-nov): en una lista nativa (<select>) la flecha o una letra solo cambian la opcion y el foco se
 * queda; se pasa al siguiente campo con Enter o al elegir con el mouse. Aqui se recuerda que listas cambiaron con el
 * teclado: una tecla (que no sea Enter ni Tab) la marca; el mouse o Enter la desmarcan.
 */
const keyed = new WeakSet<EventTarget>();

if (typeof document !== 'undefined') {
  document.addEventListener('keydown', (event) => {
    if (event.target instanceof HTMLSelectElement) {
      if (event.key === 'Enter' || event.key === 'Tab') {
        keyed.delete(event.target);
      } else {
        keyed.add(event.target);
      }
    }
  }, true);
  document.addEventListener('mousedown', (event) => {
    if (event.target instanceof HTMLSelectElement) {
      keyed.delete(event.target);
    }
  }, true);
}

/** La lista cambio con el teclado: no se pasa al siguiente campo (eso lo hace Enter). */
export function changedByKeyboard(select: EventTarget | null): boolean {
  return !!select && keyed.has(select);
}
