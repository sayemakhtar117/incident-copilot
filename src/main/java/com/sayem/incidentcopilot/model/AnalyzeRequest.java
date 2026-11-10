package com.sayem.incidentcopilot.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnalyzeRequest(
		@NotBlank(message = "alert must not be blank") @Size(max = 4000, message = "alert must be at most 4000 characters") String alert,
		@Size(max = 100, message = "service must be at most 100 characters") String service) {
}
