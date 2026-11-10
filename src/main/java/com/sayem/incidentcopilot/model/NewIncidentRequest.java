package com.sayem.incidentcopilot.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NewIncidentRequest(@NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 100) String service,
		@Size(max = 10) String severity, @NotBlank @Size(max = 3000) String symptoms,
		@NotBlank @Size(max = 2000) String rootCause, @NotBlank @Size(max = 2000) String resolution) {
}
