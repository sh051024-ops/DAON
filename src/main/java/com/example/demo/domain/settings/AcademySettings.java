package com.example.demo.domain.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 학원 기본 정보. 항상 id=1 하나만 존재한다(설정 화면 = 단일 레코드 편집). */
@Entity
@Table(name = "academy_settings")
@Getter
@Setter
@NoArgsConstructor
public class AcademySettings {

    @Id
    private Long id;

    @Column(name = "academy_name", nullable = false, length = 60)
    private String academyName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "address", length = 200)
    private String address;

    @Column(name = "business_reg_no", length = 20)
    private String businessRegNo;

    @Column(name = "ceo_name", length = 20)
    private String ceoName;

    @Column(name = "business_type", length = 30)
    private String businessType;
}
