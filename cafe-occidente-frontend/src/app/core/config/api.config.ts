/**
 * URL base del backend, relativa: en el servidor el mismo proxy sirve la web y manda /api al backend, y en local
 * ng serve lo hace con proxy.conf.json. Un solo build sirve para cualquier ambiente.
 */
export const API_BASE_URL = '/api';
