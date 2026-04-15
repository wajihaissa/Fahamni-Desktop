package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.quiz.Choice;
import tn.esprit.fahamni.Models.quiz.Question;
import tn.esprit.fahamni.Models.quiz.Quiz;
import tn.esprit.fahamni.utils.EnvConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiQuizAssistantService {

    private static final Pattern MESSAGE_CONTENT_PATTERN =
            Pattern.compile("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern QUESTION_BLOCK_PATTERN =
            Pattern.compile("QUESTION\\s+(\\d+)\\s*:(.*?)(?=QUESTION\\s+\\d+\\s*:|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;

    public AiQuizAssistantService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public GeneratedQuizDraft generateQuizDraft(String topic, String title, int questionCount, String difficulty) {
        String normalizedTopic = safeText(topic, "General Knowledge");
        String normalizedTitle = safeText(title, normalizedTopic + " Smart Quiz");
        int normalizedCount = Math.max(2, Math.min(5, questionCount));
        String normalizedDifficulty = safeText(difficulty, "Medium");

        Optional<Quiz> remoteDraft = tryGenerateQuizDraftWithOpenAi(
                normalizedTopic,
                normalizedTitle,
                normalizedCount,
                normalizedDifficulty
        );

        if (remoteDraft.isPresent()) {
            return new GeneratedQuizDraft(remoteDraft.get(), "OpenAI");
        }

        return new GeneratedQuizDraft(
                buildFallbackQuizDraft(normalizedTopic, normalizedTitle, normalizedCount, normalizedDifficulty),
                "Built-in generator"
        );
    }

    public String generateHint(Quiz quiz, Question question, Long selectedChoiceId) {
        if (question == null) {
            return "Read the question carefully and eliminate the weakest answer first.";
        }

        if (!isBlank(question.getHint())) {
            return question.getHint();
        }

        Choice selectedChoice = findChoiceById(question, selectedChoiceId);
        Choice correctChoice = findCorrectChoice(question);
        String keyword = quiz != null && !isBlank(quiz.getKeyword()) ? quiz.getKeyword() : "the topic";

        if (selectedChoice != null && Boolean.TRUE.equals(selectedChoice.getIsCorrect())) {
            return "You are already leaning in the right direction. Re-read the wording and confirm why that option fits best.";
        }

        if (!isBlank(question.getExplanation())) {
            return question.getExplanation();
        }

        if (selectedChoice != null && correctChoice != null) {
            return "Think about " + keyword + ": \"" + selectedChoice.getChoice()
                    + "\" sounds tempting, but the best answer is the one most directly tied to the main concept, not just a related detail.";
        }

        List<String> nonCorrectChoices = question.getChoices().stream()
                .filter(choice -> !Boolean.TRUE.equals(choice.getIsCorrect()))
                .map(Choice::getChoice)
                .filter(choice -> !isBlank(choice))
                .limit(2)
                .toList();

        if (correctChoice != null && !nonCorrectChoices.isEmpty()) {
            return "Focus on the core idea of " + keyword + ". Try ruling out options like "
                    + String.join(" and ", nonCorrectChoices) + " before deciding.";
        }

        return "Look for the answer that best matches the main idea of " + keyword + ", not just a familiar word.";
    }

    private Optional<Quiz> tryGenerateQuizDraftWithOpenAi(String topic, String title, int questionCount, String difficulty) {
        String apiKey = EnvConfig.get("OPENAI_API_KEY");
        String model = EnvConfig.get("OPENAI_MODEL");
        if (isBlank(apiKey) || isBlank(model)) {
            return Optional.empty();
        }

        String prompt = buildGenerationPrompt(topic, title, questionCount, difficulty);
        String requestBody = """
                {
                  "model": "%s",
                  "messages": [
                    {
                      "role": "system",
                      "content": "You generate short educational multiple-choice quizzes. Follow the exact response template."
                    },
                    {
                      "role": "user",
                      "content": "%s"
                    }
                  ],
                  "temperature": 0.8
                }
                """.formatted(escapeJson(model), escapeJson(prompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .timeout(Duration.ofSeconds(25))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }

            Optional<String> content = extractFirstMessageContent(response.body());
            if (content.isEmpty()) {
                return Optional.empty();
            }

            Quiz parsedQuiz = parseQuizDraft(content.get(), title, topic);
            return parsedQuiz != null && !parsedQuiz.getQuestions().isEmpty()
                    ? Optional.of(parsedQuiz)
                    : Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private Optional<String> extractFirstMessageContent(String responseBody) {
        Matcher matcher = MESSAGE_CONTENT_PATTERN.matcher(responseBody);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(unescapeJson(matcher.group(1)));
    }

    private Quiz parseQuizDraft(String content, String fallbackTitle, String fallbackTopic) {
        if (isBlank(content)) {
            return null;
        }

        Quiz quiz = new Quiz();
        quiz.setTitre(extractPrefixedValue(content, "TITLE", fallbackTitle));
        quiz.setKeyword(extractPrefixedValue(content, "KEYWORD", fallbackTopic));

        Matcher matcher = QUESTION_BLOCK_PATTERN.matcher(content);
        while (matcher.find()) {
            String block = matcher.group(2);
            Question question = parseQuestionBlock(block);
            if (question != null) {
                quiz.addQuestion(question);
            }
        }

        return quiz;
    }

    private Question parseQuestionBlock(String block) {
        String questionText = extractPrefixedValue(block, "TEXT", null);
        if (isBlank(questionText)) {
            return null;
        }

        String hint = extractPrefixedValue(block, "HINT", "");
        String explanation = extractPrefixedValue(block, "EXPLANATION", "");
        String answerLabel = extractPrefixedValue(block, "ANSWER", "A").trim().toUpperCase(Locale.ROOT);

        List<String> choices = List.of(
                extractPrefixedValue(block, "A", ""),
                extractPrefixedValue(block, "B", ""),
                extractPrefixedValue(block, "C", ""),
                extractPrefixedValue(block, "D", "")
        );

        if (choices.stream().anyMatch(this::isBlank)) {
            return null;
        }

        Question question = new Question();
        question.setQuestion(questionText.trim());
        question.setHint(hint.trim());
        question.setExplanation(explanation.trim());

        for (int index = 0; index < choices.size(); index++) {
            Choice choice = new Choice();
            choice.setChoice(choices.get(index).trim());
            choice.setIsCorrect(answerLabel.equals(String.valueOf((char) ('A' + index))));
            question.addChoice(choice);
        }

        return exactlyOneCorrect(question) ? question : null;
    }

    private Quiz buildFallbackQuizDraft(String topic, String title, int questionCount, String difficulty) {
        Quiz quiz = new Quiz();
        quiz.setTitre(title);
        quiz.setKeyword(topic);

        List<QuestionTemplate> templates = buildFallbackTemplates(topic, difficulty);
        for (int index = 0; index < Math.min(questionCount, templates.size()); index++) {
            quiz.addQuestion(templates.get(index).toQuestion());
        }

        return quiz;
    }

    private List<QuestionTemplate> buildFallbackTemplates(String topic, String difficulty) {
        String normalizedDifficulty = difficulty.toLowerCase(Locale.ROOT);
        List<QuestionTemplate> templates = new ArrayList<>();

        templates.add(new QuestionTemplate(
                "Which statement best describes the main goal of " + topic + "?",
                "Pick the option that explains the purpose, not just a tool or side effect.",
                "The correct answer defines the core purpose of " + topic + " at a high level.",
                "To solve a clearly identified learning or practical need",
                "To make every task fully automatic without review",
                "To replace all subject knowledge instantly",
                "To avoid using any structured process",
                0
        ));

        templates.add(new QuestionTemplate(
                "When studying " + topic + ", what should you do first?",
                "Start with the foundation before advanced details.",
                "Strong learning starts with basic concepts, vocabulary, and context before optimization.",
                "Understand the essential concepts and terminology",
                "Memorize rare edge cases immediately",
                "Skip examples and jump to final evaluation",
                "Ignore the problem the topic is trying to solve",
                0
        ));

        templates.add(new QuestionTemplate(
                "Which option is the best sign that someone understands " + topic + "?",
                "Real understanding shows up when the person can explain choices clearly.",
                "Explaining reasoning and applying the concept correctly is a stronger signal than memorizing isolated facts.",
                "They can justify why one solution fits better than the others",
                "They repeat keywords without context",
                "They guess quickly without checking assumptions",
                "They avoid practical examples",
                0
        ));

        templates.add(new QuestionTemplate(
                "In a " + normalizedDifficulty + " quiz about " + topic + ", what makes a distractor answer strong?",
                "A good distractor feels plausible but misses the main idea.",
                "Distractors should be believable enough to test understanding, while still being incorrect.",
                "It sounds believable but does not fully satisfy the question",
                "It is obviously unrelated and easy to reject",
                "It repeats the exact wording of the question's best answer",
                "It gives no information at all",
                0
        ));

        templates.add(new QuestionTemplate(
                "Why can hints be useful during a quiz on " + topic + "?",
                "A useful hint narrows the reasoning path without spoiling the answer.",
                "Hints support learning by nudging the learner toward the right concept instead of revealing the final choice directly.",
                "They guide the learner toward the right concept",
                "They should always reveal the correct answer immediately",
                "They make question design unnecessary",
                "They remove the need to think critically",
                0
        ));

        return templates;
    }

    private Choice findChoiceById(Question question, Long choiceId) {
        if (question == null || choiceId == null) {
            return null;
        }
        return question.getChoices().stream()
                .filter(choice -> choiceId.equals(choice.getId()))
                .findFirst()
                .orElse(null);
    }

    private Choice findCorrectChoice(Question question) {
        if (question == null) {
            return null;
        }
        return question.getChoices().stream()
                .filter(choice -> Boolean.TRUE.equals(choice.getIsCorrect()))
                .findFirst()
                .orElse(null);
    }

    private boolean exactlyOneCorrect(Question question) {
        return question.getChoices().stream()
                .filter(choice -> Boolean.TRUE.equals(choice.getIsCorrect()))
                .count() == 1;
    }

    private String buildGenerationPrompt(String topic, String title, int questionCount, String difficulty) {
        return """
                Create a multiple-choice quiz for topic "%s".
                Quiz title: %s
                Difficulty: %s
                Number of questions: %d

                Use exactly this format:
                TITLE: ...
                KEYWORD: ...
                QUESTION 1:
                TEXT: ...
                A: ...
                B: ...
                C: ...
                D: ...
                ANSWER: A
                HINT: ...
                EXPLANATION: ...

                Continue the same template for every question.
                Requirements:
                - 4 choices per question
                - exactly 1 correct answer
                - concise hints
                - concise explanations
                - no markdown
                """.formatted(topic, title, difficulty, questionCount);
    }

    private String extractPrefixedValue(String content, String prefix, String fallback) {
        Pattern pattern = Pattern.compile("(?im)^" + Pattern.quote(prefix) + "\\s*:\\s*(.+)$");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return fallback;
    }

    private String safeText(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private String unescapeJson(String value) {
        return value
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    public record GeneratedQuizDraft(Quiz quiz, String provider) {
    }

    private record QuestionTemplate(
            String questionText,
            String hintText,
            String explanationText,
            String choiceA,
            String choiceB,
            String choiceC,
            String choiceD,
            int correctIndex
    ) {
        private Question toQuestion() {
            Question question = new Question();
            question.setQuestion(questionText);
            question.setHint(hintText);
            question.setExplanation(explanationText);

            List<String> choices = List.of(choiceA, choiceB, choiceC, choiceD);
            for (int index = 0; index < choices.size(); index++) {
                Choice choice = new Choice();
                choice.setChoice(choices.get(index));
                choice.setIsCorrect(index == correctIndex);
                question.addChoice(choice);
            }
            return question;
        }
    }
}
