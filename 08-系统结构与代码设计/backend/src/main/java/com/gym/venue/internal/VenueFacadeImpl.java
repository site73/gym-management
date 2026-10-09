package com.gym.venue.internal;

import com.gym.membership.api.MembershipFacade;
import com.gym.shared.audit.AuditLogger;
import com.gym.venue.api.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 场馆与场地预约契约实现。 */
@Service
public class VenueFacadeImpl implements VenueFacade {

    /** 单次预约最长时长（小时），防止误操作长时间占用 */
    public static final int MAX_HOURS = 4;

    private final VenueRepository venueRepository;
    private final VenueBookingRepository bookingRepository;
    private final MembershipFacade membershipFacade;
    private final AuditLogger auditLogger;

    public VenueFacadeImpl(VenueRepository venueRepository, VenueBookingRepository bookingRepository,
                           MembershipFacade membershipFacade, AuditLogger auditLogger) {
        this.venueRepository = venueRepository;
        this.bookingRepository = bookingRepository;
        this.membershipFacade = membershipFacade;
        this.auditLogger = auditLogger;
    }

    /* ==================== 场馆维护 ==================== */

    @Override
    public List<VenueView> listVenues() {
        return venueRepository.findAll().stream().map(this::toVenueView).toList();
    }

    @Override
    public VenueView venueOf(Long venueId) {
        return toVenueView(loadVenue(venueId));
    }

    @Override
    @Transactional
    public VenueView createVenue(VenueDraft draft) {
        requireVenueDraft(draft);
        String code = draft.code().trim();
        if (venueRepository.existsByCode(code)) {
            throw new IllegalArgumentException("场馆编号已存在：" + code);
        }
        var entity = new VenueEntity();
        entity.setCode(code);
        applyVenueDraft(entity, draft);
        var saved = venueRepository.save(entity);
        auditLogger.log("venue.create", "venue", saved.getId(),
                saved.getCode() + " " + saved.getName() + "（" + saved.getType() + "）");
        return toVenueView(saved);
    }

    @Override
    @Transactional
    public VenueView updateVenue(Long venueId, VenueDraft draft) {
        requireVenueDraft(draft);
        var entity = loadVenue(venueId);
        applyVenueDraft(entity, draft);
        var saved = venueRepository.save(entity);
        auditLogger.log("venue.update", "venue", saved.getId(),
                saved.getCode() + " → " + saved.getStatus());
        return toVenueView(saved);
    }

    /* ==================== 场地预约 ==================== */

    @Override
    public List<VenueBookingView> listBookings(Long venueId, Long memberId) {
        List<VenueBookingEntity> list;
        if (venueId != null) {
            list = bookingRepository.findByVenueIdAndStatusOrderByStartTimeAsc(venueId, "booked");
        } else if (memberId != null) {
            list = bookingRepository.findByMemberIdOrderByStartTimeDesc(memberId);
        } else {
            list = bookingRepository.findAllByOrderByStartTimeDesc();
        }
        return assemble(list);
    }

    @Override
    public VenueBookingView bookingOf(Long bookingId) {
        return assemble(List.of(loadBooking(bookingId))).get(0);
    }

    @Override
    @Transactional
    public VenueBookingView book(Long memberId, VenueBookingDraft draft, Long operatorId) {
        if (draft == null || draft.venueId() == null) {
            throw new IllegalArgumentException("缺少场馆信息");
        }
        if (memberId == null) {
            throw new IllegalArgumentException("缺少会员 ID");
        }
        if (!membershipFacade.exists(memberId)) {
            throw new IllegalArgumentException("会员不存在：" + memberId);
        }

        var venue = loadVenue(draft.venueId());
        if (!"available".equals(venue.getStatus())) {
            throw new IllegalStateException("该场馆当前维护中，暂不可预约");
        }

        validateTime(draft);

        // 私有场馆仅对有效会籍开放；公共区域不限制（差额体现在这里）
        if (!"public".equals(venue.getType()) && !membershipFacade.isEffective(memberId)) {
            throw new IllegalStateException("私有场馆仅对有效会籍开放，请先办理或续费会籍");
        }

        if (bookingRepository.countConflict(venue.getId(), draft.startTime(), draft.endTime()) > 0) {
            throw new IllegalStateException("该时段已被预约，请换一个时间段（同一场地时段不可重叠）");
        }

        var saved = bookingRepository.save(new VenueBookingEntity(
                venue.getId(), memberId, draft.startTime(), draft.endTime(), operatorId));
        auditLogger.log("venue.book", "venue_booking", saved.getId(),
                venue.getName() + " " + draft.startTime() + " ~ " + draft.endTime()
                        + " memberId=" + memberId + (operatorId == null ? "（自助）" : "（代约）"));
        return assemble(List.of(saved)).get(0);
    }

    @Override
    @Transactional
    public void cancelBooking(Long bookingId) {
        var entity = loadBooking(bookingId);
        if (!"booked".equals(entity.getStatus())) {
            throw new IllegalStateException("当前状态不可取消：" + entity.getStatus());
        }
        entity.setStatus("cancelled");
        bookingRepository.save(entity);
        auditLogger.log("venue.cancel", "venue_booking", bookingId, "取消场地预约");
    }

    /* ==================== 内部 ==================== */

    private VenueEntity loadVenue(Long venueId) {
        return venueRepository.findById(venueId)
                .orElseThrow(() -> new IllegalArgumentException("场馆不存在：" + venueId));
    }

    private VenueBookingEntity loadBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("场地预约不存在：" + bookingId));
    }

    private void validateTime(VenueBookingDraft draft) {
        if (draft.startTime() == null || draft.endTime() == null) {
            throw new IllegalArgumentException("请选择预约开始与结束时间");
        }
        if (!draft.endTime().isAfter(draft.startTime())) {
            throw new IllegalArgumentException("结束时间必须晚于开始时间");
        }
        if (draft.startTime().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("开始时间不能早于当前时间");
        }
        if (java.time.Duration.between(draft.startTime(), draft.endTime()).toHours() > MAX_HOURS) {
            throw new IllegalArgumentException("单次预约不能超过 " + MAX_HOURS + " 小时");
        }
    }

    private void requireVenueDraft(VenueDraft draft) {
        if (draft == null) throw new IllegalArgumentException("缺少场馆信息");
        if (draft.name() == null || draft.name().isBlank()) {
            throw new IllegalArgumentException("场馆名称不能为空");
        }
        if (draft.capacity() == null || draft.capacity() < 1) {
            throw new IllegalArgumentException("容纳人数必须至少为 1");
        }
    }

    private void applyVenueDraft(VenueEntity entity, VenueDraft draft) {
        entity.setName(draft.name().trim());
        entity.setType(normalizeType(draft.type()));
        entity.setCapacity(draft.capacity());
        entity.setLocation(blankToNull(draft.location()));
        entity.setHourlyFee(draft.hourlyFee() == null ? BigDecimal.ZERO : draft.hourlyFee());
        entity.setStatus(normalizeStatus(draft.status()));
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) return "private";
        String t = type.trim();
        if (!"private".equals(t) && !"public".equals(t)) {
            throw new IllegalArgumentException("场馆类型只能是 private 或 public");
        }
        return t;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return "available";
        String s = status.trim();
        if (!"available".equals(s) && !"maintenance".equals(s)) {
            throw new IllegalArgumentException("场馆状态只能是 available 或 maintenance");
        }
        return s;
    }

    private String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private VenueView toVenueView(VenueEntity v) {
        return new VenueView(v.getId(), v.getCode(), v.getName(), v.getType(),
                v.getCapacity() == null ? 0 : v.getCapacity(), v.getLocation(),
                v.getHourlyFee(), v.getStatus());
    }

    /** 批量装配视图：一次查出场馆名与会员名，避免 N+1 */
    private List<VenueBookingView> assemble(List<VenueBookingEntity> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, VenueEntity> venues = venueRepository.findAllById(
                        list.stream().map(VenueBookingEntity::getVenueId).distinct().toList())
                .stream().collect(Collectors.toMap(VenueEntity::getId, Function.identity()));
        Map<Long, String> memberNames = membershipFacade.listMembers().stream()
                .collect(Collectors.toMap(m -> m.id(), m -> m.name(), (a, b) -> a));

        return list.stream().map(b -> {
            var v = venues.get(b.getVenueId());
            return new VenueBookingView(b.getId(), b.getVenueId(),
                    v == null ? ("#" + b.getVenueId()) : v.getName(),
                    v == null ? null : v.getType(),
                    b.getMemberId(), memberNames.getOrDefault(b.getMemberId(), "#" + b.getMemberId()),
                    b.getStartTime(), b.getEndTime(), b.getStatus(), b.getCreatedBy());
        }).toList();
    }
}
