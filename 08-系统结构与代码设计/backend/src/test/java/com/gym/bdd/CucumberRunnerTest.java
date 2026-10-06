package com.gym.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FEATURES_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * 行为驱动验收测试入口（关键规则 .feature）。
 *
 * <p>场景文件为**单一来源**：直接指向仓库中第五阶段的
 * {@code 05-测试用例与测试计划/features}，不在代码目录重复维护副本，
 * 从而满足"关键业务规则的 .feature 文件、测试代码、契约文件、迁移脚本与代码进入同一版本库"。
 *
 * <p>本轮覆盖 **S1 切片的全部三个 feature**（booking / attendance / membership，共 18 个场景）；
 * 后续切片的 warning / commission 随对应切片接入。
 *
 * <p>运行：{@code mvn test}（需 JDK 17 + Maven）。
 */
@Suite
@IncludeEngines("cucumber")
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME,
        value = "../../05-测试用例与测试计划/features/booking.feature"
              + ",../../05-测试用例与测试计划/features/attendance.feature"
              + ",../../05-测试用例与测试计划/features/membership.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.gym.bdd")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:target/cucumber-report.html")
public class CucumberRunnerTest {
}
