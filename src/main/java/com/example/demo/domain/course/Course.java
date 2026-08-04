package com.example.demo.domain.course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "course")
@Getter
@NoArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(name = "cohort_name", nullable = false, length = 40)
    private String cohortName;

    @Column(nullable = false)
    private Integer capacity;

    /** 운영중 | 모집중 | 종료 */
    @Column(nullable = false, length = 10)
    private String status;

    @Column(length = 20)
    private String instructor;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** 수료율(%) — 종료 기수만 값이 있고 진행중은 null */
    @Column(name = "completion_rate")
    private Integer completionRate;
}
