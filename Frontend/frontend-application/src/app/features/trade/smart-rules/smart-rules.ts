import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Session } from '../../../core/auth/session';
import { ToastService } from '../../../core/notifications/toast.service';
import { CreateSmartRuleRequest, SmartRule, SmartRuleAction, SmartRuleMetricType, SmartRuleOperator, SmartRuleQuantityMode } from '../../../core/trade/models/smart-rule.models';
import { SmartRuleService } from '../../../core/trade/services/smart-rule.service';
import { InstrumentsService } from '../../../../generated/trade/api/instruments.service';
import { InstrumentResponse } from '../../../../generated/trade/model/instrumentResponse';

interface RuleDraft {
  symbol: string;
  action: SmartRuleAction;
  quantityMode: SmartRuleQuantityMode;
  quantity: number | null;
  metricType: SmartRuleMetricType;
  operator: SmartRuleOperator;
  targetValue: number | null;
  lookbackPeriod: number | null;
  secondaryLookbackPeriod: number | null;
  standardDeviationMultiplier: number | null;
  enabled: boolean;
  children: RuleDraft[];
}

@Component({
  selector: 'app-smart-rules',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './smart-rules.html',
  styleUrl: './smart-rules.css'
})
export class SmartRulesComponent implements OnInit {
  private readonly smartRuleService = inject(SmartRuleService);
  private readonly session = inject(Session);
  private readonly toastService = inject(ToastService);
  private readonly instrumentsService = inject(InstrumentsService);

  readonly accountId = this.session.accountId;

  readonly rules = signal<SmartRule[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly deletingRuleId = signal<number | null>(null);

  availableInstruments = signal<InstrumentResponse[]>([]);
  filteredInstruments = signal<InstrumentResponse[]>([]);
  showDropdown = signal(false);

  draft: RuleDraft = this.createDraft();

  readonly actionOptions: SmartRuleAction[] = ['BUY', 'SELL'];
  readonly quantityModeOptions: SmartRuleQuantityMode[] = ['FIXED', 'HALF', 'ALL'];
  readonly metricOptions: { value: SmartRuleMetricType; label: string }[] = [
    { value: 'PRICE', label: 'Price' },
    { value: 'AVERAGE_COST', label: 'Average price' },
    { value: 'MOVING_AVERAGE', label: 'Moving average' },
    { value: 'PERCENT_CHANGE', label: 'Percent change' },
    { value: 'MOVING_AVERAGE_CROSSOVER', label: 'Moving-average crossover' },
    { value: 'BOLLINGER_BAND', label: 'Bollinger band' },
    { value: 'STOP_LOSS', label: 'Stop loss' }
  ];
  readonly operatorLabels: Record<SmartRuleOperator, string> = {
    GREATER_THAN: 'is greater than',
    LESS_THAN: 'is less than',
    EQUAL_TO: 'is equal to',
    CROSS_ABOVE: 'crosses above',
    CROSS_BELOW: 'crosses below',
    TOUCH_UPPER: 'touches the upper band',
    TOUCH_LOWER: 'touches the lower band'
  };

  ngOnInit(): void {
    this.loadRules();
    this.instrumentsService.getInstruments().subscribe({
      next: (instruments) => {
        const tradable = instruments.filter(i => i.tradable);
        this.availableInstruments.set(tradable);
        this.filteredInstruments.set(tradable);
      },
      error: (e) => console.error('Failed to load instruments', e)
    });
  }

  loadRules(): void {
    const accountId = this.accountId();
    if (!accountId) {
      return;
    }

    this.loading.set(true);
    this.smartRuleService.getRules(accountId).subscribe({
      next: rules => {
        this.rules.set(rules);
        this.loading.set(false);
      },
      error: error => {
        this.loading.set(false);
        this.toastService.error(error.error?.message || 'Failed to load smart rules');
      }
    });
  }

  saveRule(): void {
    const accountId = this.accountId();
    if (!accountId) {
      this.toastService.error('No trading account is available in this session.');
      return;
    }

    const validationError = this.validateDraft(this.draft, false);
    if (validationError) {
      this.toastService.warning(validationError);
      return;
    }

    this.saving.set(true);
    this.smartRuleService.createRule(accountId, this.toRequest(this.draft, null)).subscribe({
      next: rule => {
        this.rules.set([rule, ...this.rules()]);
        this.draft = this.createDraft();
        this.saving.set(false);
        this.toastService.success('Smart rule saved and armed for background execution.');
      },
      error: error => {
        this.saving.set(false);
        this.toastService.error(error.error?.message || 'Failed to save smart rule');
      }
    });
  }

  deleteRule(ruleId: number): void {
    const accountId = this.accountId();
    if (!accountId) {
      return;
    }

    this.deletingRuleId.set(ruleId);
    this.smartRuleService.deleteRule(accountId, ruleId).subscribe({
      next: () => {
        this.rules.set(this.rules().filter(rule => rule.id !== ruleId));
        this.deletingRuleId.set(null);
        this.toastService.info('Smart rule removed');
      },
      error: error => {
        this.deletingRuleId.set(null);
        this.toastService.error(error.error?.message || 'Failed to delete smart rule');
      }
    });
  }

  addChild(rule: RuleDraft): void {
    const child = this.createDraft(rule.action === 'BUY' ? 'SELL' : 'BUY', rule.symbol || '');
    if (rule.action === 'BUY') {
      child.quantityMode = 'ALL';
      child.metricType = 'STOP_LOSS';
      child.operator = 'LESS_THAN';
      child.targetValue = 10;
    }
    rule.children.push(child);
  }

  removeChild(parent: RuleDraft, index: number): void {
    parent.children.splice(index, 1);
  }

  onActionChange(rule: RuleDraft): void {
    if (rule.action === 'BUY') {
      rule.quantityMode = 'FIXED';
      if (!rule.quantity || rule.quantity < 1) {
        rule.quantity = 1;
      }
    } else if (rule.quantityMode === 'FIXED' && (!rule.quantity || rule.quantity < 1)) {
      rule.quantity = 1;
    }

    for (const child of rule.children) {
      child.action = rule.action === 'BUY' ? 'SELL' : 'BUY';
      child.symbol = rule.symbol;
      this.onActionChange(child);
    }
  }

  onSymbolInput(rule: RuleDraft, value: string): void {
    rule.symbol = value;
    this.onActionChange(rule); // propagate to children
    if (!value) {
      this.filteredInstruments.set(this.availableInstruments());
      return;
    }
    const lower = value.toLowerCase();
    this.filteredInstruments.set(
      this.availableInstruments().filter(i => 
        i.symbol.toLowerCase().includes(lower) || 
        i.displayName.toLowerCase().includes(lower)
      )
    );
  }

  selectInstrument(rule: RuleDraft, symbol: string): void {
    rule.symbol = symbol;
    this.onActionChange(rule); // propagate to children
    this.showDropdown.set(false);
  }

  hideDropdown(): void {
    setTimeout(() => {
      this.showDropdown.set(false);
    }, 200);
  }

  onQuantityModeChange(rule: RuleDraft): void {
    if (rule.quantityMode === 'FIXED') {
      rule.quantity = rule.quantity && rule.quantity > 0 ? rule.quantity : 1;
      return;
    }
    rule.quantity = null;
  }

  onMetricChange(rule: RuleDraft): void {
    const operators = this.operatorsFor(rule);
    if (!operators.includes(rule.operator)) {
      rule.operator = operators[0];
    }

    if (rule.metricType === 'BOLLINGER_BAND') {
      rule.lookbackPeriod = rule.lookbackPeriod || 20;
      rule.standardDeviationMultiplier = rule.standardDeviationMultiplier || 2;
      rule.targetValue = null;
    }

    if (rule.metricType === 'MOVING_AVERAGE') {
      rule.lookbackPeriod = rule.lookbackPeriod || 20;
    }

    if (rule.metricType === 'MOVING_AVERAGE_CROSSOVER') {
      rule.lookbackPeriod = rule.lookbackPeriod || 20;
      rule.secondaryLookbackPeriod = rule.secondaryLookbackPeriod || 50;
      rule.targetValue = null;
    }

    if (rule.metricType === 'STOP_LOSS') {
      rule.operator = 'LESS_THAN';
      rule.targetValue = rule.targetValue || 10;
      rule.quantityMode = rule.action === 'SELL' ? rule.quantityMode : 'FIXED';
    }
  }

  operatorsFor(rule: RuleDraft): SmartRuleOperator[] {
    switch (rule.metricType) {
      case 'MOVING_AVERAGE_CROSSOVER':
        return ['CROSS_ABOVE', 'CROSS_BELOW'];
      case 'BOLLINGER_BAND':
        return ['TOUCH_LOWER', 'TOUCH_UPPER'];
      case 'STOP_LOSS':
        return ['LESS_THAN'];
      default:
        return ['GREATER_THAN', 'LESS_THAN', 'EQUAL_TO'];
    }
  }

  availableQuantityModes(rule: RuleDraft): SmartRuleQuantityMode[] {
    return rule.action === 'BUY' ? ['FIXED'] : this.quantityModeOptions;
  }

  needsTargetValue(rule: RuleDraft): boolean {
    return rule.metricType !== 'BOLLINGER_BAND' && rule.metricType !== 'MOVING_AVERAGE_CROSSOVER';
  }

  needsLookback(rule: RuleDraft): boolean {
    return ['MOVING_AVERAGE', 'MOVING_AVERAGE_CROSSOVER', 'BOLLINGER_BAND'].includes(rule.metricType);
  }

  needsSecondaryLookback(rule: RuleDraft): boolean {
    return rule.metricType === 'MOVING_AVERAGE_CROSSOVER';
  }

  needsStdDeviation(rule: RuleDraft): boolean {
    return rule.metricType === 'BOLLINGER_BAND';
  }

  metricLabel(metric: SmartRuleMetricType): string {
    return this.metricOptions.find(option => option.value === metric)?.label || metric;
  }

  describeRule(rule: Pick<SmartRule, 'action' | 'quantityMode' | 'quantity' | 'symbol' | 'metricType' | 'operator' | 'targetValue' | 'lookbackPeriod' | 'secondaryLookbackPeriod' | 'standardDeviationMultiplier'>): string {
    const quantityText = rule.quantityMode === 'FIXED'
      ? `${rule.quantity ?? 0}`
      : rule.quantityMode === 'HALF'
        ? 'half'
        : 'all';

    if (rule.metricType === 'MOVING_AVERAGE_CROSSOVER') {
      return `${rule.action} ${quantityText} of ${rule.symbol} when the ${rule.lookbackPeriod}-day average ${this.operatorLabels[rule.operator]} the ${rule.secondaryLookbackPeriod}-day average`;
    }

    if (rule.metricType === 'BOLLINGER_BAND') {
      return `${rule.action} ${quantityText} of ${rule.symbol} when price ${this.operatorLabels[rule.operator]}`;
    }

    if (rule.metricType === 'STOP_LOSS') {
      return `${rule.action} ${quantityText} of ${rule.symbol} when stop loss reaches ${rule.targetValue}% from the parent fill price`;
    }

    const metricLabel = this.metricLabel(rule.metricType).toLowerCase();
    return `${rule.action} ${quantityText} of ${rule.symbol} when ${metricLabel} ${this.operatorLabels[rule.operator]} ${rule.targetValue}`;
  }

  private createDraft(action: SmartRuleAction = 'BUY', symbol: string = ''): RuleDraft {
    return {
      symbol,
      action,
      quantityMode: action === 'BUY' ? 'FIXED' : 'ALL',
      quantity: action === 'BUY' ? 1 : null,
      metricType: 'PRICE',
      operator: 'LESS_THAN',
      targetValue: null,
      lookbackPeriod: null,
      secondaryLookbackPeriod: null,
      standardDeviationMultiplier: null,
      enabled: true,
      children: []
    };
  }

  private validateDraft(rule: RuleDraft, isChild: boolean): string | null {
    if (!rule.symbol.trim()) {
      return 'Symbol is required';
    }

    if (rule.quantityMode === 'FIXED' && (!rule.quantity || rule.quantity < 1)) {
      return 'Fixed-quantity rules must use a quantity greater than zero';
    }

    if (rule.action === 'BUY' && rule.quantityMode !== 'FIXED') {
      return 'Buy rules currently support fixed quantity only';
    }

    if (isChild && rule.metricType === 'STOP_LOSS' && rule.action !== 'SELL') {
      return 'Stop-loss child rules must be sell rules';
    }

    if (this.needsTargetValue(rule) && (rule.targetValue === null || rule.targetValue <= 0)) {
      return 'Please enter a positive target value';
    }

    if (this.needsLookback(rule) && (!rule.lookbackPeriod || rule.lookbackPeriod < 2)) {
      return 'Please enter a lookback period of at least 2';
    }

    if (this.needsSecondaryLookback(rule) && (!rule.secondaryLookbackPeriod || rule.secondaryLookbackPeriod < 2)) {
      return 'Please enter a secondary lookback period of at least 2';
    }

    for (const child of rule.children) {
      child.symbol = rule.symbol.toUpperCase();
      child.action = rule.action === 'BUY' ? 'SELL' : 'BUY';
      const childError = this.validateDraft(child, true);
      if (childError) {
        return childError;
      }
    }

    return null;
  }

  private toRequest(rule: RuleDraft, parentSymbol: string | null): CreateSmartRuleRequest {
    return {
      symbol: (parentSymbol || rule.symbol).toUpperCase(),
      action: rule.action,
      quantityMode: rule.quantityMode,
      quantity: rule.quantityMode === 'FIXED' ? Number(rule.quantity) : undefined,
      metricType: rule.metricType,
      operator: rule.operator,
      targetValue: this.needsTargetValue(rule) && rule.targetValue !== null ? Number(rule.targetValue) : undefined,
      lookbackPeriod: this.needsLookback(rule) && rule.lookbackPeriod !== null ? Number(rule.lookbackPeriod) : undefined,
      secondaryLookbackPeriod: this.needsSecondaryLookback(rule) && rule.secondaryLookbackPeriod !== null ? Number(rule.secondaryLookbackPeriod) : undefined,
      standardDeviationMultiplier: this.needsStdDeviation(rule) && rule.standardDeviationMultiplier !== null ? Number(rule.standardDeviationMultiplier) : undefined,
      enabled: rule.enabled,
      children: rule.children.map(child => this.toRequest(child, rule.symbol.toUpperCase()))
    };
  }
}