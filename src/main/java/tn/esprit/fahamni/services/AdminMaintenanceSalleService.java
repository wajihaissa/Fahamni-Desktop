package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.MaintenanceSalle;
import tn.esprit.fahamni.Models.ReclamationSalle;
import tn.esprit.fahamni.interfaces.IServices;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminMaintenanceSalleService implements IServices<MaintenanceSalle> {

    private static final String CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS maintenance_salle (
            idMaintenance INT AUTO_INCREMENT PRIMARY KEY,
            idSalle INT NOT NULL,
            idReclamation INT NULL,
            typeMaintenance VARCHAR(100) NOT NULL,
            statut VARCHAR(50) NOT NULL DEFAULT 'planifiee',
            responsable VARCHAR(150) NULL,
            detailsIntervention TEXT NULL,
            datePlanifiee DATE NULL,
            dateDebut DATE NULL,
            dateFin DATE NULL,
            dateCreation DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            CONSTRAINT uk_maintenance_salle_reclamation UNIQUE (idReclamation),
            CONSTRAINT fk_maintenance_salle_salle
                FOREIGN KEY (idSalle) REFERENCES salle(idSalle)
                ON DELETE CASCADE,
            CONSTRAINT fk_maintenance_salle_reclamation
                FOREIGN KEY (idReclamation) REFERENCES reclamation_salle(idReclamation)
                ON DELETE SET NULL
        )
        """;
    private static final String INSERT_SQL = """
        INSERT INTO maintenance_salle (
            idSalle, idReclamation, typeMaintenance, statut, responsable,
            detailsIntervention, datePlanifiee, dateDebut, dateFin, dateCreation
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    private static final String UPDATE_SQL = """
        UPDATE maintenance_salle
        SET idSalle = ?, idReclamation = ?, typeMaintenance = ?, statut = ?, responsable = ?,
            detailsIntervention = ?, datePlanifiee = ?, dateDebut = ?, dateFin = ?
        WHERE idMaintenance = ?
        """;
    private static final String DELETE_SQL = "DELETE FROM maintenance_salle WHERE idMaintenance = ?";
    private static final String SELECT_ALL_SQL = """
        SELECT m.idMaintenance, m.idSalle, m.idReclamation, m.typeMaintenance, m.statut, m.responsable,
               m.detailsIntervention, m.datePlanifiee, m.dateDebut, m.dateFin, m.dateCreation,
               s.nom AS nomSalle, s.batiment AS batimentSalle, s.localisation AS localisationSalle,
               s.typeSalle AS typeSalle, s.etat AS etatSalle, s.description AS descriptionSalle
        FROM maintenance_salle m
        INNER JOIN salle s ON s.idSalle = m.idSalle
        ORDER BY COALESCE(m.dateDebut, m.datePlanifiee, DATE(m.dateCreation)) DESC, m.idMaintenance DESC
        """;
    private static final String SELECT_BY_ID_SQL = """
        SELECT m.idMaintenance, m.idSalle, m.idReclamation, m.typeMaintenance, m.statut, m.responsable,
               m.detailsIntervention, m.datePlanifiee, m.dateDebut, m.dateFin, m.dateCreation,
               s.nom AS nomSalle, s.batiment AS batimentSalle, s.localisation AS localisationSalle,
               s.typeSalle AS typeSalle, s.etat AS etatSalle, s.description AS descriptionSalle
        FROM maintenance_salle m
        INNER JOIN salle s ON s.idSalle = m.idSalle
        WHERE m.idMaintenance = ?
        """;
    private static final String SELECT_BY_RECLAMATION_SQL = """
        SELECT m.idMaintenance, m.idSalle, m.idReclamation, m.typeMaintenance, m.statut, m.responsable,
               m.detailsIntervention, m.datePlanifiee, m.dateDebut, m.dateFin, m.dateCreation,
               s.nom AS nomSalle, s.batiment AS batimentSalle, s.localisation AS localisationSalle,
               s.typeSalle AS typeSalle, s.etat AS etatSalle, s.description AS descriptionSalle
        FROM maintenance_salle m
        INNER JOIN salle s ON s.idSalle = m.idSalle
        WHERE m.idReclamation = ?
        """;
    private static final String SELECT_BY_SALLE_SQL = """
        SELECT m.idMaintenance, m.idSalle, m.idReclamation, m.typeMaintenance, m.statut, m.responsable,
               m.detailsIntervention, m.datePlanifiee, m.dateDebut, m.dateFin, m.dateCreation,
               s.nom AS nomSalle, s.batiment AS batimentSalle, s.localisation AS localisationSalle,
               s.typeSalle AS typeSalle, s.etat AS etatSalle, s.description AS descriptionSalle
        FROM maintenance_salle m
        INNER JOIN salle s ON s.idSalle = m.idSalle
        WHERE m.idSalle = ?
        ORDER BY COALESCE(m.dateDebut, m.datePlanifiee, DATE(m.dateCreation)) DESC, m.idMaintenance DESC
        """;
    private static final String SELECT_SALLE_ETAT_SQL = "SELECT etat FROM salle WHERE idSalle = ?";
    private static final String UPDATE_SALLE_SUMMARY_SQL =
        "UPDATE salle SET etat = ?, statutDetaille = ?, dateDerniereMaintenance = ? WHERE idSalle = ?";

    private final Connection cnx;

    public AdminMaintenanceSalleService() {
        this.cnx = MyDataBase.getInstance().getCnx();
        new AdminReclamationSalleService();
        ensureTable();
    }

    @Override
    public void add(MaintenanceSalle maintenance) throws SQLException {
        validateMaintenance(maintenance, false);

        try (PreparedStatement statement = requireConnection().prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            fillInsertStatement(statement, maintenance);
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    maintenance.setIdMaintenance(generatedKeys.getInt(1));
                }
            }
        }

        synchroniserSalleDepuisMaintenances(maintenance.getIdSalle());
    }

    @Override
    public List<MaintenanceSalle> getAll() throws SQLException {
        List<MaintenanceSalle> maintenances = new ArrayList<>();

        try (PreparedStatement statement = requireConnection().prepareStatement(SELECT_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                maintenances.add(mapMaintenance(resultSet));
            }
        }

        return maintenances;
    }

    @Override
    public void update(MaintenanceSalle maintenance) throws SQLException {
        validateMaintenance(maintenance, true);

        try (PreparedStatement statement = requireConnection().prepareStatement(UPDATE_SQL)) {
            statement.setInt(1, maintenance.getIdSalle());
            setNullableInteger(statement, 2, maintenance.getIdReclamation());
            statement.setString(3, normalizeType(maintenance.getTypeMaintenance()));
            statement.setString(4, normalizeStatut(maintenance.getStatut()));
            statement.setString(5, normalizeText(maintenance.getResponsable()));
            statement.setString(6, normalizeText(maintenance.getDetailsIntervention()));
            setNullableDate(statement, 7, maintenance.getDatePlanifiee());
            setNullableDate(statement, 8, maintenance.getDateDebut());
            setNullableDate(statement, 9, maintenance.getDateFin());
            statement.setInt(10, maintenance.getIdMaintenance());

            if (statement.executeUpdate() == 0) {
                throw new SQLException("Aucune maintenance trouvee avec l'id " + maintenance.getIdMaintenance() + ".");
            }
        }

        synchroniserSalleDepuisMaintenances(maintenance.getIdSalle());
    }

    @Override
    public void delete(MaintenanceSalle maintenance) throws SQLException {
        if (maintenance == null) {
            throw new IllegalArgumentException("La maintenance est obligatoire.");
        }
        deleteById(maintenance.getIdMaintenance(), maintenance.getIdSalle());
    }

    public MaintenanceSalle recupererParId(int idMaintenance) throws SQLException {
        if (idMaintenance <= 0) {
            throw new IllegalArgumentException("L'id de la maintenance doit etre positif.");
        }

        try (PreparedStatement statement = requireConnection().prepareStatement(SELECT_BY_ID_SQL)) {
            statement.setInt(1, idMaintenance);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapMaintenance(resultSet);
                }
            }
        }

        return null;
    }

    public MaintenanceSalle recupererParReclamationId(int idReclamation) throws SQLException {
        if (idReclamation <= 0) {
            throw new IllegalArgumentException("L'id de la reclamation doit etre positif.");
        }

        try (PreparedStatement statement = requireConnection().prepareStatement(SELECT_BY_RECLAMATION_SQL)) {
            statement.setInt(1, idReclamation);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapMaintenance(resultSet);
                }
            }
        }

        return null;
    }

    public List<MaintenanceSalle> getBySalle(int idSalle) throws SQLException {
        if (idSalle <= 0) {
            throw new IllegalArgumentException("L'id de la salle doit etre positif.");
        }

        List<MaintenanceSalle> maintenances = new ArrayList<>();

        try (PreparedStatement statement = requireConnection().prepareStatement(SELECT_BY_SALLE_SQL)) {
            statement.setInt(1, idSalle);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    maintenances.add(mapMaintenance(resultSet));
                }
            }
        }

        return maintenances;
    }

    public MaintenanceSalle creerDepuisReclamation(ReclamationSalle reclamation, String commentaireAdmin) throws SQLException {
        if (reclamation == null) {
            throw new IllegalArgumentException("La reclamation source est obligatoire.");
        }

        MaintenanceSalle existante = recupererParReclamationId(reclamation.getIdReclamation());
        if (existante != null) {
            throw new IllegalStateException("Cette reclamation est deja rattachee a une maintenance.");
        }

        String commentaire = normalizeText(commentaireAdmin);
        MaintenanceSalle maintenance = new MaintenanceSalle(
            0,
            reclamation.getIdSalle(),
            reclamation.getIdReclamation(),
            reclamation.getNomSalle(),
            null,
            null,
            null,
            null,
            null,
            infererTypeDepuisProbleme(reclamation.getTypeProbleme()),
            "planifiee",
            null,
            buildDetailsDepuisReclamation(reclamation, commentaire),
            LocalDate.now(),
            null,
            null,
            LocalDateTime.now()
        );

        add(maintenance);
        return maintenance;
    }

    public List<String> getAvailableTypes() {
        return List.of("corrective", "preventive", "controle technique", "audiovisuel", "securite", "mobilier", "autre");
    }

    public List<String> getAvailableStatuts() {
        return List.of("planifiee", "en cours", "terminee", "annulee");
    }

    private void deleteById(int idMaintenance, int idSalle) throws SQLException {
        if (idMaintenance <= 0) {
            throw new IllegalArgumentException("L'id de la maintenance doit etre positif.");
        }

        try (PreparedStatement statement = requireConnection().prepareStatement(DELETE_SQL)) {
            statement.setInt(1, idMaintenance);

            if (statement.executeUpdate() == 0) {
                throw new SQLException("Aucune maintenance trouvee avec l'id " + idMaintenance + ".");
            }
        }

        if (idSalle > 0) {
            synchroniserSalleDepuisMaintenances(idSalle);
        }
    }

    private void fillInsertStatement(PreparedStatement statement, MaintenanceSalle maintenance) throws SQLException {
        statement.setInt(1, maintenance.getIdSalle());
        setNullableInteger(statement, 2, maintenance.getIdReclamation());
        statement.setString(3, normalizeType(maintenance.getTypeMaintenance()));
        statement.setString(4, normalizeStatut(maintenance.getStatut()));
        statement.setString(5, normalizeText(maintenance.getResponsable()));
        statement.setString(6, normalizeText(maintenance.getDetailsIntervention()));
        setNullableDate(statement, 7, maintenance.getDatePlanifiee());
        setNullableDate(statement, 8, maintenance.getDateDebut());
        setNullableDate(statement, 9, maintenance.getDateFin());
        setNullableTimestamp(statement, 10, maintenance.getDateCreation());
    }

    private MaintenanceSalle mapMaintenance(ResultSet resultSet) throws SQLException {
        return new MaintenanceSalle(
            resultSet.getInt("idMaintenance"),
            resultSet.getInt("idSalle"),
            getNullableInteger(resultSet, "idReclamation"),
            resultSet.getString("nomSalle"),
            resultSet.getString("batimentSalle"),
            resultSet.getString("localisationSalle"),
            resultSet.getString("typeSalle"),
            resultSet.getString("etatSalle"),
            resultSet.getString("descriptionSalle"),
            resultSet.getString("typeMaintenance"),
            resultSet.getString("statut"),
            resultSet.getString("responsable"),
            resultSet.getString("detailsIntervention"),
            toLocalDate(resultSet.getDate("datePlanifiee")),
            toLocalDate(resultSet.getDate("dateDebut")),
            toLocalDate(resultSet.getDate("dateFin")),
            toLocalDateTime(resultSet.getTimestamp("dateCreation"))
        );
    }

    private void validateMaintenance(MaintenanceSalle maintenance, boolean requireId) {
        if (maintenance == null) {
            throw new IllegalArgumentException("La maintenance est obligatoire.");
        }
        if (requireId && maintenance.getIdMaintenance() <= 0) {
            throw new IllegalArgumentException("L'id de la maintenance est obligatoire pour la modification.");
        }
        if (maintenance.getIdSalle() <= 0) {
            throw new IllegalArgumentException("La salle concernee est obligatoire.");
        }
        if (isBlank(maintenance.getTypeMaintenance())) {
            throw new IllegalArgumentException("Le type de maintenance est obligatoire.");
        }
        if (isBlank(maintenance.getStatut())) {
            throw new IllegalArgumentException("Le statut de maintenance est obligatoire.");
        }
        if (maintenance.getDateCreation() == null) {
            maintenance.setDateCreation(LocalDateTime.now());
        }
        if (maintenance.getDateFin() != null && maintenance.getDateDebut() != null
            && maintenance.getDateFin().isBefore(maintenance.getDateDebut())) {
            throw new IllegalArgumentException("La date de fin ne peut pas preceder la date de debut.");
        }
        if (maintenance.getDateFin() != null && maintenance.getDatePlanifiee() != null
            && maintenance.getDateFin().isBefore(maintenance.getDatePlanifiee())) {
            throw new IllegalArgumentException("La date de fin ne peut pas preceder la date planifiee.");
        }
    }

    private void synchroniserSalleDepuisMaintenances(int idSalle) throws SQLException {
        List<MaintenanceSalle> maintenances = getBySalle(idSalle);
        String etatActuel = recupererEtatSalle(idSalle);

        String nouvelEtat = etatActuel;
        String statutDetaille = null;
        LocalDate dateDerniereMaintenance = null;

        MaintenanceSalle maintenanceActive = maintenances.stream()
            .filter(this::estMaintenanceActive)
            .findFirst()
            .orElse(null);

        if (maintenanceActive != null) {
            nouvelEtat = "en maintenance";
            statutDetaille = buildSalleSummary(maintenanceActive, true);
            dateDerniereMaintenance = resolveReferenceDate(maintenanceActive);
        } else if (!maintenances.isEmpty()) {
            MaintenanceSalle derniereMaintenance = maintenances.get(0);
            statutDetaille = buildSalleSummary(derniereMaintenance, false);
            dateDerniereMaintenance = resolveReferenceDate(derniereMaintenance);

            if ("en maintenance".equalsIgnoreCase(etatActuel)) {
                nouvelEtat = "disponible";
            }
        } else if ("en maintenance".equalsIgnoreCase(etatActuel)) {
            nouvelEtat = "disponible";
        }

        try (PreparedStatement statement = requireConnection().prepareStatement(UPDATE_SALLE_SUMMARY_SQL)) {
            statement.setString(1, nouvelEtat == null ? "disponible" : nouvelEtat);
            statement.setString(2, normalizeText(statutDetaille));
            setNullableDate(statement, 3, dateDerniereMaintenance);
            statement.setInt(4, idSalle);
            statement.executeUpdate();
        }
    }

    private boolean estMaintenanceActive(MaintenanceSalle maintenance) {
        String statut = normalize(maintenance.getStatut());
        return "planifiee".equals(statut) || "en cours".equals(statut);
    }

    private LocalDate resolveReferenceDate(MaintenanceSalle maintenance) {
        if (maintenance.getDateFin() != null) {
            return maintenance.getDateFin();
        }
        if (maintenance.getDateDebut() != null) {
            return maintenance.getDateDebut();
        }
        return maintenance.getDatePlanifiee();
    }

    private String buildSalleSummary(MaintenanceSalle maintenance, boolean active) {
        String prefix = active ? "Maintenance active" : "Derniere maintenance";
        StringBuilder builder = new StringBuilder(prefix)
            .append(" | ")
            .append(capitalize(maintenance.getTypeMaintenance()));

        if (!isBlank(maintenance.getResponsable())) {
            builder.append(" | ").append(maintenance.getResponsable().trim());
        }

        if (!isBlank(maintenance.getDetailsIntervention())) {
            builder.append(" | ").append(maintenance.getDetailsIntervention().trim());
        }

        return builder.toString();
    }

    private String recupererEtatSalle(int idSalle) throws SQLException {
        try (PreparedStatement statement = requireConnection().prepareStatement(SELECT_SALLE_ETAT_SQL)) {
            statement.setInt(1, idSalle);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("etat");
                }
            }
        }

        return "disponible";
    }

    private void ensureTable() {
        if (cnx == null) {
            return;
        }

        try (PreparedStatement statement = cnx.prepareStatement(CREATE_TABLE_SQL)) {
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossible d'initialiser la table maintenance_salle.", exception);
        }
    }

    private Connection requireConnection() {
        if (cnx == null) {
            throw new IllegalStateException("Connexion a la base de donnees indisponible.");
        }
        return cnx;
    }

    private String infererTypeDepuisProbleme(String typeProbleme) {
        String normalized = normalize(typeProbleme);
        if (normalized.contains("audiovisuel") || normalized.contains("connexion") || normalized.contains("equipement")) {
            return "audiovisuel";
        }
        if (normalized.contains("securite")) {
            return "securite";
        }
        if (normalized.contains("mobilier")) {
            return "mobilier";
        }
        return "corrective";
    }

    private String buildDetailsDepuisReclamation(ReclamationSalle reclamation, String commentaireAdmin) {
        StringBuilder builder = new StringBuilder("Issue de la reclamation #")
            .append(reclamation.getIdReclamation())
            .append(" | ")
            .append(reclamation.getDescription());

        if (!isBlank(commentaireAdmin)) {
            builder.append(" | ").append(commentaireAdmin.trim());
        }

        return builder.toString();
    }

    private String normalizeText(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String normalizeType(String value) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("Le type de maintenance est obligatoire.");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeStatut(String value) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("Le statut de maintenance est obligatoire.");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void setNullableDate(PreparedStatement statement, int index, LocalDate value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DATE);
            return;
        }
        statement.setDate(index, Date.valueOf(value));
    }

    private void setNullableTimestamp(PreparedStatement statement, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
            return;
        }
        statement.setTimestamp(index, Timestamp.valueOf(value));
    }

    private void setNullableInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
            return;
        }
        statement.setInt(index, value);
    }

    private Integer getNullableInteger(ResultSet resultSet, String columnLabel) throws SQLException {
        int value = resultSet.getInt(columnLabel);
        return resultSet.wasNull() ? null : value;
    }

    private LocalDate toLocalDate(Date value) {
        return value == null ? null : value.toLocalDate();
    }

    private LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private String capitalize(String value) {
        if (isBlank(value)) {
            return "";
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
