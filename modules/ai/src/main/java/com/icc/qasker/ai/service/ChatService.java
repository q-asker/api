package com.icc.qasker.ai.service;

import com.icc.qasker.ai.dto.MyChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatModel chatModel;

    public MyChatResponse chat(String prompt) {
        ChatResponse response = chatModel.call(new Prompt(prompt));
        return new MyChatResponse(response.getResult().getOutput().getText());
    }
}
