package com.gym.venue.api;

import com.gym.identity.api.AuthContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 场馆入口。
 *
 * <p>查询对已登录用户开放（会员选场地、教练与门店查看都需要）；
 * <b>新增与修改仅限门店后台</b>（店长 / 管理员）。
 */
@RestController
@RequestMapping("/api/venues")
public class VenueController {

    private final VenueFacade venueFacade;

    public VenueController(VenueFacade venueFacade) {
        this.venueFacade = venueFacade;
    }

    /** 场馆列表（登录即可） */
    @GetMapping
    public List<VenueView> list() {
        return venueFacade.listVenues();
    }

    /** 单个场馆 */
    @GetMapping("/{id}")
    public VenueView detail(@PathVariable Long id) {
        return venueFacade.venueOf(id);
    }

    /** 新增场馆（门店后台） */
    @PostMapping
    public ResponseEntity<VenueView> create(@RequestBody VenueDraft draft) {
        AuthContext.requireStaff();
        return ResponseEntity.ok(venueFacade.createVenue(draft));
    }

    /** 修改场馆（门店后台） */
    @PutMapping("/{id}")
    public VenueView update(@PathVariable Long id, @RequestBody VenueDraft draft) {
        AuthContext.requireStaff();
        return venueFacade.updateVenue(id, draft);
    }
}
