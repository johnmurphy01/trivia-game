package org.bj.examples.trivia.service.game.impl;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

import org.bj.examples.trivia.dto.GameState;
import org.bj.examples.trivia.dto.SlackAttachment;
import org.bj.examples.trivia.dto.SlackRequestDoc;
import org.bj.examples.trivia.dto.SlackResponseDoc;
import org.bj.examples.trivia.dto.SlackResponseType;
import org.bj.examples.trivia.dto.SlackUser;
import org.bj.examples.trivia.exception.GameNotStartedException;
import org.bj.examples.trivia.exception.ScoreException;
import org.bj.examples.trivia.exception.WorkflowException;
import org.bj.examples.trivia.service.game.TriviaGameService;
import org.bj.examples.trivia.service.score.ScoreService;
import org.bj.examples.trivia.service.slack.DelayedSlackService;
import org.bj.examples.trivia.service.workflow.WorkflowService;
import org.bj.examples.trivia.util.SlackUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TriviaGameServiceImpl implements TriviaGameService {
    private static final String SCORES_FORMAT = "```Scores:\n\n%s```";
    private static final String GAME_NOT_STARTED_FORMAT = "A game has not yet been started. If you'd like to start a game, try `%s start`";

    private static final String NO_CORRECT_ANSWER_TARGET = "none";

    private final ScoreService scoreService;
    private final WorkflowService workflowService;
    private final DelayedSlackService delayedSlackService;

    @Autowired
    public TriviaGameServiceImpl(
            final ScoreService scoreService,
            final WorkflowService workflowService,
            final DelayedSlackService delayedSlackService
    ) {
        this.scoreService = scoreService;
        this.workflowService = workflowService;
        this.delayedSlackService = delayedSlackService;
    }

    public SlackResponseDoc start(final SlackRequestDoc requestDoc) {
        final String channelId = requestDoc.getChannelId();
        final String userId = requestDoc.getUserId();

        try {
            workflowService.onGameStarted(channelId, userId);
        } catch (GameNotStartedException e) {
            return SlackResponseDoc.failure(String.format(GAME_NOT_STARTED_FORMAT, requestDoc.getCommand()));
        } catch (WorkflowException e) {
            return SlackResponseDoc.failure(e.getMessage());
        }

        final SlackResponseDoc responseDoc = new SlackResponseDoc();
        responseDoc.setResponseType(SlackResponseType.IN_CHANNEL);
        responseDoc.setText("OK, <@" + userId + ">, please ask a question.");

        return responseDoc;
    }

    public SlackResponseDoc stop(final SlackRequestDoc requestDoc) {
        try {
            workflowService.onGameStopped(requestDoc.getChannelId(), requestDoc.getUserId());
        } catch (GameNotStartedException e) {
            return SlackResponseDoc.failure(String.format(GAME_NOT_STARTED_FORMAT, requestDoc.getCommand()));
        } catch (WorkflowException e) {
            return SlackResponseDoc.failure(e.getMessage());
        }

        final SlackResponseDoc responseDoc = new SlackResponseDoc();
        responseDoc.setResponseType(SlackResponseType.IN_CHANNEL);
        responseDoc.setText("The game has been stopped but scores have not been cleared. If you'd like to start a new game, try `" + requestDoc.getCommand() + " start`.");

        return responseDoc;
    }

    public SlackResponseDoc submitQuestion(final SlackRequestDoc requestDoc, final String question) {
        try {
            workflowService.onQuestionSubmitted(requestDoc.getChannelId(), requestDoc.getUserId(), question);
        } catch (GameNotStartedException e) {
            return SlackResponseDoc.failure(String.format(GAME_NOT_STARTED_FORMAT, requestDoc.getCommand()));
        } catch (WorkflowException e) {
            return SlackResponseDoc.failure(e.getMessage());
        }

        final SlackResponseDoc delayedResponseDoc = new SlackResponseDoc();
        delayedResponseDoc.setResponseType(SlackResponseType.IN_CHANNEL);
        delayedResponseDoc.setText("<@" + requestDoc.getUserId() + "> asked the following question:\n\n" + question);
        delayedSlackService.sendResponse(requestDoc.getResponseUrl(), delayedResponseDoc);

        final SlackResponseDoc responseDoc = new SlackResponseDoc();
        responseDoc.setResponseType(SlackResponseType.EPHEMERAL);
        responseDoc.setText("Question posted.");

        return responseDoc;
    }

    public SlackResponseDoc submitAnswer(final SlackRequestDoc requestDoc, final String answer) {
        try {
            workflowService.onAnswerSubmitted(requestDoc.getChannelId(), requestDoc.getUserId());
        } catch (GameNotStartedException e) {
            return SlackResponseDoc.failure(String.format(GAME_NOT_STARTED_FORMAT, requestDoc.getCommand()));
        } catch (WorkflowException e) {
            return SlackResponseDoc.failure(e.getMessage());
        }

        final SlackUser user = new SlackUser(requestDoc.getUserId(), requestDoc.getUsername());
        scoreService.createUserIfNotExists(requestDoc.getChannelId(), user);

        final SlackResponseDoc delayedResponseDoc = new SlackResponseDoc();
        delayedResponseDoc.setResponseType(SlackResponseType.IN_CHANNEL);
        delayedResponseDoc.setText("<@" + requestDoc.getUserId() + "> answers \"" + answer + "\"");
        delayedSlackService.sendResponse(requestDoc.getResponseUrl(), delayedResponseDoc);

        final SlackResponseDoc responseDoc = new SlackResponseDoc();
        responseDoc.setResponseType(SlackResponseType.EPHEMERAL);
        responseDoc.setText("Answer submitted.");

        return responseDoc;
    }

    public SlackResponseDoc markAnswerCorrect(final SlackRequestDoc requestDoc, final String target, final String answer) {
        String text;

        try {
            workflowService.onCorrectAnswerSelected(requestDoc.getChannelId(), requestDoc.getUserId());

            if (target.equalsIgnoreCase(NO_CORRECT_ANSWER_TARGET)) {
                //"Change" back to the original host to reset the workflow state
                workflowService.onTurnChanged(requestDoc.getChannelId(), requestDoc.getUserId(), requestDoc.getUserId());

                text = "It looks like no one was able to answer that one!\n\n";
                text += generateScoreText(requestDoc);
                text += "\n\nOK, <@" + requestDoc.getUserId() + ">, let's try another one!";
            } else {
                final String userId = SlackUtils.normalizeId(target);

                scoreService.incrementScore(requestDoc.getChannelId(), userId);
                workflowService.onTurnChanged(requestDoc.getChannelId(), requestDoc.getUserId(), userId);

                text = "<@" + userId + "> is correct";

                if (answer != null) {
                    text += " with \"" + answer + "\"";
                }

                text += "!\n\n";
                text += generateScoreText(requestDoc);
                text += "\n\nOK, <@" + userId + ">, you're up!";
            }
        } catch (GameNotStartedException e) {
            return SlackResponseDoc.failure(String.format(GAME_NOT_STARTED_FORMAT, requestDoc.getCommand()));
        } catch (WorkflowException e) {
            return SlackResponseDoc.failure(e.getMessage());
        } catch (ScoreException e) {
            final SlackResponseDoc responseDoc = SlackResponseDoc.failure("User " + target + " does not exist. Please choose a valid user.");
            responseDoc.setAttachments(Arrays.asList(new SlackAttachment("Usage: `" + requestDoc.getCommand() + " correct @jsmith Blue skies`")));
            return responseDoc;
        }

        final SlackResponseDoc delayedResponseDoc = new SlackResponseDoc();
        delayedResponseDoc.setResponseType(SlackResponseType.IN_CHANNEL);
        delayedResponseDoc.setText(text);
        delayedSlackService.sendResponse(requestDoc.getResponseUrl(), delayedResponseDoc);

        final SlackResponseDoc responseDoc = new SlackResponseDoc();
        responseDoc.setResponseType(SlackResponseType.EPHEMERAL);
        responseDoc.setText("Score updated.");
        return responseDoc;
    }

    public SlackResponseDoc getStatus(final SlackRequestDoc requestDoc) {
        final GameState gameState = workflowService.getCurrentGameState(requestDoc.getChannelId());

        final SlackResponseDoc responseDoc = new SlackResponseDoc();
        responseDoc.setResponseType(SlackResponseType.EPHEMERAL);

        if (gameState == null || gameState.getControllingUserId() == null) {
            responseDoc.setText(String.format(GAME_NOT_STARTED_FORMAT, requestDoc.getCommand()));
        } else {
            final boolean isControllingUser = gameState.getControllingUserId().equals(requestDoc.getUserId());
            String text = "It's " + (isControllingUser ? "your" : ("<@" + gameState.getControllingUserId() + ">'s")) + " turn and ";

            if (gameState.getQuestion() == null) {
                text += "no question has been asked yet.";
            } else {
                text += (isControllingUser ? "you have" : "he/she has") + " asked the following question:\n\n" + gameState.getQuestion();
            }

            responseDoc.setText(text);
        }

        return responseDoc;
    }

    public SlackResponseDoc getScores(final SlackRequestDoc requestDoc) {
        final SlackResponseDoc responseDoc = new SlackResponseDoc();
        responseDoc.setResponseType(SlackResponseType.EPHEMERAL);
        responseDoc.setText(generateScoreText(requestDoc));

        return responseDoc;
    }

    public SlackResponseDoc resetScores(final SlackRequestDoc requestDoc) {
        scoreService.resetScores(requestDoc.getChannelId());

        final SlackResponseDoc responseDoc = new SlackResponseDoc();
        responseDoc.setResponseType(SlackResponseType.IN_CHANNEL);
        responseDoc.setText("Scores have been reset!");
        responseDoc.setAttachments(Arrays.asList(new SlackAttachment(generateScoreText(requestDoc))));

        return responseDoc;
    }

    private String generateScoreText(final SlackRequestDoc requestDoc) {
        final Map<SlackUser, Long> scoresByUser = scoreService.getAllScoresByUser(requestDoc.getChannelId());

        final String scoreText;

        if (scoresByUser.isEmpty()) {
            scoreText = "No scores yet...";
        } else {
            int maxUsernameLength = 1 + scoresByUser.keySet().stream()
                    .map(SlackUser::getUsername)
                    .map(String::length)
                    .max(Comparator.comparing(Integer::valueOf))
                    .orElse(0);
            scoreText = scoresByUser.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(SlackUser::getUsername)))
                    .map(entry -> String.format("@%-" + maxUsernameLength + "s %3d", entry.getKey().getUsername() + ":", entry.getValue()))
                    .collect(Collectors.joining("\n"));
        }

        return String.format(SCORES_FORMAT, scoreText);
    }
}
