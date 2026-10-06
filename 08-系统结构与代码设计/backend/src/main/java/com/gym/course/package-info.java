/**
 * M3 课程与排课模块（← B3 课程与排课）。
 *
 * <p>职责：课程目录与排课（REQ-B3-001）、冲突检测（SYS-R3）、课程发布与名额管理（REQ-B3-002）。
 * <p>对外契约：{@code com.gym.course.api.CourseFacade}
 * （查询课程余位、占用/释放名额、校验排课冲突）。
 * <p>依赖：shared、system。被 booking / report 以接口方式调用。
 * <p>数据归属：course。
 * <p>注意：容量校验在本模块内闭环，booking 模块不得直接读写 course 表。
 */
package com.gym.course;
