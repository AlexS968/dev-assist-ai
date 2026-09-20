package dev.alexey.devassist.incident.repository;

import dev.alexey.devassist.incident.entity.Incident;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {
}
