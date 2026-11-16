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

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
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

	private final Clock clock;

	public AppointmentService(AppointmentRepository appointments, OwnerRepository owners, VetRepository vets,
			Clock clock) {
		this.appointments = appointments;
		this.owners = owners;
		this.vets = vets;
		this.clock = clock;
	}

	@Transactional
	public Appointment createAppointment(int ownerId, int petId, int vetId, LocalDateTime appointmentDateTime) {
		Assert.notNull(appointmentDateTime, "appointmentDateTime must not be null");
		validateFutureDateTime(appointmentDateTime);

		Pet pet = findPet(ownerId, petId);
		Vet vet = this.vets.findById(vetId)
			.orElseThrow(() -> new IllegalArgumentException("Vet not found with id: " + vetId));

		if (this.appointments.existsByVetIdAndAppointmentDateTime(vetId, appointmentDateTime)) {
			throw new AppointmentSlotUnavailableException(
					"Selected vet is unavailable at the chosen time. Please pick another slot.");
		}

		Appointment appointment = new Appointment();
		appointment.setPet(pet);
		appointment.setVet(vet);
		appointment.setAppointmentDateTime(appointmentDateTime);

		try {
			return this.appointments.save(appointment);
		}
		catch (DataIntegrityViolationException ex) {
			// Race-condition safety: DB unique constraint is the final guard.
			throw new AppointmentSlotUnavailableException(
					"Selected vet is unavailable at the chosen time. Please pick another slot.");
		}
	}

	@Transactional
	public Appointment rescheduleAppointment(int appointmentId, LocalDateTime newDateTime) {
		Assert.notNull(newDateTime, "newDateTime must not be null");
		validateFutureDateTime(newDateTime);

		Appointment appointment = this.appointments.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found with id: " + appointmentId));

		Integer vetId = appointment.getVet().getId();
		Assert.state(vetId != null, "appointment.vet.id must not be null");

		// Exclude self: only treat it as conflict if the new slot differs.
		if (!newDateTime.equals(appointment.getAppointmentDateTime())
				&& this.appointments.existsByVetIdAndAppointmentDateTime(vetId, newDateTime)) {
			throw new AppointmentSlotUnavailableException(
					"Selected vet is unavailable at the chosen time. Please pick another slot.");
		}

		appointment.setAppointmentDateTime(newDateTime);
		try {
			return this.appointments.save(appointment);
		}
		catch (DataIntegrityViolationException ex) {
			throw new AppointmentSlotUnavailableException(
					"Selected vet is unavailable at the chosen time. Please pick another slot.");
		}
	}

	@Transactional
	public void cancelAppointment(int appointmentId) {
		if (!this.appointments.existsById(appointmentId)) {
			return;
		}
		this.appointments.deleteById(appointmentId);
	}

	private void validateFutureDateTime(LocalDateTime dateTime) {
		LocalDateTime now = LocalDateTime.now(this.clock);
		if (!dateTime.isAfter(now)) {
			throw new IllegalArgumentException("Appointment date/time must be in the future");
		}
	}

	private Pet findPet(int ownerId, int petId) {
		Owner owner = this.owners.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException(
					"Pet with id " + petId + " not found for owner with id " + ownerId + ".");
		}
		return pet;
	}

}
