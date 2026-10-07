import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Advice } from './advice';

describe('Advice', () => {
  let component: Advice;
  let fixture: ComponentFixture<Advice>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Advice],
    }).compileComponents();

    fixture = TestBed.createComponent(Advice);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
