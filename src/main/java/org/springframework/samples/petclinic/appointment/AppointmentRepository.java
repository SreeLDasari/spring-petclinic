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
package org.springframework.samples.petclinic.appointment;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;

import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AppointmentRepository extends Repository<Appointment, Integer> {

	@Transactional
	Appointment save(Appointment appointment) throws DataAccessException;

	@Transactional(readOnly = true)
	Collection<Appointment> findByPetIdOrderByAppointmentDateAscStartTimeAsc(int petId) throws DataAccessException;

	@Transactional(readOnly = true)
	@Query("select a from Appointment a where a.vet.id = :vetId and a.appointmentDate = :date and a.status <> org.springframework.samples.petclinic.appointment.AppointmentStatus.CANCELLED and a.startTime < :endTime and (a.startTime + (a.durationMinutes * 1 minute)) > :startTime")
	Collection<Appointment> findOverlappingAppointments(@Param("vetId") int vetId, @Param("date") LocalDate date,
			@Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime) throws DataAccessException;

}
