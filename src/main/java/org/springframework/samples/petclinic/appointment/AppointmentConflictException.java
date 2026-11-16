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

import java.time.LocalDateTime;

/**
 * Raised when an appointment overlaps an existing appointment for the same vet.
 */
public class AppointmentConflictException extends RuntimeException {

	private final int vetId;

	private final LocalDateTime start;

	public AppointmentConflictException(int vetId, LocalDateTime start, String message) {
		super(message);
		this.vetId = vetId;
		this.start = start;
	}

	public int getVetId() {
		return this.vetId;
	}

	public LocalDateTime getStart() {
		return this.start;
	}

}
