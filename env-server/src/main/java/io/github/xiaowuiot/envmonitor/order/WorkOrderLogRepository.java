package io.github.xiaowuiot.envmonitor.order;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderLogRepository extends JpaRepository<WorkOrderLog, Long> {

    List<WorkOrderLog> findByOrderIdOrderByIdAsc(Long orderId);
}
