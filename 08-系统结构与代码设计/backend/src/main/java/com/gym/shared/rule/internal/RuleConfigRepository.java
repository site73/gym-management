package com.gym.shared.rule.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RuleConfigRepository extends JpaRepository<RuleConfigEntity, Long> {
    Optional<RuleConfigEntity> findByRuleCode(String ruleCode);
}
