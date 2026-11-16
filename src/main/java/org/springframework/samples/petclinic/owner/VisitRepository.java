/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.owner;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for Visit entities.
 */
public interface VisitRepository extends JpaRepository<Visit, Integer> {

	List<Visit> findByVetIdAndStatusAndStartDateTimeBetweenOrderByStartDateTimeAsc(int vetId, VisitStatus status,
			LocalDateTime startInclusive, LocalDateTime endExclusive);

	List<Visit> findByPetIdAndStartDateTimeIsNotNullOrderByStartDateTimeAsc(int petId);

	@Query("select v from Visit v where v.vet.id = :vetId and v.status = :status "
			+ "and v.startDateTime < :requestedEnd and v.startDateTime > :requestedStartMinusDuration "
			+ "and (:excludeId is null or v.id <> :excludeId)")
	List<Visit> findConflictingAppointments(@Param("vetId") int vetId, @Param("status") VisitStatus status,
			@Param("requestedStartMinusDuration") LocalDateTime requestedStartMinusDuration,
			@Param("requestedEnd") LocalDateTime requestedEnd, @Param("excludeId") Integer excludeId);

}
