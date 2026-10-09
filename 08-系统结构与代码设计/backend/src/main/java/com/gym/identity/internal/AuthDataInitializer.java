package com.gym.identity.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 账号初始化（幂等）：角色、演示账号、会员账号与档案的绑定关系。
 *
 * <p>放在 Java 侧初始化而不写进 SQL，是为了让密码哈希在各数据库（MySQL / H2）上保持一致。
 *
 * <table>
 *   <caption>演示账号</caption>
 *   <tr><th>用户名</th><th>密码</th><th>角色</th><th>进入页面</th></tr>
 *   <tr><td>member1</td><td>123456</td><td>member</td><td>会员端（张三）</td></tr>
 *   <tr><td>member2</td><td>123456</td><td>member</td><td>会员端（李四，会籍过期）</td></tr>
 *   <tr><td>manager</td><td>123456</td><td>manager</td><td>门店后台</td></tr>
 *   <tr><td>admin</td><td>123456</td><td>admin</td><td>门店后台（含管理员权限）</td></tr>
 * </table>
 */
@Component
@Order(10)
public class AuthDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthDataInitializer.class);
    private static final String DEFAULT_PASSWORD = "123456";

    private final SysUserRepository repo;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthDataInitializer(SysUserRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        ensureRole("member", "会员");
        ensureRole("manager", "店长");
        ensureRole("admin", "系统管理员");
        ensureRole("coach", "教练");

        Long member1 = ensureUser("member1", "张三（会员）", "member");
        Long member2 = ensureUser("member2", "李四（会员）", "member");
        ensureUser("manager", "门店店长", "manager");
        ensureUser("admin", "系统管理员", "admin");

        // 教练账号（coach1/coach2/coach3 绑定 coach 档案 101/102/103）
        Long coach1 = ensureUser("coach1", "王教练", "coach");
        Long coach2 = ensureUser("coach2", "李教练", "coach");
        Long coach3 = ensureUser("coach3", "赵教练", "coach");

        // 会员账号与会员档案绑定（member.user_id）
        if (member1 != null) repo.linkMember(1L, member1);
        if (member2 != null) repo.linkMember(2L, member2);

        // 教练账号与教练档案绑定（coach.user_id）
        if (coach1 != null) repo.linkCoach(101L, coach1);
        if (coach2 != null) repo.linkCoach(102L, coach2);
        if (coach3 != null) repo.linkCoach(103L, coach3);

        long total = repo.count();
        log.info("[auth] 账号初始化完成：系统用户 {} 个（默认密码 {}）", total, DEFAULT_PASSWORD);
    }

    private void ensureRole(String code, String name) {
        if (repo.countRoleByCode(code) == 0) {
            repo.insertRole(code, name);
        }
    }

    /** @return 用户 ID；已存在则返回现有 ID（不覆盖密码，避免改密被重置） */
    private Long ensureUser(String username, String displayName, String roleCode) {
        var exist = repo.findByUsername(username);
        Long userId;
        if (exist.isPresent()) {
            userId = exist.get().getId();
        } else {
            var saved = repo.save(new SysUserEntity(username, encoder.encode(DEFAULT_PASSWORD), displayName));
            userId = saved.getId();
            log.info("[auth] 新建账号：{} / {}（{}）", username, DEFAULT_PASSWORD, roleCode);
        }
        // 幂等绑定（SQL 层 not exists 兜底，重复启动不会报错）
        repo.bindRole(userId, roleCode);
        return userId;
    }

    /** 供测试与文档使用的账号清单 */
    public static List<String> demoAccounts() {
        return List.of("member1 / 123456（会员·张三）", "member2 / 123456（会员·李四·会籍过期）",
                "manager / 123456（门店后台）", "admin / 123456（门店后台·管理员）",
                "coach1 / 123456（教练·王教练）", "coach2 / 123456（教练·李教练）",
                "coach3 / 123456（教练·赵教练）");
    }
}
