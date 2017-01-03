/* tslint:disable:no-unused-variable */
import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { IntegrationsListToolbarComponent } from './list-toolbar.component';

describe('ListToolbarComponent', () => {
  let component: IntegrationsListToolbarComponent;
  let fixture: ComponentFixture<IntegrationsListToolbarComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [IntegrationsListToolbarComponent],
    })
      .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(IntegrationsListToolbarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
