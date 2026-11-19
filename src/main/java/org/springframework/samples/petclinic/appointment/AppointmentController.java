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
import java.util.List;
import java.util.Map;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.validation.Valid;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class AppointmentController {

	private static final String VIEWS_APPOINTMENT_CREATE_OR_UPDATE_FORM = "appointments/createOrUpdateAppointmentForm";

	private final AppointmentService appointmentService;

	private final OwnerRepository owners;

	public AppointmentController(AppointmentService appointmentService, OwnerRepository owners) {
		this.appointmentService = appointmentService;
		this.owners = owners;
	}

	@InitBinder
	public void setAllowedFields(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id", "*.id");
	}

	@ModelAttribute("appointmentDurations")
	public List<Integer> appointmentDurations() {
		return List.of(15, 30, 60);
	}

	@ModelAttribute("minAppointmentDate")
	public LocalDate minAppointmentDate() {
		return LocalDate.now();
	}

	@ModelAttribute("defaultStartTime")
	public LocalTime defaultStartTime() {
		return LocalTime.of(9, 0);
	}

	@ModelAttribute("vets")
	public List<Vet> vets() {
		return this.appointmentService.findAllVets().stream().toList();
	}

	@ModelAttribute("appointmentForm")
	public AppointmentForm appointmentForm() {
		AppointmentForm form = new AppointmentForm();
		form.setDurationMinutes(30);
		return form;
	}

	@ModelAttribute("owner")
	public Owner findOwner(@PathVariable("ownerId") int ownerId) {
		return this.owners.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));
	}

	@ModelAttribute("pet")
	public Pet findPet(@PathVariable("ownerId") int ownerId, @PathVariable("petId") int petId,
			Map<String, Object> model) {
		Owner owner = (Owner) model.get("owner");
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException(
					"Pet with id " + petId + " not found for owner with id " + ownerId + ".");
		}
		return pet;
	}

	@GetMapping("/owners/{ownerId}/pets/{petId}/appointments/new")
	public String initNewAppointmentForm(@ModelAttribute Owner owner, @ModelAttribute Pet pet, Model model) {
		AppointmentForm form = (AppointmentForm) model.getAttribute("appointmentForm");
		if (form.getAppointmentDate() == null) {
			form.setAppointmentDate(LocalDate.now());
		}
		if (form.getStartTime() == null) {
			form.setStartTime(LocalTime.of(9, 0));
		}
		return VIEWS_APPOINTMENT_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/owners/{ownerId}/pets/{petId}/appointments/new")
	public String processNewAppointmentForm(@PathVariable("ownerId") int ownerId, @PathVariable("petId") int petId,
			@Valid AppointmentForm appointmentForm, BindingResult result, RedirectAttributes redirectAttributes) {

		if (appointmentForm.getAppointmentDate() != null
				&& appointmentForm.getAppointmentDate().isBefore(LocalDate.now())) {
			result.rejectValue("appointmentDate", "invalid", "must be today or in the future");
		}

		if (result.hasErrors()) {
			return VIEWS_APPOINTMENT_CREATE_OR_UPDATE_FORM;
		}

		try {
			this.appointmentService.scheduleAppointment(ownerId, petId, appointmentForm.getVetId(),
					appointmentForm.getAppointmentDate(), appointmentForm.getStartTime(),
					appointmentForm.getDurationMinutes(), appointmentForm.getReason());
		}
		catch (AppointmentConflictException ex) {
			result.reject("appointment.conflict", ex.getMessage());
			return VIEWS_APPOINTMENT_CREATE_OR_UPDATE_FORM;
		}
		catch (IllegalArgumentException ex) {
			result.reject("appointment.invalid", ex.getMessage());
			return VIEWS_APPOINTMENT_CREATE_OR_UPDATE_FORM;
		}

		redirectAttributes.addFlashAttribute("message", "Appointment scheduled");
		return "redirect:/owners/{ownerId}";
	}

}
