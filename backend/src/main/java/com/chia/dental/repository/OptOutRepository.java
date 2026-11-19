package com.chia.dental.repository;

import com.chia.dental.entity.OptOut;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OptOutRepository extends JpaRepository<OptOut, Long> {
    List<OptOut> findByCustomerIdAndIsActiveTrue(Long customerId);
    Optional<OptOut> findFirstByCustomerIdAndChannelAndIsActiveTrue(Long customerId, String channel);
}
