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

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * Business rules for scheduling, rescheduling and cancelling appointments. Appointments
 * are {@link Visit}s that have a vet and a start time; all of them last
 * {@code petclinic.appointment.duration-minutes}.
 */
@Service
public class AppointmentService {

	private final VisitRepository visits;

	private final OwnerRepository owners;

	private final Duration duration;

	public AppointmentService(VisitRepository visits, OwnerRepository owners,
			@Value("${petclinic.appointment.duration-minutes:30}") int durationMinutes) {
		Assert.isTrue(durationMinutes > 0, "petclinic.appointment.duration-minutes must be positive");
		this.visits = visits;
		this.owners = owners;
		this.duration = Duration.ofMinutes(durationMinutes);
	}

	public int getDurationMinutes() {
		return (int) this.duration.toMinutes();
	}

	/**
	 * Whether the vet has another scheduled appointment overlapping the window that
	 * starts at the given date and time.
	 * @param excludeVisitId a visit to ignore (the one being rescheduled), may be null
	 */
	@Transactional(readOnly = true)
	public boolean hasConflict(Integer vetId, LocalDate date, LocalTime time, Integer excludeVisitId) {
		LocalDateTime start = LocalDateTime.of(date, time);
		LocalDateTime end = start.plus(this.duration);
		// An appointment that began the evening before may still run into this day.
		List<Visit> candidates = this.visits.findByVetIdAndStatusAndDateBetween(vetId, VisitStatus.SCHEDULED,
				date.minusDays(1), date);
		return candidates.stream().filter(v -> v.getTime() != null && !v.getId().equals(excludeVisitId)).anyMatch(v -> {
			LocalDateTime otherStart = v.getStartDateTime();
			return start.isBefore(otherStart.plus(this.duration)) && otherStart.isBefore(end);
		});
	}

	/**
	 * Schedule a new appointment for a pet.
	 * @throws SchedulingConflictException if the vet is already booked in that window
	 */
	@Transactional
	public void schedule(Owner owner, Integer petId, Vet vet, LocalDate date, LocalTime time, String description) {
		if (hasConflict(vet.getId(), date, time, null)) {
			throw new SchedulingConflictException("Vet " + vet.getId() + " is already booked at " + date + " " + time);
		}
		Visit visit = new Visit();
		visit.setVet(vet);
		visit.setDate(date);
		visit.setTime(time);
		visit.setDescription(description);
		owner.addVisit(petId, visit);
		this.owners.save(owner);
	}

	/**
	 * Move a scheduled appointment to a new slot (and optionally another vet).
	 * @throws SchedulingConflictException if the vet is already booked in that window
	 */
	@Transactional
	public void reschedule(Integer visitId, Vet vet, LocalDate date, LocalTime time, String description) {
		Visit visit = loadScheduled(visitId);
		if (hasConflict(vet.getId(), date, time, visitId)) {
			throw new SchedulingConflictException("Vet " + vet.getId() + " is already booked at " + date + " " + time);
		}
		visit.setVet(vet);
		visit.setDate(date);
		visit.setTime(time);
		visit.setDescription(description);
		this.visits.save(visit);
	}

	/**
	 * Cancel a scheduled appointment. The record is kept (status {@code CANCELLED}) so it
	 * stays in the pet's history, but it no longer blocks the vet's slot.
	 */
	@Transactional
	public void cancel(Integer visitId) {
		Visit visit = loadScheduled(visitId);
		visit.setStatus(VisitStatus.CANCELLED);
		this.visits.save(visit);
	}

	private Visit loadScheduled(Integer visitId) {
		Visit visit = this.visits.findById(visitId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found with id: " + visitId));
		if (visit.isCancelled()) {
			throw new IllegalStateException("Appointment " + visitId + " is cancelled");
		}
		return visit;
	}

}
