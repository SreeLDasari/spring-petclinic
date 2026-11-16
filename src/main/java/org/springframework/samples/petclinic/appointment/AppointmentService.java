/*
 * Copyright 2026 the original author or authors.
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

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class AppointmentService {

	private final AppointmentRepository appointmentRepository;

	private final PetRepository petRepository;

	private final VetRepository vetRepository;

	private final Clock clock;

	public AppointmentService(AppointmentRepository appointmentRepository, PetRepository petRepository,
			VetRepository vetRepository, Clock clock) {
		this.appointmentRepository = appointmentRepository;
		this.petRepository = petRepository;
		this.vetRepository = vetRepository;
		this.clock = clock;
	}

	public List<Appointment> findByPet(Integer petId) {
		return this.appointmentRepository.findByPetIdOrderByStartDateTimeAsc(petId);
	}

	public List<Appointment> findScheduledInRange(LocalDateTime startInclusive, LocalDateTime endExclusive) {
		return this.appointmentRepository.findByStatusAndStartDateTimeBetweenOrderByStartDateTimeAsc(
				AppointmentStatus.SCHEDULED, startInclusive, endExclusive);
	}

	@Transactional
	public Appointment scheduleAppointment(Integer petId, Integer vetId, LocalDateTime startDateTime,
			int durationMinutes, String reason, String notes) {
		Assert.notNull(petId, "petId is required");
		Assert.notNull(startDateTime, "startDateTime is required");
		Assert.hasText(reason, "reason is required");

		LocalDateTime now = LocalDateTime.now(this.clock);
		if (!startDateTime.isAfter(now)) {
			throw new IllegalArgumentException("Appointment must be scheduled in the future");
		}

		Pet pet = this.petRepository.findById(petId).orElseThrow();
		Vet vet = null;
		if (vetId != null) {
			vet = this.vetRepository.findById(vetId).orElseThrow();
		}

		Appointment appointment = new Appointment();
		appointment.setPet(pet);
		appointment.setVet(vet);
		appointment.setStartDateTime(startDateTime);
		appointment.setDurationMinutes(durationMinutes);
		appointment.setReason(reason);
		appointment.setNotes(notes);
		appointment.setStatus(AppointmentStatus.SCHEDULED);

		checkConflicts(appointment);

		return this.appointmentRepository.save(appointment);
	}

	@Transactional
	public Appointment cancelAppointment(Integer appointmentId) {
		Appointment appointment = this.appointmentRepository.findById(appointmentId).orElseThrow();
		if (appointment.getStatus() != AppointmentStatus.CANCELLED) {
			appointment.setStatus(AppointmentStatus.CANCELLED);
			this.appointmentRepository.save(appointment);
		}
		return appointment;
	}

	@Transactional
	public Appointment completeAppointment(Integer appointmentId, String notes) {
		Appointment appointment = this.appointmentRepository.findById(appointmentId).orElseThrow();
		LocalDateTime now = LocalDateTime.now(this.clock);
		if (appointment.getStartDateTime() != null && !appointment.getStartDateTime().isBefore(now)) {
			throw new IllegalArgumentException("Only past appointments can be completed");
		}
		appointment.setStatus(AppointmentStatus.COMPLETED);
		appointment.setNotes(notes);
		return this.appointmentRepository.save(appointment);
	}

	private void checkConflicts(Appointment proposed) {
		LocalDateTime proposedStart = proposed.getStartDateTime();
		LocalDateTime proposedEnd = proposed.getEndDateTime();

		List<Appointment> candidates;
		if (proposed.getVet() != null) {
			candidates = this.appointmentRepository.findByVetIdAndStatusNotAndStartDateTimeLessThanOrderByStartDateTimeAsc(
					proposed.getVet().getId(), AppointmentStatus.CANCELLED, proposedEnd);
		}
		else {
			candidates = this.appointmentRepository.findByPetIdAndStatusNotAndStartDateTimeLessThanOrderByStartDateTimeAsc(
					proposed.getPet().getId(), AppointmentStatus.CANCELLED, proposedEnd);
		}

		Optional<Appointment> conflict = candidates.stream()
				.filter(existing -> overlaps(proposedStart, proposedEnd, existing.getStartDateTime(),
					existing.getEndDateTime()))
				.min(Comparator.comparing(Appointment::getStartDateTime));

		if (conflict.isPresent()) {
			Appointment existing = conflict.get();
			throw new AppointmentConflictException(existing.getStartDateTime(), existing.getEndDateTime());
		}
	}

	static boolean overlaps(LocalDateTime proposedStart, LocalDateTime proposedEnd, LocalDateTime existingStart,
			LocalDateTime existingEnd) {
		return proposedStart.isBefore(existingEnd) && proposedEnd.isAfter(existingStart);
	}

}
