package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Salle;
import tn.esprit.fahamni.interfaces.IServices;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminSalleService implements IServices<Salle> {
    private static final List<String> DEFAULT_BATIMENTS = List.of(
        "Batiment A",
        "Batiment B",
        "Batiment C",
        "Batiment D",
        "Batiment E"
    );

    private static final String INSERT_SQL =
        "INSERT INTO salle (nom, capacite, localisation, typeSalle, etat, description, batiment, etage, typeDisposition, accesHandicape, statutDetaille, dateDerniereMaintenance) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_SQL =
        "UPDATE salle SET nom = ?, capacite = ?, localisation = ?, typeSalle = ?, etat = ?, description = ?, batiment = ?, etage = ?, typeDisposition = ?, accesHandicape = ?, statutDetaille = ?, dateDerniereMaintenance = ? WHERE idSalle = ?";
    private static final String DELETE_SQL = "DELETE FROM salle WHERE idSalle = ?";
    private static final String INSERT_PLACE_SQL =
        "INSERT INTO place (numero, rang, colonne, etat, idSalle) VALUES (?, ?, ?, ?, ?)";
    private static final String SELECT_PLACES_BY_SALLE_SQL =
        "SELECT numero, rang, colonne FROM place WHERE idSalle = ? ORDER BY numero ASC";
    private static final String COUNT_DUPLICATE_SALLE_SQL = """
        SELECT COUNT(*) AS total
        FROM salle
        WHERE LOWER(TRIM(nom)) = ?
          AND LOWER(TRIM(localisation)) = ?
          AND LOWER(TRIM(COALESCE(batiment, ''))) = ?
        """;
    private static final String SELECT_ACTIVE_SEANCE_BY_SALLE_SQL = """
        SELECT id, matiere, start_at
        FROM seance
        WHERE salle_id = ?
          AND start_at IS NOT NULL
          AND DATE_ADD(start_at, INTERVAL duration_min MINUTE) > NOW()
        ORDER BY start_at ASC
        LIMIT 1
        """;
    private static final String SELECT_ALL_SQL =
        "SELECT idSalle, nom, capacite, localisation, typeSalle, etat, description, batiment, etage, typeDisposition, accesHandicape, statutDetaille, dateDerniereMaintenance FROM salle ORDER BY idSalle DESC";
    private static final String SELECT_BY_ID_SQL =
        "SELECT idSalle, nom, capacite, localisation, typeSalle, etat, description, batiment, etage, typeDisposition, accesHandicape, statutDetaille, dateDerniereMaintenance FROM salle WHERE idSalle = ?";

    private final Connection cnx;

    public AdminSalleService() {
        this.cnx = MyDataBase.getInstance().getCnx();
    }

    @Override
    public void add(Salle salle) throws SQLException {
        validateSalle(salle, false);
        ensureNoDuplicateSalleForLocationAndBuilding(salle);

        Connection connection = requireConnection();
        boolean initialAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            salle.setIdSalle(insertSalle(connection, salle));
            synchronizePlacesWithCapacity(connection, salle);
            connection.commit();
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(initialAutoCommit);
        }
    }

    private void ensureNoDuplicateSalleForLocationAndBuilding(Salle salle) throws SQLException {
        try (PreparedStatement statement = requireConnection().prepareStatement(COUNT_DUPLICATE_SALLE_SQL)) {
            statement.setString(1, normalizeLookupKey(salle.getNom()));
            statement.setString(2, normalizeLookupKey(salle.getLocalisation()));
            statement.setString(3, normalizeLookupKey(salle.getBatiment()));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next() && resultSet.getInt("total") > 0) {
                    throw new IllegalArgumentException(
                        "Une salle avec ce nom existe deja dans le meme batiment et la meme localisation."
                    );
                }
            }
        }
    }

    @Override
    public List<Salle> getAll() throws SQLException {
        List<Salle> salles = new ArrayList<>();

        try (PreparedStatement statement = requireConnection().prepareStatement(SELECT_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                salles.add(mapSalle(resultSet));
            }
        }

        return salles;
    }

    @Override
    public void update(Salle salle) throws SQLException {
        validateSalle(salle, true);
        ensureNoUpcomingSeanceUsesSalle(salle.getIdSalle());

        Connection connection = requireConnection();
        boolean initialAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            updateSalle(connection, salle);
            synchronizePlacesWithCapacity(connection, salle);
            connection.commit();
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(initialAutoCommit);
        }
    }

    @Override
    public void delete(Salle salle) throws SQLException {
        if (salle == null) {
            throw new IllegalArgumentException("La salle est obligatoire.");
        }
        deleteById(salle.getIdSalle());
    }

    private void deleteById(int idSalle) throws SQLException {
        if (idSalle <= 0) {
            throw new IllegalArgumentException("L'id de la salle doit etre positif.");
        }

        ensureNoUpcomingSeanceUsesSalle(idSalle);

        try (PreparedStatement statement = requireConnection().prepareStatement(DELETE_SQL)) {
            statement.setInt(1, idSalle);

            if (statement.executeUpdate() == 0) {
                throw new SQLException("Aucune salle trouvee avec l'id " + idSalle + ".");
            }
        }
    }

    private void ensureNoUpcomingSeanceUsesSalle(int idSalle) throws SQLException {
        try (PreparedStatement statement = requireConnection().prepareStatement(SELECT_ACTIVE_SEANCE_BY_SALLE_SQL)) {
            statement.setInt(1, idSalle);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return;
                }

                throw new SQLException(
                    "Cette salle est liee a la seance non encore passee \""
                        + resultSet.getString("matiere")
                        + "\" (#" + resultSet.getInt("id")
                        + "). Supprimez ou modifiez d'abord cette seance."
                );
            }
        }
    }

    public Salle recupererParId(int idSalle) throws SQLException {
        if (idSalle <= 0) {
            throw new IllegalArgumentException("L'id de la salle doit etre positif.");
        }

        try (PreparedStatement statement = requireConnection().prepareStatement(SELECT_BY_ID_SQL)) {
            statement.setInt(1, idSalle);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapSalle(resultSet);
                }
            }
        }

        return null;
    }

    public List<String> getAvailableTypes() {
        return List.of("Cours", "Conference", "Laboratoire", "Amphitheatre");
    }

    public List<String> getAvailableBatiments() {
        return DEFAULT_BATIMENTS;
    }

    public List<String> getAvailableEtats() {
        return List.of("disponible", "en maintenance", "indisponible");
    }

    public List<String> getAvailableDispositions() {
        return List.of("classe", "u", "reunion", "conference", "atelier", "informatique");
    }

    public String getSuggestedDispositionForType(String typeSalle) {
        if (typeSalle == null || typeSalle.isBlank()) {
            return null;
        }

        return switch (typeSalle.trim().toLowerCase()) {
            case "cours" -> "classe";
            case "conference" -> "conference";
            case "laboratoire" -> "informatique";
            case "amphitheatre" -> "conference";
            default -> null;
        };
    }

    private void fillStatement(PreparedStatement statement, Salle salle) throws SQLException {
        statement.setString(1, salle.getNom().trim());
        statement.setInt(2, salle.getCapacite());
        statement.setString(3, salle.getLocalisation().trim());
        statement.setString(4, salle.getTypeSalle().trim());
        statement.setString(5, normalizeEtat(salle.getEtat()));
        statement.setString(6, normalizeText(salle.getDescription()));
        statement.setString(7, normalizeText(salle.getBatiment()));
        setNullableInteger(statement, 8, salle.getEtage());
        statement.setString(9, normalizeText(salle.getTypeDisposition()));
        statement.setBoolean(10, salle.isAccesHandicape());
        statement.setString(11, normalizeText(salle.getStatutDetaille()));
        setNullableDate(statement, 12, salle.getDateDerniereMaintenance());
    }

    private Salle mapSalle(ResultSet resultSet) throws SQLException {
        Date dateMaintenance = resultSet.getDate("dateDerniereMaintenance");

        return new Salle(
            resultSet.getInt("idSalle"),
            resultSet.getString("nom"),
            resultSet.getInt("capacite"),
            resultSet.getString("localisation"),
            resultSet.getString("typeSalle"),
            resultSet.getString("etat"),
            resultSet.getString("description"),
            resultSet.getString("batiment"),
            getNullableInteger(resultSet, "etage"),
            resultSet.getString("typeDisposition"),
            resultSet.getBoolean("accesHandicape"),
            resultSet.getString("statutDetaille"),
            dateMaintenance == null ? null : dateMaintenance.toLocalDate()
        );
    }

    private int insertSalle(Connection connection, Salle salle) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            fillStatement(statement, salle);
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        }

        throw new SQLException("Creation de la salle impossible: identifiant non genere.");
    }

    private void updateSalle(Connection connection, Salle salle) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {
            fillStatement(statement, salle);
            statement.setInt(13, salle.getIdSalle());

            if (statement.executeUpdate() == 0) {
                throw new SQLException("Aucune salle trouvee avec l'id " + salle.getIdSalle() + ".");
            }
        }
    }

    private void synchronizePlacesWithCapacity(Connection connection, Salle salle) throws SQLException {
        if (salle == null || salle.getIdSalle() <= 0 || salle.getCapacite() <= 0) {
            return;
        }

        List<PlaceSnapshot> existingPlaces = getPlaceSnapshots(connection, salle.getIdSalle());
        int missingPlaces = salle.getCapacite() - existingPlaces.size();
        if (missingPlaces <= 0) {
            return;
        }

        int preferredColumns = resolvePreferredColumnCount(salle.getTypeDisposition(), salle.getCapacite());
        int nextPlaceNumber = existingPlaces.stream()
            .mapToInt(PlaceSnapshot::numero)
            .max()
            .orElse(0) + 1;

        java.util.Set<String> occupiedPositions = existingPlaces.stream()
            .map(place -> positionKey(place.rang(), place.colonne()))
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        int cursor = 1;
        while (missingPlaces > 0) {
            int row = ((cursor - 1) / preferredColumns) + 1;
            int column = ((cursor - 1) % preferredColumns) + 1;
            cursor++;

            String positionKey = positionKey(row, column);
            if (occupiedPositions.contains(positionKey)) {
                continue;
            }

            insertPlace(connection, nextPlaceNumber++, row, column, salle.getIdSalle());
            occupiedPositions.add(positionKey);
            missingPlaces--;
        }
    }

    private List<PlaceSnapshot> getPlaceSnapshots(Connection connection, int salleId) throws SQLException {
        List<PlaceSnapshot> places = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_PLACES_BY_SALLE_SQL)) {
            statement.setInt(1, salleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    places.add(new PlaceSnapshot(
                        resultSet.getInt("numero"),
                        resultSet.getInt("rang"),
                        resultSet.getInt("colonne")
                    ));
                }
            }
        }
        return places;
    }

    private void insertPlace(Connection connection, int numero, int rang, int colonne, int salleId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_PLACE_SQL)) {
            statement.setInt(1, numero);
            statement.setInt(2, rang);
            statement.setInt(3, colonne);
            statement.setString(4, "disponible");
            statement.setInt(5, salleId);
            statement.executeUpdate();
        }
    }

    private int resolvePreferredColumnCount(String typeDisposition, int capacity) {
        if (capacity <= 1) {
            return 1;
        }
        return Math.max(1, Math.min(capacity, 6));
    }

    private String positionKey(int rang, int colonne) {
        return rang + ":" + colonne;
    }

    private void validateSalle(Salle salle, boolean requireId) {
        if (salle == null) {
            throw new IllegalArgumentException("La salle est obligatoire.");
        }
        if (requireId && salle.getIdSalle() <= 0) {
            throw new IllegalArgumentException("L'id de la salle est obligatoire pour la modification.");
        }
        if (isBlank(salle.getNom())) {
            throw new IllegalArgumentException("Le nom de la salle est obligatoire.");
        }
        if (salle.getCapacite() <= 0) {
            throw new IllegalArgumentException("La capacite doit etre superieure a zero.");
        }
        if (isBlank(salle.getLocalisation())) {
            throw new IllegalArgumentException("La localisation est obligatoire.");
        }
        if (isBlank(salle.getBatiment())) {
            throw new IllegalArgumentException("Le batiment est obligatoire.");
        }
        if (isBlank(salle.getTypeSalle())) {
            throw new IllegalArgumentException("Le type de salle est obligatoire.");
        }
        if (isBlank(salle.getEtat())) {
            throw new IllegalArgumentException("L'etat de la salle est obligatoire.");
        }
        LocalDate dateDerniereMaintenance = salle.getDateDerniereMaintenance();
        if (dateDerniereMaintenance != null && dateDerniereMaintenance.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La date de derniere maintenance ne peut pas etre dans le futur.");
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

    private String normalizeLookupKey(String value) {
        return isBlank(value) ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEtat(String value) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("L'etat de la salle est obligatoire.");
        }
        return value.trim().toLowerCase();
    }

    private void setNullableInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
            return;
        }
        statement.setInt(index, value);
    }

    private void setNullableDate(PreparedStatement statement, int index, LocalDate value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DATE);
            return;
        }
        statement.setDate(index, Date.valueOf(value));
    }

    private Integer getNullableInteger(ResultSet resultSet, String columnLabel) throws SQLException {
        int value = resultSet.getInt(columnLabel);
        return resultSet.wasNull() ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record PlaceSnapshot(int numero, int rang, int colonne) {
    }
}
