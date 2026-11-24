/** Campos que no se pasan a mayusculas: el usuario y la contrasena (tambien con "Mostrar contrasena"). */
const KEEP_CASE = '[data-keep-case]';

function isTyped(el: EventTarget | null): el is HTMLInputElement | HTMLTextAreaElement {
  if (el instanceof HTMLTextAreaElement) {
    return !el.closest(KEEP_CASE);
  }
  // Solo los de texto: correo, contrasena, busqueda, fechas y numeros quedan como se digitan.
  return el instanceof HTMLInputElement && el.type === 'text' && !el.closest(KEEP_CASE);
}

function upperCase(event: Event): void {
  const el = event.target;
  if (!isTyped(el) || (event as InputEvent).isComposing) {
    return;
  }
  const upper = el.value.toLocaleUpperCase('es-CO');
  if (upper !== el.value) {
    const { selectionStart, selectionEnd } = el;
    el.value = upper;
    el.setSelectionRange(selectionStart, selectionEnd);
  }
}

/**
 * Demo del 23-nov: todo lo que se digita (nombres, direcciones, detalles, empresa, placa, finca...) se ve en mayusculas
 * mientras se escribe, en toda la app, sin tocar cada pantalla. Escucha en captura sobre el documento, asi el valor ya
 * esta en mayusculas cuando la pantalla lo lee. El servidor tambien lo guarda en mayusculas (UpperCaseConverter).
 */
export function installUppercaseInputs(doc: Document = document): () => void {
  doc.addEventListener('input', upperCase, true);
  return () => doc.removeEventListener('input', upperCase, true);
}
