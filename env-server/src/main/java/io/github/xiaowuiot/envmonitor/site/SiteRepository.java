package io.github.xiaowuiot.envmonitor.site;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteRepository extends JpaRepository<Site, Long> {

    List<Site> findAllByOrderByIdAsc();

}
