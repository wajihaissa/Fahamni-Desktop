package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.AiRoomDesignProposal;
import tn.esprit.fahamni.Models.AiRoomDesignRequest;
import tn.esprit.fahamni.Models.Equipement;
import tn.esprit.fahamni.Models.Salle;
import tn.esprit.fahamni.utils.AiApiConfig;

import java.text.Normalizer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
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

    private static final Pattern CAPACITY_CONTEXT_PATTERN = Pattern.compile("\\b(?:pour|de|capacite\\s*(?:de)?|capacite)\\s*(\\d{1,4})\\b");
    private static final Pattern CAPACITY_SUBJECT_PATTERN = Pattern.compile("\\b(\\d{1,4})\\s*(?:place\\w*|person\\w*|pers\\w*|participant\\w*|etudiant\\w*)\\b");
    private static final Pattern FLOOR_PATTERN = Pattern.compile("\\betage\\s*(\\d{1,2})\\b");
    private static final Pattern GENERIC_EQUIPMENT_PATTERN = Pattern.compile("^(?:equipement|materiel|item|device)\\d*$");
    private static final Set<String> HYBRID_KEYWORDS = Set.of("hybride", "hybrid", "visioconference", "visio", "captation", "stream", "camera");
    private static final Set<String> ACCESSIBILITY_KEYWORDS = Set.of("handicap", "pmr", "fauteuil", "accessibilite");
    private static final Set<String> EXPLICIT_COMPUTER_KEYWORDS = Set.of(
        "ordinateur",
        "ordinateurs",
        "pc",
        "pcs",
        "poste fixe",
        "postes fixes",
        "poste informatique",
        "postes informatiques"
    );
    private static final Set<String> EXPLICIT_CAMERA_KEYWORDS = Set.of("camera", "webcam");
    private static final Set<String> EXPLICIT_VIDEOCONF_KEYWORDS = Set.of(
        "visioconference",
        "visio",
        "kit visio",
        "kit visioconference"
    );
    private static final Set<String> COMPUTER_EQUIPMENT_KEYWORDS = Set.of("ordinateur", "pc", "poste", "machine", "portable", "laptop");
    private static final Set<String> COMPUTER_CONTEXT_KEYWORDS = Set.of("informatique", "ordinateur", "pc", "poste", "machine", "coding", "developpement");
    private static final Set<String> SCREEN_EQUIPMENT_KEYWORDS = Set.of("ecran", "moniteur", "tv", "television", "affichage");
    private static final Set<String> PROJECTOR_EQUIPMENT_KEYWORDS = Set.of("projecteur", "videoprojecteur", "projector");
    private static final Set<String> BOARD_EQUIPMENT_KEYWORDS = Set.of("tableau", "whiteboard", "board", "interactif");
    private static final Set<String> CAMERA_EQUIPMENT_KEYWORDS = Set.of("camera", "webcam");
    private static final Set<String> VIDEOCONF_EQUIPMENT_KEYWORDS = Set.of("visioconference", "visio", "audio-video", "audiovideo");
    private static final Set<String> IGNORED_EQUIPMENT_MATCH_TOKENS = Set.of(
        "salle",
        "conference",
        "cours",
        "atelier",
        "etudiant",
        "etudiants",
        "personne",
        "personnes",
        "places",
        "place",
        "unite",
        "unites",
        "equipement",
        "materiel",
        "avec",
        "pour",
        "informatique",
        "projection",
        "affichage",
        "audio",
        "video",
        "audio-video",
        "prototype",
        "interactif",
        "kit",
        "hd",
        "travail",
        "pratique",
        "pratiques"
    );

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
        List<Salle> historicalSalles = loadHistoricalSalles();
        AiRoomDesignApiClient.DesignSuggestion suggestion = apiClient.generateRoomDesign(
            buildApiInstructions(),
            buildApiPrompt(request, equipementCatalog)
        );

        int capacity = resolveCapacity(request, normalizedBrief, suggestion.capacity());
        String roomType = resolveRoomType(suggestion.roomType(), request, normalizedBrief);
        String disposition = resolveDisposition(suggestion.disposition(), request, normalizedBrief, roomType);
        boolean accessible = suggestion.accessible()
            || request.accessibilityRequired()
            || mentionsAny(normalizedBrief, ACCESSIBILITY_KEYWORDS);
        String explicitBuilding = firstNonBlank(extractExplicitBuilding(normalizedBrief, salleService.getAvailableBatiments()), request.preferredBuilding());
        Integer explicitFloor = firstNonBlankInteger(
            extractFirstInteger(FLOOR_PATTERN, normalizedBrief),
            request.preferredFloor(),
            extractFirstInteger(FLOOR_PATTERN, normalize(request.preferredLocation()))
        );
        String explicitLocation = firstNonBlank(extractExplicitLocation(normalizedBrief), sanitizeLocation(request.preferredLocation()));

        String building = resolveBuilding(explicitBuilding, roomType, historicalSalles);
        Integer floor = resolveFloor(explicitFloor, roomType, building, historicalSalles);
        String location = resolveLocation(explicitLocation, building, floor, roomType, disposition);
        String roomName = resolveRoomName(request, roomType, disposition, capacity);
        LinkedHashSet<String> promptRequestedEquipmentTypes = new LinkedHashSet<>(resolveMentionedEquipmentTypes(normalizedBrief, equipementCatalog));

        LinkedHashMap<Integer, Integer> suggestedEquipements = resolveFixedEquipements(
            request,
            equipementCatalog,
            normalizedBrief,
            roomType,
            disposition,
            capacity,
            promptRequestedEquipmentTypes,
            suggestion.equipmentPreferences()
        );
        String fixedEquipmentSummary = buildFixedEquipmentSummary(suggestedEquipements, equipementCatalog);
        String conceptLabel = buildConceptLabel(roomType, disposition, capacity, request.variantIndex());
        boolean defaultEquipmentPackUsed = promptRequestedEquipmentTypes.isEmpty() && !suggestedEquipements.isEmpty();
        String rationale = firstNonBlank(
            suggestion.rationale(),
            buildRationale(
                normalizedBrief,
                roomType,
                disposition,
                accessible,
                fixedEquipmentSummary,
                explicitBuilding == null,
                explicitFloor == null,
                explicitLocation == null,
                defaultEquipmentPackUsed
            )
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
            buildCompactDescription(roomType, disposition, capacity, accessible, fixedEquipmentSummary),
            building,
            floor,
            disposition,
            accessible,
            "Concept Gemini - variante " + request.variantIndex(),
            null
        );

        String previewHeadline = "Conception Gemini | " + salle.getNom();
        String previewSummary = summary
            + "\nAccessibilite: " + (accessible ? "oui" : "non")
            + " | Materiel suggere: " + fixedEquipmentSummary;
        String previewLegend = "Source: Gemini + regles metier | " + rationale
            + "\nAucune sauvegarde automatique. Revenez dans le backoffice pour confirmer.";
        String adminSummary = buildAdminSummary(
            building,
            floor,
            location,
            fixedEquipmentSummary,
            explicitBuilding == null,
            explicitFloor == null,
            explicitLocation == null,
            defaultEquipmentPackUsed
        );

        return new AiRoomDesignProposal(
            salle,
            suggestedEquipements,
            previewHeadline,
            previewSummary,
            previewLegend,
            adminSummary,
            "Gemini + regles metier",
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
            Les champs summary et rationale doivent rester sur une seule phrase courte, sans depasser 180 caracteres chacun.
            Si le brief mentionne un nombre de places/personnes, gardez exactement ce nombre.
            Si le brief ne mentionne pas de batiment, de localisation ou d'etage, vous pouvez laisser ces champs vides: le systeme les completera avec des regles metier.
            Si le brief cite des equipements precis, limitez-vous a ces equipements ou a leurs synonymes proches.
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
            .filter(this::isAiSelectableEquipment)
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
            - etage prefere: %s
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

            Regles de coherence strictes:
            - si le brief mentionne une capacite, ne la modifiez pas
            - si le brief ne precise pas batiment, localisation ou etage, laissez ces champs sobres ou vides
            - si le brief demande un projecteur et un tableau, n'ajoutez pas de kit visio, camera ou PC sans demande explicite
            - concentrez-vous surtout sur l'intention pedagogique, le type, la disposition, la capacite et un resume coherent

            Objectif:
            Proposez une salle pedagogique realiste, directement previsualisable en 3D, avec un resumee clair et une justification courte.
            """.formatted(
            safeText(request.brief()),
            safeText(request.preferredName()),
            safeText(request.preferredBuilding()),
            request.preferredFloor() == null ? "non renseigne" : request.preferredFloor(),
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

    private int resolveCapacity(AiRoomDesignRequest request, String normalizedBrief, int suggestedCapacity) {
        Integer explicitCapacity = extractExplicitCapacity(normalizedBrief);
        if (explicitCapacity != null) {
            return explicitCapacity;
        }
        if (suggestedCapacity > 0) {
            return suggestedCapacity;
        }
        return request.preferredCapacity();
    }

    private Integer extractExplicitCapacity(String normalizedBrief) {
        Integer contextualCapacity = extractFirstInteger(CAPACITY_CONTEXT_PATTERN, normalizedBrief);
        if (contextualCapacity != null) {
            return contextualCapacity;
        }
        return extractFirstInteger(CAPACITY_SUBJECT_PATTERN, normalizedBrief);
    }

    private Integer extractFirstInteger(Pattern pattern, String source) {
        if (pattern == null || source == null || source.isBlank()) {
            return null;
        }

        Matcher matcher = pattern.matcher(source);
        if (!matcher.find()) {
            return null;
        }

        try {
            return Math.max(1, Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String resolveRoomType(String suggestedRoomType, AiRoomDesignRequest request, String normalizedBrief) {
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
        if (suggestedRoomType != null) {
            return matchAvailableValue(salleService.getAvailableTypes(), suggestedRoomType, salleService.getAvailableTypes().get(0));
        }
        return "Cours";
    }

    private String resolveDisposition(
        String suggestedDisposition,
        AiRoomDesignRequest request,
        String normalizedBrief,
        String roomType
    ) {
        String requestedDisposition = firstNonBlank(
            request.preferredDisposition(),
            inferDispositionFromBrief(normalizedBrief),
            suggestedDisposition
        );
        return salleService.resolveDispositionForType(roomType, requestedDisposition);
    }

    private String inferDispositionFromBrief(String normalizedBrief) {
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
        return null;
    }

    private String resolveBuilding(String explicitBuilding, String roomType, List<Salle> historicalSalles) {
        List<String> availableBuildings = salleService.getAvailableBatiments();
        if (explicitBuilding != null) {
            return matchAvailableValue(availableBuildings, explicitBuilding, firstAvailableBuilding(availableBuildings));
        }

        String historicalBuilding = resolveHistoricalBuilding(roomType, historicalSalles);
        if (historicalBuilding != null) {
            return matchAvailableValue(availableBuildings, historicalBuilding, firstAvailableBuilding(availableBuildings));
        }
        return firstAvailableBuilding(availableBuildings);
    }

    private String extractExplicitBuilding(String normalizedBrief, List<String> availableBuildings) {
        if (normalizedBrief == null || normalizedBrief.isBlank() || availableBuildings == null) {
            return null;
        }

        for (String building : availableBuildings) {
            String normalizedBuilding = normalize(building);
            if (!normalizedBuilding.isBlank() && normalizedBrief.contains(normalizedBuilding)) {
                return building;
            }
        }
        return null;
    }

    private Integer resolveFloor(Integer explicitFloor, String roomType, String building, List<Salle> historicalSalles) {
        if (explicitFloor != null) {
            return explicitFloor;
        }

        Integer historicalFloor = resolveHistoricalFloor(roomType, building, historicalSalles);
        if (historicalFloor != null) {
            return historicalFloor;
        }
        return defaultFloorFor(roomType);
    }

    private String resolveLocation(String explicitLocation, String building, Integer floor, String roomType, String disposition) {
        if (explicitLocation != null) {
            return explicitLocation;
        }
        return defaultLocationFor(building, floor, roomType, disposition);
    }

    private String resolveRoomName(AiRoomDesignRequest request, String roomType, String disposition, int capacity) {
        return firstNonBlank(
            request.preferredName(),
            defaultRoomNameFor(roomType, disposition, capacity)
        );
    }

    private String defaultRoomNameFor(String roomType, String disposition, int capacity) {
        return switch (normalize(roomType)) {
            case "conference" -> "Salle Conference " + capacity;
            case "laboratoire" -> "Laboratoire " + capacity;
            case "amphitheatre" -> "Amphitheatre " + capacity;
            default -> "Salle " + capitalize(disposition) + " " + capacity;
        };
    }

    private List<Salle> loadHistoricalSalles() {
        try {
            return salleService.getAll();
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            return List.of();
        }
    }

    private String resolveHistoricalBuilding(String roomType, List<Salle> historicalSalles) {
        return historicalSalles.stream()
            .filter(Objects::nonNull)
            .filter(salle -> normalize(salle.getTypeSalle()).equals(normalize(roomType)))
            .map(Salle::getBatiment)
            .filter(value -> value != null && !value.isBlank())
            .collect(Collectors.groupingBy(value -> value, Collectors.counting()))
            .entrySet()
            .stream()
            .max(Map.Entry.<String, Long>comparingByValue()
                .thenComparing(Map.Entry.comparingByKey()))
            .map(Map.Entry::getKey)
            .orElseGet(() -> historicalSalles.stream()
                .filter(Objects::nonNull)
                .map(Salle::getBatiment)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null));
    }

    private Integer resolveHistoricalFloor(String roomType, String building, List<Salle> historicalSalles) {
        return historicalSalles.stream()
            .filter(Objects::nonNull)
            .filter(salle -> normalize(salle.getTypeSalle()).equals(normalize(roomType)))
            .filter(salle -> Objects.equals(normalize(salle.getBatiment()), normalize(building)))
            .map(Salle::getEtage)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(value -> value, Collectors.counting()))
            .entrySet()
            .stream()
            .max(Map.Entry.<Integer, Long>comparingByValue()
                .thenComparing(Map.Entry.comparingByKey(Comparator.naturalOrder())))
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    private String firstAvailableBuilding(List<String> availableBuildings) {
        return availableBuildings == null || availableBuildings.isEmpty() ? "Bloc A" : availableBuildings.get(0);
    }

    private Integer defaultFloorFor(String roomType) {
        return switch (normalize(roomType)) {
            case "amphitheatre" -> 0;
            case "laboratoire" -> 2;
            default -> 1;
        };
    }

    private String defaultLocationFor(String building, Integer floor, String roomType, String disposition) {
        List<String> parts = new ArrayList<>();
        if (building != null && !building.isBlank()) {
            parts.add(building);
        }
        if (floor != null) {
            parts.add("Etage " + floor);
        }
        parts.add(defaultZoneLabel(roomType, disposition));
        return String.join(" - ", parts);
    }

    private String defaultZoneLabel(String roomType, String disposition) {
        String normalizedType = normalize(roomType);
        String normalizedDisposition = normalize(disposition);
        if ("u".equals(normalizedDisposition) || "reunion".equals(normalizedDisposition)) {
            return "Zone reunion";
        }

        return switch (normalizedType) {
            case "conference" -> "Zone conference";
            case "laboratoire" -> "Pole informatique";
            case "amphitheatre" -> "Espace amphitheatre";
            default -> "Espace pedagogique";
        };
    }

    private String extractExplicitLocation(String normalizedBrief) {
        if (normalizedBrief == null || normalizedBrief.isBlank()) {
            return null;
        }

        Matcher roomMatcher = Pattern.compile("\\bsalle\\s+[a-z]{0,2}\\d{1,4}\\b").matcher(normalizedBrief);
        if (roomMatcher.find()) {
            return capitalizeWords(roomMatcher.group());
        }

        Matcher zoneMatcher = Pattern.compile("\\b(?:aile|zone)\\s+[a-z0-9-]{2,12}\\b").matcher(normalizedBrief);
        if (zoneMatcher.find()) {
            return capitalizeWords(zoneMatcher.group());
        }

        return null;
    }

    private String sanitizeLocation(String location) {
        String normalizedLocation = firstNonBlank(location);
        if (normalizedLocation == null) {
            return null;
        }

        String comparableValue = normalize(normalizedLocation);
        if (comparableValue.matches("^(etage|niveau)\\s*\\d{1,2}$")) {
            return null;
        }
        return normalizedLocation;
    }

    private LinkedHashMap<Integer, Integer> resolveFixedEquipements(
        AiRoomDesignRequest request,
        List<Equipement> equipementCatalog,
        String normalizedBrief,
        String roomType,
        String disposition,
        int capacity,
        Set<String> promptRequestedEquipmentTypes,
        List<String> preferredEquipmentHints
    ) {
        LinkedHashSet<String> requestedTypes = new LinkedHashSet<>();
        if (promptRequestedEquipmentTypes != null) {
            promptRequestedEquipmentTypes.stream()
                .map(this::canonicalizeRequestedEquipmentType)
                .filter(Objects::nonNull)
                .forEach(requestedTypes::add);
        }

        if (requestedTypes.isEmpty()) {
            requestedTypes.addAll(resolveBusinessEquipmentPackTypes(roomType, disposition, capacity, normalizedBrief));
        }
        if (requestedTypes.isEmpty() && preferredEquipmentHints != null) {
            preferredEquipmentHints.stream()
                .map(this::canonicalizeRequestedEquipmentType)
                .filter(Objects::nonNull)
                .forEach(requestedTypes::add);
        }

        LinkedHashMap<Integer, Integer> suggestions = new LinkedHashMap<>();
        for (String requestedType : requestedTypes) {
            String canonicalRequestedType = canonicalizeRequestedEquipmentType(requestedType);
            if (canonicalRequestedType == null) {
                continue;
            }

            Equipement match = findBestEquipmentMatch(canonicalRequestedType, equipementCatalog, normalizedBrief);
            if (match == null || suggestions.containsKey(match.getIdEquipement())) {
                continue;
            }
            if (!isEquipmentSuggestionCompatibleWithContext(match, normalizedBrief, roomType, disposition)) {
                continue;
            }

            int quantity = resolveSuggestedQuantity(match, capacity, roomType, disposition, request.combinedHints());
            if (quantity > 0) {
                suggestions.put(match.getIdEquipement(), quantity);
            }
        }

        return suggestions;
    }

    private Set<String> resolveBusinessEquipmentPackTypes(String roomType, String disposition, int capacity, String normalizedBrief) {
        LinkedHashSet<String> defaults = new LinkedHashSet<>();
        String normalizedType = normalize(roomType);
        String normalizedDisposition = normalize(disposition);

        switch (normalizedType) {
            case "conference" -> {
                defaults.add("projecteur");
                defaults.add("tableau interactif");
                defaults.add("ecran");
            }
            case "laboratoire" -> {
                defaults.add("ordinateur");
                defaults.add("projecteur");
                defaults.add("tableau interactif");
            }
            case "amphitheatre" -> {
                defaults.add("projecteur");
                defaults.add("ecran");
            }
            default -> {
                defaults.add("tableau interactif");
                defaults.add("projecteur");
            }
        }

        if (capacity >= 28 && !"laboratoire".equals(normalizedType)) {
            defaults.add("ecran");
        }
        if ("u".equals(normalizedDisposition) || "reunion".equals(normalizedDisposition)) {
            defaults.add("ecran");
        }
        if (mentionsAny(normalizedBrief, HYBRID_KEYWORDS)) {
            defaults.add("kit visioconference");
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

        if (mentionsAnyPromptKeyword(normalizedBrief, Set.of("projecteur", "videoprojecteur", "projector"))) {
            mentionedTypes.add("projecteur");
        }
        if (mentionsAnyPromptKeyword(normalizedBrief, Set.of("tableau interactif", "tableau", "whiteboard", "board"))) {
            mentionedTypes.add("tableau interactif");
        }
        if (mentionsAnyPromptKeyword(normalizedBrief, EXPLICIT_COMPUTER_KEYWORDS)) {
            mentionedTypes.add("ordinateur");
        }
        if (mentionsAnyPromptKeyword(normalizedBrief, Set.of("ecran", "tv", "television", "moniteur"))) {
            mentionedTypes.add("ecran");
        }
        if (mentionsAnyPromptKeyword(normalizedBrief, EXPLICIT_CAMERA_KEYWORDS)) {
            mentionedTypes.add("camera");
        }
        if (mentionsAnyPromptKeyword(normalizedBrief, EXPLICIT_VIDEOCONF_KEYWORDS)) {
            mentionedTypes.add("kit visioconference");
        }

        for (Equipement equipement : equipementCatalog) {
            if (!isAiSelectableEquipment(equipement)) {
                continue;
            }

            String canonicalType = resolveCatalogEquipmentRequestType(equipement);
            if (canonicalType == null) {
                continue;
            }

            String searchableText = normalize(
                safeText(equipement.getNom()) + " " + safeText(equipement.getTypeEquipement())
            );
            if (!searchableText.isBlank() && containsExplicitEquipmentPhrase(normalizedBrief, searchableText)) {
                mentionedTypes.add(canonicalType);
                continue;
            }

            for (String token : searchableText.split("[\\s/-]+")) {
                if (isMeaningfulEquipmentToken(token) && containsPromptKeyword(normalizedBrief, token)) {
                    mentionedTypes.add(canonicalType);
                    break;
                }
            }
        }

        return mentionedTypes;
    }

    private Equipement findBestEquipmentMatch(String requestedType, List<Equipement> equipementCatalog, String normalizedBrief) {
        String canonicalRequestedType = canonicalizeRequestedEquipmentType(requestedType);
        if (canonicalRequestedType == null) {
            return null;
        }

        Equipement bestMatch = null;
        int bestScore = Integer.MIN_VALUE;
        String normalizedRequestedType = normalize(canonicalRequestedType);

        for (Equipement equipement : equipementCatalog) {
            if (!isAiSelectableEquipment(equipement)) {
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
        boolean strictSemanticRequest = requiresStrictSemanticMatch(requestedType);
        boolean compatibleEquipment = !strictSemanticRequest
            || isRequestedTypeCompatible(requestedType, equipement, normalizedType, normalizedName, normalizedDescription);

        if (!compatibleEquipment) {
            return Integer.MIN_VALUE / 4;
        }

        int score = 0;
        if (normalizedType.equals(requestedType)) {
            score += 100;
        }
        if (isSemanticEquipmentMatch(requestedType, normalizedType, normalizedName, normalizedDescription)) {
            score += 90;
        }
        if (!normalizedType.isBlank() && (normalizedType.contains(requestedType) || requestedType.contains(normalizedType))) {
            score += 60;
        }
        if (normalizedName.contains(requestedType) || requestedType.contains(normalizedName)) {
            score += 70;
        }
        if (!strictSemanticRequest && !normalizedDescription.isBlank() && normalizedDescription.contains(requestedType)) {
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

    private int resolveSuggestedQuantity(Equipement equipement, int capacity, String roomType, String disposition, String combinedHints) {
        String normalizedHints = normalize(combinedHints);
        int availableQuantity = Math.max(0, equipement.getQuantiteDisponible());
        if (availableQuantity <= 0) {
            return 0;
        }

        if (isComputerLikeEquipment(equipement)) {
            boolean immersiveComputerRoom = "laboratoire".equals(normalize(roomType))
                || "informatique".equals(normalize(disposition))
                || mentionsAnyPromptKeyword(normalizedHints, COMPUTER_CONTEXT_KEYWORDS);
            int targetQuantity = immersiveComputerRoom ? capacity : 1;
            return Math.min(availableQuantity, targetQuantity);
        }
        if (isVideoCaptureLikeEquipment(equipement) && mentionsAnyPromptKeyword(normalizedHints, HYBRID_KEYWORDS)) {
            return Math.min(availableQuantity, 2);
        }
        return 1;
    }

    private boolean isSemanticEquipmentMatch(
        String requestedType,
        String normalizedType,
        String normalizedName,
        String normalizedDescription
    ) {
        if ("ordinateur".equals(requestedType)) {
            return containsAnyKeyword(normalizedType, COMPUTER_EQUIPMENT_KEYWORDS)
                || containsAnyKeyword(normalizedName, COMPUTER_EQUIPMENT_KEYWORDS)
                || containsAnyKeyword(normalizedDescription, COMPUTER_EQUIPMENT_KEYWORDS);
        }
        if ("ecran".equals(requestedType)) {
            return containsAnyKeyword(normalizedType, SCREEN_EQUIPMENT_KEYWORDS)
                || containsAnyKeyword(normalizedName, SCREEN_EQUIPMENT_KEYWORDS)
                || containsAnyKeyword(normalizedType, BOARD_EQUIPMENT_KEYWORDS)
                || containsAnyKeyword(normalizedName, BOARD_EQUIPMENT_KEYWORDS);
        }
        if ("projecteur".equals(requestedType)) {
            return containsAnyKeyword(normalizedType, PROJECTOR_EQUIPMENT_KEYWORDS)
                || containsAnyKeyword(normalizedName, PROJECTOR_EQUIPMENT_KEYWORDS);
        }
        if ("tableau interactif".equals(requestedType)) {
            return containsAnyKeyword(normalizedType, BOARD_EQUIPMENT_KEYWORDS)
                || containsAnyKeyword(normalizedName, BOARD_EQUIPMENT_KEYWORDS);
        }
        if ("camera".equals(requestedType)) {
            return containsAnyKeyword(normalizedType, CAMERA_EQUIPMENT_KEYWORDS)
                || containsAnyKeyword(normalizedName, CAMERA_EQUIPMENT_KEYWORDS)
                || containsAnyKeyword(normalizedDescription, CAMERA_EQUIPMENT_KEYWORDS);
        }
        if ("kit visioconference".equals(requestedType)) {
            return containsAnyKeyword(normalizedType, VIDEOCONF_EQUIPMENT_KEYWORDS)
                || containsAnyKeyword(normalizedName, VIDEOCONF_EQUIPMENT_KEYWORDS)
                || containsAnyKeyword(normalizedDescription, VIDEOCONF_EQUIPMENT_KEYWORDS);
        }
        return false;
    }

    private boolean containsExplicitEquipmentPhrase(String normalizedBrief, String searchableText) {
        if (normalizedBrief == null || normalizedBrief.isBlank() || searchableText == null || searchableText.isBlank()) {
            return false;
        }

        if (searchableText.length() < 6) {
            return false;
        }
        return normalizedBrief.contains(searchableText);
    }

    private boolean isMeaningfulEquipmentToken(String token) {
        return token != null
            && token.length() >= 4
            && !IGNORED_EQUIPMENT_MATCH_TOKENS.contains(token)
            && !token.matches("\\d+");
    }

    private boolean requiresStrictSemanticMatch(String requestedType) {
        return requestedType != null && Set.of(
            "ordinateur",
            "ecran",
            "projecteur",
            "tableau interactif",
            "camera",
            "kit visioconference"
        ).contains(requestedType);
    }

    private boolean isRequestedTypeCompatible(
        String requestedType,
        Equipement equipement,
        String normalizedType,
        String normalizedName,
        String normalizedDescription
    ) {
        if (requestedType == null || equipement == null) {
            return false;
        }

        if ("ordinateur".equals(requestedType)) {
            return isComputerLikeEquipment(equipement);
        }
        if ("ecran".equals(requestedType)) {
            return !isComputerLikeEquipment(equipement)
                && (containsAnyKeyword(normalizedType, SCREEN_EQUIPMENT_KEYWORDS)
                || containsAnyKeyword(normalizedName, SCREEN_EQUIPMENT_KEYWORDS)
                || containsAnyKeyword(normalizedType, BOARD_EQUIPMENT_KEYWORDS)
                || containsAnyKeyword(normalizedName, BOARD_EQUIPMENT_KEYWORDS));
        }
        if ("projecteur".equals(requestedType)) {
            return containsAnyKeyword(normalizedType, PROJECTOR_EQUIPMENT_KEYWORDS)
                || containsAnyKeyword(normalizedName, PROJECTOR_EQUIPMENT_KEYWORDS);
        }
        if ("tableau interactif".equals(requestedType)) {
            return containsAnyKeyword(normalizedType, BOARD_EQUIPMENT_KEYWORDS)
                || containsAnyKeyword(normalizedName, BOARD_EQUIPMENT_KEYWORDS);
        }
        if ("camera".equals(requestedType)) {
            return containsAnyKeyword(normalizedType, CAMERA_EQUIPMENT_KEYWORDS)
                || containsAnyKeyword(normalizedName, CAMERA_EQUIPMENT_KEYWORDS)
                || containsAnyKeyword(normalizedDescription, CAMERA_EQUIPMENT_KEYWORDS);
        }
        if ("kit visioconference".equals(requestedType)) {
            return containsAnyKeyword(normalizedType, VIDEOCONF_EQUIPMENT_KEYWORDS)
                || containsAnyKeyword(normalizedName, VIDEOCONF_EQUIPMENT_KEYWORDS)
                || containsAnyKeyword(normalizedDescription, VIDEOCONF_EQUIPMENT_KEYWORDS);
        }
        return true;
    }

    private boolean isComputerLikeEquipment(Equipement equipement) {
        if (equipement == null) {
            return false;
        }

        return containsAnyKeyword(normalize(equipement.getTypeEquipement()), COMPUTER_EQUIPMENT_KEYWORDS)
            || containsAnyKeyword(normalize(equipement.getNom()), COMPUTER_EQUIPMENT_KEYWORDS)
            || containsAnyKeyword(normalize(equipement.getDescription()), COMPUTER_EQUIPMENT_KEYWORDS);
    }

    private boolean isVideoCaptureLikeEquipment(Equipement equipement) {
        if (equipement == null) {
            return false;
        }

        return containsAnyKeyword(normalize(equipement.getTypeEquipement()), CAMERA_EQUIPMENT_KEYWORDS)
            || containsAnyKeyword(normalize(equipement.getNom()), CAMERA_EQUIPMENT_KEYWORDS)
            || containsAnyKeyword(normalize(equipement.getDescription()), CAMERA_EQUIPMENT_KEYWORDS)
            || containsAnyKeyword(normalize(equipement.getNom()), VIDEOCONF_EQUIPMENT_KEYWORDS)
            || containsAnyKeyword(normalize(equipement.getDescription()), VIDEOCONF_EQUIPMENT_KEYWORDS);
    }

    private boolean isEquipmentSuggestionCompatibleWithContext(
        Equipement equipement,
        String normalizedBrief,
        String roomType,
        String disposition
    ) {
        if (equipement == null) {
            return false;
        }

        if (isComputerLikeEquipment(equipement)) {
            return "laboratoire".equals(normalize(roomType))
                || "informatique".equals(normalize(disposition))
                || mentionsAnyPromptKeyword(normalizedBrief, COMPUTER_CONTEXT_KEYWORDS);
        }
        if (isVideoCaptureLikeEquipment(equipement)) {
            return mentionsAnyPromptKeyword(normalizedBrief, HYBRID_KEYWORDS)
                || mentionsAnyPromptKeyword(normalizedBrief, EXPLICIT_CAMERA_KEYWORDS)
                || mentionsAnyPromptKeyword(normalizedBrief, EXPLICIT_VIDEOCONF_KEYWORDS);
        }
        return true;
    }

    private String resolveCatalogEquipmentRequestType(Equipement equipement) {
        if (equipement == null) {
            return null;
        }

        return canonicalizeRequestedEquipmentType(
            safeText(equipement.getNom())
                + " "
                + safeText(equipement.getTypeEquipement())
                + " "
                + safeText(equipement.getDescription())
        );
    }

    private String canonicalizeRequestedEquipmentType(String value) {
        String normalizedValue = normalize(value);
        if (normalizedValue.isBlank()) {
            return null;
        }
        if (containsAnyKeyword(normalizedValue, VIDEOCONF_EQUIPMENT_KEYWORDS)) {
            return "kit visioconference";
        }
        if (containsAnyKeyword(normalizedValue, CAMERA_EQUIPMENT_KEYWORDS)) {
            return "camera";
        }
        if (containsAnyKeyword(normalizedValue, PROJECTOR_EQUIPMENT_KEYWORDS)) {
            return "projecteur";
        }
        if (containsAnyKeyword(normalizedValue, BOARD_EQUIPMENT_KEYWORDS)) {
            return "tableau interactif";
        }
        if (containsAnyKeyword(normalizedValue, SCREEN_EQUIPMENT_KEYWORDS)) {
            return "ecran";
        }
        if (containsAnyKeyword(normalizedValue, COMPUTER_EQUIPMENT_KEYWORDS)) {
            return "ordinateur";
        }
        return null;
    }

    private boolean mentionsAnyPromptKeyword(String source, Set<String> keywords) {
        if (source == null || source.isBlank() || keywords == null || keywords.isEmpty()) {
            return false;
        }

        for (String keyword : keywords) {
            if (containsPromptKeyword(source, keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsPromptKeyword(String source, String keyword) {
        if (source == null || source.isBlank() || keyword == null || keyword.isBlank()) {
            return false;
        }

        String normalizedSource = normalize(source);
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword.isBlank()) {
            return false;
        }
        if (normalizedKeyword.contains(" ")) {
            return normalizedSource.contains(normalizedKeyword);
        }

        return Pattern.compile("(^|[^a-z0-9])" + Pattern.quote(normalizedKeyword) + "([^a-z0-9]|$)")
            .matcher(normalizedSource)
            .find();
    }

    private boolean containsAnyKeyword(String source, Set<String> keywords) {
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
        String fixedEquipmentSummary,
        boolean buildingAutoCompleted,
        boolean floorAutoCompleted,
        boolean locationAutoCompleted,
        boolean defaultEquipmentPackUsed
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
        if (buildingAutoCompleted || floorAutoCompleted || locationAutoCompleted) {
            reasons.add("infrastructure completee par regles metier");
        }
        if (defaultEquipmentPackUsed) {
            reasons.add("pack materiel standard suggere");
        }
        return String.join(", ", reasons);
    }

    public static String buildCompactDescription(
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

        List<String> complements = new ArrayList<>();
        if (accessible) {
            complements.add("acces PMR");
        }
        if (fixedEquipmentSummary != null && !"aucun".equalsIgnoreCase(fixedEquipmentSummary)) {
            complements.add(buildEquipmentDescriptionFragment(fixedEquipmentSummary));
        }
        if (!complements.isEmpty()) {
            builder.append(" avec ").append(String.join(" et ", complements));
        }

        builder.append(".");
        return builder.toString();
    }

    private static String buildEquipmentDescriptionFragment(String fixedEquipmentSummary) {
        List<String> highlights = extractEquipmentHighlights(fixedEquipmentSummary);
        if (highlights.isEmpty()) {
            return "equipements integres";
        }
        if (highlights.size() == 1) {
            return highlights.get(0) + " integre";
        }
        return highlights.get(0) + " et " + highlights.get(1) + " integres";
    }

    private static List<String> extractEquipmentHighlights(String fixedEquipmentSummary) {
        if (fixedEquipmentSummary == null || fixedEquipmentSummary.isBlank()) {
            return List.of();
        }

        List<String> highlights = new ArrayList<>();
        for (String part : fixedEquipmentSummary.split(",")) {
            String cleanedPart = part.replaceAll("\\sx\\d+\\s*$", "").trim();
            if (!cleanedPart.isEmpty()) {
                highlights.add(cleanedPart);
            }
            if (highlights.size() >= 2) {
                break;
            }
        }
        return highlights;
    }

    private String buildAdminSummary(
        String building,
        Integer floor,
        String location,
        String fixedEquipmentSummary,
        boolean buildingAutoCompleted,
        boolean floorAutoCompleted,
        boolean locationAutoCompleted,
        boolean defaultEquipmentPackUsed
    ) {
        StringBuilder builder = new StringBuilder("Proposition preparee par Gemini puis completee avec les regles metier du backoffice.");
        builder.append(" Infrastructure suggeree: ")
            .append(firstNonBlank(buildInfrastructureSummary(building, floor, location), "non renseignee"))
            .append(".");

        if (defaultEquipmentPackUsed) {
            builder.append(" Pack materiel suggere automatiquement selon le type de salle et la capacite: ")
                .append(firstNonBlank(fixedEquipmentSummary, "aucun"))
                .append(".");
        } else {
            builder.append(" Materiel fixe retenu d'apres le prompt ou vos ajustements: ")
                .append(firstNonBlank(fixedEquipmentSummary, "aucun"))
                .append(".");
        }

        List<String> autoCompletedFields = new ArrayList<>();
        if (buildingAutoCompleted) {
            autoCompletedFields.add("batiment");
        }
        if (floorAutoCompleted) {
            autoCompletedFields.add("etage");
        }
        if (locationAutoCompleted) {
            autoCompletedFields.add("localisation");
        }
        if (!autoCompletedFields.isEmpty()) {
            builder.append(" Completes automatiquement: ")
                .append(String.join(", ", autoCompletedFields))
                .append(".");
        }

        builder.append(" Ajustez librement les champs avant l'apercu 3D et la creation.");
        return builder.toString();
    }

    private String buildInfrastructureSummary(String building, Integer floor, String location) {
        List<String> parts = new ArrayList<>();
        if (building != null && !building.isBlank()) {
            parts.add(building);
        }
        if (floor != null) {
            parts.add("etage " + floor);
        }
        if (location != null && !location.isBlank()) {
            parts.add(location);
        }
        return parts.isEmpty() ? null : String.join(" | ", parts);
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

    private boolean isAiSelectableEquipment(Equipement equipement) {
        return isEquipmentAvailable(equipement)
            && !looksGenericEquipmentValue(equipement.getNom())
            && !looksGenericEquipmentValue(equipement.getTypeEquipement());
    }

    private boolean looksGenericEquipmentValue(String value) {
        String normalizedValue = normalize(value).replaceAll("[\\s_-]+", "");
        return !normalizedValue.isBlank() && GENERIC_EQUIPMENT_PATTERN.matcher(normalizedValue).matches();
    }

    private String matchAvailableValue(List<String> values, String requestedValue, String fallback) {
        if (requestedValue == null || values == null) {
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

    private Integer firstNonBlankInteger(Integer... values) {
        if (values == null) {
            return null;
        }

        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String safeText(String value) {
        return value == null ? "non renseigne" : value;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        String normalizedValue = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "");
        return normalizedValue.toLowerCase(Locale.ROOT).trim();
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "Salle";
        }

        String trimmed = value.trim();
        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT) + trimmed.substring(1);
    }

    private String capitalizeWords(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String[] words = value.trim().split("\\s+");
        List<String> capitalizedWords = new ArrayList<>(words.length);
        for (String word : words) {
            capitalizedWords.add(capitalize(word));
        }
        return String.join(" ", capitalizedWords);
    }
}
