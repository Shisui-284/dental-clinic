package com.chia.dental.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal {
    private Long id;          // staffId hoặc customerId
    private String username;  // username (staff) hoặc phone (customer)
    private String role;      // MANAGER, RECEPTIONIST, DOCTOR, hoặc CUSTOMER
    private String type;      // STAFF hoặc CUSTOMER
}

