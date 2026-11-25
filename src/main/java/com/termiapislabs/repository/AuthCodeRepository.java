package com.termiapislabs.repository;

import com.termiapislabs.model.AuthCode;
import com.termiapislabs.model.AuthCodeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthCodeRepository extends JpaRepository<AuthCode, UUID> {
    Optional<AuthCode> findTopByUserIdAndTypeOrderByCreatedAtDesc(UUID userId, AuthCodeType type);
    List<AuthCode> findByUserIdAndTypeAndInvalidatedAtIsNull(UUID userId, AuthCodeType type);
}
