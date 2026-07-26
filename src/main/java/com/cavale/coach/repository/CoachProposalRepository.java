package com.cavale.coach.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cavale.coach.domain.CoachProposal;

public interface CoachProposalRepository extends JpaRepository<CoachProposal, UUID> {
}
