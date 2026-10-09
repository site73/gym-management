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

    @Modifying
    @Query(value = "update coach set user_id = :userId where id = :coachId", nativeQuery = true)
    void linkCoach(@Param("coachId") Long coachId, @Param("userId") Long userId);

    /* ---------- 账号管理（超级管理员） ---------- */

    /** 全部账号（按创建顺序） */
    @Query(value = "select id, username, display_name, status, created_at, password_reset_at "
                 + "from sys_user order by id", nativeQuery = true)
    java.util.List<Object[]> listAccounts();

    /** 重置密码（仅更新哈希与重置时间） */
    @Modifying
    @Query(value = "update sys_user set password_hash = :hash, password_reset_at = now() where id = :userId",
            nativeQuery = true)
    void resetPassword(@Param("userId") Long userId, @Param("hash") String hash);

    /** 启用 / 停用账号 */
    @Modifying
    @Query(value = "update sys_user set status = :status where id = :userId", nativeQuery = true)
    void updateStatus(@Param("userId") Long userId, @Param("status") String status);

    /** 单账号的角色编码 */
    @Query(value = "select r.code from sys_role r join sys_user_role ur on ur.role_id = r.id "
                 + "where ur.user_id = :userId order by r.id", nativeQuery = true)
    java.util.List<String> roleCodesOf(@Param("userId") Long userId);

    /** 全部「用户-角色」对应关系（批量，避免账号列表 N+1 查询） */
    @Query(value = "select ur.user_id, r.code from sys_user_role ur "
                 + "join sys_role r on r.id = ur.role_id order by ur.user_id, r.id", nativeQuery = true)
    java.util.List<Object[]> allUserRoles();

    /** 全部「账号-会员」绑定 */
    @Query(value = "select id, user_id from member where user_id is not null", nativeQuery = true)
    java.util.List<Object[]> allMemberBindings();

    /** 全部「账号-教练」绑定 */
    @Query(value = "select id, user_id from coach where user_id is not null", nativeQuery = true)
    java.util.List<Object[]> allCoachBindings();

    @Query(value = "select id from coach where user_id = :userId", nativeQuery = true)
    Long findCoachIdByUserId(@Param("userId") Long userId);
}
