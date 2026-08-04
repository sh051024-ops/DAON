package com.example.demo.domain.crm;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lead_meta")
@Getter @Setter
@NoArgsConstructor
public class LeadMeta {

    @Id
    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "lead_status", length = 20)
    private String leadStatus;

    @Column(columnDefinition = "TEXT")
    private String memo;

    public LeadMeta(Long studentId, String leadStatus, String memo) {
        this.studentId = studentId;
        this.leadStatus = leadStatus;
        this.memo = memo;
    }
}
