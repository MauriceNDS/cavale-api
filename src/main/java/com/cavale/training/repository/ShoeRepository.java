package com.cavale.training.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cavale.training.domain.Shoe;

public interface ShoeRepository extends JpaRepository<Shoe, UUID> {

    /** Active shoes first, then retired; each group newest first. */
    List<Shoe> findByUserIdOrderByRetiredAscCreatedAtDesc(UUID userId);
}
