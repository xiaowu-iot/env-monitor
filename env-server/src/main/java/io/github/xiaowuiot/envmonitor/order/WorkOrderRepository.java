package io.github.xiaowuiot.envmonitor.order;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long>, JpaSpecificationExecutor<WorkOrder> {

    long countByCreatedAtAfter(LocalDateTime time);

    boolean existsByCode(String code);

    List<WorkOrder> findByHandlerIdAndStatusOrderByIdDesc(Long handlerId, WorkOrder.Status status);

    List<WorkOrder> findByReviewerIdAndStatusOrderByIdDesc(Long reviewerId, WorkOrder.Status status);

    List<WorkOrder> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
}
