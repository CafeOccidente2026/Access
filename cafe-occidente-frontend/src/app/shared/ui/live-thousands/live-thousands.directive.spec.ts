import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { LiveThousandsDirective } from './live-thousands.directive';

@Component({
  standalone: true,
  imports: [LiveThousandsDirective],
  template: `<input [appLiveThousands]="value()" [allowNegative]="negative" (liveThousands)="value.set($event); seen = $event" (input)="handlerSaw = $any($event.target).value" />`,
})
class HostComponent {
  readonly value = signal<string | number>('');
  negative = false;
  seen = '';
  handlerSaw = '';
}

function setup(value: string | number, negative = false) {
  const fixture = TestBed.createComponent(HostComponent);
  fixture.componentInstance.value.set(value);
  fixture.componentInstance.negative = negative;
  fixture.detectChanges();
  TestBed.tick();
  const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
  return { fixture, input, host: fixture.componentInstance };
}

function type(input: HTMLInputElement, value: string): void {
  input.value = value;
  input.setSelectionRange(value.length, value.length);
  input.dispatchEvent(new Event('input'));
}

describe('LiveThousandsDirective', () => {
  it('shows the value of the screen with thousands', () => {
    expect(setup(8379840).input.value).toBe('8.379.840');
  });

  it('formats while typing and hands the clean number to the screen, before its own (input) handler', () => {
    const { fixture, input, host } = setup('');
    type(input, '45690');
    fixture.detectChanges();
    TestBed.tick();
    expect(input.value).toBe('45.690');
    expect(host.seen).toBe('45690');
    expect(host.handlerSaw).toBe('45.690');
  });

  it('a new value from the screen replaces what is shown', () => {
    const { fixture, input, host } = setup('100');
    host.value.set(2500);
    fixture.detectChanges();
    TestBed.tick();
    expect(input.value).toBe('2.500');
  });

  it('allowNegative keeps the minus sign', () => {
    const { input, host } = setup(-2300, true);
    expect(input.value).toBe('-2.300');
    type(input, '-23000');
    expect(host.seen).toBe('-23000');
  });

  it('Backspace right after a dot deletes the digit before it', () => {
    const { input } = setup('45690');
    input.setSelectionRange(3, 3);
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Backspace' }));
    expect(input.selectionStart).toBe(2);
  });
});
