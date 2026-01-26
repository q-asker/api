package com.icc.qasker.util.repository;

import com.icc.qasker.util.entity.UpdateLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UpdateLogRepository extends JpaRepository<UpdateLog, Long> {

    List<UpdateLog> findTop3ByOrderByCreatedAtDesc();
}
