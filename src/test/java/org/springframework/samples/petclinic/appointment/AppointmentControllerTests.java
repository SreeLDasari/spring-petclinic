package org.springframework.samples.petclinic.appointment;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AppointmentController.class)
@DisabledInNativeImage
@DisabledInAotMode
class AppointmentControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AppointmentService appointmentService;

	@MockitoBean
	private AppointmentRepository appointmentRepository;

	private Owner owner;

	private Pet pet;

	private Vet vet;

	@BeforeEach
	void setup() {
		this.owner = new Owner();
		this.owner.setId(1);
		this.owner.setFirstName("George");
		this.owner.setLastName("Franklin");

		this.pet = new Pet();
		this.pet.setId(2);
		this.pet.setName("Leo");
		this.owner.addPet(this.pet);

		this.vet = new Vet();
		this.vet.setId(3);
		this.vet.setFirstName("James");
		this.vet.setLastName("Carter");

		given(this.appointmentService.findAllVets()).willReturn(List.of(this.vet));
		given(this.appointmentService.findOwner(1)).willReturn(this.owner);
		given(this.appointmentService.findPet(this.owner, 2)).willReturn(this.pet);
		given(this.appointmentService.getAvailableSlots(3, LocalDate.now(), null)).willReturn(List.of());
	}

	@Test
	void initCreateForm() throws Exception {
		mockMvc.perform(get("/owners/{ownerId}/pets/{petId}/appointments/new", 1, 2))
			.andExpect(status().isOk())
			.andExpect(model().attributeExists("appointmentForm"))
			.andExpect(view().name("appointments/appointmentForm"));
	}

	@Test
	void createAppointmentConflictShowsGlobalErrorAndDoesNotSave() throws Exception {
		given(this.appointmentService.getAvailableSlots(3, LocalDate.of(2026, 1, 1), null)).willReturn(List.of());
		given(this.appointmentService.createAppointment(org.mockito.ArgumentMatchers.any(Appointment.class)))
			.willThrow(new SlotAlreadyBookedException());

		mockMvc.perform(post("/owners/{ownerId}/pets/{petId}/appointments", 1, 2).param("appointmentDate", "2026-01-01")
			.param("vetId", "3")
			.param("appointmentTime", "09:00"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasGlobalErrors("appointmentForm"))
			.andExpect(view().name("appointments/appointmentForm"));

		verify(this.appointmentService, never()).rescheduleAppointment(org.mockito.ArgumentMatchers.anyInt(),
				org.mockito.ArgumentMatchers.any(Appointment.class));
	}

}
