package com.zkuba.trading.exception;

public class SmartRuleNotFoundException extends NotFoundException {
    public SmartRuleNotFoundException(Long ruleId) {
        super("SRL-404", "Smart rule not found: " + ruleId);
    }
}