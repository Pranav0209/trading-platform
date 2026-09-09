package com.zkuba.trading.service;

import com.zkuba.trading.domain.entity.User;
import com.zkuba.trading.domain.enums.UserRole;
import com.zkuba.trading.domain.enums.UserStatus;
import com.zkuba.trading.exception.DuplicateUserException;
import com.zkuba.trading.exception.UserNotFoundException;
import com.zkuba.trading.dto.request.RegisterRequest;
import com.zkuba.trading.dto.response.RegisterResponse;
import com.zkuba.trading.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.findByUsername(request.username()).isPresent()
                || userRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateUserException();
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.ROLE_USER);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        return new RegisterResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getStatus().name());
    }

    @Override
    public User get(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }
}
