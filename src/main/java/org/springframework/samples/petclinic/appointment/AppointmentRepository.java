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

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {

	boolean existsByVetIdAndAppointmentDateTime(Integer vetId, LocalDateTime appointmentDateTime);

	@Query("select a from Appointment a join fetch a.vet where a.pet.id = :petId and a.appointmentDateTime >= :from order by a.appointmentDateTime asc")
	List<Appointment> findUpcomingByPetId(@Param("petId") Integer petId, @Param("from") LocalDateTime from);

	@Query("select a from Appointment a join fetch a.vet where a.pet.id = :petId order by a.appointmentDateTime asc")
	List<Appointment> findByPetIdWithVet(@Param("petId") Integer petId);

}
