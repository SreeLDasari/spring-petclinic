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

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class AppointmentService {

	private final AppointmentRepository appointments;

	private final OwnerRepository owners;

	private final VetRepository vets;

	public AppointmentService(AppointmentRepository appointments, OwnerRepository owners, VetRepository vets) {
		this.appointments = appointments;
		this.owners = owners;
		this.vets = vets;
	}

	@Transactional
	public Appointment scheduleAppointment(int ownerId, int petId, int vetId, LocalDateTime startDateTime,
			int durationMinutes, String reason) {
		Owner owner = this.owners.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException(
					"Pet with id " + petId + " not found for owner with id " + ownerId + ".");
		}
		Vet vet = this.vets.findById(vetId)
			.orElseThrow(() -> new IllegalArgumentException("Vet not found with id: " + vetId));

		Appointment appointment = new Appointment();
		appointment.setPet(pet);
		appointment.setVet(vet);
		appointment.setStartDateTime(startDateTime);
		appointment.setDurationMinutes(durationMinutes);
		appointment.setReason(reason);
		appointment.setStatus(AppointmentStatus.SCHEDULED);

		validateNoVetOverlap(null, vetId, startDateTime, durationMinutes);

		return this.appointments.save(appointment);
	}

	@Transactional
	public Appointment rescheduleAppointment(int appointmentId, int vetId, LocalDateTime startDateTime,
			int durationMinutes, String reason) {
		Appointment appointment = this.appointments.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found with id: " + appointmentId));
		Vet vet = this.vets.findById(vetId)
			.orElseThrow(() -> new IllegalArgumentException("Vet not found with id: " + vetId));

		validateNoVetOverlap(appointmentId, vetId, startDateTime, durationMinutes);

		appointment.setVet(vet);
		appointment.setStartDateTime(startDateTime);
		appointment.setDurationMinutes(durationMinutes);
		appointment.setReason(reason);
		appointment.setStatus(AppointmentStatus.SCHEDULED);
		return this.appointments.save(appointment);
	}

	@Transactional
	public void cancelAppointment(int appointmentId) {
		Appointment appointment = this.appointments.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found with id: " + appointmentId));
		appointment.setStatus(AppointmentStatus.CANCELLED);
		this.appointments.save(appointment);
	}

	@Transactional(readOnly = true)
	public Collection<Appointment> findAppointments(LocalDateTime fromDateTime, LocalDateTime toDateTime, Integer vetId,
			AppointmentStatus status) {
		Assert.notNull(fromDateTime, "fromDateTime must not be null");
		Assert.notNull(toDateTime, "toDateTime must not be null");
		Assert.notNull(status, "status must not be null");
		return this.appointments.findByFilters(fromDateTime, toDateTime, vetId, status);
	}

	@Transactional(readOnly = true)
	public Collection<Appointment> findUpcomingAppointmentsForPet(int petId, LocalDateTime fromDateTime) {
		return this.appointments.findUpcomingForPet(petId, AppointmentStatus.SCHEDULED, fromDateTime);
	}

	private void validateNoVetOverlap(Integer appointmentId, int vetId, LocalDateTime startDateTime, int durationMinutes) {
		LocalDateTime endDateTime = startDateTime.plusMinutes(durationMinutes);

		Collection<Appointment> potentialConflicts = this.appointments.findStartingWithin(vetId,
				AppointmentStatus.SCHEDULED, startDateTime.minusMinutes(durationMinutes), endDateTime);

		boolean conflict = potentialConflicts.stream().anyMatch((appt) -> {
			if (appointmentId != null && appointmentId.equals(appt.getId())) {
				return false;
			}
			LocalDateTime existingStart = appt.getStartDateTime();
			LocalDateTime existingEnd = existingStart.plusMinutes(appt.getDurationMinutes());
			return startDateTime.isBefore(existingEnd) && endDateTime.isAfter(existingStart);
		});

		if (conflict) {
			throw new VetDoubleBookedException("Vet is already booked for the selected time range.");
		}
	}

}
