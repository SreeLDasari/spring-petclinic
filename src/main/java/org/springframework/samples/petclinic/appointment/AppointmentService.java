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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentService {

	private final AppointmentRepository appointmentRepository;

	private final PetRepository petRepository;

	private final VetRepository vetRepository;

	public AppointmentService(AppointmentRepository appointmentRepository, PetRepository petRepository,
			VetRepository vetRepository) {
		this.appointmentRepository = appointmentRepository;
		this.petRepository = petRepository;
		this.vetRepository = vetRepository;
	}

	@Transactional
	public Appointment scheduleAppointment(int petId, int vetId, LocalDateTime startDateTime, int durationMinutes,
			String reason) {
		LocalDateTime normalizedStart = normalizeDateTime(startDateTime);
		LocalDateTime normalizedEnd = normalizedStart.plusMinutes(durationMinutes);

		Pet pet = this.petRepository.findById(petId)
				.orElseThrow(() -> new IllegalArgumentException("Pet not found: " + petId));
		Vet vet = this.vetRepository.findById(vetId)
				.orElseThrow(() -> new IllegalArgumentException("Vet not found: " + vetId));

		List<Appointment> candidates = this.appointmentRepository.findPotentialConflicts(vetId, normalizedStart.minusDays(1),
				normalizedEnd, AppointmentStatus.CANCELLED);
		boolean conflict = candidates.stream().anyMatch(existing -> overlaps(existing, normalizedStart, normalizedEnd));
		if (conflict) {
			throw new AppointmentConflictException(vetId, normalizedStart,
					"Selected vet is not available at " + normalizedStart + " (conflicts with an existing appointment)."
							+ "");
		}

		Appointment appointment = new Appointment();
		appointment.setPet(pet);
		appointment.setVet(vet);
		appointment.setStartDateTime(normalizedStart);
		appointment.setDurationMinutes(durationMinutes);
		appointment.setReason(reason);
		appointment.setStatus(AppointmentStatus.SCHEDULED);
		return this.appointmentRepository.save(appointment);
	}

	@Transactional(readOnly = true)
	public List<Appointment> getVetAgenda(int vetId, LocalDate date) {
		LocalDate day = date != null ? date : LocalDate.now();
		LocalDateTime dayStart = day.atStartOfDay();
		LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
		return this.appointmentRepository.findForVetInWindow(vetId, dayStart, dayEnd, AppointmentStatus.CANCELLED);
	}

	@Transactional
	public void cancelAppointment(int appointmentId) {
		Appointment appointment = this.appointmentRepository.findById(appointmentId)
				.orElseThrow(() -> new IllegalArgumentException("Appointment not found: " + appointmentId));
		if (!appointment.isCancelled()) {
			appointment.setStatus(AppointmentStatus.CANCELLED);
			this.appointmentRepository.save(appointment);
		}
	}

	private static boolean overlaps(Appointment existing, LocalDateTime start, LocalDateTime end) {
		if (existing.isCancelled()) {
			return false;
		}
		LocalDateTime existingStart = normalizeDateTime(existing.getStartDateTime());
		LocalDateTime existingEnd = existingStart.plusMinutes(existing.getDurationMinutes());
		return existingStart.isBefore(end) && start.isBefore(existingEnd);
	}

	private static LocalDateTime normalizeDateTime(LocalDateTime input) {
		if (input == null) {
			return null;
		}
		LocalDateTime noSeconds = input.truncatedTo(ChronoUnit.MINUTES);
		// Ensure time component exists (in case input is date-only)
		if (noSeconds.toLocalTime() == null) {
			return LocalDateTime.of(noSeconds.toLocalDate(), LocalTime.MIDNIGHT);
		}
		return noSeconds;
	}

}
