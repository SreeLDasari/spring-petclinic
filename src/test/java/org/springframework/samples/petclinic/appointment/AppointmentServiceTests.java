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

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AppointmentServiceTests {

	@Autowired
	private AppointmentService appointmentService;

	@Test
	@Transactional
	void shouldPreventVetDoubleBooking() {
		int ownerId = 1;
		int petId = 1;
		int vetId = 1;
		LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);

		this.appointmentService.scheduleAppointment(ownerId, petId, vetId, start, 30, "Checkup");

		assertThatThrownBy(() -> this.appointmentService.scheduleAppointment(ownerId, petId, vetId,
				start.plusMinutes(15), 30, "Overlap"))
			.isInstanceOf(VetDoubleBookedException.class);
	}

}
