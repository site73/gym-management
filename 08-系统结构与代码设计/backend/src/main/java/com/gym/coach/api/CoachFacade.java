package com.gym.coach.api;

import java.util.List;

/**
 * 教练模块对外契约。
 *
 * <p>教练档案被课程模块引用（{@code course.coach_id}），因此不提供物理删除，
 * 停用请把 {@code status} 置为 {@code leave}。
 */
public interface CoachFacade {

    /** 全部教练（排课下拉、门店管理均使用） */
    List<CoachView> listCoaches();

    /** 单个教练 */
    CoachView coachOf(Long coachId);

    /** 新增教练 */
    CoachView createCoach(CoachDraft draft);

    /** 修改教练 */
    CoachView updateCoach(Long coachId, CoachDraft draft);

    /** 教练是否存在 */
    boolean exists(Long coachId);
}
