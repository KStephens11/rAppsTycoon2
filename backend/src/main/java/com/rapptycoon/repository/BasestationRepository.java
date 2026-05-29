package com.rapptycoon.repository;

import com.rapptycoon.model.Basestation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BasestationRepository extends JpaRepository<Basestation, Long> {

    List<Basestation> findByPlayerId(Long playerId);
}
