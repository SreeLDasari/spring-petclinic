package org.springframework.samples.petclinic.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.vet.VetRepository;

class AppointmentServiceTests {

	@Test
	void availableSlotsExcludeBookedButIncludeCancelled() {
		AppointmentRepository appointmentRepository = Mockito.mock(AppointmentRepository.class);
		OwnerRepository ownerRepository = Mockito.mock(OwnerRepository.class);
		VetRepository vetRepository = Mockito.mock(VetRepository.class);

		AppointmentService service = new AppointmentService(appointmentRepository, ownerRepository, vetRepository);

		LocalDate date = LocalDate.now();
		int vetId = 1;

		Appointment booked = new Appointment();
		booked.setAppointmentDate(date);
		booked.setAppointmentTime(LocalTime.of(9, 0));
		booked.setStatus(AppointmentStatus.BOOKED);
		booked.setId(10);

		Appointment cancelled = new Appointment();
		cancelled.setAppointmentDate(date);
		cancelled.setAppointmentTime(LocalTime.of(9, 30));
		cancelled.setStatus(AppointmentStatus.CANCELLED);
		cancelled.setId(11);

		Mockito.when(appointmentRepository.findByVetIdAndAppointmentDateOrderByAppointmentTimeAsc(vetId, date))
			.thenReturn(List.of(booked, cancelled));

		List<LocalTime> available = service.getAvailableSlots(vetId, date, null);
		assertThat(available).doesNotContain(LocalTime.of(9, 0));
		assertThat(available).contains(LocalTime.of(9, 30));
	}

}
