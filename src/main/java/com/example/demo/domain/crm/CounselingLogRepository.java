package com.example.demo.domain.crm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CounselingLogRepository extends JpaRepository<CounselingLog, Long> {
    List<CounselingLog> findByStudentIdOrderByIdDesc(Long studentId);
}
