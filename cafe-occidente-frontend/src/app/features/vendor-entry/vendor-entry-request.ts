import { VendorCreateRequest } from '../../core/models/vendor.model';
import { VendorEntryContent, VendorField, VendorKind } from './vendor-entry.model';

/** usuario@dominio, sin espacios ni otra @ (regex sin retroceso, S8786). */
const EMAIL = /^[^\s@]+@([^\s@]+)$/;

/** Como antes: el dominio lleva un punto que no es ni el primero ni el ultimo caracter. */
function isEmail(value: string): boolean {
  const domain = EMAIL.exec(value)?.[1];
  return domain != null && domain.slice(1, -1).includes('.');
}

/** Primer problema del formulario como mensaje del JSON, o null si se puede guardar. */
export function validationError(
  fields: readonly VendorField[],
  values: Record<string, string>,
  messages: VendorEntryContent['messages'],
): string | null {
  const missing = fields.find((f) => f.required && !(values[f.key] ?? '').trim());
  if (missing) {
    return messages.required.replace('{label}', missing.label);
  }
  const email = (values['email'] ?? '').trim();
  return email && !isEmail(email) ? messages.email : null;
}

/** Solo viaja lo que tiene la pantalla elegida; vacio = null. */
export function buildVendorRequest(
  kind: VendorKind,
  fields: readonly VendorField[],
  values: Record<string, string>,
): VendorCreateRequest {
  const shown = new Set(fields.flatMap((f) => (f.besideSex ? [f.key, 'sex'] : [f.key])));
  const value = (key: string) => (shown.has(key) ? (values[key] ?? '').trim() || null : null);
  return {
    legalPerson: kind === 'legal',
    idNumber: value('idNumber') ?? '',
    firstName: value('firstName'),
    secondName: value('secondName'),
    lastName: value('lastName'),
    secondLastName: value('secondLastName'),
    sex: value('sex'),
    phone: value('phone'),
    address: value('address'),
    postalCode: value('postalCode'),
    email: value('email'),
    businessName: value('businessName'),
    classification: value('classification'),
  };
}
