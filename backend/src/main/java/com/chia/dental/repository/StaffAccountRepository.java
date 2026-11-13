package com.chia.dental.repository;

import com.chia.dental.entity.StaffAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffAccountRepository extends JpaRepository<StaffAccount, Long> {
    Optional<StaffAccount> findByUsername(String username);
    List<StaffAccount> findByRoleRoleNameAndStatusTrue(String roleName);
}

