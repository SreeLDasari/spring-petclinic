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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

	static final LocalTime DEFAULT_START_TIME = LocalTime.of(9, 0);

	static final LocalTime DEFAULT_END_TIME = LocalTime.of(17, 0);

	static final int DEFAULT_SLOT_MINUTES = 30;

	private final AppointmentRepository appointmentRepository;

	private final OwnerRepository ownerRepository;

	private final VetRepository vetRepository;

	public AppointmentService(AppointmentRepository appointmentRepository, OwnerRepository ownerRepository,
			VetRepository vetRepository) {
		this.appointmentRepository = appointmentRepository;
		this.ownerRepository = ownerRepository;
		this.vetRepository = vetRepository;
	}

	@Transactional(readOnly = true)
	public Appointment findById(int appointmentId) {
		return this.appointmentRepository.findById(appointmentId)
			.orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
	}

	@Transactional(readOnly = true)
	public Owner findOwner(int ownerId) {
		return this.ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));
	}

	@Transactional(readOnly = true)
	public Owner findOwnerByPetId(int petId) {
		return this.ownerRepository.findByPetsId(petId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found for pet id: " + petId));
	}

	@Transactional(readOnly = true)
	public Pet findPet(Owner owner, int petId) {
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException("Pet not found with id: " + petId + " for owner: " + owner.getId());
		}
		return pet;
	}

	@Transactional(readOnly = true)
	public List<Vet> findAllVets() {
		return List.copyOf(this.vetRepository.findAll());
	}

	@Transactional(readOnly = true)
	public List<Appointment> findSchedule(LocalDate scheduleDate, Integer vetId) {
		Assert.notNull(scheduleDate, "scheduleDate must not be null");
		return this.appointmentRepository.findByAppointmentDateAndOptionalVet(scheduleDate, vetId);
	}

	@Transactional(readOnly = true)
	public List<LocalTime> getAvailableSlots(int vetId, LocalDate appointmentDate, Integer excludeAppointmentId) {
		Assert.notNull(appointmentDate, "appointmentDate must not be null");
		List<Appointment> appointments = this.appointmentRepository
			.findByVetIdAndAppointmentDateOrderByAppointmentTimeAsc(vetId, appointmentDate);

		Set<LocalTime> bookedTimes = new HashSet<>();
		for (Appointment appointment : appointments) {
			if (appointment.getStatus() == AppointmentStatus.BOOKED
					&& (excludeAppointmentId == null || !excludeAppointmentId.equals(appointment.getId()))) {
				bookedTimes.add(appointment.getAppointmentTime());
			}
		}

		List<LocalTime> available = new ArrayList<>();
		for (LocalTime slot : generateDailySlots(DEFAULT_START_TIME, DEFAULT_END_TIME, DEFAULT_SLOT_MINUTES)) {
			if (!bookedTimes.contains(slot)) {
				available.add(slot);
			}
		}
		return available;
	}

	@Transactional
	public Appointment createAppointment(Appointment appointment) {
		Assert.notNull(appointment, "appointment must not be null");
		Assert.notNull(appointment.getPet(), "appointment.pet must not be null");
		Assert.notNull(appointment.getVet(), "appointment.vet must not be null");
		Assert.notNull(appointment.getAppointmentDate(), "appointment.appointmentDate must not be null");
		Assert.notNull(appointment.getAppointmentTime(), "appointment.appointmentTime must not be null");

		if (this.appointmentRepository.existsBookedConflict(appointment.getVet().getId(), appointment.getAppointmentDate(),
				appointment.getAppointmentTime(), null)) {
			throw new SlotAlreadyBookedException();
		}

		appointment.setStatus(AppointmentStatus.BOOKED);
		return this.appointmentRepository.save(appointment);
	}

	@Transactional
	public Appointment rescheduleAppointment(int appointmentId, Appointment updated) {
		Appointment existing = findById(appointmentId);
		Assert.notNull(updated, "updated must not be null");
		Assert.notNull(updated.getVet(), "updated.vet must not be null");
		Assert.notNull(updated.getPet(), "updated.pet must not be null");
		Assert.notNull(updated.getAppointmentDate(), "updated.appointmentDate must not be null");
		Assert.notNull(updated.getAppointmentTime(), "updated.appointmentTime must not be null");

		if (this.appointmentRepository.existsBookedConflict(updated.getVet().getId(), updated.getAppointmentDate(),
				updated.getAppointmentTime(), existing.getId())) {
			throw new SlotAlreadyBookedException();
		}

		existing.setAppointmentDate(updated.getAppointmentDate());
		existing.setAppointmentTime(updated.getAppointmentTime());
		existing.setVet(updated.getVet());
		existing.setPet(updated.getPet());
		existing.setReason(updated.getReason());
		// do not modify status here; cancel is separate action
		return this.appointmentRepository.save(existing);
	}

	@Transactional
	public Appointment cancelAppointment(int appointmentId) {
		Appointment appointment = findById(appointmentId);
		appointment.setStatus(AppointmentStatus.CANCELLED);
		return this.appointmentRepository.save(appointment);
	}

	static List<LocalTime> generateDailySlots(LocalTime startInclusive, LocalTime endExclusive, int slotMinutes) {
		Assert.notNull(startInclusive, "startInclusive must not be null");
		Assert.notNull(endExclusive, "endExclusive must not be null");
		Assert.isTrue(slotMinutes > 0, "slotMinutes must be positive");

		List<LocalTime> slots = new ArrayList<>();
		LocalTime current = startInclusive;
		while (current.isBefore(endExclusive)) {
			slots.add(current);
			current = current.plusMinutes(slotMinutes);
		}
		return slots;
	}

	static String buildOwnerDisplayName(Owner owner) {
		Assert.notNull(owner, "owner must not be null");
		String firstName = owner.getFirstName();
		String lastName = owner.getLastName();

		if (firstName == null && lastName == null) {
			return "";
		}
		if (firstName == null) {
			return lastName;
		}
		if (lastName == null) {
			return firstName;
		}
		return firstName + " " + lastName;
	}

}