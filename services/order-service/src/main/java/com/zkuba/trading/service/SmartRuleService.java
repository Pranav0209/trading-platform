package com.zkuba.trading.service;

import com.zkuba.trading.dto.request.CreateSmartRuleRequest;
import com.zkuba.trading.dto.response.SmartRuleResponse;
import java.util.List;

public interface SmartRuleService {

    List<SmartRuleResponse> getRules(Long accountId);

    SmartRuleResponse createRule(Long accountId, CreateSmartRuleRequest request);

    void deleteRule(Long accountId, Long ruleId);
}