package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.AiRoomDesignProposal;
import tn.esprit.fahamni.Models.AiRoomDesignRequest;
import tn.esprit.fahamni.Models.Equipement;
import tn.esprit.fahamni.Models.Salle;
import tn.esprit.fahamni.utils.AiApiConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AiRoomDesignService {

    private static final Pattern CAPACITY_PATTERN = Pattern.compile("(\\d{1,4})\\s*(place|places|personne|personnes|participant|participants|etudiant|etudiants)");
    private static final Set<String> HYBRID_KEYWORDS = Set.of("hybride", "hybrid", "visioconference", "visio", "captation", "stream", "camera");
    private static final Set<String> ACCESSIBILITY_KEYWORDS = Set.of("handicap", "pmr", "fauteuil", "accessibilite");

    private final AdminSalleService salleService;
    private final AiApiConfig apiConfig;
    private final AiRoomDesignApiClient apiClient;

    public AiRoomDesignService(AdminSalleService salleService) {
        this(salleService, AiApiConfig.fromEnv());
    }

    AiRoomDesignService(AdminSalleService salleService, AiApiConfig apiConfig) {
        this.salleService = Objects.requireNonNull(salleService, "salleService");
        this.apiConfig = Objects.requireNonNull(apiConfig, "apiConfig");
        this.apiClient = new AiRoomDesignApiClient(apiConfig);
    }

    public AiRoomDesignProposal generateDesign(AiRoomDesignRequest request, List<Equipement> equipementCatalog) {
        if (request == null) {
            throw new IllegalArgumentException("La demande AI est obligatoire.");
        }

        apiConfig.validate();

        List<Equipement> safeCatalog = equipementCatalog == null ? List.of() : List.copyOf(equipementCatalog);
        try {
            return generateApiDesign(request, safeCatalog);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                "Echec de l'appel API Gemini: " + normalizeApiErrorMessage(exception.getMessage()),
                exception
            );
        }
    }

    private AiRoomDesignProposal generateApiDesign(AiRoomDesignRequest request, List<Equipement> equipementCatalog) {
        String normalizedBrief = normalize(request.combinedHints());
        AiRoomDesignApiClient.DesignSuggestion suggestion = apiClient.generateRoomDesign(
            buildApiInstructions(),
            buildApiPrompt(request, equipementCatalog)
        );

        int capacity = suggestion.capacity() > 0 ? suggestion.capacity() : resolveCapacity(request, normalizedBrief);
        String roomType = resolveRoomType(suggestion.roomType(), request, normalizedBrief);
        String disposition = resolveDisposition(suggestion.disposition(), request, normalizedBrief, roomType);
        boolean accessible = suggestion.accessible()
            || request.accessibilityRequired()
            || mentionsAny(normalizedBrief, ACCESSIBILITY_KEYWORDS);
        String building = resolveBuilding(suggestion.building(), request);
        String location = firstNonBlank(
            suggestion.location(),
            request.preferredLocation(),
            defaultLocationFor(building, roomType, disposition)
        );
        String roomName = firstNonBlank(
            suggestion.roomName(),
            request.preferredName(),
            defaultRoomNameFor(roomType, disposition, capacity)
        );

        LinkedHashMap<Integer, Integer> suggestedEquipements = resolveFixedEquipements(
            request,
            equipementCatalog,
            normalizedBrief,
            roomType,
            disposition,
            capacity,
            suggestion.equipmentPreferences()
        );
        String fixedEquipmentSummary = buildFixedEquipmentSummary(suggestedEquipements, equipementCatalog);
        String conceptLabel = buildConceptLabel(roomType, disposition, capacity, request.variantIndex());
        String rationale = firstNonBlank(
            suggestion.rationale(),
            buildRationale(normalizedBrief, roomType, disposition, accessible, fixedEquipmentSummary)
        );
        String summary = firstNonBlank(
            suggestion.summary(),
            conceptLabel + " | Materiel suggere: " + fixedEquipmentSummary
        );

        Salle salle = new Salle(
            0,
            roomName,
            capacity,
            location,
            roomType,
            "disponible",
            buildDescription(roomType, disposition, capacity, accessible, fixedEquipmentSummary),
            building,
            null,
            disposition,
            accessible,
            "Concept Gemini - variante " + request.variantIndex(),
            null
        );

        String previewHeadline = "Conception Gemini | " + salle.getNom();
        String previewSummary = summary
            + "\nAccessibilite: " + (accessible ? "oui" : "non")
            + " | Materiel suggere: " + fixedEquipmentSummary;
        String previewLegend = "Source: API Gemini reelle | " + rationale
            + "\nAucune sauvegarde automatique. Revenez dans le backoffice pour confirmer.";
        String adminSummary = "Proposition preparee depuis l'API Gemini. Ajustez librement les champs, puis lancez un apercu 3D si vous voulez verifier le rendu. Materiel fixe suggere: "
            + fixedEquipmentSummary
            + ".";

        return new AiRoomDesignProposal(
            salle,
            suggestedEquipements,
            previewHeadline,
            previewSummary,
            previewLegend,
            adminSummary,
            "API Gemini reelle",
            fixedEquipmentSummary,
            request.variantIndex()
        );
    }

    private String buildApiInstructions() {
        return """
            Vous concevez des salles academiques pour une application desktop de gestion de salles et materiel.
            Retournez uniquement une proposition JSON conforme au schema fourni.
            Utilisez des valeurs coherentes et exploitables dans un backoffice.
            Respectez les types de salle, les dispositions et les contraintes indiquees dans le brief.
            Les champs doivent rester courts, concrets et en francais.
            Pour equipment_preferences, proposez seulement des noms ou types de materiel compatibles avec le catalogue fourni.
            N'inventez jamais d'identifiants techniques.
            """;
    }

    private String buildApiPrompt(AiRoomDesignRequest request, List<Equipement> equipementCatalog) {
        String buildings = String.join(", ", salleService.getAvailableBatiments());
        String roomTypes = String.join(", ", salleService.getAvailableTypes());
        String dispositions = String.join(", ", salleService.getAvailableDispositions());
        String compatibilityGuide = salleService.getAvailableTypes().stream()
            .map(type -> type + " -> " + String.join(", ", salleService.getAvailableDispositionsForType(type)))
            .collect(Collectors.joining(" | "));

        String equipmentCatalogSummary = equipementCatalog.stream()
            .filter(Objects::nonNull)
            .filter(this::isEquipmentAvailable)
            .map(equipement -> equipement.getNom()
                + " [type=" + safeText(equipement.getTypeEquipement())
                + ", stock=" + equipement.getQuantiteDisponible()
                + "]")
            .collect(Collectors.joining(" ; "));

        return """
            Brief admin:
            %s

            Brouillon courant:
            - nom prefere: %s
            - batiment prefere: %s
            - localisation preferee: %s
            - capacite cible: %d
            - type prefere: %s
            - disposition preferee: %s
            - accessibilite requise: %s
            - indices materiel: %s

            Valeurs autorisees:
            - batiments: %s
            - types de salle: %s
            - dispositions globales: %s
            - compatibilite type/disposition: %s

            Catalogue materiel disponible:
            %s

            Objectif:
            Proposez une salle pedagogique realiste, directement previsualisable en 3D, avec un resumee clair et une justification courte.
            """.formatted(
            safeText(request.brief()),
            safeText(request.preferredName()),
            safeText(request.preferredBuilding()),
            safeText(request.preferredLocation()),
            request.preferredCapacity(),
            safeText(request.preferredType()),
            safeText(request.preferredDisposition()),
            request.accessibilityRequired() ? "oui" : "non",
            safeText(request.equipmentHints()),
            buildings,
            roomTypes,
            dispositions,
            compatibilityGuide,
            equipmentCatalogSummary.isBlank() ? "Aucun materiel disponible." : equipmentCatalogSummary
        );
    }

    private int resolveCapacity(AiRoomDesignRequest request, String normalizedBrief) {
        Matcher matcher = CAPACITY_PATTERN.matcher(normalizedBrief);
        if (matcher.find()) {
            try {
                return Math.max(1, Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                return request.preferredCapacity();
            }
        }
        return request.preferredCapacity();
    }

    private String resolveRoomType(String suggestedRoomType, AiRoomDesignRequest request, String normalizedBrief) {
        if (suggestedRoomType != null) {
            return matchAvailableValue(salleService.getAvailableTypes(), suggestedRoomType, salleService.getAvailableTypes().get(0));
        }
        if (request.preferredType() != null) {
            return matchAvailableValue(salleService.getAvailableTypes(), request.preferredType(), salleService.getAvailableTypes().get(0));
        }

        if (mentionsAny(normalizedBrief, Set.of("amphi", "amphitheatre", "auditorium", "conference pleniere"))) {
            return "Amphitheatre";
        }
        if (mentionsAny(normalizedBrief, Set.of("labo", "laboratoire", "info", "informatique", "ordinateur", "pc", "machine"))) {
            return "Laboratoire";
        }
        if (mentionsAny(normalizedBrief, Set.of("conference", "reunion", "meeting", "board", "presentation", "pitch"))) {
            return "Conference";
        }
        return "Cours";
    }

    private String resolveDisposition(
        String suggestedDisposition,
        AiRoomDesignRequest request,
        String normalizedBrief,
        String roomType
    ) {
        String requestedDisposition = firstNonBlank(suggestedDisposition, request.preferredDisposition());
        if (requestedDisposition == null) {
            requestedDisposition = inferDispositionFromBrief(normalizedBrief, roomType);
        }
        return salleService.resolveDispositionForType(roomType, requestedDisposition);
    }

    private String inferDispositionFromBrief(String normalizedBrief, String roomType) {
        if (mentionsAny(normalizedBrief, Set.of(" en u", "fer a cheval"))) {
            return "u";
        }
        if (mentionsAny(normalizedBrief, Set.of("reunion", "table centrale", "boardroom"))) {
            return "reunion";
        }
        if (mentionsAny(normalizedBrief, Set.of("atelier", "workshop", "collaboratif"))) {
            return "atelier";
        }
        if (mentionsAny(normalizedBrief, Set.of("ordinateur", "pc", "informatique", "coding"))) {
            return "informatique";
        }
        if (mentionsAny(normalizedBrief, Set.of("conference", "auditorium", "scene", "amphi"))) {
            return "conference";
        }
        return salleService.getSuggestedDispositionForType(roomType);
    }

    private String resolveBuilding(String suggestedBuilding, AiRoomDesignRequest request) {
        List<String> availableBuildings = salleService.getAvailableBatiments();
        String requestedBuilding = firstNonBlank(suggestedBuilding, request.preferredBuilding());
        if (requestedBuilding == null) {
            return availableBuildings.isEmpty() ? "Bloc A" : availableBuildings.get(0);
        }
        return matchAvailableValue(
            availableBuildings,
            requestedBuilding,
            availableBuildings.isEmpty() ? "Bloc A" : availableBuildings.get(0)
        );
    }

    private String defaultLocationFor(String building, String roomType, String disposition) {
        return building + " - " + switch (normalize(roomType)) {
            case "conference" -> "zone conference " + disposition;
            case "laboratoire" -> "pole pratique " + disposition;
            case "amphitheatre" -> "espace evenementiel";
            default -> "espace pedagogique " + disposition;
        };
    }

    private String defaultRoomNameFor(String roomType, String disposition, int capacity) {
        return switch (normalize(roomType)) {
            case "conference" -> "Studio Conference " + capacity;
            case "laboratoire" -> "Lab " + capitalize(disposition) + " " + capacity;
            case "amphitheatre" -> "Amphi Concept " + capacity;
            default -> "Salle " + capitalize(disposition) + " " + capacity;
        };
    }

    private LinkedHashMap<Integer, Integer> resolveFixedEquipements(
        AiRoomDesignRequest request,
        List<Equipement> equipementCatalog,
        String normalizedBrief,
        String roomType,
        String disposition,
        int capacity,
        List<String> preferredEquipmentHints
    ) {
        LinkedHashSet<String> requestedTypes = new LinkedHashSet<>();
        if (preferredEquipmentHints != null) {
            preferredEquipmentHints.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(requestedTypes::add);
        }
        requestedTypes.addAll(resolveDefaultEquipmentTypes(roomType, disposition));
        requestedTypes.addAll(resolveMentionedEquipmentTypes(normalizedBrief, equipementCatalog));

        LinkedHashMap<Integer, Integer> suggestions = new LinkedHashMap<>();
        for (String requestedType : requestedTypes) {
            Equipement match = findBestEquipmentMatch(requestedType, equipementCatalog, normalizedBrief);
            if (match == null || suggestions.containsKey(match.getIdEquipement())) {
                continue;
            }

            int quantity = resolveSuggestedQuantity(match, capacity, roomType, request.combinedHints());
            if (quantity > 0) {
                suggestions.put(match.getIdEquipement(), quantity);
            }
        }

        return suggestions;
    }

    private Set<String> resolveDefaultEquipmentTypes(String roomType, String disposition) {
        LinkedHashSet<String> defaults = new LinkedHashSet<>();
        String normalizedType = normalize(roomType);
        String normalizedDisposition = normalize(disposition);

        switch (normalizedType) {
            case "conference" -> {
                defaults.add("projecteur");
                defaults.add("camera");
            }
            case "laboratoire" -> defaults.add("ordinateur");
            case "amphitheatre" -> {
                defaults.add("projecteur");
                defaults.add("camera");
            }
            default -> {
                defaults.add("projecteur");
                defaults.add("tableau interactif");
            }
        }

        if ("u".equals(normalizedDisposition) || "reunion".equals(normalizedDisposition)) {
            defaults.add("camera");
        }
        if ("atelier".equals(normalizedDisposition)) {
            defaults.add("tableau interactif");
        }

        return defaults;
    }

    private Set<String> resolveMentionedEquipmentTypes(String normalizedBrief, List<Equipement> equipementCatalog) {
        LinkedHashSet<String> mentionedTypes = new LinkedHashSet<>();
        if (normalizedBrief.isBlank()) {
            return mentionedTypes;
        }

        for (Equipement equipement : equipementCatalog) {
            if (!isEquipmentAvailable(equipement)) {
                continue;
            }

            String searchableText = normalize(
                safeText(equipement.getNom()) + " "
                    + safeText(equipement.getTypeEquipement()) + " "
                    + safeText(equipement.getDescription())
            );
            if (!searchableText.isBlank() && normalizedBrief.contains(searchableText)) {
                mentionedTypes.add(normalize(equipement.getTypeEquipement()));
                continue;
            }

            for (String token : searchableText.split("\\s+")) {
                if (token.length() >= 4 && normalizedBrief.contains(token)) {
                    mentionedTypes.add(normalize(equipement.getTypeEquipement()));
                    break;
                }
            }
        }

        if (mentionsAny(normalizedBrief, Set.of("tableau", "whiteboard"))) {
            mentionedTypes.add("tableau interactif");
        }
        if (mentionsAny(normalizedBrief, HYBRID_KEYWORDS)) {
            mentionedTypes.add("camera");
        }

        return mentionedTypes;
    }

    private Equipement findBestEquipmentMatch(String requestedType, List<Equipement> equipementCatalog, String normalizedBrief) {
        Equipement bestMatch = null;
        int bestScore = Integer.MIN_VALUE;
        String normalizedRequestedType = normalize(requestedType);

        for (Equipement equipement : equipementCatalog) {
            if (!isEquipmentAvailable(equipement)) {
                continue;
            }

            int score = scoreEquipmentMatch(normalizedRequestedType, equipement, normalizedBrief);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = equipement;
            }
        }

        return bestScore <= 0 ? null : bestMatch;
    }

    private int scoreEquipmentMatch(String requestedType, Equipement equipement, String normalizedBrief) {
        String normalizedType = normalize(equipement.getTypeEquipement());
        String normalizedName = normalize(equipement.getNom());
        String normalizedDescription = normalize(equipement.getDescription());

        int score = 0;
        if (normalizedType.equals(requestedType)) {
            score += 100;
        }
        if (normalizedName.contains(requestedType)) {
            score += 70;
        }
        if (!normalizedDescription.isBlank() && normalizedDescription.contains(requestedType)) {
            score += 30;
        }
        if (!normalizedBrief.isBlank() && normalizedBrief.contains(normalizedType)) {
            score += 20;
        }
        if (!normalizedBrief.isBlank() && normalizedBrief.contains(normalizedName)) {
            score += 20;
        }
        score += Math.min(12, Math.max(0, equipement.getQuantiteDisponible()));
        return score;
    }

    private int resolveSuggestedQuantity(Equipement equipement, int capacity, String roomType, String combinedHints) {
        String normalizedType = normalize(equipement.getTypeEquipement());
        int availableQuantity = Math.max(0, equipement.getQuantiteDisponible());
        if (availableQuantity <= 0) {
            return 0;
        }

        if ("ordinateur".equals(normalizedType)) {
            int targetQuantity = normalize(roomType).equals("laboratoire")
                ? capacity
                : Math.max(2, Math.min(capacity, 6));
            return Math.min(availableQuantity, targetQuantity);
        }
        if ("camera".equals(normalizedType) && mentionsAny(normalize(combinedHints), HYBRID_KEYWORDS)) {
            return Math.min(availableQuantity, 2);
        }
        return 1;
    }

    private String buildFixedEquipmentSummary(Map<Integer, Integer> suggestedEquipements, List<Equipement> equipementCatalog) {
        if (suggestedEquipements == null || suggestedEquipements.isEmpty()) {
            return "aucun";
        }

        List<String> parts = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : suggestedEquipements.entrySet()) {
            Equipement equipement = findEquipementById(entry.getKey(), equipementCatalog);
            if (equipement != null) {
                parts.add(equipement.getNom() + " x" + entry.getValue());
            }
        }

        return parts.isEmpty() ? "aucun" : String.join(", ", parts);
    }

    private String buildConceptLabel(String roomType, String disposition, int capacity, int variantIndex) {
        return "Variante " + variantIndex
            + " | " + roomType
            + " | disposition " + disposition
            + " | " + capacity + " places";
    }

    private String buildRationale(
        String normalizedBrief,
        String roomType,
        String disposition,
        boolean accessible,
        String fixedEquipmentSummary
    ) {
        List<String> reasons = new ArrayList<>();
        reasons.add("type " + roomType.toLowerCase(Locale.ROOT));
        reasons.add("disposition " + disposition);
        reasons.add(accessible ? "circulation PMR prise en compte" : "circulation standard");
        if (fixedEquipmentSummary != null && !"aucun".equalsIgnoreCase(fixedEquipmentSummary)) {
            reasons.add("materiel cible: " + fixedEquipmentSummary);
        }
        if (!normalizedBrief.isBlank()) {
            reasons.add("brief admin interprete");
        }
        return String.join(", ", reasons);
    }

    private String buildDescription(
        String roomType,
        String disposition,
        int capacity,
        boolean accessible,
        String fixedEquipmentSummary
    ) {
        StringBuilder builder = new StringBuilder("Salle de ");
        builder.append(roomType == null ? "cours" : roomType.toLowerCase(Locale.ROOT))
            .append(" en disposition ")
            .append(disposition == null ? "standard" : disposition)
            .append(", pensee pour ")
            .append(Math.max(1, capacity))
            .append(" places");

        if (accessible) {
            builder.append(" avec acces PMR");
        }

        if (fixedEquipmentSummary != null && !"aucun".equalsIgnoreCase(fixedEquipmentSummary)) {
            builder.append(" et equipements integres");
        }

        builder.append(".");
        return builder.toString();
    }

    private Equipement findEquipementById(int equipementId, List<Equipement> equipementCatalog) {
        for (Equipement equipement : equipementCatalog) {
            if (equipement != null && equipement.getIdEquipement() == equipementId) {
                return equipement;
            }
        }
        return null;
    }

    private boolean isEquipmentAvailable(Equipement equipement) {
        return equipement != null
            && equipement.getIdEquipement() > 0
            && equipement.getQuantiteDisponible() > 0
            && "disponible".equals(normalize(equipement.getEtat()));
    }

    private String matchAvailableValue(List<String> values, String requestedValue, String fallback) {
        if (requestedValue == null) {
            return fallback;
        }

        String normalizedRequestedValue = normalize(requestedValue);
        for (String value : values) {
            if (normalize(value).equals(normalizedRequestedValue)) {
                return value;
            }
        }
        return fallback;
    }

    private boolean mentionsAny(String source, Set<String> keywords) {
        if (source == null || source.isBlank() || keywords == null || keywords.isEmpty()) {
            return false;
        }

        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && source.contains(normalize(keyword))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeApiErrorMessage(String message) {
        String normalizedMessage = firstNonBlank(message, "reponse non disponible");
        return normalizedMessage.replace('\n', ' ').trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String safeText(String value) {
        return value == null ? "non renseigne" : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "Salle";
        }

        String trimmed = value.trim();
        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT) + trimmed.substring(1);
    }
}
