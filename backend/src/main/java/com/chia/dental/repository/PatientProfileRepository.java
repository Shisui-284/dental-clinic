package com.chia.dental.repository;

import com.chia.dental.entity.PatientProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PatientProfileRepository extends JpaRepository<PatientProfile, Long> {
    List<PatientProfile> findByCustomerCustomerId(Long customerId);
    Optional<PatientProfile> findByPatientIdAndCustomerCustomerId(Long patientId, Long customerId);
    boolean existsByCustomerPhoneAndFullNameIgnoreCaseAndBirthYear(String phone, String fullName, Short birthYear);
    boolean existsByCustomerPhone(String phone);
}
