package com.gym.course.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 课程查询入口（小程序端与后台共用）。
 * 只依赖模块对外契约 {@link CourseFacade}，不直接访问仓储。
 */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseFacade courseFacade;

    public CourseController(CourseFacade courseFacade) {
        this.courseFacade = courseFacade;
    }

    @GetMapping
    public List<CourseView> list() {
        return courseFacade.listCourses();
    }
}
