export type SmartRuleAction = 'BUY' | 'SELL';
export type SmartRuleQuantityMode = 'FIXED' | 'HALF' | 'ALL';
export type SmartRuleMetricType =
  | 'PRICE'
  | 'AVERAGE_COST'
  | 'MOVING_AVERAGE'
  | 'PERCENT_CHANGE'
  | 'MOVING_AVERAGE_CROSSOVER'
  | 'BOLLINGER_BAND'
  | 'STOP_LOSS';
export type SmartRuleOperator =
  | 'GREATER_THAN'
  | 'LESS_THAN'
  | 'EQUAL_TO'
  | 'CROSS_ABOVE'
  | 'CROSS_BELOW'
  | 'TOUCH_UPPER'
  | 'TOUCH_LOWER';

export interface CreateSmartRuleRequest {
  symbol: string;
  action: SmartRuleAction;
  quantityMode: SmartRuleQuantityMode;
  quantity?: number;
  metricType: SmartRuleMetricType;
  operator: SmartRuleOperator;
  targetValue?: number;
  lookbackPeriod?: number;
  secondaryLookbackPeriod?: number;
  standardDeviationMultiplier?: number;
  enabled?: boolean;
  children?: CreateSmartRuleRequest[];
}

export interface SmartRule {
  id: number;
  symbol: string;
  instrumentName: string;
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
  activationReferencePrice: number | null;
  activatedQuantity: number | null;
  activatedAt: string | null;
  lastTriggeredAt: string | null;
  children: SmartRule[];
}