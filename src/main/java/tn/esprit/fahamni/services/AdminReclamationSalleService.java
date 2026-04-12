package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.ReclamationSalle;
import tn.esprit.fahamni.interfaces.IServices;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AdminReclamationSalleService implements IServices<ReclamationSalle> {

    private static final String CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS reclamation_salle (
            idReclamation INT AUTO_INCREMENT PRIMARY KEY,
            idSalle INT NOT NULL,
            nomDeclarant VARCHAR(150) NOT NULL,
            referenceSeance VARCHAR(150) NULL,
            typeProbleme VARCHAR(100) NOT NULL,
            priorite VARCHAR(50) NOT NULL,
            statut VARCHAR(80) NOT NULL DEFAULT 'nouvelle',
            description TEXT NOT NULL,
            commentaireAdmin TEXT NULL,
            dateReclamation DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            dateTraitement DATETIME NULL,
            CONSTRAINT fk_reclamation_salle_salle
                FOREIGN KEY (idSalle) REFERENCES salle(idSalle)
                ON DELETE CASCADE
        )
        """;
    private static final String INSERT_SQL =
        "INSERT INTO reclamation_salle (idSalle, nomDeclarant, referenceSeance, typeProbleme, priorite, statut, description, commentaireAdmin, dateReclamation, dateTraitement) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_SQL =
        "UPDATE reclamation_salle SET idSalle = ?, nomDeclarant = ?, referenceSeance = ?, typeProbleme = ?, priorite = ?, statut = ?, description = ?, commentaireAdmin = ?, dateReclamation = ?, dateTraitement = ? WHERE idReclamation = ?";
    private static final String DELETE_SQL = "DELETE FROM reclamation_salle WHERE idReclamation = ?";
    private static final String SELECT_ALL_SQL = """
        SELECT r.idReclamation, r.idSalle, s.nom AS nomSalle, r.nomDeclarant, r.referenceSeance,
               r.typeProbleme, r.priorite, r.statut, r.description, r.commentaireAdmin,
               r.dateReclamation, r.dateTraitement
        FROM reclamation_salle r
        LEFT JOIN salle s ON s.idSalle = r.idSalle
        ORDER BY r.dateReclamation DESC, r.idReclamation DESC
        """;
    private static final String SELECT_BY_ID_SQL = """
        SELECT r.idReclamation, r.idSalle, s.nom AS nomSalle, r.nomDeclarant, r.referenceSeance,
               r.typeProbleme, r.priorite, r.statut, r.description, r.commentaireAdmin,
               r.dateReclamation, r.dateTraitement
        FROM reclamation_salle r
        LEFT JOIN salle s ON s.idSalle = r.idSalle
        WHERE r.idReclamation = ?
        """;
    private static final String UPDATE_STATUS_SQL =
        "UPDATE reclamation_salle SET statut = ?, commentaireAdmin = ?, dateTraitement = ? WHERE idReclamation = ?";
    private final Connection cnx;

    public AdminReclamationSalleService() {
        this.cnx = MyDataBase.getInstance().getCnx();
        ensureTable();
    }

    @Override
    public void add(ReclamationSalle reclamation) throws SQLException {
        validateReclamation(reclamation, false);

        try (PreparedStatement statement = requireConnection().prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            fillStatement(statement, reclamation);
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    reclamation.setIdReclamation(generatedKeys.getInt(1));
                }
            }
        }
    }

    @Override
    public List<ReclamationSalle> getAll() throws SQLException {
        List<ReclamationSalle> reclamations = new ArrayList<>();

        try (PreparedStatement statement = requireConnection().prepareStatement(SELECT_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                reclamations.add(mapReclamation(resultSet));
            }
        }

        return reclamations;
    }

    @Override
    public void update(ReclamationSalle reclamation) throws SQLException {
        validateReclamation(reclamation, true);

        try (PreparedStatement statement = requireConnection().prepareStatement(UPDATE_SQL)) {
            fillStatement(statement, reclamation);
            statement.setInt(11, reclamation.getIdReclamation());

            if (statement.executeUpdate() == 0) {
                throw new SQLException("Aucune reclamation trouvee avec l'id " + reclamation.getIdReclamation() + ".");
            }
        }
    }

    @Override
    public void delete(ReclamationSalle reclamation) throws SQLException {
        if (reclamation == null) {
            throw new IllegalArgumentException("La reclamation est obligatoire.");
        }
        deleteById(reclamation.getIdReclamation());
    }

    public ReclamationSalle recupererParId(int idReclamation) throws SQLException {
        if (idReclamation <= 0) {
            throw new IllegalArgumentException("L'id de la reclamation doit etre positif.");
        }

        try (PreparedStatement statement = requireConnection().prepareStatement(SELECT_BY_ID_SQL)) {
            statement.setInt(1, idReclamation);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapReclamation(resultSet);
                }
            }
        }

        return null;
    }

    public List<String> getAvailableTypesProbleme() {
        return List.of(
            "equipement defectueux",
            "probleme de proprete",
            "probleme de securite",
            "mobilier endommage",
            "connexion ou audiovisuel",
            "autre"
        );
    }

    public List<String> getAvailablePriorites() {
        return List.of("faible", "moyenne", "haute", "critique");
    }

    public List<String> getAvailableStatuts() {
        return List.of("nouvelle", "en cours d'analyse", "convertie en maintenance", "resolue", "rejetee");
    }

    public void demarrerAnalyse(int idReclamation, String commentaireAdmin) throws SQLException {
        updateStatut(idReclamation, "en cours d'analyse", commentaireAdmin, true);
    }

    public void marquerResolue(int idReclamation, String commentaireAdmin) throws SQLException {
        updateStatut(idReclamation, "resolue", commentaireAdmin, true);
    }

    public void rejeter(int idReclamation, String commentaireAdmin) throws SQLException {
        updateStatut(idReclamation, "rejetee", commentaireAdmin, true);
    }

    public void convertirEnMaintenance(int idReclamation, String commentaireAdmin) throws SQLException {
        ReclamationSalle reclamation = recupererParId(idReclamation);
        if (reclamation == null) {
            throw new SQLException("Aucune reclamation trouvee avec l'id " + idReclamation + ".");
        }

        String commentaire = normalizeText(commentaireAdmin);
        if (commentaire == null) {
            commentaire = "Reclamation convertie en maintenance pour verification technique.";
        }

        AdminMaintenanceSalleService maintenanceService = new AdminMaintenanceSalleService();
        Connection connection = requireConnection();
        boolean initialAutoCommit = connection.getAutoCommit();

        try {
            connection.setAutoCommit(false);
            maintenanceService.creerDepuisReclamation(reclamation, commentaire);
            updateStatut(idReclamation, "convertie en maintenance", commentaire, true);
            connection.commit();
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(initialAutoCommit);
        }
    }

    private void deleteById(int idReclamation) throws SQLException {
        if (idReclamation <= 0) {
            throw new IllegalArgumentException("L'id de la reclamation doit etre positif.");
        }

        try (PreparedStatement statement = requireConnection().prepareStatement(DELETE_SQL)) {
            statement.setInt(1, idReclamation);

            if (statement.executeUpdate() == 0) {
                throw new SQLException("Aucune reclamation trouvee avec l'id " + idReclamation + ".");
            }
        }
    }

    private void updateStatut(int idReclamation, String statut, String commentaireAdmin, boolean stampProcessingDate) throws SQLException {
        if (idReclamation <= 0) {
            throw new IllegalArgumentException("L'id de la reclamation doit etre positif.");
        }

        String statutNormalise = normalizeStatut(statut);
        LocalDateTime dateTraitement = stampProcessingDate ? LocalDateTime.now() : null;

        try (PreparedStatement statement = requireConnection().prepareStatement(UPDATE_STATUS_SQL)) {
            statement.setString(1, statutNormalise);
            statement.setString(2, normalizeText(commentaireAdmin));
            setNullableTimestamp(statement, 3, dateTraitement);
            statement.setInt(4, idReclamation);

            if (statement.executeUpdate() == 0) {
                throw new SQLException("Aucune reclamation trouvee avec l'id " + idReclamation + ".");
            }
        }
    }

    private void fillStatement(PreparedStatement statement, ReclamationSalle reclamation) throws SQLException {
        statement.setInt(1, reclamation.getIdSalle());
        statement.setString(2, reclamation.getNomDeclarant().trim());
        statement.setString(3, normalizeText(reclamation.getReferenceSeance()));
        statement.setString(4, normalizeTypeProbleme(reclamation.getTypeProbleme()));
        statement.setString(5, normalizePriorite(reclamation.getPriorite()));
        statement.setString(6, normalizeStatut(reclamation.getStatut()));
        statement.setString(7, reclamation.getDescription().trim());
        statement.setString(8, normalizeText(reclamation.getCommentaireAdmin()));
        setNullableTimestamp(statement, 9, reclamation.getDateReclamation());
        setNullableTimestamp(statement, 10, reclamation.getDateTraitement());
    }

    private ReclamationSalle mapReclamation(ResultSet resultSet) throws SQLException {
        return new ReclamationSalle(
            resultSet.getInt("idReclamation"),
            resultSet.getInt("idSalle"),
            resultSet.getString("nomSalle"),
            resultSet.getString("nomDeclarant"),
            resultSet.getString("referenceSeance"),
            resultSet.getString("typeProbleme"),
            resultSet.getString("priorite"),
            resultSet.getString("statut"),
            resultSet.getString("description"),
            resultSet.getString("commentaireAdmin"),
            toLocalDateTime(resultSet.getTimestamp("dateReclamation")),
            toLocalDateTime(resultSet.getTimestamp("dateTraitement"))
        );
    }

    private void validateReclamation(ReclamationSalle reclamation, boolean requireId) {
        if (reclamation == null) {
            throw new IllegalArgumentException("La reclamation est obligatoire.");
        }
        if (requireId && reclamation.getIdReclamation() <= 0) {
            throw new IllegalArgumentException("L'id de la reclamation est obligatoire pour la modification.");
        }
        if (reclamation.getIdSalle() <= 0) {
            throw new IllegalArgumentException("La salle concernee est obligatoire.");
        }
        if (isBlank(reclamation.getNomDeclarant())) {
            throw new IllegalArgumentException("Le nom du declarant est obligatoire.");
        }
        if (isBlank(reclamation.getTypeProbleme())) {
            throw new IllegalArgumentException("Le type de probleme est obligatoire.");
        }
        if (isBlank(reclamation.getPriorite())) {
            throw new IllegalArgumentException("La priorite est obligatoire.");
        }
        if (isBlank(reclamation.getStatut())) {
            throw new IllegalArgumentException("Le statut de la reclamation est obligatoire.");
        }
        if (isBlank(reclamation.getDescription())) {
            throw new IllegalArgumentException("La description du signalement est obligatoire.");
        }
        if (reclamation.getDateReclamation() == null) {
            reclamation.setDateReclamation(LocalDateTime.now());
        }
    }

    private void ensureTable() {
        if (cnx == null) {
            return;
        }

        try (PreparedStatement statement = cnx.prepareStatement(CREATE_TABLE_SQL)) {
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossible d'initialiser la table reclamation_salle.", exception);
        }
    }

    private Connection requireConnection() {
        if (cnx == null) {
            throw new IllegalStateException("Connexion a la base de donnees indisponible.");
        }
        return cnx;
    }

    private String normalizeText(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String normalizeTypeProbleme(String value) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("Le type de probleme est obligatoire.");
        }
        return value.trim().toLowerCase();
    }

    private String normalizePriorite(String value) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("La priorite est obligatoire.");
        }
        return value.trim().toLowerCase();
    }

    private String normalizeStatut(String value) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("Le statut de la reclamation est obligatoire.");
        }
        return value.trim().toLowerCase();
    }

    private void setNullableTimestamp(PreparedStatement statement, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
            return;
        }
        statement.setTimestamp(index, Timestamp.valueOf(value));
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
