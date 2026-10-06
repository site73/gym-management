package com.gym;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 健身房会员运营与课程服务业务 —— 模块化单体应用入口。
 *
 * <p>架构约定（详见《系统结构设计说明》）：
 * <ul>
 *   <li>按业务模块分包：system / membership / course / booking / payment / assessment / warning / equipment / report</li>
 *   <li>仅 shared 可被所有模块依赖；模块之间通过接口契约与领域事件通信</li>
 *   <li>禁止跨模块直接访问对方数据表，禁止模块间循环依赖（由 ArchUnit 守卫）</li>
 *   <li>规则参数外置在数据库 rule_config，改阈值无需发版</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
public class GymApplication {

    public static void main(String[] args) {
        SpringApplication.run(GymApplication.class, args);
    }
}
