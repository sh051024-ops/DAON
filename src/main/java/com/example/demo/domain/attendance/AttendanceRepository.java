package com.example.demo.domain.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findByAttendanceDateBetween(LocalDate start, LocalDate end);

    List<Attendance> findByAttendanceDate(LocalDate date);

    /** 데이터상 가장 최근 날짜 (데모의 '오늘') */
    @Query("select max(a.attendanceDate) from Attendance a")
    LocalDate findLatestDate();

    /** 데이터상 가장 오래된 날짜 */
    @Query("select min(a.attendanceDate) from Attendance a")
    LocalDate findEarliestDate();
}
