package com.chatsocial.event;

public record BlockChanged(String firstUserId, String secondUserId, String conversationId) {}
