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
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class AppointmentService {

	private final AppointmentRepository appointmentRepository;

	private final OwnerRepository owners;

	private final VetRepository vets;

	public AppointmentService(AppointmentRepository appointmentRepository, OwnerRepository owners, VetRepository vets) {
		this.appointmentRepository = appointmentRepository;
		this.owners = owners;
		this.vets = vets;
	}

	@Transactional(readOnly = true)
	public Collection<Vet> findAllVets() throws DataAccessException {
		return this.vets.findAll();
	}

	@Transactional(readOnly = true)
	public Collection<Appointment> findAppointmentsForPet(int petId) throws DataAccessException {
		return this.appointmentRepository.findByPetIdOrderByAppointmentDateAscStartTimeAsc(petId);
	}

	@Transactional
	public Appointment scheduleAppointment(int ownerId, int petId, int vetId, LocalDate appointmentDate,
			LocalTime startTime, int durationMinutes, String reason) {

		Assert.notNull(appointmentDate, "appointmentDate must not be null");
		Assert.notNull(startTime, "startTime must not be null");
		Assert.isTrue(durationMinutes > 0, "durationMinutes must be > 0");

		if (appointmentDate.isBefore(LocalDate.now())) {
			throw new IllegalArgumentException("Appointment date must be today or in the future.");
		}

		Pet pet = this.owners.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId))
			.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException("Pet with id " + petId + " not found for owner with id " + ownerId + ".");
		}

		Vet vet = this.vets.findAll().stream().filter(v -> v.getId() != null && v.getId() == vetId).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Vet not found with id: " + vetId));

		LocalTime endTime = startTime.plusMinutes(durationMinutes);
		Collection<Appointment> overlaps = this.appointmentRepository.findOverlappingAppointments(vetId, appointmentDate,
				startTime, endTime);
		if (!overlaps.isEmpty()) {
			throw new AppointmentConflictException(
					"Selected vet is not available at the chosen time. Please pick another slot.");
		}

		Appointment appointment = new Appointment();
		appointment.setPet(pet);
		appointment.setVet(vet);
		appointment.setAppointmentDate(appointmentDate);
		appointment.setStartTime(startTime);
		appointment.setDurationMinutes(durationMinutes);
		appointment.setStatus(AppointmentStatus.SCHEDULED);
		appointment.setReason(reason);

		return this.appointmentRepository.save(appointment);
	}

}
