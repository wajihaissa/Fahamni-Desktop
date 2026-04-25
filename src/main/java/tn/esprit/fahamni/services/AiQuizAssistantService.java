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
    private static final Pattern METADATA_TOPIC_PATTERN =
            Pattern.compile("(?im)^TOPIC\\s*:\\s*(.+)$");
    private static final Pattern METADATA_DIFFICULTY_PATTERN =
            Pattern.compile("(?im)^DIFFICULTY\\s*:\\s*(.+)$");

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

    public QuestionMetadata inferQuestionMetadata(String quizKeyword, String quizTitle, String questionText, List<String> choices) {
        String fallbackTopic = safeText(quizKeyword, "General");
        String normalizedQuestion = safeText(questionText, "");
        List<String> normalizedChoices = choices == null ? List.of() : choices.stream()
                .filter(choice -> !isBlank(choice))
                .map(String::trim)
                .toList();

        Optional<QuestionMetadata> remoteMetadata = tryInferQuestionMetadataWithOpenAi(
                fallbackTopic,
                safeText(quizTitle, "Quiz"),
                normalizedQuestion,
                normalizedChoices
        );
        if (remoteMetadata.isPresent()) {
            return remoteMetadata.get();
        }

        return buildFallbackMetadata(fallbackTopic, normalizedQuestion, normalizedChoices);
    }

    public String generateHint(Quiz quiz, Question question, Long selectedChoiceId) {
        if (question == null) {
            return "Read the question carefully and focus on the main idea it is testing.";
        }

        String keyword = quiz != null && !isBlank(quiz.getKeyword()) ? quiz.getKeyword() : "the topic";
        Optional<String> remoteHint = tryGenerateHintWithOpenAi(quiz, question, selectedChoiceId);
        if (remoteHint.isPresent()) {
            return remoteHint.get();
        }

        Choice selectedChoice = findChoiceById(question, selectedChoiceId);

        if (selectedChoice != null && Boolean.TRUE.equals(selectedChoice.getIsCorrect())) {
            return "You are on a promising track. Re-read the wording and make sure your choice matches the main idea, not just a familiar phrase.";
        }

        if (selectedChoice != null) {
            return deriveQuestionSpecificHint(question, keyword, true);
        }

        return deriveQuestionSpecificHint(question, keyword, false);
    }

    private Optional<String> tryGenerateHintWithOpenAi(Quiz quiz, Question question, Long selectedChoiceId) {
        String apiKey = EnvConfig.get("OPENAI_API_KEY");
        String model = EnvConfig.get("OPENAI_MODEL");
        if (isBlank(apiKey) || isBlank(model) || question == null || isBlank(question.getQuestion())) {
            return Optional.empty();
        }

        String prompt = buildHintPrompt(quiz, question, selectedChoiceId);
        String requestBody = """
                {
                  "model": "%s",
                  "messages": [
                    {
                      "role": "system",
                      "content": "You write short, non-revealing quiz hints. Never mention the correct answer. Never rule out answer choices. Never say option, choice, eliminate, or rule out."
                    },
                    {
                      "role": "user",
                      "content": "%s"
                    }
                  ],
                  "temperature": 0.7
                }
                """.formatted(escapeJson(model), escapeJson(prompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }

            Optional<String> content = extractFirstMessageContent(response.body())
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(this::sanitizeHintText);

            return content.filter(value -> !value.isBlank());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private Optional<QuestionMetadata> tryInferQuestionMetadataWithOpenAi(
            String quizKeyword,
            String quizTitle,
            String questionText,
            List<String> choices
    ) {
        String apiKey = EnvConfig.get("OPENAI_API_KEY");
        String model = EnvConfig.get("OPENAI_MODEL");
        if (isBlank(apiKey) || isBlank(model) || isBlank(questionText)) {
            return Optional.empty();
        }

        String prompt = buildQuestionMetadataPrompt(quizKeyword, quizTitle, questionText, choices);
        String requestBody = """
                {
                  "model": "%s",
                  "messages": [
                    {
                      "role": "system",
                      "content": "You classify quiz questions. Reply using exactly two lines: TOPIC: ... and DIFFICULTY: Easy|Medium|Hard"
                    },
                    {
                      "role": "user",
                      "content": "%s"
                    }
                  ],
                  "temperature": 0.2
                }
                """.formatted(escapeJson(model), escapeJson(prompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .timeout(Duration.ofSeconds(15))
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

            return parseQuestionMetadata(content.get(), quizKeyword);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private String deriveQuestionSpecificHint(Question question, String keyword, boolean hasSelection) {
        String prompt = question != null && !isBlank(question.getQuestion())
                ? question.getQuestion().trim().toLowerCase(Locale.ROOT)
                : "";

        if (prompt.startsWith("why ")) {
            return "Focus on the reason or benefit the question is asking about in " + keyword + ".";
        }

        if (prompt.contains("what should you do first") || prompt.contains("first step")) {
            return "Think about the foundation someone should understand before moving into details in " + keyword + ".";
        }

        if (prompt.contains("best describes") || prompt.contains("main goal") || prompt.contains("main purpose")) {
            return "Look for the definition or core purpose behind " + keyword + ", not a side effect or extreme claim.";
        }

        if (prompt.contains("best sign") || prompt.contains("understands")) {
            return "Think about what would genuinely show understanding of " + keyword + " in practice.";
        }

        if (prompt.contains("distractor")) {
            return "Focus on what makes an answer seem believable while still missing the main idea.";
        }

        if (hasSelection) {
            return "Re-read the question and check whether your current choice answers exactly what is being asked about " + keyword + ".";
        }

        return "Use the wording of this question as your guide and focus on the main concept it is testing in " + keyword + ".";
    }

    private String buildHintPrompt(Quiz quiz, Question question, Long selectedChoiceId) {
        String topic = quiz != null && !isBlank(quiz.getKeyword()) ? quiz.getKeyword().trim() : "General knowledge";
        String title = quiz != null && !isBlank(quiz.getTitre()) ? quiz.getTitre().trim() : "Quiz";
        String selectedChoiceText = findChoiceById(question, selectedChoiceId) != null
                ? findChoiceById(question, selectedChoiceId).getChoice()
                : "";

        StringBuilder prompt = new StringBuilder();
        prompt.append("Quiz title: ").append(title).append("\n");
        prompt.append("Topic: ").append(topic).append("\n");
        prompt.append("Question: ").append(question.getQuestion().trim()).append("\n");
        prompt.append("Answer candidates:\n");

        char label = 'A';
        for (Choice choice : question.getChoices()) {
            if (choice != null && !isBlank(choice.getChoice())) {
                prompt.append(label).append(". ").append(choice.getChoice().trim()).append("\n");
                label++;
            }
        }

        if (!isBlank(selectedChoiceText)) {
            prompt.append("Current learner selection: ").append(selectedChoiceText.trim()).append("\n");
        }

        prompt.append("""
                Write exactly one short hint for this question.
                Requirements:
                - 1 sentence only
                - help the learner get closer conceptually
                - do not reveal or imply the right answer
                - do not refer to any answer candidate directly
                - do not say option, choice, eliminate, rule out, correct answer, or wrong answer
                """);
        return prompt.toString();
    }

    private String sanitizeHintText(String hint) {
        String normalized = hint == null ? "" : hint.trim();
        if (normalized.isEmpty()) {
            return normalized;
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("option")
                || lower.contains("options")
                || lower.contains("choice")
                || lower.contains("choices")
                || lower.contains("rule out")
                || lower.contains("ruling out")
                || lower.contains("eliminate")
                || lower.contains("correct answer")
                || lower.contains("wrong answer")) {
            return "";
        }

        return normalized.replaceAll("\\s+", " ");
    }

    private String buildQuestionMetadataPrompt(String quizKeyword, String quizTitle, String questionText, List<String> choices) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Quiz title: ").append(quizTitle).append("\n");
        prompt.append("Quiz keyword: ").append(quizKeyword).append("\n");
        prompt.append("Question: ").append(questionText).append("\n");
        if (choices != null && !choices.isEmpty()) {
            prompt.append("Choices:\n");
            char label = 'A';
            for (String choice : choices) {
                prompt.append(label).append(". ").append(choice).append("\n");
                label++;
            }
        }
        prompt.append("""
                Classify this question.
                Rules:
                - TOPIC should be short and specific
                - use the actual subject of the question, not a generic label
                - DIFFICULTY must be exactly one of: Easy, Medium, Hard
                """);
        return prompt.toString();
    }

    private Optional<QuestionMetadata> parseQuestionMetadata(String content, String fallbackTopic) {
        Matcher topicMatcher = METADATA_TOPIC_PATTERN.matcher(content);
        Matcher difficultyMatcher = METADATA_DIFFICULTY_PATTERN.matcher(content);
        boolean hasTopic = topicMatcher.find();
        boolean hasDifficulty = difficultyMatcher.find();
        if (!hasTopic && !hasDifficulty) {
            return Optional.empty();
        }

        String topic = hasTopic ? topicMatcher.group(1).trim() : fallbackTopic;
        String difficulty = hasDifficulty ? difficultyMatcher.group(1).trim() : "Medium";
        topic = safeText(topic, fallbackTopic);
        difficulty = normalizeDifficultyLabel(difficulty);
        return Optional.of(new QuestionMetadata(topic, difficulty));
    }

    private QuestionMetadata buildFallbackMetadata(String fallbackTopic, String questionText, List<String> choices) {
        String normalizedQuestion = questionText == null ? "" : questionText.toLowerCase(Locale.ROOT);
        int signalScore = normalizedQuestion.length();
        if (choices != null) {
            signalScore += choices.stream().mapToInt(choice -> choice == null ? 0 : choice.length()).sum() / Math.max(1, choices.size());
        }

        String difficulty;
        if (signalScore < 90 && !normalizedQuestion.contains("why") && !normalizedQuestion.contains("best")) {
            difficulty = "Easy";
        } else if (signalScore > 180 || normalizedQuestion.contains("why") || normalizedQuestion.contains("distractor")) {
            difficulty = "Hard";
        } else {
            difficulty = "Medium";
        }

        String topic = fallbackTopic;
        if (normalizedQuestion.contains("life")) {
            topic = "Life";
        } else if (normalizedQuestion.contains("russian")) {
            topic = "Russian";
        } else if (normalizedQuestion.contains("valorant")) {
            topic = "Valorant";
        }

        return new QuestionMetadata(topic, difficulty);
    }

    private String normalizeDifficultyLabel(String difficulty) {
        if (isBlank(difficulty)) {
            return "Medium";
        }
        String normalized = difficulty.trim().toLowerCase(Locale.ROOT);
        if ("easy".equals(normalized)) {
            return "Easy";
        }
        if ("hard".equals(normalized)) {
            return "Hard";
        }
        return "Medium";
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
                "Focus on the main purpose, not just a tool or side effect.",
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
                "Which sign best shows that someone understands " + topic + "?",
                "Real understanding shows up when the person can explain the concept clearly.",
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

    public record QuestionMetadata(String topic, String difficulty) {
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
