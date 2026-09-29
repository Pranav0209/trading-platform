package com.zkuba.trading.service;

import com.zkuba.trading.dto.response.PendingUserResponse;
import com.zkuba.trading.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;

    @Autowired
    public AdminUserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public List<PendingUserResponse> getPendingUsers() {
        return userRepository.findUsersWithoutAccounts().stream()
                .map(u -> new PendingUserResponse(u.getId(), u.getUsername(), u.getEmail()))
                .collect(Collectors.toList());
    }
}
