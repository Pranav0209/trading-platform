package com.zkuba.trading.service;

import com.zkuba.trading.domain.entity.User;
import com.zkuba.trading.dto.request.RegisterRequest;
import com.zkuba.trading.dto.response.RegisterResponse;

public interface UserService {

    RegisterResponse register(RegisterRequest request);

    User get(Long id);
}
