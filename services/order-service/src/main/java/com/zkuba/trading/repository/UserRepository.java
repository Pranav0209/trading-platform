package com.zkuba.trading.repository;

import com.zkuba.trading.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u FROM User u WHERE NOT EXISTS (SELECT 1 FROM Account a WHERE a.userId = u.id)")
    List<User> findUsersWithoutAccounts();
}
