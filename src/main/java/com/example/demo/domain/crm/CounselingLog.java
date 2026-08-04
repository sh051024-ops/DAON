package com.example.demo.domain.crm;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "counseling_log")
@Getter @Setter
@NoArgsConstructor
public class CounselingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "log_date", nullable = false, length = 20)
    private String logDate;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(length = 200)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String memo;

    public CounselingLog(Long studentId, String logDate, String type, String summary, String memo) {
        this.studentId = studentId;
        this.logDate = logDate;
        this.type = type;
        this.summary = summary;
        this.memo = memo;
    }
}
