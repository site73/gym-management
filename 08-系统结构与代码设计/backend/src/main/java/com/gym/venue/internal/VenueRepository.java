package com.gym.venue.internal;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueRepository extends JpaRepository<VenueEntity, Long> {

    boolean existsByCode(String code);
}
