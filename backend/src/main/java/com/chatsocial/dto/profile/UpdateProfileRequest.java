package com.chatsocial.dto.profile;

import com.chatsocial.enums.Gender;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Getter
public class UpdateProfileRequest {
    @Size(max = 100)
    private String fullName;
    @Size(max = 1000)
    private String bio;
    @Past
    private LocalDate dateOfBirth;
    private Gender gender;
    @JsonIgnore
    @Schema(hidden = true)
    private final Set<String> providedFields = new HashSet<>();

    public void setFullName(String fullName) {
        this.fullName = fullName == null ? null : fullName.trim();
        providedFields.add("fullName");
    }

    public void setBio(String bio) {
        this.bio = bio == null ? null : bio.trim();
        providedFields.add("bio");
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
        providedFields.add("dateOfBirth");
    }

    public void setGender(Gender gender) {
        this.gender = gender;
        providedFields.add("gender");
    }

    public boolean hasField(String field) {
        return providedFields.contains(field);
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "fullName must not be null or blank when provided")
    public boolean isFullNameValid() {
        return !hasField("fullName") || fullName != null && !fullName.isBlank();
    }
}
