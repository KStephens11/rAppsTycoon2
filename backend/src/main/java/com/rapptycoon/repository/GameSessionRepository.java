package com.rapptycoon.repository;

import com.rapptycoon.model.GameSession;
import com.rapptycoon.model.GameSessionState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

    Optional<GameSession> findBySessionCode(String sessionCode);

    List<GameSession> findByState(GameSessionState state);
}
