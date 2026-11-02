package com.chatsocial.enums;

public enum ProfileImageType {
    AVATAR("avatars"), COVER("covers");

    private final String folder;

    ProfileImageType(String folder) {
        this.folder = folder;
    }

    public String getFolder() {
        return folder;
    }
}
