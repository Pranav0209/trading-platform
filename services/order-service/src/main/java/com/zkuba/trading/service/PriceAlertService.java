package com.zkuba.trading.service;

import com.zkuba.trading.dto.request.CreateAlertRequest;
import com.zkuba.trading.dto.response.AlertResponse;
import com.zkuba.trading.domain.entity.PriceAlert.AlertState;
import java.util.List;

public interface PriceAlertService {

    /**
     * Get all alerts for an account
     */
    List<AlertResponse> getAlerts(Long accountId, AlertState stateFilter);

    /**
     * Get a specific alert (with ownership check)
     */
    AlertResponse getAlert(Long accountId, Long alertId);

    /**
     * Create a new price alert for an account.
     * Enforces 100-alert limit (soft warning, hard at 409).
     */
    AlertResponse createAlert(Long accountId, CreateAlertRequest request);

    /**
     * Delete (cancel) a price alert
     */
    void deleteAlert(Long accountId, Long alertId);

    /**
     * Reset a triggered alert back to ACTIVE
     */
    AlertResponse resetAlert(Long accountId, Long alertId);
}
