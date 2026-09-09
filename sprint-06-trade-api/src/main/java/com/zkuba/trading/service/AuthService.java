package com.zkuba.trading.service;

import com.zkuba.trading.domain.entity.User;
import com.zkuba.trading.domain.enums.UserStatus;
import com.zkuba.trading.domain.exception.UnauthorizedException;
import com.zkuba.trading.dto.request.LoginRequest;
import com.zkuba.trading.dto.response.LoginResponse;
import com.zkuba.trading.repository.UserRepository;
import com.zkuba.trading.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username()).orElse(null);
        if (user == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())
                || user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException();
        }
        return new LoginResponse(
                jwtService.generate(user.getId(), user.getUsername(), user.getRole().name()),
                "Bearer",
                jwtService.expirationMs());
    }
}
