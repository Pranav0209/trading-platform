import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SmartRulesComponent } from './smart-rules';
import { SmartRuleService } from '../../../core/trade/services/smart-rule.service';
import { of } from 'rxjs';
import { MarketDataService } from '../../../core/services/market-data.service';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

describe('SmartRulesComponent', () => {
  let component: SmartRulesComponent;
  let fixture: ComponentFixture<SmartRulesComponent>;
  let mockSmartRuleService: any;
  let mockMarketDataService: any;

  beforeEach(async () => {
    mockSmartRuleService = {
      getSmartRules: vi.fn().mockReturnValue(of([])),
      createSmartRule: vi.fn().mockReturnValue(of({})),
      deleteSmartRule: vi.fn().mockReturnValue(of({}))
    };

    mockMarketDataService = {
      searchInstruments: vi.fn().mockReturnValue(of([]))
    };

    await TestBed.configureTestingModule({
      imports: [SmartRulesComponent, FormsModule, CommonModule],
      providers: [
        { provide: SmartRuleService, useValue: mockSmartRuleService },
        { provide: MarketDataService, useValue: mockMarketDataService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SmartRulesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });


});
