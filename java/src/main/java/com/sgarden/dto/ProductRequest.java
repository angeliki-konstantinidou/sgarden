package com.sgarden.dto;

import com.sgarden.validation.NullOrNotBlank;
import com.sgarden.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductRequest {

    @NotBlank(groups = OnCreate.class, message = "name is required")
    @NullOrNotBlank(message = "name must not be blank")
    @Size(max = 100, message = "name must not exceed 100 characters")
    private String name;

    @Size(max = 500, message = "description must not exceed 500 characters")
    private String description;

    @Size(max = 50, message = "category must not exceed 50 characters")
    private String category;

    @NotNull(groups = OnCreate.class, message = "price is required")
    @Positive(message = "price must be greater than 0")
    private Double price;

    @PositiveOrZero(message = "stock must be 0 or greater")
    private Integer stock;
}
