package io.github.xiaowuiot.envmonitor.data;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LatestValueRepository extends JpaRepository<LatestValue, Long> {

    Optional<LatestValue> findByDeviceIdAndFactorCode(Long deviceId, String factorCode);

    List<LatestValue> findByDeviceId(Long deviceId);

    List<LatestValue> findByDeviceIdIn(Collection<Long> deviceIds);
}
