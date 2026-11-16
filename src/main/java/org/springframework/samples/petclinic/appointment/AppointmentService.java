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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentService {

	private final AppointmentRepository appointmentRepository;

	private final OwnerRepository ownerRepository;

	private final VetRepository vetRepository;

	private final Clock clock;

	private final LocalTime clinicStart;

	private final LocalTime clinicEnd;

	private final int slotMinutes;

	public AppointmentService(AppointmentRepository appointmentRepository, OwnerRepository ownerRepository,
			VetRepository vetRepository, Clock clock,
			@Value("${clinic.hours.start:09:00}") LocalTime clinicStart,
			@Value("${clinic.hours.end:17:00}") LocalTime clinicEnd,
			@Value("${clinic.slot-minutes:30}") int slotMinutes) {
		this.appointmentRepository = appointmentRepository;
		this.ownerRepository = ownerRepository;
		this.vetRepository = vetRepository;
		this.clock = clock;
		this.clinicStart = clinicStart;
		this.clinicEnd = clinicEnd;
		this.slotMinutes = slotMinutes;
	}

	@Transactional(readOnly = true)
	public List<LocalTime> getAvailableSlots(Integer vetId, LocalDate date) {
		if (vetId == null || date == null) {
			return List.of();
		}

		LocalDate today = LocalDate.now(this.clock);
		if (date.isBefore(today)) {
			return List.of();
		}

		List<Appointment> booked = this.appointmentRepository.findByVetIdAndAppointmentDateAndStatusIn(vetId, date,
				EnumSet.of(AppointmentStatus.BOOKED));

		LocalTime now = LocalTime.now(this.clock);

		List<LocalTime> slots = new ArrayList<>();
		LocalTime slot = this.clinicStart;
		while (!slot.plusMinutes(this.slotMinutes).isAfter(this.clinicEnd)) {
			LocalTime slotEnd = slot.plusMinutes(this.slotMinutes);

			boolean isPastToday = date.isEqual(today) && !slot.isAfter(now);
			boolean overlaps = booked.stream()
					.anyMatch(a -> overlaps(slot, slotEnd, a.getStartTime(), a.getEndTime()));

			if (!isPastToday && !overlaps) {
				slots.add(slot);
			}
			slot = slot.plusMinutes(this.slotMinutes);
		}
		return slots;
	}

	@Transactional
	public Appointment book(Integer ownerId, Integer petId, Integer vetId, LocalDate date, LocalTime startTime) {
		validateFuture(date, startTime);

		Owner owner = this.ownerRepository.findById(ownerId)
				.orElseThrow(() -> new IllegalArgumentException("Owner not found: " + ownerId));
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException("Pet not found: " + petId);
		}

		Vet vet = this.vetRepository.findById(vetId)
				.orElseThrow(() -> new IllegalArgumentException("Vet not found: " + vetId));

		Appointment appointment = new Appointment();
		appointment.setOwner(owner);
		appointment.setPet(pet);
		appointment.setVet(vet);
		appointment.setAppointmentDate(date);
		appointment.setStartTime(startTime);
		appointment.setDurationMinutes(this.slotMinutes);
		appointment.setStatus(AppointmentStatus.BOOKED);

		assertNoVetOverlap(appointment, null);
		return this.appointmentRepository.save(appointment);
	}

	@Transactional(readOnly = true)
	public Appointment getAppointment(int appointmentId) {
		return this.appointmentRepository.findById(appointmentId)
				.orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
	}

	@Transactional
	public Appointment reschedule(Integer appointmentId, LocalDate newDate, LocalTime newStartTime) {
		if (newDate == null || newStartTime == null) {
			throw new IllegalArgumentException("New appointment date and start time are required.");
		}

		Appointment appointment = getAppointment(appointmentId);

		if (appointment.getStatus() != AppointmentStatus.BOOKED) {
			return appointment;
		}

		validateFuture(newDate, newStartTime);
		appointment.setAppointmentDate(newDate);
		appointment.setStartTime(newStartTime);
		appointment.setDurationMinutes(this.slotMinutes);

		assertNoVetOverlap(appointment, appointmentId);
		return this.appointmentRepository.save(appointment);
	}

	@Transactional
	public Appointment cancel(Integer appointmentId) {
		Appointment appointment = this.appointmentRepository.findById(appointmentId)
				.orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
		appointment.setStatus(AppointmentStatus.CANCELLED);
		return this.appointmentRepository.save(appointment);
	}

	private void validateFuture(LocalDate date, LocalTime startTime) {
		if (date == null || startTime == null) {
			throw new IllegalArgumentException("Appointment date and start time are required.");
		}

		LocalDate today = LocalDate.now(this.clock);
		LocalTime now = LocalTime.now(this.clock);

		if (date.isBefore(today) || (date.isEqual(today) && !startTime.isAfter(now))) {
			throw new IllegalArgumentException("Appointments must be scheduled in the future.");
		}
	}

	private void assertNoVetOverlap(Appointment candidate, Integer appointmentIdToExclude) {
		List<Appointment> booked = this.appointmentRepository.findByVetIdAndAppointmentDateAndStatusIn(
				candidate.getVet().getId(), candidate.getAppointmentDate(), EnumSet.of(AppointmentStatus.BOOKED));

		LocalTime newStart = candidate.getStartTime();
		LocalTime newEnd = candidate.getEndTime();

		for (Appointment existing : booked) {
			if (appointmentIdToExclude != null && appointmentIdToExclude.equals(existing.getId())) {
				continue;
			}
			if (overlaps(newStart, newEnd, existing.getStartTime(), existing.getEndTime())) {
				String message = String.format("Selected slot conflicts with an existing appointment between %s and %s.",
						existing.getStartTime(), existing.getEndTime());
				throw new AppointmentConflictException(message, existing.getStartTime(), existing.getEndTime());
			}
		}
	}

	static boolean overlaps(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
		return start1.isBefore(end2) && end1.isAfter(start2);
	}

}
