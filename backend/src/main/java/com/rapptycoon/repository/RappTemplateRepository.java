package com.rapptycoon.repository;

import com.rapptycoon.model.RappTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RappTemplateRepository extends JpaRepository<RappTemplate, Long> {
}
