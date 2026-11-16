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

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.vet.Vet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Simple JavaBean domain object representing a visit.
 *
 * @author Ken Krebs
 * @author Dave Syer
 */
@Entity
@Table(name = "visits")
public class Visit extends BaseEntity {

	/**
	 * Legacy visit date. Kept for backwards compatibility with existing flows.
	 */
	@Column(name = "visit_date")
	@DateTimeFormat(pattern = "yyyy-MM-dd")
	private LocalDate date;

	/**
	 * Scheduled appointment start date/time. Nullable for legacy visits.
	 */
	@Column(name = "start_date_time")
	@DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
	private LocalDateTime startDateTime;

	/**
	 * Appointment status. Nullable for legacy visits. Defaults to {@link VisitStatus#SCHEDULED}.
	 */
	@Column(name = "status")
	@Enumerated(EnumType.STRING)
	private VisitStatus status;

	/**
	 * Assigned vet for scheduled appointment. Nullable for legacy visits.
	 */
	@ManyToOne
	@JoinColumn(name = "vet_id")
	private Vet vet;

	/**
	 * Associated pet for this visit. This association is read-only to avoid breaking the existing
	 * Pet aggregate mapping (Pet owns the relationship via its visits collection).
	 */
	@ManyToOne
	@JoinColumn(name = "pet_id", insertable = false, updatable = false)
	private Pet pet;

	@NotBlank
	@Size(max = 255)
	private String description;

	/**
	 * Creates a new instance of Visit for tomorrow
	 */
	public Visit() {
		this.date = LocalDate.now().plusDays(1);
		this.status = VisitStatus.SCHEDULED;
	}

	public LocalDate getDate() {
		return this.date;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public LocalDateTime getStartDateTime() {
		return this.startDateTime;
	}

	public void setStartDateTime(LocalDateTime startDateTime) {
		this.startDateTime = startDateTime;
	}

	public VisitStatus getStatus() {
		return this.status;
	}

	public void setStatus(VisitStatus status) {
		this.status = status;
	}

	public Vet getVet() {
		return this.vet;
	}

	public void setVet(Vet vet) {
		this.vet = vet;
	}

	public Pet getPet() {
		return this.pet;
	}

	public boolean isCancelled() {
		return this.status == VisitStatus.CANCELLED;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

}