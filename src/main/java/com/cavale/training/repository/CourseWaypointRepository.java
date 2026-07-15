package com.cavale.training.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cavale.training.domain.CourseWaypoint;

public interface CourseWaypointRepository extends JpaRepository<CourseWaypoint, UUID> {

    List<CourseWaypoint> findByCourseIdOrderByDistanceKm(UUID courseId);
}
