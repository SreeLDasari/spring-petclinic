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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-end MVC tests of the appointment workflow (schedule, reschedule, cancel,
 * conflict handling) against the in-memory database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AppointmentControllerTests {

	private static final String BASE = "/owners/1/pets/1/appointments";

	private static final String DAY = LocalDate.now().plusDays(20).toString();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void removeAppointments() {
		this.jdbc.update("delete from visits where vet_id is not null");
	}

	private ResultActions schedule(String vetId, String date, String time, String reason) throws Exception {
		return this.mockMvc.perform(post(BASE + "/new").param("vetId", vetId)
			.param("date", date)
			.param("time", time)
			.param("description", reason));
	}

	private int idOf(String reason) {
		return this.jdbc.queryForObject("select id from visits where description = ?", Integer.class, reason);
	}

	@Test
	void scheduleFormIsShown() throws Exception {
		this.mockMvc.perform(get(BASE + "/new"))
			.andExpect(status().isOk())
			.andExpect(view().name("appointments/appointmentForm"))
			.andExpect(model().attributeExists("appointmentForm", "vets", "pet", "owner"));
	}

	@Test
	void scheduledAppointmentAppearsInUpcomingList() throws Exception {
		schedule("1", DAY, "10:00", "annual checkup").andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl(BASE))
			.andExpect(flash().attribute("message", "appointment.booked"));

		this.mockMvc.perform(get(BASE))
			.andExpect(status().isOk())
			.andExpect(view().name("appointments/appointmentList"))
			.andExpect(model().attribute("upcoming", org.hamcrest.Matchers.hasSize(1)))
			.andExpect(content().string(containsString("annual checkup")))
			.andExpect(content().string(containsString(DAY)))
			.andExpect(content().string(containsString("10:00")));
	}

	@Test
	void missingFieldsAreRejected() throws Exception {
		this.mockMvc.perform(post(BASE + "/new").param("description", ""))
			.andExpect(status().isOk())
			.andExpect(view().name("appointments/appointmentForm"))
			.andExpect(model().attributeHasFieldErrors("appointmentForm", "vetId", "date", "time", "description"));
	}

	@Test
	void unknownVetIsRejected() throws Exception {
		schedule("9999", DAY, "10:00", "x").andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode("appointmentForm", "vetId", "notFound"));
	}

	@Test
	void pastAppointmentIsRejected() throws Exception {
		schedule("1", LocalDate.now().minusDays(1).toString(), "10:00", "x").andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode("appointmentForm", "date", "appointment.past"));
	}

	@Test
	void overlappingAppointmentForSameVetIsBlockedWithMessage() throws Exception {
		schedule("1", DAY, "10:00", "first").andExpect(status().is3xxRedirection());

		schedule("1", DAY, "10:15", "second").andExpect(status().isOk())
			.andExpect(view().name("appointments/appointmentForm"))
			.andExpect(model().attributeHasFieldErrorCode("appointmentForm", "time", "appointment.conflict"))
			.andExpect(content().string(containsString("already has an appointment that overlaps")));

		schedule("2", DAY, "10:15", "other vet").andExpect(status().is3xxRedirection());
		schedule("1", DAY, "10:30", "adjacent").andExpect(status().is3xxRedirection());
	}

	@Test
	void rescheduleMovesAppointmentAndFreesPreviousSlot() throws Exception {
		schedule("1", DAY, "10:00", "to move").andExpect(status().is3xxRedirection());
		int id = idOf("to move");

		this.mockMvc.perform(get(BASE + "/" + id + "/reschedule"))
			.andExpect(status().isOk())
			.andExpect(view().name("appointments/appointmentForm"))
			.andExpect(content().string(containsString("10:00")));

		this.mockMvc
			.perform(post(BASE + "/" + id + "/reschedule").param("vetId", "1")
				.param("date", DAY)
				.param("time", "14:00")
				.param("description", "to move"))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("message", "appointment.rescheduled"));

		this.mockMvc.perform(get(BASE))
			.andExpect(content().string(containsString("14:00")))
			.andExpect(content().string(not(containsString("10:00"))));
		schedule("1", DAY, "10:00", "reuses old slot").andExpect(status().is3xxRedirection());
	}

	@Test
	void rescheduleIntoBookedWindowIsBlocked() throws Exception {
		schedule("1", DAY, "10:00", "booked").andExpect(status().is3xxRedirection());
		schedule("1", DAY, "12:00", "mover").andExpect(status().is3xxRedirection());
		int id = idOf("mover");

		this.mockMvc
			.perform(post(BASE + "/" + id + "/reschedule").param("vetId", "1")
				.param("date", DAY)
				.param("time", "10:20")
				.param("description", "mover"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode("appointmentForm", "time", "appointment.conflict"));
	}

	@Test
	void cancelledAppointmentLeavesUpcomingListAndFreesSlot() throws Exception {
		schedule("1", DAY, "10:00", "to cancel").andExpect(status().is3xxRedirection());
		int id = idOf("to cancel");

		this.mockMvc.perform(post(BASE + "/" + id + "/cancel"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl(BASE))
			.andExpect(flash().attribute("message", "appointment.cancelled"));

		this.mockMvc.perform(get(BASE))
			.andExpect(model().attribute("upcoming", org.hamcrest.Matchers.empty()))
			.andExpect(
					model().attribute("history", org.hamcrest.Matchers.hasSize(org.hamcrest.Matchers.greaterThan(0))));
		this.mockMvc.perform(get("/owners/1")).andExpect(content().string(not(containsString("to cancel"))));
		schedule("1", DAY, "10:00", "reuses slot").andExpect(status().is3xxRedirection());
	}

	@Test
	void cancellingTwiceIsReportedNotFailing() throws Exception {
		schedule("1", DAY, "10:00", "twice").andExpect(status().is3xxRedirection());
		int id = idOf("twice");
		this.mockMvc.perform(post(BASE + "/" + id + "/cancel")).andExpect(status().is3xxRedirection());

		this.mockMvc.perform(post(BASE + "/" + id + "/cancel"))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("error", "appointment.unavailable"));
	}

	@Test
	void appointmentOfAnotherPetCannotBeCancelled() throws Exception {
		schedule("1", DAY, "10:00", "pet one").andExpect(status().is3xxRedirection());
		int id = idOf("pet one");

		this.mockMvc.perform(post("/owners/2/pets/2/appointments/" + id + "/cancel"))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("error", "appointment.unavailable"));
		this.mockMvc.perform(get(BASE)).andExpect(model().attribute("upcoming", org.hamcrest.Matchers.hasSize(1)));
	}

	@Test
	void ownerPageLinksToAppointments() throws Exception {
		this.mockMvc.perform(get("/owners/1"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("1/pets/1/appointments/new")));
	}

}
