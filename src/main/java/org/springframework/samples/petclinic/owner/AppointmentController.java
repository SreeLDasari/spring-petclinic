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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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

import jakarta.validation.Valid;

/**
 * Lets clinic staff schedule, reschedule and cancel the appointments of a pet and see its
 * upcoming appointments and history.
 */
@Controller
@RequestMapping("/owners/{ownerId}/pets/{petId}/appointments")
class AppointmentController {

	private static final String FORM_VIEW = "appointments/appointmentForm";

	private static final String LIST_REDIRECT = "redirect:/owners/{ownerId}/pets/{petId}/appointments";

	private static final Comparator<Visit> BY_START = Comparator.comparing(Visit::getDate)
		.thenComparing(Visit::getTime, Comparator.nullsFirst(Comparator.naturalOrder()));

	private final OwnerRepository owners;

	private final VetRepository vets;

	private final AppointmentService appointments;

	AppointmentController(OwnerRepository owners, VetRepository vets, AppointmentService appointments) {
		this.owners = owners;
		this.vets = vets;
		this.appointments = appointments;
	}

	@ModelAttribute
	void loadOwnerAndPet(@PathVariable("ownerId") int ownerId, @PathVariable("petId") int petId, Model model) {
		Owner owner = this.owners.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException("Pet with id " + petId + " not found for owner with id " + ownerId);
		}
		model.addAttribute("owner", owner);
		model.addAttribute("pet", pet);
		model.addAttribute("vets",
				this.vets.findAll()
					.stream()
					.sorted(Comparator.comparing(Vet::getLastName).thenComparing(Vet::getFirstName))
					.toList());
		model.addAttribute("minVisitDate", LocalDate.now());
		model.addAttribute("durationMinutes", this.appointments.getDurationMinutes());
	}

	@GetMapping
	String listAppointments(Model model) {
		LocalDateTime now = LocalDateTime.now();
		List<Visit> all = List.copyOf(pet(model).getVisits());
		model.addAttribute("upcoming", all.stream().filter(v -> v.isUpcoming(now)).sorted(BY_START).toList());
		model.addAttribute("history",
				all.stream().filter(v -> !v.isUpcoming(now)).sorted(BY_START.reversed()).toList());
		return "appointments/appointmentList";
	}

	@GetMapping("/new")
	String initScheduleForm(Model model) {
		model.addAttribute("appointmentForm", new AppointmentForm());
		return FORM_VIEW;
	}

	@PostMapping("/new")
	String processScheduleForm(@PathVariable int petId, @Valid AppointmentForm appointmentForm, BindingResult result,
			Model model, RedirectAttributes redirectAttributes) {
		Vet vet = validate(appointmentForm, result);
		if (!result.hasErrors()) {
			try {
				this.appointments.schedule(owner(model), petId, vet, appointmentForm.getDate(),
						appointmentForm.getTime(), appointmentForm.getDescription());
				redirectAttributes.addFlashAttribute("message", "appointment.booked");
				return LIST_REDIRECT;
			}
			catch (SchedulingConflictException ex) {
				result.rejectValue("time", "appointment.conflict");
			}
		}
		return FORM_VIEW;
	}

	@GetMapping("/{visitId}/reschedule")
	String initRescheduleForm(@PathVariable int visitId, Model model, RedirectAttributes redirectAttributes) {
		Visit visit = findChangeable(model, visitId);
		if (visit == null) {
			redirectAttributes.addFlashAttribute("error", "appointment.unavailable");
			return LIST_REDIRECT;
		}
		model.addAttribute("appointmentForm", AppointmentForm.from(visit));
		model.addAttribute("reschedule", true);
		return FORM_VIEW;
	}

	@PostMapping("/{visitId}/reschedule")
	String processRescheduleForm(@PathVariable int visitId, @Valid AppointmentForm appointmentForm,
			BindingResult result, Model model, RedirectAttributes redirectAttributes) {
		if (findChangeable(model, visitId) == null) {
			redirectAttributes.addFlashAttribute("error", "appointment.unavailable");
			return LIST_REDIRECT;
		}
		model.addAttribute("reschedule", true);
		Vet vet = validate(appointmentForm, result);
		if (!result.hasErrors()) {
			try {
				this.appointments.reschedule(visitId, vet, appointmentForm.getDate(), appointmentForm.getTime(),
						appointmentForm.getDescription());
				redirectAttributes.addFlashAttribute("message", "appointment.rescheduled");
				return LIST_REDIRECT;
			}
			catch (SchedulingConflictException ex) {
				result.rejectValue("time", "appointment.conflict");
			}
		}
		return FORM_VIEW;
	}

	@PostMapping("/{visitId}/cancel")
	String processCancel(@PathVariable int visitId, Model model, RedirectAttributes redirectAttributes) {
		if (findChangeable(model, visitId) == null) {
			redirectAttributes.addFlashAttribute("error", "appointment.unavailable");
		}
		else {
			this.appointments.cancel(visitId);
			redirectAttributes.addFlashAttribute("message", "appointment.cancelled");
		}
		return LIST_REDIRECT;
	}

	/**
	 * Resolves the selected vet and checks that the requested slot is in the future.
	 * @return the vet, or null if it could not be resolved (an error is then recorded)
	 */
	private Vet validate(AppointmentForm form, BindingResult result) {
		Vet vet = null;
		if (form.getVetId() != null) {
			Optional<Vet> found = this.vets.findById(form.getVetId());
			if (found.isPresent()) {
				vet = found.get();
			}
			else {
				result.rejectValue("vetId", "notFound");
			}
		}
		if (form.getDate() != null && form.getTime() != null
				&& LocalDateTime.of(form.getDate(), form.getTime()).isBefore(LocalDateTime.now())) {
			result.rejectValue("date", "appointment.past");
		}
		return vet;
	}

	/** The visit of this pet with the given id if it is still upcoming, else null. */
	private Visit findChangeable(Model model, int visitId) {
		return pet(model).getVisits()
			.stream()
			.filter(v -> v.getId() != null && v.getId() == visitId)
			.filter(v -> v.isUpcoming(LocalDateTime.now()))
			.findFirst()
			.orElse(null);
	}

	private Owner owner(Model model) {
		return (Owner) model.getAttribute("owner");
	}

	private Pet pet(Model model) {
		return (Pet) model.getAttribute("pet");
	}

}
