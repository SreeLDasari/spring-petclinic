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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

@Controller
class AppointmentController {

	private static final String VIEWS_APPOINTMENT_CREATE_OR_UPDATE_FORM = "appointments/createOrUpdateAppointmentForm";

	private final AppointmentService appointmentService;

	private final AppointmentRepository appointments;

	private final OwnerRepository owners;

	private final VetRepository vets;

	public AppointmentController(AppointmentService appointmentService, AppointmentRepository appointments,
			OwnerRepository owners, VetRepository vets) {
		this.appointmentService = appointmentService;
		this.appointments = appointments;
		this.owners = owners;
		this.vets = vets;
	}

	@InitBinder
	public void setAllowedFields(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id", "*.id");
	}

	@ModelAttribute("vets")
	public Collection<Vet> populateVets() {
		return this.vets.findAll();
	}

	@GetMapping("/owners/{ownerId}/pets/{petId}/appointments/new")
	public String initCreationForm(@PathVariable int ownerId, @PathVariable int petId, Model model) {
		Owner owner = this.owners.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException(
					"Pet with id " + petId + " not found for owner with id " + ownerId + ".");
		}

		model.addAttribute("owner", owner);
		model.addAttribute("pet", pet);
		model.addAttribute("appointmentForm", new AppointmentForm());
		model.addAttribute("formMode", "create");
		return VIEWS_APPOINTMENT_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/owners/{ownerId}/pets/{petId}/appointments")
	public String processCreationForm(@PathVariable int ownerId, @PathVariable int petId,
			@Valid @ModelAttribute("appointmentForm") AppointmentForm form, BindingResult result, Model model,
			RedirectAttributes redirectAttributes) {

		if (result.hasErrors()) {
			addContextModel(ownerId, petId, model);
			model.addAttribute("formMode", "create");
			return VIEWS_APPOINTMENT_CREATE_OR_UPDATE_FORM;
		}

		LocalDateTime dateTime = LocalDateTime.of(form.getDate(), form.getTime());
		try {
			this.appointmentService.createAppointment(ownerId, petId, form.getVetId(), dateTime);
		}
		catch (AppointmentSlotUnavailableException ex) {
			result.reject("slotUnavailable", ex.getMessage());
			addContextModel(ownerId, petId, model);
			model.addAttribute("formMode", "create");
			return VIEWS_APPOINTMENT_CREATE_OR_UPDATE_FORM;
		}
		catch (IllegalArgumentException ex) {
			result.reject("invalid", ex.getMessage());
			addContextModel(ownerId, petId, model);
			model.addAttribute("formMode", "create");
			return VIEWS_APPOINTMENT_CREATE_OR_UPDATE_FORM;
		}

		redirectAttributes.addFlashAttribute("message", "Appointment scheduled");
		return "redirect:/owners/{ownerId}";
	}

	@GetMapping("/appointments/{appointmentId}/edit")
	public String initUpdateForm(@PathVariable int appointmentId, Model model) {
		Appointment appointment = this.appointments.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found with id: " + appointmentId));
		Owner owner = appointment.getPet().getOwner();
		Pet pet = appointment.getPet();

		AppointmentForm form = new AppointmentForm();
		form.setVetId(appointment.getVet().getId());
		form.setDate(appointment.getAppointmentDateTime().toLocalDate());
		form.setTime(appointment.getAppointmentDateTime().toLocalTime());

		model.addAttribute("owner", owner);
		model.addAttribute("pet", pet);
		model.addAttribute("appointment", appointment);
		model.addAttribute("appointmentForm", form);
		model.addAttribute("formMode", "edit");
		return VIEWS_APPOINTMENT_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/appointments/{appointmentId}")
	public String processUpdateForm(@PathVariable int appointmentId,
			@Valid @ModelAttribute("appointmentForm") AppointmentForm form, BindingResult result, Model model,
			RedirectAttributes redirectAttributes) {

		Appointment appointment = this.appointments.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found with id: " + appointmentId));
		Owner owner = appointment.getPet().getOwner();
		Pet pet = appointment.getPet();

		if (result.hasErrors()) {
			model.addAttribute("owner", owner);
			model.addAttribute("pet", pet);
			model.addAttribute("appointment", appointment);
			model.addAttribute("formMode", "edit");
			return VIEWS_APPOINTMENT_CREATE_OR_UPDATE_FORM;
		}

		LocalDateTime newDateTime = LocalDateTime.of(form.getDate(), form.getTime());
		try {
			this.appointmentService.rescheduleAppointment(appointmentId, newDateTime);
		}
		catch (AppointmentSlotUnavailableException ex) {
			result.reject("slotUnavailable", ex.getMessage());
			model.addAttribute("owner", owner);
			model.addAttribute("pet", pet);
			model.addAttribute("appointment", appointment);
			model.addAttribute("formMode", "edit");
			return VIEWS_APPOINTMENT_CREATE_OR_UPDATE_FORM;
		}
		catch (IllegalArgumentException ex) {
			result.reject("invalid", ex.getMessage());
			model.addAttribute("owner", owner);
			model.addAttribute("pet", pet);
			model.addAttribute("appointment", appointment);
			model.addAttribute("formMode", "edit");
			return VIEWS_APPOINTMENT_CREATE_OR_UPDATE_FORM;
		}

		redirectAttributes.addFlashAttribute("message", "Appointment rescheduled");
		return "redirect:/owners/" + owner.getId();
	}

	@GetMapping("/appointments/{appointmentId}/cancel")
	public String initCancelForm(@PathVariable int appointmentId, Model model) {
		Appointment appointment = this.appointments.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found with id: " + appointmentId));
		model.addAttribute("appointment", appointment);
		model.addAttribute("owner", appointment.getPet().getOwner());
		model.addAttribute("pet", appointment.getPet());
		return "appointments/cancelAppointment";
	}

	@PostMapping("/appointments/{appointmentId}/cancel")
	public String processCancel(@PathVariable int appointmentId, RedirectAttributes redirectAttributes) {
		Appointment appointment = this.appointments.findById(appointmentId).orElse(null);
		if (appointment == null) {
			redirectAttributes.addFlashAttribute("message", "Appointment already cancelled");
			return "redirect:/owners";
		}
		Integer ownerId = appointment.getPet().getOwner().getId();
		this.appointmentService.cancelAppointment(appointmentId);
		redirectAttributes.addFlashAttribute("message", "Appointment cancelled");
		return "redirect:/owners/" + ownerId;
	}

	private void addContextModel(int ownerId, int petId, Model model) {
		Owner owner = this.owners.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException(
					"Pet with id " + petId + " not found for owner with id " + ownerId + ".");
		}
		model.addAttribute("owner", owner);
		model.addAttribute("pet", pet);
	}

}
