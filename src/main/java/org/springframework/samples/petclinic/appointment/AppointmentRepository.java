/*
 * Copyright 2012-2026 the original author or authors.
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
package org.springframework.samples.petclinic.appointment;

import java.time.LocalDateTime;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link Appointment}.
 */
public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {

	@Query("select a from Appointment a "
			+ "where a.vet.id = :vetId and a.status = :status "
			+ "and a.startDateTime < :endDateTime and a.startDateTime >= :startDateTime")
	Collection<Appointment> findStartingWithin(@Param("vetId") int vetId, @Param("status") AppointmentStatus status,
			@Param("startDateTime") LocalDateTime startDateTime, @Param("endDateTime") LocalDateTime endDateTime);

	@Query("select a from Appointment a "
			+ "where a.status = :status and a.startDateTime >= :fromDateTime and a.startDateTime <= :toDateTime "
			+ "and (:vetId is null or a.vet.id = :vetId) "
			+ "order by a.startDateTime asc")
	Collection<Appointment> findByFilters(@Param("fromDateTime") LocalDateTime fromDateTime,
			@Param("toDateTime") LocalDateTime toDateTime, @Param("vetId") Integer vetId,
			@Param("status") AppointmentStatus status);

	@Query("select a from Appointment a "
			+ "where a.pet.id = :petId and a.status = :status and a.startDateTime >= :fromDateTime "
			+ "order by a.startDateTime asc")
	Collection<Appointment> findUpcomingForPet(@Param("petId") int petId, @Param("status") AppointmentStatus status,
			@Param("fromDateTime") LocalDateTime fromDateTime);

}
