import { signal } from '@angular/core';
import { Subject } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { saveOnce } from './save-once';

describe('saveOnce', () => {
  it('sends a single request while one is in progress and frees the lock when it ends', () => {
    const saving = signal(false);
    const pending = new Subject<string>();
    const next = vi.fn();
    const request = vi.fn(() => pending);

    saveOnce(saving, request, { next });
    saveOnce(saving, request, { next });

    expect(request).toHaveBeenCalledTimes(1);
    expect(saving()).toBe(true);
    pending.next('ok');
    pending.complete();
    expect(next).toHaveBeenCalledWith('ok');
    expect(saving()).toBe(false);
  });
});
