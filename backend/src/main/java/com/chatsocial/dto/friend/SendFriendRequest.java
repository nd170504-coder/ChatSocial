package com.chatsocial.dto.friend;

import com.chatsocial.serialization.NormalizedIdentifierDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import tools.jackson.databind.annotation.JsonDeserialize;

public record SendFriendRequest(
        @NotBlank
        @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
                message = "must be a valid user UUID")
        @JsonDeserialize(using = NormalizedIdentifierDeserializer.class)
        String userId
) {}
