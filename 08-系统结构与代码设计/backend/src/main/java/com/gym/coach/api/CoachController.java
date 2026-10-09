package com.gym.coach.api;

import com.gym.identity.api.AuthContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 教练入口。
 *
 * <p>查询对已登录用户开放（排课的教练下拉、教练本人查看自己档案都需要）；
 * <b>新增与修改仅限门店后台</b>（店长 / 管理员）。
 */
@RestController
@RequestMapping("/api/coaches")
public class CoachController {

    private final CoachFacade coachFacade;

    public CoachController(CoachFacade coachFacade) {
        this.coachFacade = coachFacade;
    }

    /** 教练列表（登录即可） */
    @GetMapping
    public List<CoachView> list() {
        return coachFacade.listCoaches();
    }

    /** 教练本人档案（教练端头部展示用） */
    @GetMapping("/me")
    public CoachView me() {
        AuthContext.requireCoach();
        return coachFacade.coachOf(AuthContext.current().coachId());
    }

    /** 单个教练 */
    @GetMapping("/{id}")
    public CoachView detail(@PathVariable Long id) {
        return coachFacade.coachOf(id);
    }

    /** 新增教练（门店后台） */
    @PostMapping
    public ResponseEntity<CoachView> create(@RequestBody CoachDraft draft) {
        AuthContext.requireStaff();
        return ResponseEntity.ok(coachFacade.createCoach(draft));
    }

    /** 修改教练（门店后台） */
    @PutMapping("/{id}")
    public CoachView update(@PathVariable Long id, @RequestBody CoachDraft draft) {
        AuthContext.requireStaff();
        return coachFacade.updateCoach(id, draft);
    }
}
