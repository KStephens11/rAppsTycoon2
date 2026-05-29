package com.rapptycoon.repository;

import com.rapptycoon.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findBySessionId(Long sessionId);

    Optional<Player> findBySessionToken(String sessionToken);
}
