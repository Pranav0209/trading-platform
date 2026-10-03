package com.zkuba.trading.service;

import com.zkuba.trading.dto.response.PendingUserResponse;
import java.util.List;

public interface AdminUserService {
    List<PendingUserResponse> getPendingUsers();
}
