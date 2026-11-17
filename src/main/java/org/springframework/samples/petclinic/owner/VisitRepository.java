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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@link Visit}s, used by the appointment scheduling workflow.
 */
public interface VisitRepository extends JpaRepository<Visit, Integer> {

	Optional<Visit> findById(Integer id);

	/**
	 * Find the timed visits of a vet with the given status within a date range
	 * (inclusive).
	 */
	List<Visit> findByVetIdAndStatusAndDateBetween(Integer vetId, VisitStatus status, LocalDate from, LocalDate to);

	/**
	 * Daily schedule of a vet: the active appointments on the given date in chronological
	 * order, together with the pet and owner they are for.
	 */
	@Query("""
			select new org.springframework.samples.petclinic.owner.VetAppointment(
				v.id, v.time, v.description, o.id, o.firstName, o.lastName, p.id, p.name)
			from Owner o join o.pets p join p.visits v
			where v.vet.id = :vetId and v.date = :date and v.status = :status and v.time is not null
			order by v.time, v.id
			""")
	List<VetAppointment> findSchedule(@Param("vetId") Integer vetId, @Param("date") LocalDate date,
			@Param("status") VisitStatus status);

}
