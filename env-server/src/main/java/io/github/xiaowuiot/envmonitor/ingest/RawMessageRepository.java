package io.github.xiaowuiot.envmonitor.ingest;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface RawMessageRepository extends JpaRepository<RawMessage, Long> {

    Page<RawMessage> findByMnOrderByIdDesc(String mn, Pageable pageable);

    Page<RawMessage> findByMnAndCnOrderByIdDesc(String mn, String cn, Pageable pageable);

    Page<RawMessage> findAllByOrderByIdDesc(Pageable pageable);

    /**
     * 发过报文、但还没登记的 MN：现场装好设备后，管理员在这里一键登记
     */
    @Query("select r.mn, count(r), max(r.createdAt), max(r.remote) from RawMessage r "
            + "where r.deviceId is null and r.direction = 'IN' and r.mn is not null group by r.mn order by max(r.createdAt) desc")
    List<Object[]> unknownDevices();

    @Modifying
    @Transactional
    @Query("delete from RawMessage r where r.createdAt < ?1")
    int deleteBefore(LocalDateTime time);

    @Modifying
    @Transactional
    @Query("update RawMessage r set r.deviceId = ?2 where r.mn = ?1 and r.deviceId is null")
    int bindDevice(String mn, Long deviceId);
}
