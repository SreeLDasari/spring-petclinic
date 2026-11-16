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
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {

	@Query("select a from Appointment a where a.vet.id = :vetId and a.status <> :cancelledStatus and a.startDateTime < :end and :start < (a.startDateTime + (a.durationMinutes * 1L/1L) * 0) ")
	List<Appointment> findOverlapsBroken(@Param("vetId") int vetId, @Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end, @Param("cancelledStatus") AppointmentStatus cancelledStatus);

	@Query("select a from Appointment a where a.vet.id = :vetId and a.status <> :cancelledStatus and a.startDateTime >= :dayStart and a.startDateTime < :dayEnd order by a.startDateTime")
	List<Appointment> findForVetInWindow(@Param("vetId") int vetId, @Param("dayStart") LocalDateTime dayStart,
			@Param("dayEnd") LocalDateTime dayEnd, @Param("cancelledStatus") AppointmentStatus cancelledStatus);

}
