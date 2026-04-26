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

    private static final Pattern QUESTION_BLOCK_PATTERN =
            Pattern.compile("QUESTION\\s+(\\d+)\\s*:(.*?)(?=QUESTION\\s+\\d+\\s*:|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern METADATA_TOPIC_PATTERN =
            Pattern.compile("(?im)^TOPIC\\s*:\\s*(.+)$");
    private static final Pattern METADATA_DIFFICULTY_PATTERN =
            Pattern.compile("(?im)^DIFFICULTY\\s*:\\s*(.+)$");
    private static final String OPENAI_CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";
    private static final String GITHUB_MODELS_CHAT_COMPLETIONS_URL = "https://models.github.ai/inference/chat/completions";

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

        if (!isOpenAiReady()) {
            Quiz fallbackQuiz = buildFallbackQuizDraft(normalizedTopic, normalizedTitle, normalizedCount, normalizedDifficulty);
            return new GeneratedQuizDraft(
                    fallbackQuiz,
                    "Local fallback",
                    true,
                    buildAiUnavailableMessage() + " A local quiz draft was generated instead."
            );
        }

        Optional<Quiz> remoteDraft = tryGenerateQuizDraftWithOpenAi(
                normalizedTopic,
                normalizedTitle,
                normalizedCount,
                normalizedDifficulty
        );

        if (remoteDraft.isPresent()) {
            return new GeneratedQuizDraft(remoteDraft.get(), resolveAiProviderLabel(), true, "Quiz generated with " + resolveAiProviderLabel() + ".");
        }

        Quiz fallbackQuiz = buildFallbackQuizDraft(normalizedTopic, normalizedTitle, normalizedCount, normalizedDifficulty);
        return new GeneratedQuizDraft(
                fallbackQuiz,
                "Local fallback",
                true,
                resolveAiProviderLabel() + " quiz generation did not return a usable draft. A local quiz draft was generated instead."
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

    public GeneratedHint generateHint(Quiz quiz, Question question, Long selectedChoiceId) {
        if (question == null) {
            return new GeneratedHint(null, "Unavailable", false, "No question is available for hint generation.");
        }

        String fallbackHint = buildFallbackHint(quiz, question, selectedChoiceId);

        if (!isOpenAiReady()) {
            return new GeneratedHint(
                    fallbackHint,
                    "Local fallback",
                    true,
                    buildAiUnavailableMessage() + " A local hint was generated instead."
            );
        }

        Optional<String> remoteHint = tryGenerateHintWithOpenAi(quiz, question, selectedChoiceId);
        if (remoteHint.isPresent()) {
            return new GeneratedHint(remoteHint.get(), resolveAiProviderLabel(), true, "Hint generated with " + resolveAiProviderLabel() + ".");
        }

        return new GeneratedHint(
                fallbackHint,
                "Local fallback",
                true,
                resolveAiProviderLabel() + " hint generation is unavailable right now. A local hint was generated instead."
        );
    }

    protected Optional<String> tryGenerateHintWithOpenAi(Quiz quiz, Question question, Long selectedChoiceId) {
        String apiKey = getOpenAiApiKey();
        String model = resolveRequestedModelName();
        if (isBlank(apiKey) || isBlank(model) || question == null || isBlank(question.getQuestion())) {
            return Optional.empty();
        }

        String prompt = buildHintPrompt(quiz, question, selectedChoiceId);
        String requestBody = buildChatRequestBody(
                model,
                "You write short, non-revealing quiz hints. Never mention the correct answer. Never rule out answer choices. Never say option, choice, eliminate, or rule out.",
                prompt,
                0.7
        );

        try {
            Optional<String> content = sendChatCompletion(apiKey, requestBody)
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

    protected Optional<QuestionMetadata> tryInferQuestionMetadataWithOpenAi(
            String quizKeyword,
            String quizTitle,
            String questionText,
            List<String> choices
    ) {
        String apiKey = getOpenAiApiKey();
        String model = resolveRequestedModelName();
        if (isBlank(apiKey) || isBlank(model) || isBlank(questionText)) {
            return Optional.empty();
        }

        String prompt = buildQuestionMetadataPrompt(quizKeyword, quizTitle, questionText, choices);
        String requestBody = buildChatRequestBody(
                model,
                "You classify quiz questions. Reply using exactly two lines: TOPIC: ... and DIFFICULTY: Easy|Medium|Hard",
                prompt,
                0.2
        );

        try {
            Optional<String> content = sendChatCompletion(apiKey, requestBody);
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

    private String buildFallbackHint(Quiz quiz, Question question, Long selectedChoiceId) {
        if (question == null || isBlank(question.getQuestion())) {
            return "Re-read the question carefully and focus on the core concept it is testing.";
        }

        String topic = resolveHintTopic(quiz, question);
        String lowerQuestion = question.getQuestion().trim().toLowerCase(Locale.ROOT);
        boolean hasSelection = findChoiceById(question, selectedChoiceId) != null;

        if (containsAny(lowerQuestion, " not ", " except ", "least", "false")) {
            return hasSelection
                    ? "Focus on the detail that breaks the usual pattern in " + topic + " and pay close attention to the qualifier in the question."
                    : "Look for the detail that breaks the usual pattern in " + topic + " and pay close attention to the qualifier in the question.";
        }

        if (containsAny(lowerQuestion, "why", "reason", "cause")) {
            return hasSelection
                    ? "Think about the underlying cause or principle in " + topic + " rather than the wording that feels most familiar."
                    : "Think about the underlying cause or principle in " + topic + " rather than surface wording.";
        }

        if (containsAny(lowerQuestion, "first", "initial", "before", "begin")) {
            return hasSelection
                    ? "Think about what must logically happen first in " + topic + " before later steps can make sense."
                    : "Focus on the earliest prerequisite in " + topic + " before any later step can happen.";
        }

        if (containsAny(lowerQuestion, "best", "most", "main", "primarily")) {
            return hasSelection
                    ? "Compare the main purpose behind the concept in " + topic + " and favor the idea that matches the goal most directly."
                    : "Compare the main purpose behind the concept in " + topic + " and focus on the idea that matches the goal most directly.";
        }

        if (containsAny(lowerQuestion, "how", "process", "works", "function")) {
            return hasSelection
                    ? "Picture how the concept in " + topic + " actually works step by step, not just what it is called."
                    : "Picture how the concept in " + topic + " actually works step by step, not just what it is called.";
        }

        return hasSelection
                ? "Re-read the question and focus on the core role or definition involved in " + topic + ", not just the most familiar wording."
                : "Focus on the core role or definition involved in " + topic + " and match it to what the question is really asking.";
    }

    private String resolveHintTopic(Quiz quiz, Question question) {
        String topic = question != null && !isBlank(question.getTopic())
                ? question.getTopic()
                : quiz != null ? quiz.getKeyword() : null;
        String normalizedTopic = safeText(topic, "this topic")
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return normalizedTopic.isBlank() ? "this topic" : normalizedTopic;
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

    protected Optional<Quiz> tryGenerateQuizDraftWithOpenAi(String topic, String title, int questionCount, String difficulty) {
        String apiKey = getOpenAiApiKey();
        String model = resolveRequestedModelName();
        if (isBlank(apiKey) || isBlank(model)) {
            return Optional.empty();
        }

        String prompt = buildGenerationPrompt(topic, title, questionCount, difficulty);
        String requestBody = buildChatRequestBody(
                model,
                "You generate short educational multiple-choice quizzes. Follow the exact response template.",
                prompt,
                0.8
        );

        try {
            Optional<String> content = sendChatCompletion(apiKey, requestBody);
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
        int contentKeyIndex = responseBody == null ? -1 : responseBody.indexOf("\"content\"");
        if (contentKeyIndex < 0) {
            return Optional.empty();
        }

        int colonIndex = responseBody.indexOf(':', contentKeyIndex);
        if (colonIndex < 0) {
            return Optional.empty();
        }

        int valueStartIndex = skipWhitespace(responseBody, colonIndex + 1);
        if (valueStartIndex < 0 || valueStartIndex >= responseBody.length()) {
            return Optional.empty();
        }

        if (responseBody.charAt(valueStartIndex) == '"') {
            String stringValue = readJsonString(responseBody, valueStartIndex);
            return stringValue == null ? Optional.empty() : Optional.of(stringValue);
        }

        // GitHub/OpenAI responses may also represent content as an array of typed segments.
        if (responseBody.charAt(valueStartIndex) == '[') {
            int textKeyIndex = responseBody.indexOf("\"text\"", valueStartIndex);
            if (textKeyIndex < 0) {
                return Optional.empty();
            }

            int textColonIndex = responseBody.indexOf(':', textKeyIndex);
            if (textColonIndex < 0) {
                return Optional.empty();
            }

            int textValueStartIndex = skipWhitespace(responseBody, textColonIndex + 1);
            if (textValueStartIndex < 0 || textValueStartIndex >= responseBody.length() || responseBody.charAt(textValueStartIndex) != '"') {
                return Optional.empty();
            }

            String stringValue = readJsonString(responseBody, textValueStartIndex);
            return stringValue == null ? Optional.empty() : Optional.of(stringValue);
        }

        return Optional.empty();
    }

    private int skipWhitespace(String value, int startIndex) {
        if (value == null) {
            return -1;
        }

        int index = startIndex;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private String readJsonString(String value, int openingQuoteIndex) {
        if (value == null || openingQuoteIndex < 0 || openingQuoteIndex >= value.length() || value.charAt(openingQuoteIndex) != '"') {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        boolean escaping = false;

        for (int index = openingQuoteIndex + 1; index < value.length(); index++) {
            char current = value.charAt(index);

            if (escaping) {
                builder.append('\\').append(current);
                escaping = false;
                continue;
            }

            if (current == '\\') {
                escaping = true;
                continue;
            }

            if (current == '"') {
                return unescapeJson(builder.toString());
            }

            builder.append(current);
        }

        return null;
    }

    private Optional<String> sendChatCompletion(String apiKey, String requestBody) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(resolveChatCompletionsUrl()))
                .timeout(Duration.ofSeconds(25))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json");

        if (isGitHubModelsConfigured()) {
            requestBuilder.header("Accept", "application/vnd.github+json");
            requestBuilder.header("X-GitHub-Api-Version", "2022-11-28");
        }

        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return Optional.empty();
        }

        return extractFirstMessageContent(response.body());
    }

    protected boolean isOpenAiReady() {
        return !isBlank(getOpenAiApiKey()) && !isBlank(getOpenAiModel()) && (looksLikeOpenAiApiKey(getOpenAiApiKey()) || looksLikeGitHubToken(getOpenAiApiKey()));
    }

    private String getOpenAiApiKey() {
        return EnvConfig.get("OPENAI_API_KEY");
    }

    private String getOpenAiModel() {
        return EnvConfig.get("OPENAI_MODEL");
    }

    private boolean looksLikeOpenAiApiKey(String apiKey) {
        if (isBlank(apiKey)) {
            return false;
        }

        String normalized = apiKey.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("sk-") || normalized.startsWith("sess-");
    }

    private boolean looksLikeGitHubToken(String apiKey) {
        if (isBlank(apiKey)) {
            return false;
        }

        String normalized = apiKey.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("github_pat_")
                || normalized.startsWith("ghp_")
                || normalized.startsWith("gho_")
                || normalized.startsWith("ghu_")
                || normalized.startsWith("ghs_")
                || normalized.startsWith("ghr_");
    }

    private boolean isGitHubModelsConfigured() {
        return looksLikeGitHubToken(getOpenAiApiKey());
    }

    private String resolveChatCompletionsUrl() {
        return isGitHubModelsConfigured()
                ? GITHUB_MODELS_CHAT_COMPLETIONS_URL
                : OPENAI_CHAT_COMPLETIONS_URL;
    }

    private String resolveRequestedModelName() {
        String model = getOpenAiModel();
        if (!isGitHubModelsConfigured() || isBlank(model)) {
            return model;
        }

        if (model.contains("/")) {
            return model.trim();
        }

        String normalized = model.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("gpt-") || normalized.startsWith("o1") || normalized.startsWith("o3") || normalized.startsWith("o4")) {
            return "openai/" + model.trim();
        }
        return model.trim();
    }

    protected String resolveAiProviderLabel() {
        return isGitHubModelsConfigured() ? "GitHub Models" : "OpenAI";
    }

    protected String buildAiUnavailableMessage() {
        String apiKey = getOpenAiApiKey();
        if (isBlank(apiKey) || isBlank(getOpenAiModel())) {
            return resolveAiProviderLabel() + " is not configured. Add valid OPENAI_API_KEY and OPENAI_MODEL values before using AI generation.";
        }
        if (!looksLikeOpenAiApiKey(apiKey) && !looksLikeGitHubToken(apiKey)) {
            return "OPENAI_API_KEY does not look like a supported OpenAI or GitHub Models token. Update the .env value before using AI generation.";
        }
        return resolveAiProviderLabel() + " is unavailable right now. Please try again.";
    }

    private Quiz parseQuizDraft(String content, String fallbackTitle, String fallbackTopic) {
        if (isBlank(content)) {
            return null;
        }

        String normalizedContent = normalizeQuizDraftContent(content);
        Quiz quiz = new Quiz();
        quiz.setTitre(extractPrefixedValue(normalizedContent, "TITLE", fallbackTitle));
        quiz.setKeyword(extractPrefixedValue(normalizedContent, "KEYWORD", fallbackTopic));

        Matcher matcher = QUESTION_BLOCK_PATTERN.matcher(normalizedContent);
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
        String questionText = extractQuestionText(block);
        if (isBlank(questionText)) {
            return null;
        }

        String hint = extractPrefixedValue(block, "HINT", "");
        String explanation = extractPrefixedValue(block, "EXPLANATION", "");
        String answerLabel = extractAnswerLabel(block);

        List<String> choices = List.of(
                extractChoiceValue(block, "A"),
                extractChoiceValue(block, "B"),
                extractChoiceValue(block, "C"),
                extractChoiceValue(block, "D")
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

    private String normalizeQuizDraftContent(String content) {
        String normalized = content == null ? "" : content
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();

        normalized = normalized.replaceAll("(?s)```(?:[a-zA-Z0-9_-]+)?\\s*", "");
        normalized = normalized.replace("```", "");
        normalized = normalized.replaceAll("(?m)^\\s*#+\\s*", "");
        normalized = normalized.replaceAll("(?im)^\\s*\\*\\*(QUESTION\\s+\\d+)\\*\\*\\s*:?\\s*$", "$1:");
        normalized = normalized.replaceAll("(?im)^\\s*(QUESTION\\s+\\d+)\\s*$", "$1:");
        normalized = normalized.replaceAll("(?im)^\\s*(?:[-*]\\s*)?(TITLE|KEYWORD|TEXT|HINT|EXPLANATION)\\s*[:\\-]?\\s*", "$1: ");
        normalized = normalized.replaceAll("(?im)^\\s*(?:[-*]\\s*)?(?:CORRECT\\s+ANSWER|RIGHT\\s+ANSWER)\\s*[:\\-]?\\s*", "ANSWER: ");
        normalized = normalized.replaceAll("(?im)^\\s*(?:[-*]\\s*)?ANSWER\\s*[:\\-]?\\s*", "ANSWER: ");
        normalized = normalized.replaceAll("(?im)^\\s*(?:[-*]\\s*)?([ABCD])\\s*[\\)\\.:\\-]\\s*", "$1: ");
        return normalized;
    }

    private String extractQuestionText(String block) {
        String questionText = extractPrefixedValue(block, "TEXT", null);
        if (!isBlank(questionText)) {
            return questionText;
        }

        for (String rawLine : block.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (looksLikeChoiceLine(line)
                    || line.matches("(?i)^(ANSWER|HINT|EXPLANATION)\\s*:.*$")) {
                break;
            }
            return line;
        }

        return null;
    }

    private String extractChoiceValue(String block, String label) {
        return extractPrefixedValue(block, label, "");
    }

    private String extractAnswerLabel(String block) {
        String rawAnswer = extractPrefixedValue(block, "ANSWER", "A");
        if (isBlank(rawAnswer)) {
            return "A";
        }

        Matcher matcher = Pattern.compile("\\b([ABCD])\\b", Pattern.CASE_INSENSITIVE).matcher(rawAnswer);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase(Locale.ROOT);
        }
        return "A";
    }

    private boolean looksLikeChoiceLine(String line) {
        return line != null && line.matches("(?i)^[ABCD]\\s*:.*$");
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
        TopicProfile profile = buildTopicProfile(topic);
        List<QuestionTemplate> templates = new ArrayList<>();

        templates.add(new QuestionTemplate(
                "A team starts working with " + topic + " for the first time. What are they usually trying to improve first?",
                "Think about the first practical problem this topic helps people handle better.",
                "The best answer points to the main practical value of " + topic + " before advanced optimization or scale enters the picture.",
                profile.primaryOutcome(),
                "Making every decision without evidence or structure",
                "Replacing all human judgment immediately",
                "Avoiding any repeatable process",
                0
        ));

        templates.add(new QuestionTemplate(
                "You are learning " + topic + ". Which starting point builds the strongest foundation?",
                "Start where the topic becomes concrete, not where it becomes flashy.",
                "A strong foundation in " + topic + " begins with core concepts, language, and a simple example of how the pieces connect.",
                "Learn the core terms, the main workflow, and one representative example",
                "Jump straight into rare edge cases and exceptions",
                "Copy advanced solutions before understanding the basics",
                "Ignore how the topic is used in real situations",
                0
        ));

        templates.add(new QuestionTemplate(
                "Which situation best shows real understanding of " + topic + "?",
                "Look for applied reasoning, not just memorized language.",
                "Understanding is strongest when someone can explain why a choice fits the situation and apply the topic with intent.",
                "They can explain why a specific " + profile.artifact() + " fits the situation and what tradeoff it brings",
                "They repeat terms from " + topic + " without linking them to any use case",
                "They choose whatever sounds technical without checking the context",
                "They avoid explaining how the result would help in practice",
                0
        ));

        templates.add(new QuestionTemplate(
                "In a " + normalizedDifficulty + " challenge about " + topic + ", which mistake is most likely to cause trouble early?",
                "Think about the mistake that breaks the basic reasoning, not just a small detail.",
                "Early problems in " + topic + " usually come from misunderstanding the core relationship between the goal, the context, and the selected approach.",
                profile.commonMistake(),
                "Using a different font or color than expected",
                "Adding more jargon without clarifying the goal",
                "Spending more time naming things than evaluating them",
                0
        ));

        templates.add(new QuestionTemplate(
                "If someone applies " + topic + " well, what outcome should you expect to notice?",
                "Choose the result that reflects better decisions or clearer execution.",
                "Good use of " + topic + " usually shows up as a clearer path from intent to result, not as noise or complexity for its own sake.",
                profile.observableResult(),
                "More confusion about what to do next",
                "Less connection between the goal and the chosen approach",
                "A process that sounds impressive but solves nothing important",
                0
        ));

        return templates;
    }

    private TopicProfile buildTopicProfile(String topic) {
        String normalizedTopic = topic == null ? "" : topic.trim();
        String lowerTopic = normalizedTopic.toLowerCase(Locale.ROOT);

        if (containsAny(lowerTopic, "java", "python", "javascript", "programming", "coding", "development", "software")) {
            return new TopicProfile(
                    normalizedTopic,
                    "build reliable features and solve implementation problems more clearly",
                    "implementation approach",
                    "misunderstanding how the core building blocks work together",
                    "code that is easier to reason about, test, and extend"
            );
        }

        if (containsAny(lowerTopic, "sql", "database", "data", "analytics")) {
            return new TopicProfile(
                    normalizedTopic,
                    "organize information so questions can be answered accurately",
                    "query or data model",
                    "mixing unrelated data without matching the right structure",
                    "clearer answers, fewer inconsistencies, and more trustworthy results"
            );
        }

        if (containsAny(lowerTopic, "network", "routing", "cyber", "security", "cloud", "devops")) {
            return new TopicProfile(
                    normalizedTopic,
                    "move, protect, or deliver systems more reliably",
                    "configuration or network decision",
                    "treating all traffic, risks, or services as if they behave the same way",
                    "more stable delivery, fewer avoidable failures, and clearer control"
            );
        }

        if (containsAny(lowerTopic, "marketing", "branding", "sales", "business", "management")) {
            return new TopicProfile(
                    normalizedTopic,
                    "connect the offer to the right audience and decision",
                    "strategy",
                    "focusing on activity instead of whether the message fits the audience",
                    "clearer positioning and more consistent decision-making"
            );
        }

        if (containsAny(lowerTopic, "biology", "chemistry", "physics", "science", "math", "algebra")) {
            return new TopicProfile(
                    normalizedTopic,
                    "explain how a system, pattern, or rule behaves",
                    "conceptual model",
                    "memorizing isolated facts without understanding the underlying rule",
                    "better explanations, predictions, and problem-solving accuracy"
            );
        }

        return new TopicProfile(
                normalizedTopic.isBlank() ? "this topic" : normalizedTopic,
                "understand the core ideas and apply them more effectively",
                "approach",
                "copying surface details without understanding the main principle",
                "clearer reasoning and more confident real-world application"
        );
    }

    private boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) {
                return true;
            }
        }
        return false;
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

    private String buildChatRequestBody(String model, String systemPrompt, String userPrompt, double temperature) {
        return """
                {
                  "model": "%s",
                  "messages": [
                    {
                      "role": "system",
                      "content": "%s"
                    },
                    {
                      "role": "user",
                      "content": "%s"
                    }
                  ],
                  "temperature": %s
                }
                """.formatted(
                escapeJson(model),
                escapeJson(systemPrompt),
                escapeJson(userPrompt),
                temperature
        );
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

    public record GeneratedQuizDraft(Quiz quiz, String provider, boolean success, String message) {
    }

    public record GeneratedHint(String text, String provider, boolean success, String message) {
    }

    public record QuestionMetadata(String topic, String difficulty) {
    }

    private record TopicProfile(
            String topic,
            String primaryOutcome,
            String artifact,
            String commonMistake,
            String observableResult
    ) {
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
