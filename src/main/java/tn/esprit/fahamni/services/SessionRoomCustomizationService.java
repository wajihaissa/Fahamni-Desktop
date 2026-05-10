package tn.esprit.fahamni.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import tn.esprit.fahamni.Models.Salle;
import tn.esprit.fahamni.Models.Seance;
import tn.esprit.fahamni.Models.SessionRoomCustomizationConfig;
import tn.esprit.fahamni.Models.SessionRoomCustomizationRequest;
import tn.esprit.fahamni.utils.DatabaseSchemaUtils;
import tn.esprit.fahamni.utils.MyDataBase;
import tn.esprit.fahamni.utils.SeatSelectionLayoutResolver;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SessionRoomCustomizationService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_IN_REVIEW = "in_review";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String ADMIN_NOTIFICATION_PREFIX = "Personnalisation salle | ";

    private static final String TABLE_NAME = "demande_personnalisation_salle";
    private static final String SELECT_BY_SEANCE_SQL = """
        SELECT idDemande, seance_id, base_salle_id, tuteur_id, status,
               requested_config_json, approved_config_json,
               comment_tuteur, comment_admin, created_at, reviewed_at
        FROM demande_personnalisation_salle
        WHERE seance_id = ?
        """;
    private static final String SELECT_ALL_SQL = """
        SELECT idDemande, seance_id, base_salle_id, tuteur_id, status,
               requested_config_json, approved_config_json,
               comment_tuteur, comment_admin, created_at, reviewed_at
        FROM demande_personnalisation_salle
        ORDER BY created_at DESC, idDemande DESC
        """;
    private static final String INSERT_SQL = """
        INSERT INTO demande_personnalisation_salle (
            seance_id, base_salle_id, tuteur_id,
            requested_disposition, requested_capacity, requested_table_style, requested_chair_style,
            accessibility_required, requested_config_json, approved_config_json,
            comment_tuteur, comment_admin, status, created_at, reviewed_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    private static final String UPDATE_REQUEST_SQL = """
        UPDATE demande_personnalisation_salle
        SET base_salle_id = ?, tuteur_id = ?,
            requested_disposition = ?, requested_capacity = ?, requested_table_style = ?, requested_chair_style = ?,
            accessibility_required = ?, requested_config_json = ?, approved_config_json = ?,
            comment_tuteur = ?, comment_admin = ?, status = ?, reviewed_at = ?
        WHERE seance_id = ?
        """;
    private static final String UPDATE_STATUS_SQL = """
        UPDATE demande_personnalisation_salle
        SET approved_config_json = ?, comment_admin = ?, status = ?, reviewed_at = ?
        WHERE seance_id = ?
        """;
    private static final String DELETE_BY_SEANCE_SQL = "DELETE FROM demande_personnalisation_salle WHERE seance_id = ?";
    private static final String SELECT_SEANCE_CAPACITY_SQL = "SELECT max_participants FROM seance WHERE id = ?";
    private static final String PUBLISH_DRAFT_SEANCE_SQL = """
        UPDATE seance
        SET status = 1, updated_at = ?
        WHERE id = ? AND status = 0
        """;
    private static final String PUBLISH_ALL_APPROVED_DRAFT_SEANCES_SQL = """
        UPDATE seance s
        INNER JOIN demande_personnalisation_salle d ON d.seance_id = s.id
        SET s.status = 1, s.updated_at = ?
        WHERE s.status = 0 AND d.status = ?
        """;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Connection cnx;
    private final NotificationService notificationService;

    public SessionRoomCustomizationService() {
        this.cnx = MyDataBase.getInstance().getCnx();
        this.notificationService = new NotificationService();
    }

    public SessionRoomCustomizationRequest saveOrUpdateRequest(
        int seanceId,
        int baseSalleId,
        int tuteurId,
        SessionRoomCustomizationConfig requestedConfig,
        String commentTuteur
    ) throws SQLException {
        requireCustomizationTable();
        validateRequestArguments(seanceId, baseSalleId, tuteurId, requestedConfig);
        validateConfigurationSupportsSessionCapacity(seanceId, requestedConfig);

        SessionRoomCustomizationRequest existing = findBySeanceId(seanceId);
        SessionRoomCustomizationConfig normalizedConfig = requestedConfig;
        String requestedConfigJson = writeConfig(normalizedConfig);
        String normalizedCommentTuteur = normalizeText(commentTuteur);
        LocalDateTime now = LocalDateTime.now();

        if (existing == null) {
            try (PreparedStatement statement = requireConnection().prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
                fillRequestStatement(
                    statement,
                    seanceId,
                    baseSalleId,
                    tuteurId,
                    normalizedConfig,
                    requestedConfigJson,
                    normalizedCommentTuteur,
                    STATUS_PENDING,
                    now,
                    null
                );
                statement.executeUpdate();

                int generatedId = 0;
                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        generatedId = generatedKeys.getInt(1);
                    }
                }
                SessionRoomCustomizationRequest createdRequest = new SessionRoomCustomizationRequest(
                    generatedId,
                    seanceId,
                    baseSalleId,
                    tuteurId,
                    STATUS_PENDING,
                    normalizedConfig,
                    null,
                    normalizedCommentTuteur,
                    null,
                    now,
                    null
                );
                notifyAdminAboutCustomizationRequest(seanceId, baseSalleId, false);
                return createdRequest;
            }
        }

        try (PreparedStatement statement = requireConnection().prepareStatement(UPDATE_REQUEST_SQL)) {
            fillRequestUpdateStatement(
                statement,
                baseSalleId,
                tuteurId,
                normalizedConfig,
                requestedConfigJson,
                normalizedCommentTuteur,
                seanceId
            );
            statement.executeUpdate();
        }

        notifyAdminAboutCustomizationRequest(seanceId, baseSalleId, true);

        return new SessionRoomCustomizationRequest(
            existing.idDemande(),
            seanceId,
            baseSalleId,
            tuteurId,
            STATUS_PENDING,
            normalizedConfig,
            null,
            normalizedCommentTuteur,
            null,
            existing.createdAt() == null ? now : existing.createdAt(),
            null
        );
    }

    public SessionRoomCustomizationRequest findBySeanceId(int seanceId) throws SQLException {
        if (seanceId <= 0 || cnx == null || !hasCustomizationTable()) {
            return null;
        }

        try (PreparedStatement statement = requireConnection().prepareStatement(SELECT_BY_SEANCE_SQL)) {
            statement.setInt(1, seanceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapRequest(resultSet);
                }
            }
        }

        return null;
    }

    public List<SessionRoomCustomizationRequest> listAllRequests() {
        if (cnx == null || !hasCustomizationTable()) {
            return List.of();
        }

        List<SessionRoomCustomizationRequest> requests = new java.util.ArrayList<>();
        try (PreparedStatement statement = requireConnection().prepareStatement(SELECT_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                requests.add(mapRequest(resultSet));
            }
        } catch (SQLException | IllegalStateException exception) {
            return List.of();
        }
        return List.copyOf(requests);
    }

    public SessionRoomCustomizationRequest findBySeanceIdQuietly(int seanceId) {
        try {
            return findBySeanceId(seanceId);
        } catch (SQLException | IllegalStateException exception) {
            return null;
        }
    }

    public SessionRoomCustomizationRequest approveRequest(
        int seanceId,
        SessionRoomCustomizationConfig approvedConfig,
        String commentAdmin
    ) throws SQLException {
        requireCustomizationTable();
        SessionRoomCustomizationRequest existing = requireRequest(seanceId);
        SessionRoomCustomizationConfig effectiveConfig = approvedConfig == null
            ? existing.requestedConfig()
            : approvedConfig;
        return updateAdministrativeStatus(seanceId, STATUS_APPROVED, effectiveConfig, commentAdmin);
    }

    public SessionRoomCustomizationRequest markInReview(int seanceId, String commentAdmin) throws SQLException {
        return updateAdministrativeStatus(seanceId, STATUS_IN_REVIEW, null, commentAdmin);
    }

    public SessionRoomCustomizationRequest rejectRequest(int seanceId, String commentAdmin) throws SQLException {
        return updateAdministrativeStatus(seanceId, STATUS_REJECTED, null, commentAdmin);
    }

    public SessionRoomCustomizationRequest cancelRequest(int seanceId, String commentAdmin) throws SQLException {
        return updateAdministrativeStatus(seanceId, STATUS_CANCELLED, null, commentAdmin);
    }

    public void deleteBySeanceId(int seanceId) throws SQLException {
        if (seanceId <= 0 || cnx == null || !hasCustomizationTable()) {
            return;
        }

        SessionRoomCustomizationRequest existingRequest = findBySeanceId(seanceId);
        try (PreparedStatement statement = requireConnection().prepareStatement(DELETE_BY_SEANCE_SQL)) {
            statement.setInt(1, seanceId);
            int deletedRows = statement.executeUpdate();
            if (deletedRows > 0 && existingRequest != null) {
                notifyTutorAboutRequestDeletion(existingRequest);
            }
        }
    }

    public void publishApprovedDraftSessions() {
        if (cnx == null || !hasCustomizationTable()) {
            return;
        }

        try (PreparedStatement statement = requireConnection().prepareStatement(PUBLISH_ALL_APPROVED_DRAFT_SEANCES_SQL)) {
            statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            statement.setString(2, STATUS_APPROVED);
            statement.executeUpdate();
        } catch (SQLException | IllegalStateException exception) {
            // Best-effort self-healing: ignore and keep standard loading flow available.
        }
    }

    public SessionRoomCustomizationConfig resolveApprovedConfigurationForSeance(int seanceId) {
        SessionRoomCustomizationRequest request = findBySeanceIdQuietly(seanceId);
        return request == null ? null : request.effectiveApprovedConfig();
    }

    public String resolveEffectiveDispositionForSeance(int seanceId, String fallbackDisposition) {
        SessionRoomCustomizationConfig configuration = resolveApprovedConfigurationForSeance(seanceId);
        if (configuration == null || configuration.disposition() == null || configuration.disposition().isBlank()) {
            return fallbackDisposition;
        }
        return configuration.disposition();
    }

    public Salle resolveEffectiveSalleForSeance(Seance seance, Salle baseSalle) {
        if (baseSalle == null || seance == null || seance.getId() <= 0) {
            return baseSalle;
        }

        SessionRoomCustomizationConfig configuration = resolveApprovedConfigurationForSeance(seance.getId());
        if (configuration == null) {
            return baseSalle;
        }

        return applyConfigurationToSalle(baseSalle, configuration);
    }

    public int resolveEffectiveSeatCountForSeance(Seance seance, int fallbackSeatCount) {
        if (seance == null || seance.getId() <= 0) {
            return fallbackSeatCount;
        }

        SessionRoomCustomizationConfig configuration = resolveApprovedConfigurationForSeance(seance.getId());
        if (configuration == null) {
            return fallbackSeatCount;
        }
        if (configuration.capacity() != null) {
            return configuration.capacity();
        }
        if (configuration.hasSeatLayout()) {
            return configuration.seatLayoutSize();
        }
        return fallbackSeatCount;
    }

    public Set<Integer> resolveApprovedSeatKeysForSeance(int seanceId) {
        SessionRoomCustomizationConfig configuration = resolveApprovedConfigurationForSeance(seanceId);
        if (configuration == null || !configuration.hasSeatLayout()) {
            return Set.of();
        }

        LinkedHashSet<Integer> seatKeys = new LinkedHashSet<>();
        for (SessionRoomCustomizationConfig.SeatLayoutSlot slot : configuration.seatLayout()) {
            seatKeys.add(slot.seatKey());
        }
        return Set.copyOf(seatKeys);
    }

    public List<SeatSelectionLayoutResolver.SeatLayoutInput> resolveEffectiveSeatLayoutInputs(
        int seanceId,
        List<SeatSelectionLayoutResolver.SeatLayoutInput> fallbackInputs
    ) {
        SessionRoomCustomizationConfig configuration = resolveApprovedConfigurationForSeance(seanceId);
        if (configuration == null || !configuration.hasSeatLayout()) {
            return fallbackInputs == null ? List.of() : List.copyOf(fallbackInputs);
        }

        return configuration.seatLayout().stream()
            .map(slot -> new SeatSelectionLayoutResolver.SeatLayoutInput(slot.seatKey(), slot.row(), slot.column()))
            .toList();
    }

    public String getPublicationBlockingReason(Seance seance) {
        if (seance == null || seance.getId() <= 0 || seance.getStatus() != 1 || !seance.isPresentiel()) {
            return null;
        }

        SessionRoomCustomizationRequest request = findBySeanceIdQuietly(seance.getId());
        if (request == null || request.isCancelled()) {
            return null;
        }

        if (!request.isApproved()) {
            return switch (request.status()) {
                case STATUS_PENDING -> "La personnalisation de salle liee a cette seance est encore en attente de validation admin.";
                case STATUS_IN_REVIEW -> "La personnalisation de salle liee a cette seance est en cours d'analyse par l'admin.";
                case STATUS_REJECTED -> "La personnalisation de salle liee a cette seance a ete rejetee. Ajustez-la ou annulez la demande avant publication.";
                default -> "La personnalisation de salle liee a cette seance doit etre approuvee avant publication.";
            };
        }

        SessionRoomCustomizationConfig approvedConfiguration = request.effectiveApprovedConfig();
        if (approvedConfiguration == null) {
            return null;
        }

        if (approvedConfiguration.capacity() != null && seance.getMaxParticipants() > approvedConfiguration.capacity()) {
            return "La personnalisation approuvee limite cette seance a "
                + approvedConfiguration.capacity()
                + " participant(s). Reduisez la capacite ou demandez une autre configuration.";
        }

        if (approvedConfiguration.hasSeatLayout() && seance.getMaxParticipants() > approvedConfiguration.seatLayoutSize()) {
            return "La personnalisation approuvee prevoit seulement "
                + approvedConfiguration.seatLayoutSize()
                + " place(s) utilisables pour cette seance.";
        }

        return null;
    }

    private SessionRoomCustomizationRequest updateAdministrativeStatus(
        int seanceId,
        String nextStatus,
        SessionRoomCustomizationConfig approvedConfig,
        String commentAdmin
    ) throws SQLException {
        requireCustomizationTable();
        SessionRoomCustomizationRequest existing = requireRequest(seanceId);
        if (!existing.canBeReviewedByAdmin()) {
            throw new IllegalStateException(existing.isCancelled()
                ? "Cette demande est annulee. Seule la visualisation reste disponible."
                : "Cette demande a deja ete acceptee ou refusee. Seule la visualisation reste disponible.");
        }
        String approvedConfigJson = approvedConfig == null ? null : writeConfig(approvedConfig);
        String normalizedStatus = normalizeStatus(nextStatus);
        String normalizedCommentAdmin = normalizeText(commentAdmin);
        LocalDateTime reviewedAt = LocalDateTime.now();

        Connection connection = requireConnection();
        boolean previousAutoCommit = connection.getAutoCommit();
        if (previousAutoCommit) {
            connection.setAutoCommit(false);
        }

        try {
            try (PreparedStatement statement = connection.prepareStatement(UPDATE_STATUS_SQL)) {
                statement.setString(1, approvedConfigJson);
                statement.setString(2, normalizedCommentAdmin);
                statement.setString(3, normalizedStatus);
                statement.setTimestamp(4, Timestamp.valueOf(reviewedAt));
                statement.setInt(5, seanceId);
                statement.executeUpdate();
            }

            if (STATUS_APPROVED.equals(normalizedStatus)) {
                publishDraftSessionIfPresent(connection, seanceId, reviewedAt);
            }

            if (previousAutoCommit) {
                connection.commit();
            }
        } catch (SQLException | RuntimeException exception) {
            rollbackQuietly(connection, previousAutoCommit);
            throw exception;
        } finally {
            restoreAutoCommit(connection, previousAutoCommit);
        }

        if (!normalizeStatus(existing.status()).equals(normalizedStatus)) {
            notifyTutorAboutStatusChange(existing, normalizedStatus, normalizedCommentAdmin);
        }

        return new SessionRoomCustomizationRequest(
            existing.idDemande(),
            existing.seanceId(),
            existing.baseSalleId(),
            existing.tuteurId(),
            normalizedStatus,
            existing.requestedConfig(),
            approvedConfig,
            existing.commentTuteur(),
            normalizedCommentAdmin,
            existing.createdAt(),
            reviewedAt
        );
    }

    private void notifyAdminAboutCustomizationRequest(int seanceId, int baseSalleId, boolean updated) {
        if (notificationService == null || seanceId <= 0) {
            return;
        }

        StringBuilder message = new StringBuilder(ADMIN_NOTIFICATION_PREFIX);
        message.append(updated ? "Demande mise a jour" : "Nouvelle demande");
        message.append(" pour la seance #").append(seanceId);
        if (baseSalleId > 0) {
            message.append(" | salle #").append(baseSalleId);
        }
        notificationService.createForAdmin(message.toString(), 0);
    }

    private void notifyTutorAboutStatusChange(
        SessionRoomCustomizationRequest request,
        String nextStatus,
        String commentAdmin
    ) {
        if (notificationService == null || request == null || request.tuteurId() <= 0) {
            return;
        }

        String message = buildTutorStatusNotificationMessage(request, nextStatus, commentAdmin);
        if (message == null || message.isBlank()) {
            return;
        }
        notificationService.createForUser(request.tuteurId(), message, 0);
    }

    private void notifyTutorAboutRequestDeletion(SessionRoomCustomizationRequest request) {
        if (notificationService == null || request == null || request.tuteurId() <= 0) {
            return;
        }

        String message = "Personnalisation salle | Votre demande pour la seance #"
            + request.seanceId()
            + " a ete supprimee car la seance n'est plus disponible.";
        notificationService.createForUser(request.tuteurId(), message, 0);
    }

    private String buildTutorStatusNotificationMessage(
        SessionRoomCustomizationRequest request,
        String nextStatus,
        String commentAdmin
    ) {
        if (request == null) {
            return null;
        }

        StringBuilder message = new StringBuilder("Personnalisation salle | ");
        message.append("Votre demande pour la seance #").append(request.seanceId()).append(" ");

        switch (normalizeStatus(nextStatus)) {
            case STATUS_APPROVED ->
                message.append("a ete approuvee par l'admin.");
            case STATUS_REJECTED ->
                message.append("a ete refusee par l'admin.");
            case STATUS_IN_REVIEW ->
                message.append("est en cours d'analyse par l'admin.");
            case STATUS_CANCELLED ->
                message.append("a ete annulee par l'admin.");
            default ->
                message.append("a change de statut.");
        }

        if (commentAdmin != null && !commentAdmin.isBlank()) {
            message.append(" Commentaire admin: ").append(commentAdmin);
        }
        return message.toString();
    }

    private SessionRoomCustomizationRequest requireRequest(int seanceId) throws SQLException {
        if (seanceId <= 0) {
            throw new IllegalArgumentException("La seance liee a la personnalisation est invalide.");
        }

        SessionRoomCustomizationRequest request = findBySeanceId(seanceId);
        if (request == null) {
            throw new IllegalArgumentException("Aucune demande de personnalisation n'est liee a cette seance.");
        }
        return request;
    }

    private void fillRequestStatement(
        PreparedStatement statement,
        int seanceId,
        int baseSalleId,
        int tuteurId,
        SessionRoomCustomizationConfig requestedConfig,
        String requestedConfigJson,
        String commentTuteur,
        String status,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt
    ) throws SQLException {
        statement.setInt(1, seanceId);
        statement.setInt(2, baseSalleId);
        statement.setInt(3, tuteurId);
        statement.setString(4, requestedConfig == null ? null : requestedConfig.disposition());
        if (requestedConfig == null || requestedConfig.capacity() == null) {
            statement.setNull(5, java.sql.Types.INTEGER);
        } else {
            statement.setInt(5, requestedConfig.capacity());
        }
        statement.setString(6, requestedConfig == null ? null : requestedConfig.tableStyle());
        statement.setString(7, requestedConfig == null ? null : requestedConfig.chairStyle());
        statement.setBoolean(8, requestedConfig != null && Boolean.TRUE.equals(requestedConfig.accessibilityRequired()));
        statement.setString(9, requestedConfigJson);
        statement.setString(10, null);
        statement.setString(11, commentTuteur);
        statement.setString(12, null);
        statement.setString(13, normalizeStatus(status));
        statement.setTimestamp(14, Timestamp.valueOf(createdAt == null ? LocalDateTime.now() : createdAt));
        if (reviewedAt == null) {
            statement.setNull(15, java.sql.Types.TIMESTAMP);
        } else {
            statement.setTimestamp(15, Timestamp.valueOf(reviewedAt));
        }
    }

    private void fillRequestUpdateStatement(
        PreparedStatement statement,
        int baseSalleId,
        int tuteurId,
        SessionRoomCustomizationConfig requestedConfig,
        String requestedConfigJson,
        String commentTuteur,
        int seanceId
    ) throws SQLException {
        statement.setInt(1, baseSalleId);
        statement.setInt(2, tuteurId);
        statement.setString(3, requestedConfig == null ? null : requestedConfig.disposition());
        if (requestedConfig == null || requestedConfig.capacity() == null) {
            statement.setNull(4, java.sql.Types.INTEGER);
        } else {
            statement.setInt(4, requestedConfig.capacity());
        }
        statement.setString(5, requestedConfig == null ? null : requestedConfig.tableStyle());
        statement.setString(6, requestedConfig == null ? null : requestedConfig.chairStyle());
        statement.setBoolean(7, requestedConfig != null && Boolean.TRUE.equals(requestedConfig.accessibilityRequired()));
        statement.setString(8, requestedConfigJson);
        statement.setString(9, null);
        statement.setString(10, commentTuteur);
        statement.setString(11, null);
        statement.setString(12, STATUS_PENDING);
        statement.setNull(13, java.sql.Types.TIMESTAMP);
        statement.setInt(14, seanceId);
    }

    private SessionRoomCustomizationRequest mapRequest(ResultSet resultSet) throws SQLException {
        return new SessionRoomCustomizationRequest(
            resultSet.getInt("idDemande"),
            resultSet.getInt("seance_id"),
            resultSet.getInt("base_salle_id"),
            resultSet.getInt("tuteur_id"),
            resultSet.getString("status"),
            readConfig(resultSet.getString("requested_config_json")),
            readConfig(resultSet.getString("approved_config_json")),
            resultSet.getString("comment_tuteur"),
            resultSet.getString("comment_admin"),
            toLocalDateTime(resultSet.getTimestamp("created_at")),
            toLocalDateTime(resultSet.getTimestamp("reviewed_at"))
        );
    }

    private Salle applyConfigurationToSalle(Salle baseSalle, SessionRoomCustomizationConfig configuration) {
        if (baseSalle == null || configuration == null) {
            return baseSalle;
        }

        return new Salle(
            baseSalle.getIdSalle(),
            baseSalle.getNom(),
            configuration.capacity() == null ? baseSalle.getCapacite() : configuration.capacity(),
            baseSalle.getLocalisation(),
            baseSalle.getTypeSalle(),
            baseSalle.getEtat(),
            baseSalle.getDescription(),
            baseSalle.getBatiment(),
            baseSalle.getEtage(),
            firstNonBlank(configuration.disposition(), baseSalle.getTypeDisposition()),
            configuration.accessibilityRequiredOrDefault(baseSalle.isAccesHandicape()),
            baseSalle.getStatutDetaille(),
            baseSalle.getDateDerniereMaintenance()
        );
    }

    private SessionRoomCustomizationConfig readConfig(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.readValue(rawJson, SessionRoomCustomizationConfig.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String writeConfig(SessionRoomCustomizationConfig configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("La configuration de personnalisation est obligatoire.");
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(configuration);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Serialisation de la personnalisation impossible.", exception);
        }
    }

    private void validateRequestArguments(int seanceId, int baseSalleId, int tuteurId, SessionRoomCustomizationConfig requestedConfig) {
        if (seanceId <= 0) {
            throw new IllegalArgumentException("La seance liee a la personnalisation est invalide.");
        }
        if (baseSalleId <= 0) {
            throw new IllegalArgumentException("La salle de base de la personnalisation est obligatoire.");
        }
        if (tuteurId <= 0) {
            throw new IllegalArgumentException("Le tuteur lie a la personnalisation est obligatoire.");
        }
        if (requestedConfig == null) {
            throw new IllegalArgumentException("La configuration demandee est obligatoire.");
        }
    }

    private void validateConfigurationSupportsSessionCapacity(int seanceId, SessionRoomCustomizationConfig requestedConfig) throws SQLException {
        if (seanceId <= 0 || requestedConfig == null || cnx == null) {
            return;
        }

        int customizationCapacity = resolveCustomizationEffectiveCapacity(requestedConfig);
        if (customizationCapacity <= 0) {
            return;
        }

        Integer sessionCapacity = findSessionCapacity(seanceId);
        if (sessionCapacity == null || sessionCapacity <= 0 || sessionCapacity <= customizationCapacity) {
            return;
        }

        throw new IllegalArgumentException(
            "La personnalisation prevoit seulement " + customizationCapacity
                + " place(s), alors que la seance est reglee sur " + sessionCapacity
                + " participant(s). Reduisez d'abord la capacite de la seance ou augmentez la personnalisation."
        );
    }

    private Integer findSessionCapacity(int seanceId) throws SQLException {
        try (PreparedStatement statement = requireConnection().prepareStatement(SELECT_SEANCE_CAPACITY_SQL)) {
            statement.setInt(1, seanceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("max_participants");
                }
            }
        }
        return null;
    }

    private int resolveCustomizationEffectiveCapacity(SessionRoomCustomizationConfig configuration) {
        if (configuration == null) {
            return 0;
        }
        if (configuration.hasSeatLayout()) {
            return configuration.seatLayoutSize();
        }
        return configuration.capacity() == null ? 0 : configuration.capacity();
    }

    private void publishDraftSessionIfPresent(Connection connection, int seanceId, LocalDateTime updatedAt) throws SQLException {
        if (connection == null || seanceId <= 0) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(PUBLISH_DRAFT_SEANCE_SQL)) {
            statement.setTimestamp(1, Timestamp.valueOf(updatedAt == null ? LocalDateTime.now() : updatedAt));
            statement.setInt(2, seanceId);
            statement.executeUpdate();
        }
    }

    private Connection requireConnection() {
        if (cnx == null) {
            throw new IllegalStateException("Connexion a la base de donnees indisponible.");
        }
        return cnx;
    }

    private void rollbackQuietly(Connection connection, boolean previousAutoCommit) {
        if (connection == null || !previousAutoCommit) {
            return;
        }

        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    private void restoreAutoCommit(Connection connection, boolean previousAutoCommit) {
        if (connection == null || !previousAutoCommit) {
            return;
        }

        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
        }
    }

    private void requireCustomizationTable() {
        if (!hasCustomizationTable()) {
            throw new IllegalStateException(
                "La table "
                    + TABLE_NAME
                    + " est absente. Executez le script SQL session_room_customization_schema.sql dans phpMyAdmin avant d'utiliser cette fonctionnalite."
            );
        }
    }

    private boolean hasCustomizationTable() {
        if (cnx == null) {
            return false;
        }

        try {
            return DatabaseSchemaUtils.tableExists(cnx, TABLE_NAME);
        } catch (SQLException exception) {
            return false;
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            return STATUS_PENDING;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case STATUS_PENDING, STATUS_IN_REVIEW, STATUS_APPROVED, STATUS_REJECTED, STATUS_CANCELLED -> normalized;
            default -> STATUS_PENDING;
        };
    }

    private String firstNonBlank(String primaryValue, String fallbackValue) {
        if (primaryValue != null && !primaryValue.isBlank()) {
            return primaryValue;
        }
        return fallbackValue;
    }
}
