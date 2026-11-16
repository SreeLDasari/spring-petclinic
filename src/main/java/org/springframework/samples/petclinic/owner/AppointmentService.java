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
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentService {

	private final OwnerRepository owners;

	private final VetRepository vets;

	private final VisitRepository visits;

	private final Duration appointmentDuration;

	public AppointmentService(OwnerRepository owners, VetRepository vets, VisitRepository visits,
			@Value("${petclinic.appointments.duration-minutes:30}") long appointmentDurationMinutes) {
		this.owners = owners;
		this.vets = vets;
		this.visits = visits;
		this.appointmentDuration = Duration.ofMinutes(appointmentDurationMinutes);
	}

	@Transactional
	public Visit scheduleAppointment(int ownerId, int petId, int vetId, LocalDateTime startDateTime,
			String description) {
		Owner owner = owners.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException("Pet not found with id: " + petId + " for owner " + ownerId);
		}

		Vet vet = loadVet(vetId);
		assertNoConflict(vetId, startDateTime, null);

		Visit visit = new Visit();
		visit.setStartDateTime(startDateTime);
		visit.setVet(vet);
		visit.setStatus(VisitStatus.SCHEDULED);
		visit.setDescription(description);
		// keep legacy date column populated for sorting/backwards compatibility
		visit.setDate(startDateTime.toLocalDate());

		pet.addVisit(visit);
		owners.save(owner);
		return visit;
	}

	@Transactional
	public Visit rescheduleAppointment(int appointmentId, int vetId, LocalDateTime newStartDateTime) {
		Visit existing = visits.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found with id: " + appointmentId));

		assertNoConflict(vetId, newStartDateTime, existing.getId());
		Vet vet = loadVet(vetId);

		existing.setVet(vet);
		existing.setStartDateTime(newStartDateTime);
		existing.setDate(newStartDateTime.toLocalDate());
		existing.setStatus(VisitStatus.SCHEDULED);

		return visits.save(existing);
	}

	@Transactional
	public void cancelAppointment(int appointmentId) {
		Visit existing = visits.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found with id: " + appointmentId));
		if (existing.getStatus() == VisitStatus.CANCELLED) {
			return;
		}
		existing.setStatus(VisitStatus.CANCELLED);
		visits.save(existing);
	}

	@Transactional(readOnly = true)
	public List<Visit> getVetScheduleForDate(int vetId, LocalDate date) {
		LocalDateTime start = date.atStartOfDay();
		LocalDateTime end = date.plusDays(1).atStartOfDay();
		return visits.findByVetIdAndStatusAndStartDateTimeBetweenOrderByStartDateTimeAsc(vetId, VisitStatus.SCHEDULED,
				start, end);
	}

	@Transactional(readOnly = true)
	public Duration getAppointmentDuration() {
		return this.appointmentDuration;
	}

	private Vet loadVet(int vetId) {
		// VetRepository is a Spring Data Repository without findById in this fork, so we
		// currently resolve from findAll.
		return vets.findAll().stream()
			.filter(v -> v.getId() != null && v.getId() == vetId)
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Vet not found with id: " + vetId));
	}

	private void assertNoConflict(int vetId, LocalDateTime requestedStart, Integer excludeId) {
		LocalDateTime requestedEnd = requestedStart.plus(appointmentDuration);
		LocalDateTime requestedStartMinusDuration = requestedStart.minus(appointmentDuration);

		List<Visit> candidates = visits.findConflictingAppointments(vetId, VisitStatus.SCHEDULED,
				requestedStartMinusDuration, requestedEnd, excludeId);

		for (Visit existing : candidates) {
			LocalDateTime existingStart = existing.getStartDateTime();
			if (existingStart == null) {
				continue;
			}
			LocalDateTime existingEnd = existingStart.plus(appointmentDuration);
			boolean overlaps = existingStart.isBefore(requestedEnd) && existingEnd.isAfter(requestedStart);
			if (overlaps) {
				throw new SchedulingConflictException(
						"Selected time overlaps with another appointment for this veterinarian.");
			}
		}
	}

}
