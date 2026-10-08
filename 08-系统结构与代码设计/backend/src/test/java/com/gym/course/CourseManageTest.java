package com.gym.course;

import com.gym.course.api.CourseDraft;
import com.gym.course.api.CourseFacade;
import com.gym.course.api.CourseView;
import com.gym.course.internal.CourseEntity;
import com.gym.course.internal.CourseFacadeImpl;
import com.gym.course.internal.CourseRepository;
import com.gym.shared.audit.AuditLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 课程维护单元测试（管理员的增、改、下架、上架）。
 *
 * <p>重点覆盖四类校验：编号唯一、时间合法、容量合理、排课不冲突（SYS-R3）。
 */
class CourseManageTest {

    private final CourseRepository repo = mock(CourseRepository.class);
    private final AuditLogger audit = mock(AuditLogger.class);
    private final CourseFacade facade = new CourseFacadeImpl(repo, audit);

    private static final LocalDateTime START = LocalDateTime.now().plusDays(1);
    private static final LocalDateTime END = START.plusHours(1);

    private CourseDraft draft(String code, String name, int capacity) {
        return new CourseDraft(code, name, "group", 101L, "A 厅", START, END, capacity);
    }

    private CourseEntity existing(Long id, int capacity, int booked) {
        var c = new CourseEntity(id, "C001", "动感单车", 101L, "A 厅", START, END, capacity);
        c.setBookedCount(booked);
        return c;
    }

    /** 让 save 返回入参，模拟落库后的实体 */
    private void stubSave() {
        when(repo.save(any(CourseEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("新建课程成功：默认团课、已发布、已约 0")
    void create_ok() {
        stubSave();
        when(repo.existsByCode("C100")).thenReturn(false);
        when(repo.countConflict(any(), any(), any(), any())).thenReturn(0L);

        CourseView v = facade.createCourse(draft("C100", "普拉提", 12));

        assertThat(v.code()).isEqualTo("C100");
        assertThat(v.name()).isEqualTo("普拉提");
        assertThat(v.type()).isEqualTo("group");
        assertThat(v.status()).isEqualTo("published");
        assertThat(v.bookedCount()).isZero();
        assertThat(v.remaining()).isEqualTo(12);
        assertThat(v.statusCn()).isEqualTo("已发布");
    }

    @Test
    @DisplayName("课程编号重复 → 拒绝")
    void create_duplicate_code() {
        when(repo.existsByCode("C100")).thenReturn(true);

        assertThatThrownBy(() -> facade.createCourse(draft("C100", "普拉提", 12)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("课程编号已存在");
    }

    @Test
    @DisplayName("结束时间早于开始时间 → 拒绝")
    void create_bad_time_range() {
        var d = new CourseDraft("C101", "普拉提", "group", 101L, "A 厅", END, START, 10);
        assertThatThrownBy(() -> facade.createCourse(d))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("结束时间必须晚于开始时间");
    }

    @Test
    @DisplayName("开始时间早于当前时间 → 拒绝（不能排过去的课）")
    void create_past_time() {
        var d = new CourseDraft("C102", "普拉提", "group", 101L, "A 厅",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().minusHours(22), 10);
        assertThatThrownBy(() -> facade.createCourse(d))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("开始时间不能早于当前时间");
    }

    @Test
    @DisplayName("容量小于 1 → 拒绝")
    void create_bad_capacity() {
        assertThatThrownBy(() -> facade.createCourse(draft("C103", "普拉提", 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("容量必须至少为 1");
    }

    @Test
    @DisplayName("名称为空 → 拒绝")
    void create_blank_name() {
        assertThatThrownBy(() -> facade.createCourse(draft("C104", "  ", 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("课程名称不能为空");
    }

    @Test
    @DisplayName("教练或场地在该时段已有排课 → 拒绝（SYS-R3）")
    void create_conflict() {
        when(repo.existsByCode("C105")).thenReturn(false);
        when(repo.countConflict(any(), any(), any(), any())).thenReturn(1L);

        assertThatThrownBy(() -> facade.createCourse(draft("C105", "普拉提", 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已有排课");
    }

    @Test
    @DisplayName("修改课程成功，且冲突检测排除自身")
    void update_ok() {
        stubSave();
        var c = existing(11L, 20, 3);
        when(repo.findById(11L)).thenReturn(Optional.of(c));
        when(repo.countConflictExcluding(any(), any(), any(), any(), any())).thenReturn(0L);

        CourseView v = facade.updateCourse(11L, draft("C001", "动感单车进阶", 25));

        assertThat(v.name()).isEqualTo("动感单车进阶");
        assertThat(v.capacity()).isEqualTo(25);
        assertThat(v.remaining()).isEqualTo(22);   // 25 - 3 已约
    }

    @Test
    @DisplayName("修改后的容量小于已预约人数 → 拒绝（防超卖）")
    void update_capacity_below_booked() {
        var c = existing(11L, 20, 5);
        when(repo.findById(11L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> facade.updateCourse(11L, draft("C001", "动感单车", 4)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("容量不能小于已预约人数");
    }

    @Test
    @DisplayName("课程不存在 → 拒绝")
    void update_not_found() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.updateCourse(99L, draft("C001", "动感单车", 20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("课程不存在");
    }

    @Test
    @DisplayName("下架课程：状态变为 cancelled，且不物理删除")
    void cancel_course() {
        stubSave();
        var c = existing(11L, 20, 0);
        when(repo.findById(11L)).thenReturn(Optional.of(c));

        facade.cancelCourse(11L);

        assertThat(c.getStatus()).isEqualTo("cancelled");
    }

    @Test
    @DisplayName("重新上架：状态回到 published")
    void publish_course() {
        stubSave();
        var c = existing(11L, 20, 0);
        c.setStatus("cancelled");
        when(repo.findById(11L)).thenReturn(Optional.of(c));

        CourseView v = facade.publishCourse(11L);

        assertThat(v.status()).isEqualTo("published");
    }

    @Test
    @DisplayName("已结束的课程不能重新上架")
    void publish_finished_course() {
        var c = new CourseEntity(12L, "C009", "旧课", 101L, "A 厅",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(2).plusHours(1), 10);
        c.setStatus("cancelled");
        when(repo.findById(12L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> facade.publishCourse(12L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("课程已结束");
    }

    @Test
    @DisplayName("课程详情与状态中文名")
    void course_detail() {
        var c = existing(11L, 20, 20);
        c.setStatus("full");
        when(repo.findById(11L)).thenReturn(Optional.of(c));

        CourseView v = facade.courseOf(11L);

        assertThat(v.remaining()).isZero();
        assertThat(v.statusCn()).isEqualTo("已满员");
        assertThat(v.typeCn()).isEqualTo("团课");
    }
}
