package io.github.xiaowuiot.envmonitor.data;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface MonitorDataRepository extends JpaRepository<MonitorData, Long> {

    List<MonitorData> findByDeviceIdAndFactorCodeAndDataTypeAndDataTimeBetweenOrderByDataTimeAsc(
            Long deviceId, String factorCode, DataType dataType, LocalDateTime from, LocalDateTime to);

    List<MonitorData> findByDeviceIdAndDataTypeAndDataTimeBetweenOrderByDataTimeAsc(
            Long deviceId, DataType dataType, LocalDateTime from, LocalDateTime to);

    @Query("select d from MonitorData d where d.deviceId = ?1 and d.factorCode = ?2 and d.dataType = ?3 order by d.dataTime desc")
    List<MonitorData> findRecent(Long deviceId, String factorCode, DataType dataType, Pageable pageable);

    boolean existsByDeviceIdAndFactorCodeAndDataTypeAndDataTime(Long deviceId, String factorCode, DataType dataType,
                                                                 LocalDateTime dataTime);

    long countByReceivedAtAfter(LocalDateTime time);

    @Modifying
    @Transactional
    @Query("delete from MonitorData d where d.dataType = ?1 and d.dataTime < ?2")
    int deleteOld(DataType type, LocalDateTime before);
}
