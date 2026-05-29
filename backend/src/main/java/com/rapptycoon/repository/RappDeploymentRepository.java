package com.rapptycoon.repository;

import com.rapptycoon.model.DeploymentStatus;
import com.rapptycoon.model.RappDeployment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RappDeploymentRepository extends JpaRepository<RappDeployment, Long> {

    List<RappDeployment> findByBasestationId(Long basestationId);

    List<RappDeployment> findByPlayerIdAndStatus(Long playerId, DeploymentStatus status);

    List<RappDeployment> findByBasestationIdAndStatus(Long basestationId, DeploymentStatus status);
}
