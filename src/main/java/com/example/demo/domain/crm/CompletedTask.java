package com.example.demo.domain.crm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "completed_task")
@Getter @Setter
@NoArgsConstructor
public class CompletedTask {

    @Id
    @Column(length = 255)
    private String task;

    public CompletedTask(String task) {
        this.task = task;
    }
}
