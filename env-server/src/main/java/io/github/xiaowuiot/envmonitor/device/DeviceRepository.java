package io.github.xiaowuiot.envmonitor.device;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByMn(String mn);

    boolean existsByMn(String mn);

    List<Device> findAllByOrderByIdAsc();

    List<Device> findBySiteIdInOrderByIdAsc(Collection<Long> siteIds);

    List<Device> findBySiteId(Long siteId);

    List<Device> findByOnlineTrueAndLastSeenAtBefore(LocalDateTime time);

    List<Device> findByOnlineFalseAndEnabledTrue();

    long countByOnlineTrue();

    @Modifying
    @Transactional
    @Query("update Device d set d.online = false where d.online = true")
    int markAllOffline();
}
