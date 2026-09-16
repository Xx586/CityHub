package com.cityhub.cache;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record CacheEnvelope(JsonNode data, LocalDateTime expireAt) {
}

