package org.bj.examples.trivia.service.game.impl;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.bj.examples.trivia.dto.GameState;
import org.bj.examples.trivia.dto.SlackRequestDoc;
import org.bj.examples.trivia.dto.SlackResponseDoc;
import org.bj.examples.trivia.dto.SlackResponseType;
import org.bj.examples.trivia.dto.SlackUser;
import org.bj.examples.trivia.service.score.ScoreService;
import org.bj.examples.trivia.service.workflow.WorkflowService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TriviaGameServiceImplTest {
    @InjectMocks
    public TriviaGameServiceImpl cut;

    @Mock
    private WorkflowService workflowService;

    @Mock
    private ScoreService scoreService;

    //region getStatus
    @Test
    public void testGetStatusWithNullGameState() {
        final String channelId = "channel";
        final SlackRequestDoc requestDoc = new SlackRequestDoc();
        requestDoc.setChannelId(channelId);
        requestDoc.setCommand("/command");

        given(workflowService.getCurrentGameState(anyString())).willReturn(null);

        final SlackResponseDoc responseDoc = cut.getStatus(requestDoc);

        assertThat(responseDoc, is(notNullValue()));
        assertThat(responseDoc.getResponseType(), is(equalTo(SlackResponseType.EPHEMERAL)));
        assertThat(responseDoc.getText(), is(equalTo("A game has not yet been started. If you'd like to start a game, try `/command start`")));
    }

    @Test
    public void testGetStatusWithNullHostInGameState() {
        final String channelId = "channel";
        final SlackRequestDoc requestDoc = new SlackRequestDoc();
        requestDoc.setChannelId(channelId);
        requestDoc.setCommand("/command");

        final GameState gameState = new GameState();

        given(workflowService.getCurrentGameState(anyString())).willReturn(gameState);

        final SlackResponseDoc responseDoc = cut.getStatus(requestDoc);

        assertThat(responseDoc, is(notNullValue()));
        assertThat(responseDoc.getResponseType(), is(equalTo(SlackResponseType.EPHEMERAL)));
        assertThat(responseDoc.getText(), is(equalTo("A game has not yet been started. If you'd like to start a game, try `/command start`")));
    }

    @Test
    public void testGetStatusWithNoTopicInGameState() {
        final String channelId = "channel";
        final String userId = "U12345";

        final SlackRequestDoc requestDoc = new SlackRequestDoc();
        requestDoc.setChannelId(channelId);
        requestDoc.setCommand("/command");
        requestDoc.setUserId(userId);

        final GameState gameState = new GameState();
        gameState.setControllingUserId(userId);

        given(workflowService.getCurrentGameState(anyString())).willReturn(gameState);

        final SlackResponseDoc responseDoc = cut.getStatus(requestDoc);

        assertThat(responseDoc, is(notNullValue()));
        assertThat(responseDoc.getResponseType(), is(equalTo(SlackResponseType.EPHEMERAL)));
        assertThat(responseDoc.getText(), is(equalTo("*Topic:* None\n*Turn:* Yours\n*Question:* Waiting...")));
    }

    @Test
    public void testGetStatusWithSameHostAndNoQuestionInGameState() {
        final String channelId = "channel";
        final String userId = "U12345";
        final String topic = "some topic";

        final SlackRequestDoc requestDoc = new SlackRequestDoc();
        requestDoc.setChannelId(channelId);
        requestDoc.setCommand("/command");
        requestDoc.setUserId(userId);

        final GameState gameState = new GameState();
        gameState.setControllingUserId(userId);
        gameState.setTopic(topic);

        given(workflowService.getCurrentGameState(anyString())).willReturn(gameState);

        final SlackResponseDoc responseDoc = cut.getStatus(requestDoc);

        assertThat(responseDoc, is(notNullValue()));
        assertThat(responseDoc.getResponseType(), is(equalTo(SlackResponseType.EPHEMERAL)));
        assertThat(responseDoc.getText(), is(equalTo("*Topic:* some topic\n*Turn:* Yours\n*Question:* Waiting...")));
    }

    @Test
    public void testGetStatusWithDifferentHostAndNoQuestionInGameState() {
        final String channelId = "channel";
        final String userId = "U12345";
        final String controllingUserId = "U6789";
        final String topic = "some topic";

        final SlackRequestDoc requestDoc = new SlackRequestDoc();
        requestDoc.setChannelId(channelId);
        requestDoc.setCommand("/command");
        requestDoc.setUserId(userId);

        final GameState gameState = new GameState();
        gameState.setControllingUserId(controllingUserId);
        gameState.setTopic(topic);

        given(workflowService.getCurrentGameState(anyString())).willReturn(gameState);

        final SlackResponseDoc responseDoc = cut.getStatus(requestDoc);

        assertThat(responseDoc, is(notNullValue()));
        assertThat(responseDoc.getResponseType(), is(equalTo(SlackResponseType.EPHEMERAL)));
        assertThat(responseDoc.getText(), is(equalTo("*Topic:* some topic\n*Turn:* <@U6789>\n*Question:* Waiting...")));
    }

    @Test
    public void testGetStatusWithSameHostAndQuestionInGameState() {
        final String channelId = "channel";
        final String userId = "U12345";
        final String topic = "some topic";
        final String question = "some question?";

        final SlackRequestDoc requestDoc = new SlackRequestDoc();
        requestDoc.setChannelId(channelId);
        requestDoc.setCommand("/command");
        requestDoc.setUserId(userId);

        final GameState gameState = new GameState();
        gameState.setControllingUserId(userId);
        gameState.setTopic(topic);
        gameState.setQuestion(question);

        given(workflowService.getCurrentGameState(anyString())).willReturn(gameState);

        final SlackResponseDoc responseDoc = cut.getStatus(requestDoc);

        assertThat(responseDoc, is(notNullValue()));
        assertThat(responseDoc.getResponseType(), is(equalTo(SlackResponseType.EPHEMERAL)));
        assertThat(responseDoc.getText(), is(equalTo("*Topic:* some topic\n*Turn:* Yours\n*Question:*\n\nsome question?\n\n*Answers:* Waiting...")));
    }

    @Test
    public void testGetStatusWithDifferentHostAndQuestionInGameState() {
        final String channelId = "channel";
        final String userId = "U12345";
        final String controllingUserId = "U6789";
        final String topic = "some topic";
        final String question = "some question?";

        final SlackRequestDoc requestDoc = new SlackRequestDoc();
        requestDoc.setChannelId(channelId);
        requestDoc.setCommand("/command");
        requestDoc.setUserId(userId);

        final GameState gameState = new GameState();
        gameState.setControllingUserId(controllingUserId);
        gameState.setTopic(topic);
        gameState.setQuestion(question);

        given(workflowService.getCurrentGameState(anyString())).willReturn(gameState);

        final SlackResponseDoc responseDoc = cut.getStatus(requestDoc);

        assertThat(responseDoc, is(notNullValue()));
        assertThat(responseDoc.getResponseType(), is(equalTo(SlackResponseType.EPHEMERAL)));
        assertThat(responseDoc.getText(), is(equalTo("*Topic:* some topic\n*Turn:* <@U6789>\n*Question:*\n\nsome question?\n\n*Answers:* Waiting...")));
    }

    @Test
    public void testGetStatusWithQuestionAndAnswersInGameState() {
        final String channelId = "channel";
        final String userId = "U12345";
        final String topic = "some topic";
        final String question = "some question?";
        final List<GameState.Answer> answers = ImmutableList.of(
                new GameState.Answer("U1111", "jimbob", "answer 1", LocalDateTime.of(2018, 10, 9, 16, 30, 33)),
                new GameState.Answer("U2222", "joe", "answer 2", LocalDateTime.of(2018, 10, 9, 16, 32, 21)),
                new GameState.Answer("U3333", "muchlongerusername", "answer 3", LocalDateTime.of(2018, 10, 9, 16, 34, 25))
        );

        final SlackRequestDoc requestDoc = new SlackRequestDoc();
        requestDoc.setChannelId(channelId);
        requestDoc.setCommand("/command");
        requestDoc.setUserId(userId);

        final GameState gameState = new GameState();
        gameState.setControllingUserId(userId);
        gameState.setTopic(topic);
        gameState.setQuestion(question);
        gameState.setAnswers(answers);

        given(workflowService.getCurrentGameState(anyString())).willReturn(gameState);

        final SlackResponseDoc responseDoc = cut.getStatus(requestDoc);

        assertThat(responseDoc, is(notNullValue()));
        assertThat(responseDoc.getResponseType(), is(equalTo(SlackResponseType.EPHEMERAL)));
        assertThat(responseDoc.getText(), is(equalTo("*Topic:* some topic\n*Turn:* Yours\n*Question:*\n\nsome question?\n\n*Answers:*\n\n```10/09/2018 11:30:33 AM   @jimbob                answer 1\n10/09/2018 11:32:21 AM   @joe                   answer 2\n10/09/2018 11:34:25 AM   @muchlongerusername    answer 3```")));
    }
    //endregion

    @Test
    public void testGetScoresFormatsAndSortsCorrectly() {
        final Map<SlackUser, Long> scoresByUser = ImmutableMap.of(
                new SlackUser("1234", "test4"), 1L,
                new SlackUser("1235", "longertest2"), 103L,
                new SlackUser("1236", "unmanageablylongertest3"), 12L,
                new SlackUser("1237", "test1"), 1L
        );
        final String channelId = "channel";
        final SlackRequestDoc requestDoc = new SlackRequestDoc();
        requestDoc.setChannelId(channelId);

        given(scoreService.getAllScoresByUser(channelId)).willReturn(scoresByUser);

        final SlackResponseDoc responseDoc = cut.getScores(requestDoc);

        /*
         * ```Scores:
         *
         * @longertest2:             103
         * @unmanageablylongertest3:  12
         * @test1:                     1
         * @test4:                     1```
         */
        assertThat(responseDoc.getText(), is("```Scores:\n\n@longertest2:             103\n@unmanageablylongertest3:  12\n@test1:                     1\n@test4:                     1```"));
    }

    @Test
    public void testGetScoresWithNoUsers() {
        final String channelId = "channel";
        final SlackRequestDoc requestDoc = new SlackRequestDoc();
        requestDoc.setChannelId(channelId);

        given(scoreService.getAllScoresByUser(channelId)).willReturn(ImmutableMap.of());

        final SlackResponseDoc responseDoc = cut.getScores(requestDoc);

        /*
         * ```Scores:
         *
         * No scores yet...```
         */
        assertThat(responseDoc.getText(), is("```Scores:\n\nNo scores yet...```"));
    }
}
