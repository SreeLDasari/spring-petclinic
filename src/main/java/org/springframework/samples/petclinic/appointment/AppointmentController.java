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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.Vet;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

@Controller
class AppointmentController {

	private final OwnerRepository owners;

	private final VetRepository vets;

	private final AppointmentService appointmentService;

	public AppointmentController(OwnerRepository owners, VetRepository vets, AppointmentService appointmentService) {
		this.owners = owners;
		this.vets = vets;
		this.appointmentService = appointmentService;
	}

	@InitBinder
	public void setAllowedFields(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id", "*.id");
	}

	@ModelAttribute("owner")
	public Owner findOwner(@PathVariable("ownerId") int ownerId) {
		return this.owners.findById(ownerId)
				.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));
	}

	@ModelAttribute("pet")
	public Pet findPet(@ModelAttribute("owner") Owner owner, @PathVariable("petId") int petId) {
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException(
					"Pet with id " + petId + " not found for owner with id " + owner.getId() + ".");
		}
		return pet;
	}

	@ModelAttribute("vets")
	public Collection<Vet> populateVets() {
		return this.vets.findAll();
	}

	@GetMapping("/owners/{ownerId}/pets/{petId}/appointments/new")
	public String initScheduleForm(@ModelAttribute("pet") Pet pet, Model model) {
		model.addAttribute("appointmentForm", new AppointmentForm());
		return "appointments/createOrUpdateAppointmentForm";
	}

	@PostMapping("/owners/{ownerId}/pets/{petId}/appointments")
	public String processScheduleForm(@ModelAttribute("pet") Pet pet, @Valid AppointmentForm appointmentForm,
			BindingResult result, RedirectAttributes redirectAttributes) {

		if (result.hasErrors()) {
			return "appointments/createOrUpdateAppointmentForm";
		}

		LocalDateTime start = LocalDateTime.of(appointmentForm.getDate(), appointmentForm.getTime());
		try {
			this.appointmentService.scheduleAppointment(pet.getId(), appointmentForm.getVetId(), start,
					appointmentForm.getDurationMinutes(), appointmentForm.getReason());
		}
		catch (AppointmentConflictException ex) {
			result.reject("appointment.conflict", ex.getMessage());
			return "appointments/createOrUpdateAppointmentForm";
		}

		redirectAttributes.addFlashAttribute("message", "Appointment scheduled");
		return "redirect:/owners/{ownerId}";
	}

	@GetMapping("/vets/{vetId}/agenda")
	public String showVetAgenda(@PathVariable("vetId") int vetId,
			@RequestParam(value = "date", required = false) LocalDate date, Model model) {
		model.addAttribute("date", date != null ? date : LocalDate.now());
		model.addAttribute("appointments", this.appointmentService.getVetAgenda(vetId, date));
		model.addAttribute("vetId", vetId);
		return "appointments/vetAgenda";
	}

	@PostMapping("/appointments/{appointmentId}/cancel")
	public String cancel(@PathVariable("appointmentId") int appointmentId,
			@RequestParam(value = "vetId", required = false) Integer vetId,
			@RequestParam(value = "date", required = false) LocalDate date, RedirectAttributes redirectAttributes) {
		this.appointmentService.cancelAppointment(appointmentId);
		redirectAttributes.addFlashAttribute("message", "Appointment cancelled");
		if (vetId != null) {
			if (date != null) {
				redirectAttributes.addAttribute("date", date);
			}
			return "redirect:/vets/" + vetId + "/agenda";
		}
		return "redirect:/";
	}

}
