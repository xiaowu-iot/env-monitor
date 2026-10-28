package io.github.xiaowuiot.envmonitor.alarm;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlarmRuleRepository extends JpaRepository<AlarmRule, Long> {

    List<AlarmRule> findByEnabledTrue();

    List<AlarmRule> findAllByOrderByIdAsc();
}
