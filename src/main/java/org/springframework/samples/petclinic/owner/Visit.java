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
import java.time.LocalTime;

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

	@Column(name = "visit_date")
	@DateTimeFormat(pattern = "yyyy-MM-dd")
	private LocalDate date;

	@NotBlank
	@Size(max = 255)
	private String description;

	@Column(name = "visit_time")
	@DateTimeFormat(pattern = "HH:mm")
	private LocalTime time;

	@ManyToOne
	@JoinColumn(name = "vet_id")
	private Vet vet;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private VisitStatus status = VisitStatus.SCHEDULED;

	/**
	 * Creates a new instance of Visit for tomorrow
	 */
	public Visit() {
		this.date = LocalDate.now().plusDays(1);
	}

	public LocalDate getDate() {
		return this.date;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public LocalTime getTime() {
		return this.time;
	}

	public void setTime(LocalTime time) {
		this.time = time;
	}

	public Vet getVet() {
		return this.vet;
	}

	public void setVet(Vet vet) {
		this.vet = vet;
	}

	public VisitStatus getStatus() {
		return this.status;
	}

	public void setStatus(VisitStatus status) {
		this.status = status;
	}

	public boolean isCancelled() {
		return this.status == VisitStatus.CANCELLED;
	}

	/**
	 * Start of the appointment, or {@code null} for legacy visits that have no time.
	 */
	public LocalDateTime getStartDateTime() {
		return (this.date != null && this.time != null) ? LocalDateTime.of(this.date, this.time) : null;
	}

	/**
	 * A visit is upcoming if it is not cancelled and has not started yet. Legacy visits
	 * without a time count as upcoming for the whole day.
	 */
	public boolean isUpcoming(LocalDateTime now) {
		if (isCancelled() || this.date == null) {
			return false;
		}
		LocalDateTime start = getStartDateTime();
		return (start != null) ? !start.isBefore(now) : !this.date.isBefore(now.toLocalDate());
	}

}
