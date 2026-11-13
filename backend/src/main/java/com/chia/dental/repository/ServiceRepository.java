package com.chia.dental.repository;

import com.chia.dental.entity.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceRepository extends JpaRepository<ServiceEntity, Long> {
    List<ServiceEntity> findByIsActiveTrueOrderByServiceIdAsc();
    List<ServiceEntity> findByServiceIdInAndIsActiveTrue(List<Long> serviceIds);
}

