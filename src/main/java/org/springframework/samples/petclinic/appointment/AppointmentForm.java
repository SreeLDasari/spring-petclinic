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

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Web form backing object.
 */
public class AppointmentForm {

	@NotNull
	@FutureOrPresent
	@DateTimeFormat(pattern = "yyyy-MM-dd")
	private LocalDate appointmentDate;

	@NotNull
	@DateTimeFormat(pattern = "HH:mm")
	private LocalTime appointmentTime;

	@NotNull
	private Integer vetId;

	@Size(max = 255)
	private String reason;

	public LocalDate getAppointmentDate() {
		return this.appointmentDate;
	}

	public void setAppointmentDate(LocalDate appointmentDate) {
		this.appointmentDate = appointmentDate;
	}

	public LocalTime getAppointmentTime() {
		return this.appointmentTime;
	}

	public void setAppointmentTime(LocalTime appointmentTime) {
		this.appointmentTime = appointmentTime;
	}

	public Integer getVetId() {
		return this.vetId;
	}

	public void setVetId(Integer vetId) {
		this.vetId = vetId;
	}

	public String getReason() {
		return this.reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

}
