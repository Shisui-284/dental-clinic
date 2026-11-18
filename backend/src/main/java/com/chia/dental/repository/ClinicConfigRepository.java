package com.chia.dental.repository;

import com.chia.dental.entity.ClinicConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClinicConfigRepository extends JpaRepository<ClinicConfig, String> {

    @Query(value = "SELECT config_value #>> '{}' FROM clinic_config WHERE config_key = :key", nativeQuery = true)
    Optional<String> findRawTextValueByKey(@Param("key") String key);
}

