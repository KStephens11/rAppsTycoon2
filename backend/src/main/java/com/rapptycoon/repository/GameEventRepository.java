package com.rapptycoon.repository;

import com.rapptycoon.model.GameEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameEventRepository extends JpaRepository<GameEvent, Long> {

    List<GameEvent> findBySessionIdAndResolvedFalse(Long sessionId);

    List<GameEvent> findByBasestationIdAndResolvedFalse(Long basestationId);
}
