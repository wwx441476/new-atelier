package com.example.atelier.infra.persistence.service;

import com.example.atelier.domain.dashboard.DashboardScreen;
import com.example.atelier.infra.persistence.JpaDashboardScreenRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DashboardScreenService {

    private final JpaDashboardScreenRepository repository;

    public DashboardScreenService(JpaDashboardScreenRepository repository) {
        this.repository = repository;
    }

    public List<DashboardScreen> listAll() {
        return repository.findAll();
    }

    public Optional<DashboardScreen> getById(String id) {
        return repository.findById(id);
    }

    public Optional<DashboardScreen> getByCode(String code) {
        return repository.findByCode(code);
    }

    public DashboardScreen save(DashboardScreen screen) {
        return repository.save(screen);
    }

    public void deleteByCode(String code) {
        repository.deleteByCode(code);
    }
}
