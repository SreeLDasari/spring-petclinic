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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Tests the appointment rules (create / reschedule / cancel / overlap detection) against
 * the real repositories and the in-memory database.
 */
@SpringBootTest
@Transactional
class AppointmentServiceTests {

	private static final int OWNER_ID = 1;

	private static final int PET_ID = 1;

	private static final LocalDate DAY = LocalDate.now().plusDays(10);

	@Autowired
	private AppointmentService service;

	@Autowired
	private OwnerRepository owners;

	@Autowired
	private VetRepository vets;

	@Autowired
	private VisitRepository visits;

	@PersistenceContext
	private EntityManager em;

	private Owner owner;

	private Vet vet;

	private Vet otherVet;

	@BeforeEach
	void setUp() {
		this.owner = this.owners.findById(OWNER_ID).orElseThrow();
		this.vet = this.vets.findById(1).orElseThrow();
		this.otherVet = this.vets.findById(2).orElseThrow();
	}

	private void book(Vet v, LocalDate date, LocalTime time) {
		this.service.schedule(this.owner, PET_ID, v, date, time, "checkup");
		this.em.flush();
	}

	private Visit find(LocalDate date, LocalTime time) {
		return this.owner.getPet(PET_ID)
			.getVisits()
			.stream()
			.filter(v -> date.equals(v.getDate()) && time.equals(v.getTime()))
			.findFirst()
			.orElseThrow();
	}

	@Test
	void scheduleStoresAppointmentOnPet() {
		book(this.vet, DAY, LocalTime.of(10, 0));

		Visit visit = find(DAY, LocalTime.of(10, 0));
		assertThat(visit.getId()).isNotNull();
		assertThat(visit.getVet().getId()).isEqualTo(this.vet.getId());
		assertThat(visit.getStatus()).isEqualTo(VisitStatus.SCHEDULED);
		assertThat(this.service.getDurationMinutes()).isEqualTo(30);
	}

	@Test
	void sameVetOverlappingWindowIsRejected() {
		book(this.vet, DAY, LocalTime.of(10, 0));

		for (LocalTime t : List.of(LocalTime.of(10, 0), LocalTime.of(10, 15), LocalTime.of(10, 29), LocalTime.of(9, 45),
				LocalTime.of(9, 31))) {
			assertThatThrownBy(() -> this.service.schedule(this.owner, PET_ID, this.vet, DAY, t, "x")).as("start %s", t)
				.isInstanceOf(SchedulingConflictException.class);
		}
	}

	@Test
	void adjacentWindowsAreAllowed() {
		book(this.vet, DAY, LocalTime.of(10, 0));

		book(this.vet, DAY, LocalTime.of(10, 30));
		book(this.vet, DAY, LocalTime.of(9, 30));

		assertThat(this.service.hasConflict(this.vet.getId(), DAY, LocalTime.of(11, 0), null)).isFalse();
	}

	@Test
	void otherVetOrOtherDayIsNotAConflict() {
		book(this.vet, DAY, LocalTime.of(10, 0));

		book(this.otherVet, DAY, LocalTime.of(10, 0));
		book(this.vet, DAY.plusDays(1), LocalTime.of(10, 0));
	}

	@Test
	void appointmentRunningPastMidnightBlocksNextDay() {
		book(this.vet, DAY, LocalTime.of(23, 45));

		assertThat(this.service.hasConflict(this.vet.getId(), DAY.plusDays(1), LocalTime.of(0, 10), null)).isTrue();
		assertThat(this.service.hasConflict(this.vet.getId(), DAY.plusDays(1), LocalTime.of(0, 15), null)).isFalse();
	}

	@Test
	void rescheduleMovesAppointmentAndFreesPreviousSlot() {
		book(this.vet, DAY, LocalTime.of(10, 0));
		Visit visit = find(DAY, LocalTime.of(10, 0));

		this.service.reschedule(visit.getId(), this.vet, DAY, LocalTime.of(14, 0), "checkup");
		this.em.flush();

		assertThat(visit.getTime()).isEqualTo(LocalTime.of(14, 0));
		assertThat(this.service.hasConflict(this.vet.getId(), DAY, LocalTime.of(10, 0), null)).isFalse();
		book(this.vet, DAY, LocalTime.of(10, 0));
	}

	@Test
	void rescheduleIntoBookedWindowIsRejected() {
		book(this.vet, DAY, LocalTime.of(10, 0));
		book(this.vet, DAY, LocalTime.of(12, 0));
		Visit visit = find(DAY, LocalTime.of(12, 0));

		assertThatThrownBy(() -> this.service.reschedule(visit.getId(), this.vet, DAY, LocalTime.of(10, 20), "x"))
			.isInstanceOf(SchedulingConflictException.class);
		assertThat(find(DAY, LocalTime.of(12, 0))).isSameAs(visit);
	}

	@Test
	void rescheduleMayOverlapItsOwnPreviousSlot() {
		book(this.vet, DAY, LocalTime.of(10, 0));
		Visit visit = find(DAY, LocalTime.of(10, 0));

		this.service.reschedule(visit.getId(), this.vet, DAY, LocalTime.of(10, 15), "checkup");

		assertThat(visit.getTime()).isEqualTo(LocalTime.of(10, 15));
	}

	@Test
	void rescheduleCanSwitchVet() {
		book(this.vet, DAY, LocalTime.of(10, 0));
		book(this.otherVet, DAY, LocalTime.of(11, 0));
		Visit visit = find(DAY, LocalTime.of(10, 0));

		this.service.reschedule(visit.getId(), this.otherVet, DAY, LocalTime.of(12, 0), "x");

		assertThat(visit.getVet().getId()).isEqualTo(this.otherVet.getId());
		assertThat(this.service.hasConflict(this.vet.getId(), DAY, LocalTime.of(10, 0), null)).isFalse();
		assertThatThrownBy(() -> this.service.reschedule(visit.getId(), this.otherVet, DAY, LocalTime.of(11, 10), "x"))
			.isInstanceOf(SchedulingConflictException.class);
	}

	@Test
	void cancelFreesSlotAndKeepsHistory() {
		book(this.vet, DAY, LocalTime.of(10, 0));
		Visit visit = find(DAY, LocalTime.of(10, 0));

		this.service.cancel(visit.getId());
		this.em.flush();

		assertThat(visit.isCancelled()).isTrue();
		assertThat(visit.isUpcoming(java.time.LocalDateTime.now())).isFalse();
		assertThat(this.visits.findById(visit.getId())).isPresent();
		book(this.vet, DAY, LocalTime.of(10, 0));
	}

	@Test
	void cancelledAppointmentCannotBeCancelledOrRescheduled() {
		book(this.vet, DAY, LocalTime.of(10, 0));
		Visit visit = find(DAY, LocalTime.of(10, 0));
		this.service.cancel(visit.getId());

		assertThatThrownBy(() -> this.service.cancel(visit.getId())).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> this.service.reschedule(visit.getId(), this.vet, DAY, LocalTime.of(15, 0), "x"))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void scheduleReturnsActiveAppointmentsInChronologicalOrder() {
		book(this.vet, DAY, LocalTime.of(15, 0));
		book(this.vet, DAY, LocalTime.of(9, 0));
		book(this.vet, DAY, LocalTime.of(11, 0));
		book(this.otherVet, DAY, LocalTime.of(10, 0));
		this.service.cancel(find(DAY, LocalTime.of(11, 0)).getId());
		this.em.flush();

		List<VetAppointment> schedule = this.visits.findSchedule(this.vet.getId(), DAY, VisitStatus.SCHEDULED);

		assertThat(schedule).extracting(VetAppointment::time).containsExactly(LocalTime.of(9, 0), LocalTime.of(15, 0));
		assertThat(schedule).extracting(VetAppointment::petName).containsOnly("Leo");
		assertThat(schedule).extracting(VetAppointment::ownerLastName).containsOnly("Franklin");
	}

}
