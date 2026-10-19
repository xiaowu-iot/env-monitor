package io.github.xiaowuiot.envmonitor.alarm;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AlarmRepository extends JpaRepository<Alarm, Long>, JpaSpecificationExecutor<Alarm> {

    @Query("select a from Alarm a where a.ruleId = ?1 and a.deviceId = ?2 and a.factorCode = ?3 "
            + "and a.status <> io.github.xiaowuiot.envmonitor.alarm.AlarmStatus.CLOSED")
    Optional<Alarm> findOpen(Long ruleId, Long deviceId, String factorCode);

    List<Alarm> findByStatusNot(AlarmStatus status);

    List<Alarm> findByStatus(AlarmStatus status);

    List<Alarm> findByFirstAtBetween(LocalDateTime from, LocalDateTime to);
}
