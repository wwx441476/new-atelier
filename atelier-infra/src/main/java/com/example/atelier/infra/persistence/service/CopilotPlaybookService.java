package com.example.atelier.infra.persistence.service;

import com.example.atelier.domain.copilot.CopilotPlaybook;
import com.example.atelier.infra.persistence.JpaCopilotPlaybookRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CopilotPlaybookService {

    private final JpaCopilotPlaybookRepository repository;

    public CopilotPlaybookService(JpaCopilotPlaybookRepository repository) {
        this.repository = repository;
    }

    public List<CopilotPlaybook> listAll() {
        return repository.findAll();
    }

    public Optional<CopilotPlaybook> getById(String id) {
        return repository.findById(id);
    }

    public Optional<CopilotPlaybook> getByCode(String code) {
        return repository.findByCode(code);
    }

    public CopilotPlaybook save(CopilotPlaybook playbook) {
        return repository.save(playbook);
    }

    public void incrementUsage(String id) {
        repository.incrementUsage(id);
    }

    public void deleteByCode(String code) {
        repository.deleteByCode(code);
    }
}
