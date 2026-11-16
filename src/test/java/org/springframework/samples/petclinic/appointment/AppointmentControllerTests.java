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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AppointmentController.class)
@Import(TestClockConfiguration.class)
class AppointmentControllerTests {

	@Autowired
	MockMvc mockMvc;

	@MockBean
	AppointmentService appointmentService;

	@MockBean
	OwnerRepository ownerRepository;

	@MockBean
	VetRepository vetRepository;

	@Test
	void scheduleAppointmentConflictRendersFormWithGlobalError() throws Exception {
		Owner owner = new Owner();
		owner.setId(1);
		Pet pet = new Pet();
		pet.setId(7);
		owner.addPet(pet);

		BDDMockito.given(this.ownerRepository.findById(1)).willReturn(Optional.of(owner));
		BDDMockito.given(this.appointmentService.scheduleAppointment(eq(7), any(), any(), anyInt(), any(), any()))
				.willThrow(new AppointmentConflictException(LocalDateTime.of(2030, 1, 1, 10, 0),
						LocalDateTime.of(2030, 1, 1, 10, 30)));

		mockMvc.perform(post("/owners/{ownerId}/pets/{petId}/appointments/new", 1, 7)
				.param("startDateTime", "2030-01-01T10:15")
				.param("durationMinutes", "30")
				.param("reason", "checkup"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasErrors("appointment"))
			.andExpect(model().attributeHasGlobalErrorCode("appointment", "conflict"))
			.andExpect(view().name(AppointmentController.VIEW_CREATE_OR_UPDATE_APPOINTMENT_FORM));
	}

}

class TestClockConfiguration {

	@org.springframework.context.annotation.Bean
	Clock clock() {
		return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
	}

}
