package com.example.demo.domain.crm;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CompletedTaskRepository extends JpaRepository<CompletedTask, String> {
}
