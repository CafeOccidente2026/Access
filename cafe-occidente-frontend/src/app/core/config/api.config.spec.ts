import { describe, expect, it } from 'vitest';

import proxy from '../../../../proxy.conf.json';
import { API_BASE_URL } from './api.config';

describe('API_BASE_URL', () => {
  it('is relative, so the same build works on any server behind the proxy that serves /api', () => {
    expect(API_BASE_URL).toBe('/api');
  });

  it('ng serve sends /api to the local backend on 8090', () => {
    expect(proxy['/api'].target).toBe('http://localhost:8090');
  });
});
