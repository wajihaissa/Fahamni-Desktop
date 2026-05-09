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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiQuizAssistantService {
    private static final int MIN_GENERATED_QUESTION_COUNT = 2;
    private static final int MAX_GENERATED_QUESTION_COUNT = 6;

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
        int normalizedCount = Math.max(MIN_GENERATED_QUESTION_COUNT, Math.min(MAX_GENERATED_QUESTION_COUNT, questionCount));
        String normalizedDifficulty = safeText(difficulty, "Medium");

        if (!isOpenAiReady()) {
            return new GeneratedQuizDraft(
                    null,
                    "Unavailable",
                    false,
                    buildAiUnavailableMessage()
            );
        }

        Optional<Quiz> remoteDraft = tryGenerateQuizDraftWithOpenAi(
                normalizedTopic,
                normalizedTitle,
                normalizedCount,
                normalizedDifficulty
        );

        if (remoteDraft.isPresent()) {
            Quiz sanitizedQuiz = sanitizeGeneratedQuizDraft(remoteDraft.get(), normalizedTopic, normalizedTitle, normalizedCount, normalizedDifficulty);
            return new GeneratedQuizDraft(sanitizedQuiz, resolveAiProviderLabel(), true, "Quiz generated with " + resolveAiProviderLabel() + ".");
        }

        return new GeneratedQuizDraft(
                null,
                resolveAiProviderLabel(),
                false,
                resolveAiProviderLabel() + " quiz generation did not return a usable draft. Please try again."
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

    public boolean requiresCodeSnippet(String questionText, List<String> choices) {
        String normalizedQuestion = safeText(questionText, "");
        List<String> normalizedChoices = choices == null ? List.of() : choices.stream()
                .filter(choice -> !isBlank(choice))
                .map(String::trim)
                .toList();

        Optional<Boolean> remoteDecision = tryDetectQuestionRequiresCodeSnippetWithOpenAi(normalizedQuestion, normalizedChoices);
        if (remoteDecision.isPresent()) {
            return remoteDecision.get();
        }

        Question fallbackQuestion = new Question();
        fallbackQuestion.setQuestion(normalizedQuestion);
        return fallbackQuestion.looksLikeCodeOutputPrompt();
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

    public CodeAnswerEvaluation evaluateCodeAnswer(Question question, String submittedAnswer) {
        if (question == null || !question.isCodeQuestion()) {
            return new CodeAnswerEvaluation(false, "Unavailable", false, "No code question is available for evaluation.");
        }
        if (isBlank(submittedAnswer)) {
            return new CodeAnswerEvaluation(false, "Local validation", true, "No answer was submitted.");
        }
        if (!isOpenAiReady()) {
            return new CodeAnswerEvaluation(false, "Unavailable", false, buildAiUnavailableMessage());
        }

        Optional<Boolean> remoteDecision = tryEvaluateCodeAnswerWithOpenAi(question, submittedAnswer);
        if (remoteDecision.isPresent()) {
            return new CodeAnswerEvaluation(remoteDecision.get(), resolveAiProviderLabel(), true, "Code answer evaluated with " + resolveAiProviderLabel() + ".");
        }

        return new CodeAnswerEvaluation(false, resolveAiProviderLabel(), false, resolveAiProviderLabel() + " code evaluation is unavailable right now.");
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

    protected Optional<Boolean> tryEvaluateCodeAnswerWithOpenAi(Question question, String submittedAnswer) {
        String apiKey = getOpenAiApiKey();
        String model = resolveRequestedModelName();
        if (isBlank(apiKey) || isBlank(model) || question == null || isBlank(question.getQuestion()) || isBlank(submittedAnswer)) {
            return Optional.empty();
        }

        String prompt = buildCodeEvaluationPrompt(question, submittedAnswer);
        String requestBody = buildChatRequestBody(
                model,
                "You evaluate learner code answers. Reply with exactly one line: VERDICT: CORRECT or VERDICT: INCORRECT",
                prompt,
                0.1
        );

        try {
            Optional<String> content = sendChatCompletion(apiKey, requestBody)
                    .map(String::trim)
                    .filter(value -> !value.isBlank());

            return content.flatMap(this::parseCodeEvaluationVerdict);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    protected Optional<Boolean> tryDetectQuestionRequiresCodeSnippetWithOpenAi(String questionText, List<String> choices) {
        String apiKey = getOpenAiApiKey();
        String model = resolveRequestedModelName();
        if (isBlank(apiKey) || isBlank(model) || isBlank(questionText)) {
            return Optional.empty();
        }

        String prompt = buildCodeSnippetDetectionPrompt(questionText, choices);
        String requestBody = buildChatRequestBody(
                model,
                "You classify whether a quiz question requires a displayed code snippet. Reply with exactly one line: REQUIRES_CODE_SNIPPET: YES or REQUIRES_CODE_SNIPPET: NO",
                prompt,
                0.1
        );

        try {
            Optional<String> content = sendChatCompletion(apiKey, requestBody)
                    .map(String::trim)
                    .filter(value -> !value.isBlank());

            return content.flatMap(this::parseCodeSnippetRequirementVerdict);
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
        if (question.isCodeOutputQuestion() && !isBlank(question.getStarterCode())) {
            prompt.append("Code shown:\n").append(question.getStarterCode().trim()).append("\n");
        }
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

    private String buildCodeEvaluationPrompt(Question question, String submittedAnswer) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Question: ").append(safeText(question.getQuestion(), "")).append("\n");
        prompt.append("Code language: ").append(safeText(question.getCodeLanguage(), "General code")).append("\n");
        if (!isBlank(question.getStarterCode())) {
            prompt.append("Starter code:\n").append(question.getStarterCode().trim()).append("\n");
        }
        prompt.append("Reference answer:\n").append(safeText(question.getExpectedAnswer(), "")).append("\n");
        if (!isBlank(question.getExplanation())) {
            prompt.append("Explanation or intent:\n").append(question.getExplanation().trim()).append("\n");
        }
        prompt.append("Learner answer:\n").append(submittedAnswer.trim()).append("\n");
        prompt.append("""
                Decide whether the learner answer correctly solves the task.
                Rules:
                - accept alternative but logically correct implementations
                - ignore formatting differences
                - reject answers that do not satisfy the requested behavior
                - be strict about actual correctness, not style
                - reply with exactly one line:
                VERDICT: CORRECT
                or
                VERDICT: INCORRECT
                """);
        return prompt.toString();
    }

    private String buildCodeSnippetDetectionPrompt(String questionText, List<String> choices) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Question: ").append(safeText(questionText, "")).append("\n");
        if (choices != null && !choices.isEmpty()) {
            prompt.append("Choices:\n");
            char label = 'A';
            for (String choice : choices) {
                prompt.append(label).append(". ").append(choice).append("\n");
                label++;
            }
        }
        prompt.append("""
                Decide whether a learner would need to see a code snippet to answer this question properly.
                Mark YES when the wording refers to code output, execution, a snippet, a program fragment, or analyzing shown code.
                Mark NO for normal concept questions, even if they are about programming.
                Reply with exactly one line:
                REQUIRES_CODE_SNIPPET: YES
                or
                REQUIRES_CODE_SNIPPET: NO
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

    private Optional<Boolean> parseCodeEvaluationVerdict(String content) {
        if (isBlank(content)) {
            return Optional.empty();
        }

        Matcher matcher = Pattern.compile("(?im)^VERDICT\\s*:\\s*(CORRECT|INCORRECT)\\s*$").matcher(content.trim());
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of("CORRECT".equalsIgnoreCase(matcher.group(1)));
    }

    private Optional<Boolean> parseCodeSnippetRequirementVerdict(String content) {
        if (isBlank(content)) {
            return Optional.empty();
        }

        Matcher matcher = Pattern.compile("(?im)^REQUIRES_CODE_SNIPPET\\s*:\\s*(YES|NO)\\s*$").matcher(content.trim());
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of("YES".equalsIgnoreCase(matcher.group(1)));
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
                "You generate original educational quizzes. Follow the exact response template and never use filler or generic template-style questions.",
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

        String questionType = normalizeQuestionType(extractPrefixedValue(block, "TYPE", Question.TYPE_MULTIPLE_CHOICE));
        String topic = extractPrefixedValue(block, "TOPIC", "");
        String difficulty = normalizeDifficultyLabel(extractPrefixedValue(block, "DIFFICULTY", "Medium"));
        String hint = extractPrefixedValue(block, "HINT", "");
        String explanation = extractPrefixedValue(block, "EXPLANATION", "");

        Question question = new Question();
        question.setQuestion(questionText.trim());
        question.setQuestionType(questionType);
        question.setTopic(topic.trim());
        question.setDifficulty(difficulty);
        question.setHint(hint.trim());
        question.setExplanation(explanation.trim());

        if (question.looksLikeCodeOutputPrompt() && !Question.TYPE_CODE.equals(questionType)) {
            question.setQuestionType(Question.TYPE_CODE_OUTPUT);
            questionType = Question.TYPE_CODE_OUTPUT;
        }

        if (Question.TYPE_CODE.equals(questionType)) {
            question.setCodeLanguage(extractPrefixedValue(block, "CODE_LANGUAGE", "").trim());
            question.setStarterCode(decodeEscapedMultilineValue(extractPrefixedValue(block, "STARTER_CODE", "")));
            question.setExpectedAnswer(decodeEscapedMultilineValue(extractPrefixedValue(block, "EXPECTED_ANSWER", "")));
            question.setCodeEvaluationMode(normalizeCodeEvaluationMode(
                    extractPrefixedValue(block, "CODE_EVALUATION_MODE", Question.CODE_EVALUATION_STRICT)
            ));
            return isBlank(question.getExpectedAnswer()) ? null : question;
        }

        if (Question.TYPE_CODE_OUTPUT.equals(questionType)) {
            question.setCodeLanguage(extractPrefixedValue(block, "CODE_LANGUAGE", "").trim());
            question.setStarterCode(decodeEscapedMultilineValue(extractPrefixedValue(block, "STARTER_CODE", "")));
            if (isBlank(question.getStarterCode())) {
                return null;
            }
        }

        if (question.looksLikeCodeOutputPrompt() && isBlank(question.getStarterCode())) {
            return null;
        }

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
        normalized = normalized.replaceAll("(?im)^\\s*(?:[-*]\\s*)?(TITLE|KEYWORD|TYPE|TOPIC|DIFFICULTY|TEXT|HINT|EXPLANATION|CODE_LANGUAGE|STARTER_CODE|EXPECTED_ANSWER|CODE_EVALUATION_MODE)\\s*[:\\-]?\\s*", "$1: ");
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
                    || line.matches("(?i)^(TYPE|TOPIC|DIFFICULTY|ANSWER|HINT|EXPLANATION|CODE_LANGUAGE|STARTER_CODE|EXPECTED_ANSWER|CODE_EVALUATION_MODE)\\s*:.*$")) {
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

    private Quiz sanitizeGeneratedQuizDraft(Quiz quiz, String topic, String title, int questionCount, String difficulty) {
        if (quiz == null) {
            return buildFallbackQuizDraft(topic, title, questionCount, difficulty);
        }

        if (isBlank(quiz.getTitre())) {
            quiz.setTitre(title);
        }
        if (isBlank(quiz.getKeyword())) {
            quiz.setKeyword(topic);
        }

        List<Question> questions = quiz.getQuestions();
        if (questions == null || questions.isEmpty()) {
            return buildFallbackQuizDraft(topic, title, questionCount, difficulty);
        }

        for (Question question : questions) {
            if (question == null) {
                continue;
            }
            if (question.isMultipleChoiceQuestion()) {
                repairGeneratedMultipleChoiceQuestion(question);
            }
        }

        return quiz;
    }

    private void repairGeneratedMultipleChoiceQuestion(Question question) {
        if (question == null || question.getChoices() == null || question.getChoices().isEmpty()) {
            return;
        }

        Choice originalCorrectChoice = findCorrectChoice(question);
        String correctChoiceText = originalCorrectChoice != null ? safeText(originalCorrectChoice.getChoice(), "") : "";

        List<String> uniqueWrongChoices = new ArrayList<>();
        Set<String> seenChoices = new LinkedHashSet<>();

        if (!isBlank(correctChoiceText)) {
            seenChoices.add(correctChoiceText.trim().toLowerCase(Locale.ROOT));
        }

        for (Choice choice : question.getChoices()) {
            if (choice == null || isBlank(choice.getChoice()) || Boolean.TRUE.equals(choice.getIsCorrect())) {
                continue;
            }
            String trimmedChoice = choice.getChoice().trim();
            String normalizedChoice = trimmedChoice.toLowerCase(Locale.ROOT);
            if (seenChoices.add(normalizedChoice)) {
                uniqueWrongChoices.add(trimmedChoice);
            }
        }

        List<String> fallbackDistractors = buildFallbackDistractors(question, correctChoiceText);
        for (String distractor : fallbackDistractors) {
            if (isBlank(distractor)) {
                continue;
            }
            String trimmedDistractor = distractor.trim();
            String normalizedDistractor = trimmedDistractor.toLowerCase(Locale.ROOT);
            if (seenChoices.add(normalizedDistractor)) {
                uniqueWrongChoices.add(trimmedDistractor);
            }
            if (uniqueWrongChoices.size() >= 3) {
                break;
            }
        }

        while (uniqueWrongChoices.size() < 3) {
            String generated = "Alternative " + (uniqueWrongChoices.size() + 2);
            if (seenChoices.add(generated.toLowerCase(Locale.ROOT))) {
                uniqueWrongChoices.add(generated);
            }
        }

        question.getChoices().clear();

        Choice repairedCorrectChoice = new Choice();
        repairedCorrectChoice.setChoice(!isBlank(correctChoiceText) ? correctChoiceText.trim() : "Correct answer");
        repairedCorrectChoice.setIsCorrect(true);
        question.addChoice(repairedCorrectChoice);

        for (int index = 0; index < 3; index++) {
            Choice wrongChoice = new Choice();
            wrongChoice.setChoice(uniqueWrongChoices.get(index));
            wrongChoice.setIsCorrect(false);
            question.addChoice(wrongChoice);
        }
    }

    private List<String> buildFallbackDistractors(Question question, String correctChoiceText) {
        List<String> distractors = new ArrayList<>();
        String normalizedCorrectChoice = safeText(correctChoiceText, "").trim();

        if (normalizedCorrectChoice.matches("-?\\d+")) {
            try {
                int numericValue = Integer.parseInt(normalizedCorrectChoice);
                distractors.add(String.valueOf(numericValue + 1));
                distractors.add(String.valueOf(numericValue - 1));
                distractors.add(String.valueOf(numericValue + 2));
            } catch (NumberFormatException ignored) {
                // Fall through to generic distractors.
            }
        } else if (normalizedCorrectChoice.equalsIgnoreCase("true") || normalizedCorrectChoice.equalsIgnoreCase("false")) {
            distractors.add(normalizedCorrectChoice.equalsIgnoreCase("true") ? "false" : "true");
            distractors.add("Compilation error");
            distractors.add("Runtime error");
        } else if (!normalizedCorrectChoice.isBlank()) {
            distractors.add(normalizedCorrectChoice + " ");
            distractors.add("Compilation error");
            distractors.add("Runtime error");
            if (question != null && question.isCodeOutputQuestion()) {
                distractors.add('"' + normalizedCorrectChoice.replace("\"", "") + '"');
            }
        }

        distractors.add("None of the above");
        distractors.add("The code does not compile");
        distractors.add("The code throws a runtime error");
        return distractors;
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

        templates.add(new QuestionTemplate(
                "When reviewing work related to " + topic + ", what is the best sign that the approach is actually correct?",
                "Look for evidence that the approach matches the goal and constraints, not just that it looks familiar.",
                "The strongest signal is that the approach fits the goal, respects the constraints, and can be explained clearly from first principles.",
                "It solves the stated problem while matching the context and constraints",
                "It uses the most advanced-sounding terminology available",
                "It copies a popular example without checking whether the situation is the same",
                "It adds extra complexity even when the simpler path already works",
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
        boolean allowCodeQuestions = allowsCodeQuestions(topic);
        int targetCodeQuestionCount = recommendCodeQuestionCount(topic, questionCount, difficulty);
        return """
                Create an original educational quiz for topic "%s".
                Quiz title: %s
                Difficulty: %s
                Number of questions: %d

                Use exactly this format:
                TITLE: ...
                KEYWORD: ...
                QUESTION 1:
                TYPE: MULTIPLE_CHOICE or CODE or CODE_OUTPUT
                TOPIC: ...
                DIFFICULTY: Easy|Medium|Hard
                TEXT: ...
                A: ...               (for MULTIPLE_CHOICE and CODE_OUTPUT)
                B: ...               (for MULTIPLE_CHOICE and CODE_OUTPUT)
                C: ...               (for MULTIPLE_CHOICE and CODE_OUTPUT)
                D: ...               (for MULTIPLE_CHOICE and CODE_OUTPUT)
                ANSWER: A            (for MULTIPLE_CHOICE and CODE_OUTPUT)
                CODE_LANGUAGE: ...   (for CODE and CODE_OUTPUT)
                STARTER_CODE: ...    (for CODE and CODE_OUTPUT, keep on one line, use \\n for line breaks if needed)
                EXPECTED_ANSWER: ... (only for CODE, keep on one line, use \\n for line breaks if needed)
                CODE_EVALUATION_MODE: STRICT or AI (only for CODE)
                HINT: ...
                EXPLANATION: ...

                Continue the same template for every question.
                Requirements:
                - write genuinely new questions, not generic placeholders or templates
                - every question must feel specific to the topic, not reusable boilerplate
                - include scenario-based or syntax-based questions when helpful
                - for MULTIPLE_CHOICE and CODE_OUTPUT questions, provide 4 choices and exactly 1 correct answer
                - use CODE_OUTPUT for "what is the output of this code" style questions
                - for CODE questions, use CODE_EVALUATION_MODE: STRICT when the answer should match a precise syntax pattern
                - use CODE_EVALUATION_MODE: AI when multiple different correct solutions are possible
                - for CODE questions, provide a precise expected answer and explain the intended behavior clearly
                - concise hints
                - concise explanations
                - no markdown
                - avoid repeating the same wording pattern across questions
                - if the topic is programming, software, SQL, or scripting, you may include a mix of CODE and CODE_OUTPUT questions
                - include at least 1 CODE_OUTPUT question when it naturally fits the topic
                - never omit TYPE, TOPIC, or DIFFICULTY
                """.formatted(topic, title, difficulty, questionCount, allowCodeQuestions ? targetCodeQuestionCount : 0);
    }

    private boolean allowsCodeQuestions(String topic) {
        String normalizedTopic = topic == null ? "" : topic.toLowerCase(Locale.ROOT);
        return containsAny(normalizedTopic, "java", "python", "javascript", "typescript", "coding", "programming",
                "software", "development", "sql", "database", "script", "bash", "shell", "c++", "c#", "php");
    }

    private int recommendCodeQuestionCount(String topic, int questionCount, String difficulty) {
        if (!allowsCodeQuestions(topic)) {
            return 0;
        }

        String normalizedDifficulty = safeText(difficulty, "Medium").toLowerCase(Locale.ROOT);
        if ("hard".equals(normalizedDifficulty)) {
            return Math.min(2, Math.max(1, questionCount / 2));
        }
        return 1;
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

    private String normalizeQuestionType(String questionType) {
        if (isBlank(questionType)) {
            return Question.TYPE_MULTIPLE_CHOICE;
        }

        String normalized = questionType.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        if ("code_editor".equals(normalized) || "code".equals(normalized)) {
            return Question.TYPE_CODE;
        }
        if (Question.TYPE_CODE_OUTPUT.equals(normalized) || "output".equals(normalized) || "codeoutput".equals(normalized)) {
            return Question.TYPE_CODE_OUTPUT;
        }
        return Question.TYPE_MULTIPLE_CHOICE;
    }

    private String normalizeCodeEvaluationMode(String value) {
        if (isBlank(value)) {
            return Question.CODE_EVALUATION_STRICT;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Question.CODE_EVALUATION_AI.equals(normalized)
                ? Question.CODE_EVALUATION_AI
                : Question.CODE_EVALUATION_STRICT;
    }

    private String decodeEscapedMultilineValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\n", "\n").replace("\\t", "\t").trim();
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

    public record CodeAnswerEvaluation(boolean correct, String provider, boolean success, String message) {
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
