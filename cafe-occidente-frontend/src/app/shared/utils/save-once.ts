import { WritableSignal } from '@angular/core';
import { Observable, Observer, finalize } from 'rxjs';

/**
 * Envia un guardado solo si no hay otro en curso (regla de CLAUDE.md: dos clics o Enter seguidos
 * hacen una sola peticion): {@code request} no se llama mientras {@code saving} este en true, y
 * {@code saving} vuelve a false cuando la peticion termina, bien o mal.
 */
export function saveOnce<T>(saving: WritableSignal<boolean>, request: () => Observable<T>, observer: Partial<Observer<T>>): void {
  if (saving()) {
    return;
  }
  saving.set(true);
  request().pipe(finalize(() => saving.set(false))).subscribe(observer);
}
