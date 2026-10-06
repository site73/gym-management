package com.gym.shared.rule.internal;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** 规则参数实体（对应表 rule_config）。params_json 以字符串存储，对外解析为 Map。 */
@Entity
@Table(name = "rule_config")
public class RuleConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_code", nullable = false, unique = true, length = 32)
    private String ruleCode;

    @Column(name = "rule_name", nullable = false, length = 64)
    private String ruleName;

    @Lob
    @Column(name = "params_json", nullable = false, columnDefinition = "text")
    private String paramsJson = "{}";

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected RuleConfigEntity() {}

    /**
     * 将 params_json 解析为 Map。
     * 简化实现：形如 {"N":3,"restrictDays":7} 的键值对解析（避免额外 JSON 依赖）。
     */
    public Map<String, Object> getParamsJson() {
        Map<String, Object> map = new HashMap<>();
        String s = paramsJson == null ? "" : paramsJson.trim();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);
        for (String pair : s.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length != 2) continue;
            String k = kv[0].trim().replace("\"", "");
            String v = kv[1].trim().replace("\"", "");
            try { map.put(k, Integer.valueOf(v)); }
            catch (NumberFormatException e1) {
                try { map.put(k, Double.valueOf(v)); }
                catch (NumberFormatException e2) { map.put(k, v); }
            }
        }
        return map;
    }

    public String getRuleCode() { return ruleCode; }
    public void setParamsJson(String p) { this.paramsJson = p; }
    public Integer getVersion() { return version; }
}
