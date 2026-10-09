package com.gym.coach.internal;

import com.gym.coach.api.CoachDraft;
import com.gym.coach.api.CoachFacade;
import com.gym.coach.api.CoachView;
import com.gym.shared.audit.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 教练档案契约实现。 */
@Service
public class CoachFacadeImpl implements CoachFacade {

    private final CoachRepository coachRepository;
    private final AuditLogger auditLogger;

    public CoachFacadeImpl(CoachRepository coachRepository, AuditLogger auditLogger) {
        this.coachRepository = coachRepository;
        this.auditLogger = auditLogger;
    }

    @Override
    public List<CoachView> listCoaches() {
        return coachRepository.findAll().stream().map(this::toView).toList();
    }

    @Override
    public CoachView coachOf(Long coachId) {
        return toView(load(coachId));
    }

    @Override
    @Transactional
    public CoachView createCoach(CoachDraft draft) {
        requireDraft(draft);
        String code = draft.code().trim();
        if (coachRepository.existsByCode(code)) {
            throw new IllegalArgumentException("教练编号已存在：" + code);
        }
        var entity = new CoachEntity();
        entity.setCode(code);
        entity.setName(draft.name().trim());
        entity.setPhone(blankToNull(draft.phone()));
        entity.setSpecialty(blankToNull(draft.specialty()));
        entity.setStatus(normalizeStatus(draft.status()));
        var saved = coachRepository.save(entity);
        auditLogger.log("coach.create", "coach", saved.getId(),
                saved.getCode() + " " + saved.getName());
        return toView(saved);
    }

    @Override
    @Transactional
    public CoachView updateCoach(Long coachId, CoachDraft draft) {
        requireDraft(draft);
        var entity = load(coachId);
        entity.setName(draft.name().trim());
        entity.setPhone(blankToNull(draft.phone()));
        entity.setSpecialty(blankToNull(draft.specialty()));
        entity.setStatus(normalizeStatus(draft.status()));
        var saved = coachRepository.save(entity);
        auditLogger.log("coach.update", "coach", saved.getId(),
                saved.getCode() + " → " + saved.getStatus());
        return toView(saved);
    }

    @Override
    public boolean exists(Long coachId) {
        return coachId != null && coachRepository.existsById(coachId);
    }

    /* ==================== 内部 ==================== */

    private CoachEntity load(Long coachId) {
        return coachRepository.findById(coachId)
                .orElseThrow(() -> new IllegalArgumentException("教练不存在：" + coachId));
    }

    private void requireDraft(CoachDraft draft) {
        if (draft == null) throw new IllegalArgumentException("缺少教练信息");
        if (draft.name() == null || draft.name().isBlank()) {
            throw new IllegalArgumentException("教练姓名不能为空");
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return "active";
        String s = status.trim();
        if (!"active".equals(s) && !"leave".equals(s)) {
            throw new IllegalArgumentException("教练状态只能是 active 或 leave");
        }
        return s;
    }

    private String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private CoachView toView(CoachEntity c) {
        return new CoachView(c.getId(), c.getCode(), c.getName(), c.getPhone(),
                c.getSpecialty(), c.getStatus(), c.getUserId());
    }
}
