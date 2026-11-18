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

import org.springframework.samples.petclinic.owner.Owner;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

@Controller
@RequestMapping
class AppointmentController {

	private static final String VIEWS_APPOINTMENT_FORM = "appointments/appointmentForm";

	private final AppointmentService appointmentService;

	private final AppointmentRepository appointmentRepository;

	public AppointmentController(AppointmentService appointmentService, AppointmentRepository appointmentRepository) {
		this.appointmentService = appointmentService;
		this.appointmentRepository = appointmentRepository;
	}

	@InitBinder("appointmentForm")
	public void initAppointmentFormBinder(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id", "*.id");
	}

	@ModelAttribute("vets")
	public List<Vet> populateVets() {
		return this.appointmentService.findAllVets();
	}

	@GetMapping("/owners/{ownerId}/pets/{petId}/appointments/new")
	public String initCreateForm(@PathVariable("ownerId") int ownerId, @PathVariable("petId") int petId,
			@RequestParam(name = "appointmentDate", required = false) LocalDate appointmentDate,
			@RequestParam(name = "vetId", required = false) Integer vetId,
			Model model) {

		Owner owner = this.appointmentService.findOwner(ownerId);
		Pet pet = this.appointmentService.findPet(owner, petId);

		AppointmentForm form = new AppointmentForm();
		form.setAppointmentDate(appointmentDate != null ? appointmentDate : LocalDate.now());
		form.setVetId(vetId);

		model.addAttribute("owner", owner);
		model.addAttribute("pet", pet);
		model.addAttribute("appointmentForm", form);
		populateAvailableSlots(model, vetId, form.getAppointmentDate(), null);
		return VIEWS_APPOINTMENT_FORM;
	}

	@PostMapping("/owners/{ownerId}/pets/{petId}/appointments")
	public String processCreateForm(@PathVariable("ownerId") int ownerId, @PathVariable("petId") int petId,
			@Valid @ModelAttribute("appointmentForm") AppointmentForm form, BindingResult result, Model model,
			RedirectAttributes redirectAttributes) {

		Owner owner = this.appointmentService.findOwner(ownerId);
		Pet pet = this.appointmentService.findPet(owner, petId);

		model.addAttribute("owner", owner);
		model.addAttribute("pet", pet);

		populateAvailableSlots(model, form.getVetId(), form.getAppointmentDate(), null);

		if (result.hasErrors()) {
			return VIEWS_APPOINTMENT_FORM;
		}

		Vet vet = resolveVet(form.getVetId());
		Appointment appointment = new Appointment();
		appointment.setPet(pet);
		appointment.setVet(vet);
		appointment.setAppointmentDate(form.getAppointmentDate());
		appointment.setAppointmentTime(form.getAppointmentTime());
		appointment.setReason(form.getReason());

		try {
			this.appointmentService.createAppointment(appointment);
		}
		catch (SlotAlreadyBookedException ex) {
			result.reject("slotAlreadyBooked", ex.getMessage());
			return VIEWS_APPOINTMENT_FORM;
		}

		redirectAttributes.addFlashAttribute("message", "Appointment booked");
		return "redirect:/owners/" + ownerId;
	}

	@GetMapping("/appointments/{appointmentId}/edit")
	public String initUpdateForm(@PathVariable("appointmentId") int appointmentId,
			@RequestParam(name = "appointmentDate", required = false) LocalDate appointmentDate,
			@RequestParam(name = "vetId", required = false) Integer vetId, Model model) {

		Appointment appointment = this.appointmentService.findById(appointmentId);
		Owner owner = appointment.getPet().getOwner();
		Pet pet = appointment.getPet();

		AppointmentForm form = new AppointmentForm();
		form.setAppointmentDate(appointmentDate != null ? appointmentDate : appointment.getAppointmentDate());
		form.setVetId(vetId != null ? vetId : appointment.getVet().getId());
		form.setAppointmentTime(appointment.getAppointmentTime());
		form.setReason(appointment.getReason());

		model.addAttribute("appointment", appointment);
		model.addAttribute("owner", owner);
		model.addAttribute("pet", pet);
		model.addAttribute("appointmentForm", form);
		populateAvailableSlots(model, form.getVetId(), form.getAppointmentDate(), appointment.getId());
		return VIEWS_APPOINTMENT_FORM;
	}

	@PostMapping("/appointments/{appointmentId}")
	public String processUpdateForm(@PathVariable("appointmentId") int appointmentId,
			@Valid @ModelAttribute("appointmentForm") AppointmentForm form, BindingResult result, Model model,
			RedirectAttributes redirectAttributes) {

		Appointment existing = this.appointmentService.findById(appointmentId);
		Owner owner = existing.getPet().getOwner();
		Pet pet = existing.getPet();

		model.addAttribute("appointment", existing);
		model.addAttribute("owner", owner);
		model.addAttribute("pet", pet);

		populateAvailableSlots(model, form.getVetId(), form.getAppointmentDate(), existing.getId());

		if (result.hasErrors()) {
			return VIEWS_APPOINTMENT_FORM;
		}

		Vet vet = resolveVet(form.getVetId());
		Appointment updated = new Appointment();
		updated.setPet(pet);
		updated.setVet(vet);
		updated.setAppointmentDate(form.getAppointmentDate());
		updated.setAppointmentTime(form.getAppointmentTime());
		updated.setReason(form.getReason());

		try {
			this.appointmentService.rescheduleAppointment(appointmentId, updated);
		}
		catch (SlotAlreadyBookedException ex) {
			result.reject("slotAlreadyBooked", ex.getMessage());
			return VIEWS_APPOINTMENT_FORM;
		}

		redirectAttributes.addFlashAttribute("message", "Appointment updated");
		return "redirect:/owners/" + owner.getId();
	}

	@PostMapping("/appointments/{appointmentId}/cancel")
	public String cancelAppointment(@PathVariable("appointmentId") int appointmentId,
			RedirectAttributes redirectAttributes) {
		Appointment appointment = this.appointmentService.cancelAppointment(appointmentId);
		redirectAttributes.addFlashAttribute("message", "Appointment cancelled");
		return "redirect:/owners/" + appointment.getPet().getOwner().getId();
	}

	@GetMapping("/appointments")
	public String showSchedule(@RequestParam(name = "scheduleDate", required = false) LocalDate scheduleDate,
			@RequestParam(name = "vetId", required = false) Integer vetId, Model model) {

		LocalDate effectiveDate = (scheduleDate != null ? scheduleDate : LocalDate.now());
		model.addAttribute("scheduleDate", effectiveDate);
		model.addAttribute("vetId", vetId);
		model.addAttribute("appointments", this.appointmentService.findSchedule(effectiveDate, vetId));
		return "appointments/appointmentList";
	}

	private void populateAvailableSlots(Model model, Integer vetId, LocalDate appointmentDate,
			Integer excludeAppointmentId) {
		List<LocalTime> availableSlots = List.of();
		if (vetId != null && appointmentDate != null) {
			availableSlots = this.appointmentService.getAvailableSlots(vetId, appointmentDate, excludeAppointmentId);
		}
		model.addAttribute("availableSlots", availableSlots);
	}

	private Vet resolveVet(Integer vetId) {
		if (vetId == null) {
			return null;
		}
		return this.appointmentService.findAllVets()
			.stream()
			.filter(v -> vetId.equals(v.getId()))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Vet not found with id: " + vetId));
	}

}
