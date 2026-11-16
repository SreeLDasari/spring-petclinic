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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;

@DataJpaTest
@Import(AppointmentService.class)
class AppointmentServiceTests {

	@Autowired
	private AppointmentService appointmentService;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private PetRepository petRepository;

	@Autowired
	private VetRepository vetRepository;

	@Test
	void scheduleAppointmentAndPreventOverlapAndAllowAfterCancel() {
		Pet pet = this.petRepository.findAll().stream().findFirst().orElseThrow();
		Vet vet = this.vetRepository.findAll().stream().findFirst().orElseThrow();

		LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
		Appointment a1 = this.appointmentService.scheduleAppointment(pet.getId(), vet.getId(), start, 30, "Checkup");
		assertThat(a1.getId()).isNotNull();

		assertThatThrownBy(() -> this.appointmentService.scheduleAppointment(pet.getId(), vet.getId(),
				start.plusMinutes(15), 30, "Overlap"))
			.isInstanceOf(AppointmentConflictException.class);

		this.appointmentService.cancelAppointment(a1.getId());
		Optional<Appointment> cancelled = this.appointmentRepository.findById(a1.getId());
		assertThat(cancelled).isPresent();
		assertThat(cancelled.get().getStatus()).isEqualTo(AppointmentStatus.CANCELLED);

		Appointment a2 = this.appointmentService.scheduleAppointment(pet.getId(), vet.getId(), start.plusMinutes(15), 30,
				"Now allowed");
		assertThat(a2.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
	}

}
