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

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AppointmentController.class)
@DisabledInNativeImage
@DisabledInAotMode
class AppointmentControllerTests {

	private static final int OWNER_ID = 1;

	private static final int PET_ID = 1;

	private static final int VET_ID = 2;

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	AppointmentService appointmentService;

	@MockitoBean
	AppointmentRepository appointmentRepository;

	@MockitoBean
	OwnerRepository owners;

	@MockitoBean
	VetRepository vets;

	@BeforeEach
	void setup() {
		Owner owner = new Owner();
		owner.setId(OWNER_ID);
		Pet pet = new Pet();
		pet.setId(PET_ID);
		owner.addPet(pet);
		given(owners.findById(OWNER_ID)).willReturn(Optional.of(owner));

		Vet vet = new Vet();
		vet.setId(VET_ID);
		given(vets.findAll()).willReturn(List.of(vet));
	}

	@Test
	void initCreationForm() throws Exception {
		mockMvc.perform(get("/owners/{ownerId}/pets/{petId}/appointments/new", OWNER_ID, PET_ID))
			.andExpect(status().isOk())
			.andExpect(model().attributeExists("appointmentForm"))
			.andExpect(view().name("appointments/createOrUpdateAppointmentForm"));
	}

	@Test
	void processCreationFormConflictRendersFormWithGlobalError() throws Exception {
		LocalDateTime dt = LocalDateTime.parse("2026-01-01T12:00:00");
		willThrow(new AppointmentSlotUnavailableException("Selected vet is unavailable"))
			.given(appointmentService)
			.createAppointment(OWNER_ID, PET_ID, VET_ID, dt);

		mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/appointments", OWNER_ID, PET_ID)
				.param("vetId", String.valueOf(VET_ID))
				.param("date", "2026-01-01")
				.param("time", "12:00"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasGlobalErrors("appointmentForm"))
			.andExpect(view().name("appointments/createOrUpdateAppointmentForm"));
	}

	@Test
	void processCancelRedirects() throws Exception {
		Appointment appt = new Appointment();
		appt.setId(10);
		Owner owner = new Owner();
		owner.setId(OWNER_ID);
		Pet pet = new Pet();
		pet.setId(PET_ID);
		owner.addPet(pet);
		appt.setPet(pet);
		appt.setVet(new Vet());
		appt.setAppointmentDateTime(LocalDateTime.parse("2026-01-01T12:00:00"));

		given(appointmentRepository.findById(10)).willReturn(Optional.of(appt));
		willDoNothing().given(appointmentService).cancelAppointment(10);

		mockMvc.perform(post("/appointments/{appointmentId}/cancel", 10))
			.andExpect(status().is3xxRedirection());
	}

}
