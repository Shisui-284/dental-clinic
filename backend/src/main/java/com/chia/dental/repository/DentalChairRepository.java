package com.chia.dental.repository;

import com.chia.dental.entity.DentalChair;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DentalChairRepository extends JpaRepository<DentalChair, Long> {
    List<DentalChair> findByStatus(String status);
}

