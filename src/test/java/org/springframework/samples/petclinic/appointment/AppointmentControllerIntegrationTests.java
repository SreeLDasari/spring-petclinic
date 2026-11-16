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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentControllerIntegrationTests {

	@Autowired
	MockMvc mockMvc;

	@Test
	void initNewAppointmentFormRenders() throws Exception {
		mockMvc.perform(get("/owners/{ownerId}/pets/{petId}/appointments/new", 1, 1))
			.andExpect(status().isOk())
			.andExpect(model().attributeExists("appointmentForm"))
			.andExpect(model().attributeExists("vets"))
			.andExpect(view().name("appointments/appointmentForm"));
	}

	@Test
	void bookingWithMissingFieldsShowsValidationErrors() throws Exception {
		mockMvc.perform(post("/owners/{ownerId}/pets/{petId}/appointments/new", 1, 1)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasErrors("appointmentForm"))
			.andExpect(view().name("appointments/appointmentForm"));
	}

	@Test
	void bookingInPastIsRejected() throws Exception {
		LocalDate yesterday = LocalDate.now().minusDays(1);
		mockMvc.perform(post("/owners/{ownerId}/pets/{petId}/appointments/new", 1, 1).param("vetId", "1")
				.param("appointmentDate", yesterday.toString()).param("startTime", "09:00"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasGlobalErrors("appointmentForm"))
			.andExpect(view().name("appointments/appointmentForm"));
	}

	@Test
	void bookingOverlapIsRejected() throws Exception {
		LocalDate date = LocalDate.now().plusDays(1);

		mockMvc.perform(post("/owners/{ownerId}/pets/{petId}/appointments/new", 1, 1).param("vetId", "1")
				.param("appointmentDate", date.toString()).param("startTime", "09:00"))
			.andExpect(status().is3xxRedirection());

		mockMvc.perform(post("/owners/{ownerId}/pets/{petId}/appointments/new", 1, 1).param("vetId", "1")
				.param("appointmentDate", date.toString()).param("startTime", "09:00"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasGlobalErrors("appointmentForm"))
			.andExpect(view().name("appointments/appointmentForm"));
	}

}
