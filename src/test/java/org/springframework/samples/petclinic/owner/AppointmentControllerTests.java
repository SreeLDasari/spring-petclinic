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
package org.springframework.samples.petclinic.owner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AppointmentController.class)
@DisabledInNativeImage
@DisabledInAotMode
class AppointmentControllerTests {

	private static final int TEST_OWNER_ID = 1;

	private static final int TEST_PET_ID = 2;

	private static final int TEST_VET_ID = 3;

	private static final int TEST_APPOINTMENT_ID = 10;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OwnerRepository owners;

	@MockitoBean
	private VisitRepository visits;

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
		owner.addPet(pet);
		given(owners.findById(TEST_OWNER_ID)).willReturn(Optional.of(owner));

		Vet vet = new Vet();
		vet.setId(TEST_VET_ID);
		given(vets.findAll()).willReturn(List.of(vet));
	}

	@Test
	void initNewAppointmentForm() throws Exception {
		mockMvc.perform(get("/owners/{ownerId}/pets/{petId}/appointments/new", TEST_OWNER_ID, TEST_PET_ID))
			.andExpect(status().isOk())
			.andExpect(model().attributeExists("appointmentForm"))
			.andExpect(view().name("appointments/createOrUpdateAppointmentForm"));
	}

	@Test
	void processNewAppointmentFormSuccess() throws Exception {
		given(appointmentService.scheduleAppointment(eq(TEST_OWNER_ID), eq(TEST_PET_ID), eq(TEST_VET_ID), any(),
				eq("Checkup"))).willReturn(new Visit());

		mockMvc.perform(post("/owners/{ownerId}/pets/{petId}/appointments/new", TEST_OWNER_ID, TEST_PET_ID)
				.param("vetId", Integer.toString(TEST_VET_ID))
				.param("date", LocalDate.now().plusDays(1).toString())
				.param("time", LocalTime.of(9, 0).toString())
				.param("description", "Checkup"))
			.andExpect(status().is3xxRedirection())
			.andExpect(view().name("redirect:/owners/{ownerId}"));
	}

	@Test
	void processNewAppointmentFormConflictShowsError() throws Exception {
		willThrow(new SchedulingConflictException("Selected time overlaps with another appointment for this veterinarian."))
			.given(appointmentService)
			.scheduleAppointment(eq(TEST_OWNER_ID), eq(TEST_PET_ID), eq(TEST_VET_ID), any(), any());

		mockMvc.perform(post("/owners/{ownerId}/pets/{petId}/appointments/new", TEST_OWNER_ID, TEST_PET_ID)
				.param("vetId", Integer.toString(TEST_VET_ID))
				.param("date", LocalDate.now().plusDays(1).toString())
				.param("time", LocalTime.of(9, 0).toString())
				.param("description", "Checkup"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasErrors("appointmentForm"))
			.andExpect(view().name("appointments/createOrUpdateAppointmentForm"));
	}

	@Test
	void vetScheduleView() throws Exception {
		given(appointmentService.getVetScheduleForDate(eq(TEST_VET_ID), any())).willReturn(List.of());
		mockMvc.perform(get("/vets/{vetId}/schedule", TEST_VET_ID).param("date", LocalDate.now().toString()))
			.andExpect(status().isOk())
			.andExpect(view().name("vets/vetSchedule"));
	}

	@Test
	void cancelAppointmentRedirects() throws Exception {
		Visit visit = new Visit();
		visit.setId(TEST_APPOINTMENT_ID);
		visit.setStartDateTime(LocalDateTime.now().plusDays(1));
		given(visits.findById(TEST_APPOINTMENT_ID)).willReturn(Optional.of(visit));
		willDoNothing().given(appointmentService).cancelAppointment(TEST_APPOINTMENT_ID);

		mockMvc.perform(post("/appointments/{appointmentId}/cancel", TEST_APPOINTMENT_ID))
			.andExpect(status().is3xxRedirection());
	}

}
