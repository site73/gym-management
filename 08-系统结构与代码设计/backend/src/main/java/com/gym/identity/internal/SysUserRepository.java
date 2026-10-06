package com.gym.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUserEntity, Long> {

    Optional<SysUserEntity> findByUsername(String username);

    /** 角色编码列表（member / manager / admin …） */
    @Query(value = "select r.code from sys_role r "
                 + "join sys_user_role ur on ur.role_id = r.id "
                 + "where ur.user_id = :userId", nativeQuery = true)
    List<String> findRoleCodes(@Param("userId") Long userId);

    @Query(value = "select count(*) from sys_role where code = :code", nativeQuery = true)
    long countRoleByCode(@Param("code") String code);

    @Modifying
    @Query(value = "insert into sys_role (code, name) values (:code, :name)", nativeQuery = true)
    void insertRole(@Param("code") String code, @Param("name") String name);

    /**
     * 绑定角色（幂等）。
     *
     * <p>用 {@code not exists} 兜住重复绑定：若直接 insert 靠主键冲突报错，
     * 异常会把外层事务标记为 rollback-only，导致应用启动失败（已踩过该坑）。
     */
    @Modifying
    @Query(value = "insert into sys_user_role (user_id, role_id) "
                 + "select :userId, r.id from sys_role r "
                 + "where r.code = :code "
                 + "  and not exists (select 1 from sys_user_role ur "
                 + "                  where ur.user_id = :userId and ur.role_id = r.id)", nativeQuery = true)
    void bindRole(@Param("userId") Long userId, @Param("code") String code);

    @Modifying
    @Query(value = "update member set user_id = :userId where id = :memberId", nativeQuery = true)
    void linkMember(@Param("memberId") Long memberId, @Param("userId") Long userId);

    @Query(value = "select user_id from member where id = :memberId", nativeQuery = true)
    Long findUserIdByMemberId(@Param("memberId") Long memberId);

    @Query(value = "select id from member where user_id = :userId", nativeQuery = true)
    Long findMemberIdByUserId(@Param("userId") Long userId);
}
