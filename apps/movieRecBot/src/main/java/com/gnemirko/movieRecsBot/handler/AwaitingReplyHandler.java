package com.gnemirko.movieRecsBot.handler;

import com.gnemirko.movieRecsBot.complaint.ComplaintFlowService;
import com.gnemirko.movieRecsBot.service.OpinionService;
import com.gnemirko.movieRecsBot.service.UserProfileService;
import com.gnemirko.movieRecsBot.util.CmdText;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AwaitingReplyHandler {

    private final MenuStateService menuStateService;
    private final UserProfileService userProfileService;
    private final ProfileReplyBuilder profileReplyBuilder;
    private final OpinionService opinionService;
    private final ComplaintFlowService complaintFlowService;

    public Optional<BotApiMethod<?>> handle(long chatId, String text) {
        var await = menuStateService.getAwait(chatId);
        if (await == MenuStateService.Await.NONE || text.startsWith("/")) {
            return Optional.empty();
        }

        return switch (await) {
            case ADD_GENRE -> Optional.of(addGenres(chatId, text));
            case ADD_ACTOR -> Optional.of(addActors(chatId, text));
            case ADD_DIRECTOR -> Optional.of(addDirectors(chatId, text));
            case ADD_BLOCK -> Optional.of(addBlocks(chatId, text));
            case ADD_OPINION -> Optional.of(opinion(chatId, text));
            case REPORT_COMPLAINT -> Optional.of(reportComplaint(chatId, text));
            case NONE -> Optional.empty();
        };
    }

    private BotApiMethod<?> addGenres(long chatId, String payload) {
        userProfileService.addGenres(chatId, CmdText.parseArgs(payload));
        menuStateService.clear(chatId);
        return profileReplyBuilder.profileMessage(chatId);
    }

    private BotApiMethod<?> addActors(long chatId, String payload) {
        userProfileService.addActors(chatId, CmdText.parseArgs(payload));
        menuStateService.clear(chatId);
        return profileReplyBuilder.profileMessage(chatId);
    }

    private BotApiMethod<?> addDirectors(long chatId, String payload) {
        userProfileService.addDirectors(chatId, CmdText.parseArgs(payload));
        menuStateService.clear(chatId);
        return profileReplyBuilder.profileMessage(chatId);
    }

    private BotApiMethod<?> addBlocks(long chatId, String payload) {
        userProfileService.blockTags(chatId, CmdText.parseArgs(payload));
        menuStateService.clear(chatId);
        return profileReplyBuilder.profileMessage(chatId);
    }

    private BotApiMethod<?> opinion(long chatId, String payload) {
        OpinionService.OpinionResult result = opinionService.save(chatId, payload);
        if (result.success()) {
            menuStateService.clear(chatId);
        }
        return result.message();
    }

    private BotApiMethod<?> reportComplaint(long chatId, String payload) {
        return complaintFlowService.handleAwaitedComplaint(chatId, payload);
    }
}
