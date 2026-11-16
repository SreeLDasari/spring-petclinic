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

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AppointmentFlowIntegrationTests {

	@LocalServerPort
	int port;

	@Autowired
	AppointmentRepository appointments;

	@Autowired
	OwnerRepository owners;

	@Autowired
	VetRepository vets;

	@Test
	void ownerDetailsStillLoadsWithAppointmentsTablePresent() {
		RestTemplate template = new RestTemplate();
		ResponseEntity<String> result = template.exchange(
				RequestEntity.get("http://localhost:" + port + "/owners/1").build(),
				String.class);
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void appointmentPersistsAndIsVisibleInRepository() {
		var owner = owners.findById(1).orElseThrow();
		var pet = owner.getPet(1);
		var vet = vets.findAll().iterator().next();

		Appointment appt = new Appointment();
		appt.setPet(pet);
		appt.setVet(vet);
		appt.setAppointmentDateTime(LocalDateTime.now().plusDays(3).withSecond(0).withNano(0));
		Appointment saved = appointments.saveAndFlush(appt);

		assertThat(appointments.findById(saved.getId())).isPresent();
	}

}