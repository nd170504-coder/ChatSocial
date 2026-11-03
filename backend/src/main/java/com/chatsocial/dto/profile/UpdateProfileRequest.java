package com.chatsocial.dto.profile;

import com.chatsocial.enums.Gender;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
public class UpdateProfileRequest {
    @NotBlank
    @Size(max = 100)
    private String fullName;

    @Size(max = 1000)
    private String bio;

    @Past
    private LocalDate dateOfBirth;

    private Gender gender;
}
