package com.ssuai.domain.chat.service;

import com.ssuai.domain.chat.dto.ChatResponse;

public interface ChatService {

    ChatResponse reply(String conversationId, String message);
}
