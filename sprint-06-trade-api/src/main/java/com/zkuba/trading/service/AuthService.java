package com.zkuba.trading.service;

import com.zkuba.trading.dto.request.LoginRequest;
import com.zkuba.trading.dto.response.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);
}
