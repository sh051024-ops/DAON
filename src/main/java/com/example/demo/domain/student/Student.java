package com.example.demo.domain.student;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "student")
@Getter @Setter
@NoArgsConstructor
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private Integer age;

    @Column(nullable = false, length = 4)
    private String gender;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    @Column(length = 20)
    private String region;

    @Column(length = 20)
    private String education;

    @Column(name = "course_category", nullable = false, length = 30)
    private String courseCategory;

    @Column(name = "class_name", nullable = false, length = 40)
    private String className;

    @Column(name = "class_start")
    private LocalDate classStart;

    @Column(name = "class_end")
    private LocalDate classEnd;

    @Column(name = "major_type", length = 10)
    private String majorType;

    @Column(name = "worker_type", length = 10)
    private String workerType;

    @Column(length = 20)
    private String channel;

    @Column(name = "gov_funded", length = 10)
    private String govFunded;

    @Column(name = "enrolled_at")
    private LocalDate enrolledAt;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "attendance_rate")
    private Integer attendanceRate;

    @Column(name = "assignment_rate")
    private Integer assignmentRate;

    @Column(length = 20)
    private String counselor;

    @Column(length = 20)
    private String instructor;

    @Column(name = "last_consulted_at")
    private LocalDate lastConsultedAt;

    @Column(name = "employment_status", length = 10)
    private String employmentStatus;

    @Column(length = 60)
    private String company;

    @Column(name = "employed_at")
    private LocalDate employedAt;

    @Column(name = "track_until")
    private LocalDate trackUntil;
}
