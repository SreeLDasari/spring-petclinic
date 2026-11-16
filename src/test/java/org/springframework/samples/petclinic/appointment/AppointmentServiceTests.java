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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.context.aot.DisabledInAotMode;

import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisabledInAotMode
@ExtendWith(MockitoExtension.class)
class AppointmentServiceTests {

	@Mock
	private AppointmentRepository appointmentRepository;

	@Mock
	private OwnerRepository owners;

	@Mock
	private VetRepository vets;

	private AppointmentService service;

	@BeforeEach
	void setup() {
		Clock fixedClock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneId.of("UTC"));
		this.service = new AppointmentService(this.appointmentRepository, this.owners, this.vets, fixedClock);
	}

	@Test
	void createAppointmentRejectsSlotUnavailableFromExistsCheck() {
		Owner owner = new Owner();
		owner.setId(1);
		Pet pet = new Pet();
		pet.setId(1);
		owner.addPet(pet);
		Vet vet = new Vet();
		vet.setId(2);

		given(this.owners.findById(1)).willReturn(Optional.of(owner));
		given(this.vets.findById(2)).willReturn(Optional.of(vet));
		LocalDateTime dt = LocalDateTime.parse("2026-01-01T12:00:00");
		given(this.appointmentRepository.existsByVetIdAndAppointmentDateTime(2, dt)).willReturn(true);

		assertThatThrownBy(() -> this.service.createAppointment(1, 1, 2, dt))
			.isInstanceOf(AppointmentSlotUnavailableException.class);
	}

	@Test
	void createAppointmentRejectsSlotUnavailableFromUniqueConstraintRace() {
		Owner owner = new Owner();
		owner.setId(1);
		Pet pet = new Pet();
		pet.setId(1);
		owner.addPet(pet);
		Vet vet = new Vet();
		vet.setId(2);

		given(this.owners.findById(1)).willReturn(Optional.of(owner));
		given(this.vets.findById(2)).willReturn(Optional.of(vet));
		LocalDateTime dt = LocalDateTime.parse("2026-01-01T12:00:00");
		given(this.appointmentRepository.existsByVetIdAndAppointmentDateTime(2, dt)).willReturn(false);
		given(this.appointmentRepository.save(ArgumentMatchers.any(Appointment.class)))
			.willThrow(new DataIntegrityViolationException("unique_vet_appointment_datetime"));

		assertThatThrownBy(() -> this.service.createAppointment(1, 1, 2, dt))
			.isInstanceOf(AppointmentSlotUnavailableException.class);
	}

}