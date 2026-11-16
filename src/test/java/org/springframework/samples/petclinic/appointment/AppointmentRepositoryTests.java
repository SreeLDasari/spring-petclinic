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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.vet.VetRepository;

@DataJpaTest
class AppointmentRepositoryTests {

	@Autowired
	AppointmentRepository appointmentRepository;

	@Autowired
	OwnerRepository owners;

	@Autowired
	VetRepository vets;

	@Test
	void uniqueConstraintPreventsDoubleBooking() {
		// using existing data.sql fixtures: owner 1 has pet 1; vet 1 exists
		var owner = owners.findById(1).orElseThrow();
		var pet = owner.getPet(1);
		var vet = vets.findAll().iterator().next();

		LocalDateTime dt = LocalDateTime.now().plusDays(2).withSecond(0).withNano(0);

		Appointment a1 = new Appointment();
		a1.setPet(pet);
		a1.setVet(vet);
		a1.setAppointmentDateTime(dt);
		appointmentRepository.saveAndFlush(a1);

		Appointment a2 = new Appointment();
		a2.setPet(pet);
		a2.setVet(vet);
		a2.setAppointmentDateTime(dt);

		assertThatThrownBy(() -> appointmentRepository.saveAndFlush(a2))
			.isInstanceOf(DataIntegrityViolationException.class);

		assertThat(appointmentRepository.existsByVetIdAndAppointmentDateTime(vet.getId(), dt)).isTrue();
	}

	@Test
	void findUpcomingByPetIdReturnsVetFetched() {
		var owner = owners.findById(1).orElseThrow();
		var pet = owner.getPet(1);
		var vet = vets.findAll().iterator().next();

		LocalDateTime dt = LocalDateTime.now().plusDays(2).withSecond(0).withNano(0);
		Appointment a1 = new Appointment();
		a1.setPet(pet);
		a1.setVet(vet);
		a1.setAppointmentDateTime(dt);
		appointmentRepository.saveAndFlush(a1);

		var upcoming = appointmentRepository.findUpcomingByPetId(pet.getId(), LocalDateTime.now());
		assertThat(upcoming).isNotEmpty();
		assertThat(upcoming.get(0).getVet().getLastName()).isNotNull();
	}

}
