package com.example.demo.domain.crm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LeadMetaRepository extends JpaRepository<LeadMeta, Long> {
    Optional<LeadMeta> findByStudentId(Long studentId);
}
