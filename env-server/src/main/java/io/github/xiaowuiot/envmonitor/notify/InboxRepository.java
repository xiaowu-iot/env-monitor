package io.github.xiaowuiot.envmonitor.notify;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface InboxRepository extends JpaRepository<InboxMessage, Long> {

    Page<InboxMessage> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadFlagFalse(Long userId);

    @Modifying
    @Transactional
    @Query("update InboxMessage m set m.readFlag = true where m.userId = ?1 and m.readFlag = false")
    int markAllRead(Long userId);
}
