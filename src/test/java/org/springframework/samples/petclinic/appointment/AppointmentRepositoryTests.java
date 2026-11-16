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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AppointmentRepositoryTests {

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private VetRepository vetRepository;

	@Test
	void findByVetIdAndDateAndStatusInShouldReturnBookedAppointments() {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().iterator().next();
		Vet vet = this.vetRepository.findAll().iterator().next();

		Appointment appt = new Appointment();
		appt.setOwner(owner);
		appt.setPet(pet);
		appt.setVet(vet);
		appt.setAppointmentDate(LocalDate.now().plusDays(1));
		appt.setStartTime(LocalTime.of(9, 0));
		appt.setDurationMinutes(30);
		appt.setStatus(AppointmentStatus.BOOKED);
		this.appointmentRepository.save(appt);

		assertThat(this.appointmentRepository.findByVetIdAndAppointmentDateAndStatusIn(vet.getId(),
				appt.getAppointmentDate(), EnumSet.of(AppointmentStatus.BOOKED))).isNotEmpty();
	}

}
