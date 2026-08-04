package com.example.demo.domain.student;

import com.example.demo.domain.student.dto.StudentDtos.PageResult;
import com.example.demo.domain.student.dto.StudentDtos.RiskStudent;
import com.example.demo.domain.student.dto.StudentDtos.StudentRow;
import com.example.demo.domain.student.dto.StudentDtos.Summary;
import com.example.demo.domain.student.dto.StudentDtos.StudentRegisterRequest;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    /** 학생 목록 (필터 + 검색 + 페이지) */
    @GetMapping
    public PageResult<StudentRow> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return studentService.getStudents(category, className, status, query, page, size);
    }

    /** 상단 요약 카드 */
    @GetMapping("/summary")
    public Summary summary() {
        return studentService.getSummary();
    }

    /** 위험 학생 TOP 5 */
    @GetMapping("/risk-top5")
    public List<RiskStudent> riskTop5() {
        return studentService.getRiskTop5();
    }

    /** 필터용 반 목록 */
    @GetMapping("/class-names")
    public List<String> classNames() {
        return studentService.getClassNames();
    }

    /** 신규 학생 등록 */
    @PostMapping
    public StudentRow register(@RequestBody StudentRegisterRequest req) {
        return studentService.registerStudent(req);
    }

    /** 학생 목록 내보내기 (CSV) */
    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "q") String query,
            HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"students_export.csv\"");
        studentService.writeStudentsCsv(category, className, status, query, response.getWriter());
    }
}
