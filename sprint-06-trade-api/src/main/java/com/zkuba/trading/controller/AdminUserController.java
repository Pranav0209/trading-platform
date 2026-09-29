package com.zkuba.trading.controller;

import com.zkuba.trading.dto.response.PendingUserResponse;
import com.zkuba.trading.service.AdminUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminUserController {

    @Autowired
    private AdminUserService adminUserService;

    @GetMapping("/pending")
    public List<PendingUserResponse> getPendingUsers() {
        return adminUserService.getPendingUsers();
    }
}
