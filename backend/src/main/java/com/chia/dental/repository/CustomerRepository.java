package com.chia.dental.repository;

import com.chia.dental.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByPhone(String phone);
    List<Customer> findByIsBlacklistedTrueOrderByCustomerIdAsc();

    @Query(value = "SELECT is_blacklisted FROM customer WHERE customer_id = :customerId", nativeQuery = true)
    Boolean findLatestBlacklistedStatus(@Param("customerId") Long customerId);
}
