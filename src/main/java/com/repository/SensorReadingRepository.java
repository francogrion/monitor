package com.repository;

import com.domain.SensorReadingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SensorReadingRepository extends JpaRepository<SensorReadingEntity, Long> {

    List<SensorReadingEntity> findAllByOrderByIdAsc();
}
