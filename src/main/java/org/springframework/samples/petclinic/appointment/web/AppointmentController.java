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

package org.springframework.samples.petclinic.appointment.web;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.appointment.AppointmentConflictException;
import org.springframework.samples.petclinic.appointment.AppointmentNotFoundException;
import org.springframework.samples.petclinic.appointment.AppointmentService;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping
public class AppointmentController {

	private static final String VIEWS_APPOINTMENT_CREATE_FORM = "appointments/appointmentForm";

	private static final String VIEWS_APPOINTMENT_EDIT_FORM = "appointments/appointmentEdit";

	private final AppointmentService appointmentService;

	private final OwnerRepository ownerRepository;

	private final VetRepository vetRepository;

	public AppointmentController(AppointmentService appointmentService, OwnerRepository ownerRepository,
			VetRepository vetRepository) {
		this.appointmentService = appointmentService;
		this.ownerRepository = ownerRepository;
		this.vetRepository = vetRepository;
	}

	@ModelAttribute("vets")
	public List<Vet> populateVets() {
		return this.vetRepository.findAll();
	}

	@GetMapping("/owners/{ownerId}/pets/{petId}/appointments/new")
	public String initNewAppointmentForm(@PathVariable("ownerId") int ownerId, @PathVariable("petId") int petId,
			Model model) {
		Owner owner = this.ownerRepository.findById(ownerId)
				.orElseThrow(() -> new IllegalArgumentException("Owner not found: " + ownerId));
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException("Pet not found: " + petId);
		}

		AppointmentForm form = new AppointmentForm();
		form.setAppointmentDate(LocalDate.now());

		model.addAttribute("owner", owner);
		model.addAttribute("pet", pet);
		model.addAttribute("appointmentForm", form);
		model.addAttribute("availableSlots", List.of());
		return VIEWS_APPOINTMENT_CREATE_FORM;
	}

	@PostMapping("/owners/{ownerId}/pets/{petId}/appointments/new")
	public String processNewAppointmentForm(@PathVariable("ownerId") int ownerId, @PathVariable("petId") int petId,
			@Valid @ModelAttribute("appointmentForm") AppointmentForm form, BindingResult result, Model model,
			RedirectAttributes redirectAttributes) {

		Owner owner = this.ownerRepository.findById(ownerId)
				.orElseThrow(() -> new IllegalArgumentException("Owner not found: " + ownerId));
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException("Pet not found: " + petId);
		}

		model.addAttribute("owner", owner);
		model.addAttribute("pet", pet);

		if (result.hasErrors()) {
			// Re-populate available slots even when validation fails so the form can be re-rendered properly.
			model.addAttribute("availableSlots",
					this.appointmentService.getAvailableSlots(form.getVetId(), form.getAppointmentDate()));
			return VIEWS_APPOINTMENT_CREATE_FORM;
		}

		// Populate available slots after validation succeeds as well (in case booking fails and we need to re-render).
		model.addAttribute("availableSlots",
				this.appointmentService.getAvailableSlots(form.getVetId(), form.getAppointmentDate()));

		try {
			Appointment appointment = this.appointmentService.book(ownerId, petId, form.getVetId(),
					form.getAppointmentDate(), form.getStartTime());
			redirectAttributes.addFlashAttribute("message",
					"Appointment booked for " + appointment.getAppointmentDate() + " at " + appointment.getStartTime());
			return "redirect:/owners/{ownerId}";
		}
		catch (AppointmentConflictException ex) {
			result.reject("conflict", ex.getMessage());
			return VIEWS_APPOINTMENT_CREATE_FORM;
		}
		catch (IllegalArgumentException ex) {
			result.reject("invalid", ex.getMessage());
			return VIEWS_APPOINTMENT_CREATE_FORM;
		}
	}

	@GetMapping("/appointments/{appointmentId}/edit")
	public String initRescheduleForm(@PathVariable("appointmentId") int appointmentId, Model model) {
		Appointment appointment = this.appointmentService.getAppointment(appointmentId);

		AppointmentRescheduleForm form = new AppointmentRescheduleForm();
		form.setAppointmentDate(appointment.getAppointmentDate());
		form.setStartTime(appointment.getStartTime());

		model.addAttribute("appointment", appointment);
		model.addAttribute("appointmentRescheduleForm", form);
		model.addAttribute("availableSlots",
				this.appointmentService.getAvailableSlots(appointment.getVet().getId(), form.getAppointmentDate()));
		return VIEWS_APPOINTMENT_EDIT_FORM;
	}

	@PostMapping("/appointments/{appointmentId}/edit")
	public String processRescheduleForm(@PathVariable("appointmentId") int appointmentId,
			@Valid @ModelAttribute("appointmentRescheduleForm") AppointmentRescheduleForm form, BindingResult result,
			Model model, RedirectAttributes redirectAttributes) {

		Appointment appointment = this.appointmentService.getAppointment(appointmentId);
		model.addAttribute("appointment", appointment);

		// Always populate slots based on the appointment's vet and the currently selected date
		// (so inputs are preserved when validation fails).
		model.addAttribute("availableSlots",
				this.appointmentService.getAvailableSlots(appointment.getVet().getId(), form.getAppointmentDate()));

		if (result.hasErrors()) {
			return VIEWS_APPOINTMENT_EDIT_FORM;
		}

		try {
			Appointment updated = this.appointmentService.reschedule(appointmentId, form.getAppointmentDate(),
					form.getStartTime());
			redirectAttributes.addFlashAttribute("message", "Appointment rescheduled.");
			return "redirect:/owners/" + updated.getOwner().getId();
		}
		catch (AppointmentConflictException ex) {
			result.reject("conflict", ex.getMessage());
			return VIEWS_APPOINTMENT_EDIT_FORM;
		}
		catch (AppointmentNotFoundException ex) {
			result.reject("notFound", ex.getMessage());
			return VIEWS_APPOINTMENT_EDIT_FORM;
		}
		catch (IllegalArgumentException ex) {
			result.reject("invalid", ex.getMessage());
			return VIEWS_APPOINTMENT_EDIT_FORM;
		}
	}

	@PostMapping("/appointments/{appointmentId}/cancel")
	public String cancel(@PathVariable("appointmentId") int appointmentId, RedirectAttributes redirectAttributes) {
		Appointment cancelled = this.appointmentService.cancel(appointmentId);
		redirectAttributes.addFlashAttribute("message", "Appointment cancelled.");
		return "redirect:/owners/" + cancelled.getOwner().getId();
	}

}
