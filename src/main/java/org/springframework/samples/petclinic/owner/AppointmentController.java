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
import java.util.List;
import java.util.Optional;

import jakarta.validation.Valid;

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
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class AppointmentController {

	private final OwnerRepository owners;

	private final VisitRepository visits;

	private final VetRepository vets;

	private final AppointmentService appointmentService;

	AppointmentController(OwnerRepository owners, VisitRepository visits, VetRepository vets,
			AppointmentService appointmentService) {
		this.owners = owners;
		this.visits = visits;
		this.vets = vets;
		this.appointmentService = appointmentService;
	}

	@InitBinder
	public void setAllowedFields(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id", "*.id", "status", "vet", "startDateTime");
	}

	@ModelAttribute("allVets")
	public List<Vet> populateVets() {
		return vets.findAll().stream().toList();
	}

	@GetMapping("/owners/{ownerId}/pets/{petId}/appointments/new")
	public String initNewAppointmentForm(@PathVariable int ownerId, @PathVariable int petId, Model model) {
		Owner owner = owners.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException("Pet not found with id: " + petId + " for owner " + ownerId);
		}
		model.addAttribute("owner", owner);
		model.addAttribute("pet", pet);
		model.addAttribute("appointmentForm", new AppointmentForm());
		return "appointments/createOrUpdateAppointmentForm";
	}

	@PostMapping("/owners/{ownerId}/pets/{petId}/appointments/new")
	public String processNewAppointmentForm(@PathVariable int ownerId, @PathVariable int petId,
			@Valid @ModelAttribute("appointmentForm") AppointmentForm form, BindingResult result, Model model,
			RedirectAttributes redirectAttributes) {
		Owner owner = owners.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException("Pet not found with id: " + petId + " for owner " + ownerId);
		}
		model.addAttribute("owner", owner);
		model.addAttribute("pet", pet);

		if (result.hasErrors()) {
			return "appointments/createOrUpdateAppointmentForm";
		}

		LocalDateTime startDateTime = LocalDateTime.of(form.getDate(), form.getTime());
		try {
			appointmentService.scheduleAppointment(ownerId, petId, form.getVetId(), startDateTime, form.getDescription());
		}
		catch (SchedulingConflictException ex) {
			result.reject("scheduleConflict", ex.getMessage());
			return "appointments/createOrUpdateAppointmentForm";
		}

		redirectAttributes.addFlashAttribute("message", "Appointment scheduled");
		return "redirect:/owners/{ownerId}";
	}

	@GetMapping("/appointments/{appointmentId}/edit")
	public ModelAndView initRescheduleForm(@PathVariable int appointmentId) {
		Visit visit = visits.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found with id: " + appointmentId));
		ModelAndView mav = new ModelAndView("appointments/createOrUpdateAppointmentForm");
		mav.addObject("appointmentId", appointmentId);
		AppointmentForm form = new AppointmentForm();
		if (visit.getVet() != null) {
			form.setVetId(visit.getVet().getId());
		}
		if (visit.getStartDateTime() != null) {
			form.setDate(visit.getStartDateTime().toLocalDate());
			form.setTime(visit.getStartDateTime().toLocalTime());
		}
		form.setDescription(visit.getDescription());
		mav.addObject("appointmentForm", form);
		mav.addObject("appointment", visit);
		return mav;
	}

	@PostMapping("/appointments/{appointmentId}/edit")
	public String processRescheduleForm(@PathVariable int appointmentId,
			@Valid @ModelAttribute("appointmentForm") AppointmentForm form, BindingResult result, Model model,
			RedirectAttributes redirectAttributes) {
		Visit visit = visits.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found with id: " + appointmentId));
		model.addAttribute("appointmentId", appointmentId);
		model.addAttribute("appointment", visit);

		if (result.hasErrors()) {
			return "appointments/createOrUpdateAppointmentForm";
		}

		LocalDateTime startDateTime = LocalDateTime.of(form.getDate(), form.getTime());
		try {
			appointmentService.rescheduleAppointment(appointmentId, form.getVetId(), startDateTime);
		}
		catch (SchedulingConflictException ex) {
			result.reject("scheduleConflict", ex.getMessage());
			return "appointments/createOrUpdateAppointmentForm";
		}

		redirectAttributes.addFlashAttribute("message", "Appointment rescheduled");
		// redirect back to owner
		return redirectToOwner(visit);
	}

	@GetMapping("/appointments/{appointmentId}/cancel")
	public ModelAndView initCancel(@PathVariable int appointmentId) {
		Visit visit = visits.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found with id: " + appointmentId));
		ModelAndView mav = new ModelAndView("appointments/cancelAppointment");
		mav.addObject("appointment", visit);
		mav.addObject("appointmentId", appointmentId);
		return mav;
	}

	@PostMapping("/appointments/{appointmentId}/cancel")
	public String processCancel(@PathVariable int appointmentId, RedirectAttributes redirectAttributes) {
		Visit visit = visits.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found with id: " + appointmentId));
		appointmentService.cancelAppointment(appointmentId);
		redirectAttributes.addFlashAttribute("message", "Appointment cancelled");
		return redirectToOwner(visit);
	}

	@GetMapping("/vets/{vetId}/schedule")
	public String vetSchedule(@PathVariable int vetId, @RequestParam Optional<LocalDate> date, Model model) {
		LocalDate selectedDate = date.orElseGet(LocalDate::now);
		model.addAttribute("selectedDate", selectedDate);
		model.addAttribute("vetId", vetId);
		model.addAttribute("appointments",
				appointmentService.getVetScheduleForDate(vetId, selectedDate));
		return "vets/vetSchedule";
	}

	private String redirectToOwner(Visit visit) {
		if (visit == null) {
			return "redirect:/owners";
		}
		Pet pet = visit.getPet();
		if (pet == null) {
			return "redirect:/owners";
		}
		Owner owner = pet.getOwner();
		if (owner == null) {
			return "redirect:/owners";
		}
		Integer ownerId = owner.getId();
		if (ownerId == null) {
			return "redirect:/owners";
		}
		return "redirect:/owners/" + ownerId;
	}

}