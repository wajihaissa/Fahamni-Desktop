package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Salle;
import tn.esprit.fahamni.interfaces.IServices;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class AdminSalleService implements IServices<Salle> {

    private static final String INSERT_SQL =
        "INSERT INTO salle (nom, capacite, localisation, typeSalle, etat, description) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_SQL =
        "UPDATE salle SET nom = ?, capacite = ?, localisation = ?, typeSalle = ?, etat = ?, description = ? WHERE idSalle = ?";
    private static final String DELETE_SQL = "DELETE FROM salle WHERE idSalle = ?";
    private static final String SELECT_ALL_SQL =
        "SELECT idSalle, nom, capacite, localisation, typeSalle, etat, description FROM salle ORDER BY idSalle DESC";
    private static final String SELECT_BY_ID_SQL =
        "SELECT idSalle, nom, capacite, localisation, typeSalle, etat, description FROM salle WHERE idSalle = ?";

    private final Connection cnx;

    public AdminSalleService() {
        this.cnx = MyDataBase.getInstance().getCnx();
    }

    @Override
    public void add(Salle salle) throws SQLException {
        validateSalle(salle, false);

        try (PreparedStatement statement = requireConnection().prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            fillStatement(statement, salle);
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    salle.setIdSalle(generatedKeys.getInt(1));
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

        try (PreparedStatement statement = requireConnection().prepareStatement(UPDATE_SQL)) {
            fillStatement(statement, salle);
            statement.setInt(7, salle.getIdSalle());

            if (statement.executeUpdate() == 0) {
                throw new SQLException("Aucune salle trouvee avec l'id " + salle.getIdSalle() + ".");
            }
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

        try (PreparedStatement statement = requireConnection().prepareStatement(DELETE_SQL)) {
            statement.setInt(1, idSalle);

            if (statement.executeUpdate() == 0) {
                throw new SQLException("Aucune salle trouvee avec l'id " + idSalle + ".");
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

    public List<String> getAvailableEtats() {
        return List.of("disponible", "en maintenance", "indisponible");
    }

    private void fillStatement(PreparedStatement statement, Salle salle) throws SQLException {
        statement.setString(1, salle.getNom().trim());
        statement.setInt(2, salle.getCapacite());
        statement.setString(3, salle.getLocalisation().trim());
        statement.setString(4, salle.getTypeSalle().trim());
        statement.setString(5, salle.getEtat().trim());
        statement.setString(6, normalizeText(salle.getDescription()));
    }

    private Salle mapSalle(ResultSet resultSet) throws SQLException {
        return new Salle(
            resultSet.getInt("idSalle"),
            resultSet.getString("nom"),
            resultSet.getInt("capacite"),
            resultSet.getString("localisation"),
            resultSet.getString("typeSalle"),
            resultSet.getString("etat"),
            resultSet.getString("description")
        );
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
        if (isBlank(salle.getTypeSalle())) {
            throw new IllegalArgumentException("Le type de salle est obligatoire.");
        }
        if (isBlank(salle.getEtat())) {
            throw new IllegalArgumentException("L'etat de la salle est obligatoire.");
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
