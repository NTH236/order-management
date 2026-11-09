package com.example.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderStatusRequest {
    @NotBlank(message = "Status cannot be blank")
    @Pattern(regexp = "^(?i)(pending|completed|cancelled)$", message = "Status must be either 'pending', 'completed', or 'cancelled'")
    private String status;
}

