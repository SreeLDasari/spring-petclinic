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

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AppointmentController.class)
@DisabledInNativeImage
@DisabledInAotMode
class AppointmentControllerTests {

	private static final int TEST_OWNER_ID = 1;

	private static final int TEST_PET_ID = 1;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OwnerRepository owners;

	@MockitoBean
	private VetRepository vets;

	@MockitoBean
	private AppointmentService appointmentService;

	@BeforeEach
	void setup() {
		Owner owner = new Owner();
		owner.setId(TEST_OWNER_ID);
		Pet pet = new Pet();
		pet.setId(TEST_PET_ID);
		pet.setName("George");
		owner.addPet(pet);
		given(this.owners.findById(TEST_OWNER_ID)).willReturn(Optional.of(owner));

		Vet vet = new Vet();
		vet.setId(2);
		vet.setFirstName("Helen");
		vet.setLastName("Leary");
		given(this.vets.findAll()).willReturn(List.of(vet));
	}

	@Test
	void initScheduleForm() throws Exception {
		mockMvc.perform(get("/owners/{ownerId}/pets/{petId}/appointments/new", TEST_OWNER_ID, TEST_PET_ID))
			.andExpect(status().isOk())
			.andExpect(view().name("appointments/createOrUpdateAppointmentForm"))
			.andExpect(model().attributeExists("appointmentForm"));
	}

	@Test
	void scheduleSuccessRedirectsToOwner() throws Exception {
		given(this.appointmentService.scheduleAppointment(org.mockito.ArgumentMatchers.eq(TEST_PET_ID),
				org.mockito.ArgumentMatchers.eq(2), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.eq(30), org.mockito.ArgumentMatchers.eq("Checkup")))
			.willReturn(new Appointment());

		mockMvc.perform(post("/owners/{ownerId}/pets/{petId}/appointments", TEST_OWNER_ID, TEST_PET_ID)
				.param("vetId", "2")
				.param("date", LocalDate.now().plusDays(1).toString())
				.param("time", "10:00")
				.param("durationMinutes", "30")
				.param("reason", "Checkup"))
			.andExpect(status().is3xxRedirection())
			.andExpect(view().name("redirect:/owners/{ownerId}"));
	}

	@Test
	void scheduleConflictRendersFormWithError() throws Exception {
		given(this.appointmentService.scheduleAppointment(org.mockito.ArgumentMatchers.eq(TEST_PET_ID),
				org.mockito.ArgumentMatchers.eq(2), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.eq(30), org.mockito.ArgumentMatchers.eq("Checkup")))
			.willThrow(new AppointmentConflictException(2, null, "conflict"));

		mockMvc.perform(post("/owners/{ownerId}/pets/{petId}/appointments", TEST_OWNER_ID, TEST_PET_ID)
				.param("vetId", "2")
				.param("date", LocalDate.now().plusDays(1).toString())
				.param("time", "10:00")
				.param("durationMinutes", "30")
				.param("reason", "Checkup"))
			.andExpect(status().isOk())
			.andExpect(view().name("appointments/createOrUpdateAppointmentForm"))
			.andExpect(model().attributeHasErrors("appointmentForm"));
	}

	@Test
	void cancelRedirectsToAgenda() throws Exception {
		doNothing().when(this.appointmentService).cancelAppointment(10);

		mockMvc.perform(post("/appointments/{appointmentId}/cancel", 10).param("vetId", "2")
				.param("date", LocalDate.now().toString()))
			.andExpect(status().is3xxRedirection());
	}

}
