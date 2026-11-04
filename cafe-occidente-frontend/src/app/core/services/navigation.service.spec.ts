import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';

import { NavigationService } from './navigation.service';

describe('NavigationService', () => {
  it('goes to the route, and does nothing without one', () => {
    const navigateByUrl = vi.fn(() => Promise.resolve(true));
    TestBed.configureTestingModule({ providers: [{ provide: Router, useValue: { navigateByUrl } }] });
    const navigation = TestBed.inject(NavigationService);

    navigation.goTo(undefined);
    navigation.goTo('/compras');

    expect(navigateByUrl).toHaveBeenCalledTimes(1);
    expect(navigateByUrl).toHaveBeenCalledWith('/compras');
  });
});
