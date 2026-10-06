package com.sender.backend.realtime;

import com.sender.backend.conversation.ConversationMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TypingControllerTest {
    @Mock ConversationMemberRepository members;
    @Mock RealtimePublisher realtime;

    @Test
    void rejectsTypingFromNonMember() {
        when(members.existsByConversationIdAndUserId(7, 11)).thenReturn(false);
        TypingController controller = new TypingController(members, realtime);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.typing(7,
                        new TypingController.TypingRequest(TypingController.TypingStateValue.STARTED),
                        principal(11)));

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(realtime);
    }

    @Test
    void rejectsMissingTypingState() {
        when(members.existsByConversationIdAndUserId(7, 11)).thenReturn(true);
        TypingController controller = new TypingController(members, realtime);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.typing(7, new TypingController.TypingRequest(null), principal(11)));

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(realtime);
    }

    private Principal principal(int userId) {
        return () -> Integer.toString(userId);
    }
}
