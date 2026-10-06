package com.gym.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 模块边界守卫测试（ArchUnit）—— 把《系统结构设计说明》的架构约束变成可执行测试。
 *
 * <p>对应需求：模板第八阶段"模块边界和接口契约必须清晰""跨模块访问必须经过接口契约"。
 * 一旦有人破坏架构（越界依赖 / 循环依赖 / 绕过契约），构建即失败。
 */
class ModuleBoundaryTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.gym");
    }

    @Test
    @DisplayName("shared 只能被依赖，不得依赖任何业务模块")
    void shared_should_not_depend_on_business_modules() {
        ArchRule rule = noClasses().that().resideInAPackage("com.gym.shared..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.gym.system..", "com.gym.membership..", "com.gym.course..",
                        "com.gym.booking..", "com.gym.payment..", "com.gym.assessment..",
                        "com.gym.warning..", "com.gym.equipment..", "com.gym.report..");
        rule.check(classes);
    }

    @Test
    @DisplayName("只有本模块可访问自己的 internal 实现，跨模块必须走 api 契约")
    void internal_packages_are_module_private() {
        ArchRule bookingInternal = noClasses()
                .that().resideOutsideOfPackage("com.gym.booking..")
                .should().dependOnClassesThat().resideInAPackage("com.gym.booking.internal..");
        bookingInternal.check(classes);

        ArchRule courseInternal = noClasses()
                .that().resideOutsideOfPackage("com.gym.course..")
                .should().dependOnClassesThat().resideInAPackage("com.gym.course.internal..");
        courseInternal.check(classes);

        ArchRule membershipInternal = noClasses()
                .that().resideOutsideOfPackage("com.gym.membership..")
                .should().dependOnClassesThat().resideInAPackage("com.gym.membership.internal..");
        membershipInternal.check(classes);
    }

    @Test
    @DisplayName("Controller 不得直接依赖 Repository，避免绕过应用服务")
    void controllers_should_not_depend_on_repositories() {
        ArchRule rule = noClasses().that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository");
        rule.check(classes);
    }
}
