import { GrowerProgram } from '../../core/models/grower.model';

/** Campos del bloque "Información Federación": solo los llena NESS, nunca el usuario. */
export const FEDERATION_KEYS = ['idNumber', 'fullName', 'program', 'quota'];

/**
 * C3 (prueba del 11-nov, punto 3): el bloque muestra la cédula, el nombre, el programa y el cupo de NESS.
 * Si la cédula no está en NESS queda vacío; los datos del vendedor siguen abajo, del tercero.
 */
export function federationValues(program: GrowerProgram | null): Record<string, string> {
  return {
    idNumber: program?.cedula ?? '',
    fullName: program?.nombres ?? '',
    program: program?.programa ?? '',
    quota: program?.cupo ?? '',
  };
}
