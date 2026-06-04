package com.rapptycoon.service;

import com.rapptycoon.model.RappTemplate;
import com.rapptycoon.repository.RappTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RappCatalogueService {

    private final RappTemplateRepository rappTemplateRepository;

    public RappCatalogueService(RappTemplateRepository rappTemplateRepository) {
        this.rappTemplateRepository = rappTemplateRepository;
    }

    @Transactional(readOnly = true)
    public List<RappTemplate> getCatalogue() {
        return rappTemplateRepository.findAll();
    }
}
