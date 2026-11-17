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
import java.time.LocalTime;
import java.util.Collection;
import java.util.Optional;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

@Controller
class AppointmentController {

	private static final String VIEW_APPOINTMENTS_FORM = "appointments/createOrUpdateAppointmentForm";

	private final AppointmentService appointmentService;

	private final OwnerRepository owners;

	private final VetRepository vets;

	AppointmentController(AppointmentService appointmentService, OwnerRepository owners, VetRepository vets) {
		this.appointmentService = appointmentService;
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
		Owner owner = loadOwner(ownerId);
		Pet pet = loadPet(owner, petId);

		AppointmentForm form = new AppointmentForm();
		form.setDurationMinutes(30);

		model.addAttribute("owner", owner);
		model.addAttribute("pet", pet);
		model.addAttribute("appointmentForm", form);
		model.addAttribute("isNew", true);
		return VIEW_APPOINTMENTS_FORM;
	}

	@PostMapping("/owners/{ownerId}/pets/{petId}/appointments/new")
	public String processCreationForm(@PathVariable int ownerId, @PathVariable int petId,
			@Valid @ModelAttribute("appointmentForm") AppointmentForm form, BindingResult result, Model model,
			RedirectAttributes redirectAttributes) {

		Owner owner = loadOwner(ownerId);
		Pet pet = loadPet(owner, petId);

		if (result.hasErrors()) {
			model.addAttribute("owner", owner);
			model.addAttribute("pet", pet);
			model.addAttribute("isNew", true);
			return VIEW_APPOINTMENTS_FORM;
		}

		try {
			this.appointmentService.scheduleAppointment(ownerId, petId, form.getVetId(), form.getStartDateTime(),
					form.getDurationMinutes(), form.getReason());
		}
		catch (VetDoubleBookedException ex) {
			result.reject("vetBooked", ex.getMessage());
			model.addAttribute("owner", owner);
			model.addAttribute("pet", pet);
			model.addAttribute("isNew", true);
			return VIEW_APPOINTMENTS_FORM;
		}

		dredirectAttributes.addFlashAttribute("message", "Appointment scheduled");
		return "redirect:/appointments";
	}

	@GetMapping("/appointments")
	public String listAppointments(@RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Integer vetId, @RequestParam(required = false) AppointmentStatus status,
			Model model) {

		LocalDate fromDate = from != null ? from : LocalDate.now();
		LocalDate toDate = to != null ? to : LocalDate.now().plusDays(30);
		AppointmentStatus effectiveStatus = status != null ? status : AppointmentStatus.SCHEDULED;

		LocalDateTime fromDateTime = fromDate.atStartOfDay();
		LocalDateTime toDateTime = toDate.atTime(LocalTime.MAX);

		Collection<Appointment> appointments = this.appointmentService.findAppointments(fromDateTime, toDateTime, vetId,
				effectiveStatus);

		model.addAttribute("appointments", appointments);
		model.addAttribute("from", fromDate);
		model.addAttribute("to", toDate);
		model.addAttribute("vetId", vetId);
		model.addAttribute("status", effectiveStatus);
		model.addAttribute("statuses", AppointmentStatus.values());
		return "appointments/appointmentsList";
	}

	@GetMapping("/appointments/{appointmentId}/edit")
	public String initUpdateForm(@PathVariable int appointmentId, Model model) {
		Appointment appointment = loadAppointment(appointmentId);

		AppointmentForm form = new AppointmentForm();
		form.setVetId(appointment.getVet().getId());
		form.setStartDateTime(appointment.getStartDateTime());
		form.setDurationMinutes(appointment.getDurationMinutes());
		form.setReason(appointment.getReason());

		model.addAttribute("appointment", appointment);
		model.addAttribute("appointmentForm", form);
		model.addAttribute("isNew", false);
		return VIEW_APPOINTMENTS_FORM;
	}

	@PostMapping("/appointments/{appointmentId}/edit")
	public String processUpdateForm(@PathVariable int appointmentId,
			@Valid @ModelAttribute("appointmentForm") AppointmentForm form, BindingResult result, Model model,
			RedirectAttributes redirectAttributes) {

		Appointment appointment = loadAppointment(appointmentId);

		if (result.hasErrors()) {
			model.addAttribute("appointment", appointment);
			model.addAttribute("isNew", false);
			return VIEW_APPOINTMENTS_FORM;
		}

		try {
			this.appointmentService.rescheduleAppointment(appointmentId, form.getVetId(), form.getStartDateTime(),
					form.getDurationMinutes(), form.getReason());
		}
		catch (VetDoubleBookedException ex) {
			result.reject("vetBooked", ex.getMessage());
			model.addAttribute("appointment", appointment);
			model.addAttribute("isNew", false);
			return VIEW_APPOINTMENTS_FORM;
		}

		redirectAttributes.addFlashAttribute("message", "Appointment updated");
		return "redirect:/appointments";
	}

	@PostMapping("/appointments/{appointmentId}/cancel")
	public String cancelAppointment(@PathVariable int appointmentId, RedirectAttributes redirectAttributes) {
		this.appointmentService.cancelAppointment(appointmentId);
		redirectAttributes.addFlashAttribute("message", "Appointment cancelled");
		return "redirect:/appointments";
	}

	private Owner loadOwner(int ownerId) {
		Optional<Owner> optionalOwner = this.owners.findById(ownerId);
		return optionalOwner.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));
	}

	private Pet loadPet(Owner owner, int petId) {
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException("Pet with id " + petId + " not found for owner with id " + owner.getId());
		}
		return pet;
	}

	private Appointment loadAppointment(int appointmentId) {
		return this.appointmentService.findAppointments(LocalDateTime.MIN, LocalDateTime.MAX, null,
				AppointmentStatus.SCHEDULED)
			.stream()
			.filter((a) -> a.getId() == appointmentId)
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found with id: " + appointmentId));
	}

}
