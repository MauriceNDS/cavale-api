package com.cavale.training.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cavale.training.domain.Course;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    Optional<Course> findByObjectiveId(UUID objectiveId);
}
