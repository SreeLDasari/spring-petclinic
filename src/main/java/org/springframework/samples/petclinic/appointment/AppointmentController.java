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

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/owners/{ownerId}/pets/{petId}/appointments")
class AppointmentController {

	static final String VIEW_CREATE_OR_UPDATE_APPOINTMENT_FORM = "appointments/createOrUpdateAppointmentForm";

	private final AppointmentService appointmentService;

	private final OwnerRepository owners;

	private final VetRepository vets;

	private final Clock clock;

	AppointmentController(AppointmentService appointmentService, OwnerRepository owners, VetRepository vets, Clock clock) {
		this.appointmentService = appointmentService;
		this.owners = owners;
		this.vets = vets;
		this.clock = clock;
	}

	@InitBinder("appointment")
	void initAppointmentBinder(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id", "pet", "vet", "status");
	}

	@ModelAttribute("owner")
	Owner findOwner(@PathVariable("ownerId") int ownerId) {
		return this.owners.findById(ownerId).orElseThrow();
	}

	@ModelAttribute("pet")
	Pet findPet(@ModelAttribute("owner") Owner owner, @PathVariable("petId") int petId) {
		return owner.getPet(petId);
	}

	@ModelAttribute("vets")
	Object populateVets() {
		return this.vets.findAll();
	}

	@GetMapping("/new")
	String initNewAppointmentForm(@ModelAttribute("pet") Pet pet, Model model) {
		Appointment appointment = new Appointment();
		appointment.setPet(pet);
		model.addAttribute("appointment", appointment);
		return VIEW_CREATE_OR_UPDATE_APPOINTMENT_FORM;
	}

	@PostMapping("/new")
	String processNewAppointmentForm(@ModelAttribute("pet") Pet pet, @Valid Appointment appointment,
			BindingResult result, Model model) {

		appointment.setPet(pet);
		if (appointment.getStartDateTime() != null
				&& !appointment.getStartDateTime().isAfter(LocalDateTime.now(this.clock))) {
			result.rejectValue("startDateTime", "invalid", "must be in the future");
		}

		if (result.hasErrors()) {
			return VIEW_CREATE_OR_UPDATE_APPOINTMENT_FORM;
		}

		Integer vetId = appointment.getVet() != null ? appointment.getVet().getId() : null;
		try {
			this.appointmentService.scheduleAppointment(pet.getId(), vetId, appointment.getStartDateTime(),
					appointment.getDurationMinutes(), appointment.getReason(), appointment.getNotes());
		}
		catch (AppointmentConflictException ex) {
			result.reject("conflict", ex.getMessage());
			model.addAttribute("appointment", appointment);
			return VIEW_CREATE_OR_UPDATE_APPOINTMENT_FORM;
		}

		return "redirect:/owners/{ownerId}";
	}

	@PostMapping("/{appointmentId}/cancel")
	String cancelAppointment(@PathVariable("appointmentId") int appointmentId) {
		this.appointmentService.cancelAppointment(appointmentId);
		return "redirect:/owners/{ownerId}";
	}

	@GetMapping("/{appointmentId}/complete")
	String initCompleteAppointmentForm(@PathVariable("appointmentId") int appointmentId, Model model) {
		Appointment appointment = this.appointmentService.findById(appointmentId).orElseThrow();
		model.addAttribute("appointment", appointment);
		return "appointments/completeAppointmentForm";
	}

	@PostMapping("/{appointmentId}/complete")
	String processCompleteAppointmentForm(@PathVariable("appointmentId") int appointmentId,
			@ModelAttribute("notes") String notes, Model model) {
		try {
			this.appointmentService.completeAppointment(appointmentId, notes);
		}
		catch (IllegalArgumentException ex) {
			model.addAttribute("completeError", ex.getMessage());
			Appointment appointment = this.appointmentService.findById(appointmentId).orElseThrow();
			model.addAttribute("appointment", appointment);
			return "appointments/completeAppointmentForm";
		}
		return "redirect:/owners/{ownerId}";
	}

}
