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

import java.time.LocalDateTime;

import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.Vet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "appointments")
public class Appointment extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "pet_id")
	@NotNull
	private Pet pet;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "vet_id")
	private Vet vet;

	@Column(name = "start_date_time")
	@NotNull
	private LocalDateTime startDateTime;

	@Column(name = "duration_minutes")
	@Min(5)
	@Max(240)
	private int durationMinutes = 30;

	@NotBlank
	@Size(max = 255)
	private String reason;

	@Size(max = 2000)
	private String notes;

	@Enumerated(EnumType.STRING)
	@NotNull
	private AppointmentStatus status = AppointmentStatus.SCHEDULED;

	public Pet getPet() {
		return this.pet;
	}

	public void setPet(Pet pet) {
		this.pet = pet;
	}

	public Vet getVet() {
		return this.vet;
	}

	public void setVet(Vet vet) {
		this.vet = vet;
	}

	public LocalDateTime getStartDateTime() {
		return this.startDateTime;
	}

	public void setStartDateTime(LocalDateTime startDateTime) {
		this.startDateTime = startDateTime;
	}

	public int getDurationMinutes() {
		return this.durationMinutes;
	}

	public void setDurationMinutes(int durationMinutes) {
		this.durationMinutes = durationMinutes;
	}

	public String getReason() {
		return this.reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public String getNotes() {
		return this.notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public AppointmentStatus getStatus() {
		return this.status;
	}

	public void setStatus(AppointmentStatus status) {
		this.status = status;
	}

	public LocalDateTime getEndDateTime() {
		if (this.startDateTime == null) {
			return null;
		}
		return this.startDateTime.plusMinutes(this.durationMinutes);
	}

}
