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

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisabledInAotMode
class AppointmentServiceTests {

	@Autowired
	private AppointmentService appointmentService;

	@Test
	@Transactional
	void shouldPreventOverlappingAppointmentsForSameVet() {
		int ownerId = 1;
		int petId = 1;
		int vetId = 1;
		LocalDate date = LocalDate.now().plusDays(1);

		this.appointmentService.scheduleAppointment(ownerId, petId, vetId, date, LocalTime.of(10, 0), 30,
				"checkup");

		assertThatThrownBy(() -> this.appointmentService.scheduleAppointment(ownerId, petId, vetId, date,
				LocalTime.of(10, 15), 30, "overlap"))
			.isInstanceOf(AppointmentConflictException.class);
	}

	@Test
	@Transactional
	void shouldAllowNonOverlappingAppointmentsForSameVet() {
		int ownerId = 1;
		int petId = 1;
		int vetId = 1;
		LocalDate date = LocalDate.now().plusDays(1);

		Appointment first = this.appointmentService.scheduleAppointment(ownerId, petId, vetId, date, LocalTime.of(10, 0),
				30, "checkup");
		Appointment second = this.appointmentService.scheduleAppointment(ownerId, petId, vetId, date, LocalTime.of(10, 30),
				30, "followup");

		assertThat(first.getId()).isNotNull();
		assertThat(second.getId()).isNotNull();
	}

}
