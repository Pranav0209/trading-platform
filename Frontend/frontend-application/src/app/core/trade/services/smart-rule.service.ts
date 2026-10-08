import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { CreateSmartRuleRequest, SmartRule } from '../models/smart-rule.models';

@Injectable({ providedIn: 'root' })
export class SmartRuleService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = environment.tradeApiUrl;

  getRules(accountId: number): Observable<SmartRule[]> {
    return this.http.get<SmartRule[]>(`${this.apiUrl}/api/v1/accounts/${accountId}/smart-rules`);
  }

  createRule(accountId: number, payload: CreateSmartRuleRequest): Observable<SmartRule> {
    return this.http.post<SmartRule>(`${this.apiUrl}/api/v1/accounts/${accountId}/smart-rules`, payload);
  }

  deleteRule(accountId: number, ruleId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/api/v1/accounts/${accountId}/smart-rules/${ruleId}`);
  }
}