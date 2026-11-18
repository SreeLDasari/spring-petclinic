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
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {

	List<Appointment> findByVetIdAndAppointmentDateOrderByAppointmentTimeAsc(int vetId, LocalDate appointmentDate);

	@Query("select count(a) > 0 from Appointment a "
			+ "where a.vet.id = :vetId and a.appointmentDate = :appointmentDate and a.appointmentTime = :appointmentTime "
			+ "and a.status = org.springframework.samples.petclinic.appointment.AppointmentStatus.BOOKED "
			+ "and (:excludeId is null or a.id <> :excludeId)")
	boolean existsBookedConflict(@Param("vetId") int vetId, @Param("appointmentDate") LocalDate appointmentDate,
			@Param("appointmentTime") LocalTime appointmentTime, @Param("excludeId") Integer excludeId);

	@Query("select a from Appointment a where a.appointmentDate = :appointmentDate "
			+ "and (:vetId is null or a.vet.id = :vetId) order by a.appointmentTime asc")
	List<Appointment> findByAppointmentDateAndOptionalVet(@Param("appointmentDate") LocalDate appointmentDate,
			@Param("vetId") Integer vetId);

}
