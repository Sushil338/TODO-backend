package com.manager.TODO.DTO;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroqRequest(String model, List<GroqMessage> messages, GroqResponseFormat response_format) {}

