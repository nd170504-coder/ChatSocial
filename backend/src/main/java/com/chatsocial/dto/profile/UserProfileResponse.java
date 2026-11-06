package com.chatsocial.dto.profile;

public record UserProfileResponse(String userId, String username, String fullName, String avatarUrl,
                                  String coverUrl, String bio, ProfileRelationship relationship, String friendRequestId) {}
