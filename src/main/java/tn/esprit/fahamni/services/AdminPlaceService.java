package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Place;
import tn.esprit.fahamni.interfaces.IServices;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class AdminPlaceService implements IServices<Place> {

    private static final String INSERT_SQL =
        "INSERT INTO place (numero, rang, colonne, etat, idSalle) VALUES (?, ?, ?, ?, ?)";
    private static final String UPDATE_SQL =
        "UPDATE place SET numero = ?, rang = ?, colonne = ?, etat = ?, idSalle = ? WHERE idPlace = ?";
    private static final String UPDATE_ETAT_SQL = "UPDATE place SET etat = ? WHERE idPlace = ?";
    private static final String DELETE_SQL = "DELETE FROM place WHERE idPlace = ?";
    private static final String SELECT_ALL_SQL =
        "SELECT idPlace, numero, rang, colonne, etat, idSalle FROM place ORDER BY idSalle, rang, colonne";
    private static final String SELECT_BY_ID_SQL =
        "SELECT idPlace, numero, rang, colonne, etat, idSalle FROM place WHERE idPlace = ?";
    private static final String SELECT_BY_SALLE_SQL =
        "SELECT idPlace, numero, rang, colonne, etat, idSalle FROM place WHERE idSalle = ? ORDER BY rang, colonne";

    private final Connection cnx;

    public AdminPlaceService() {
        this.cnx = MyDataBase.getInstance().getCnx();
    }

    @Override
    public void add(Place place) throws SQLException {
        validatePlace(place, false);

        try (PreparedStatement statement = requireConnection().prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            fillStatement(statement, place);
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    place.setIdPlace(generatedKeys.getInt(1));
                }
            }
        }
    }

    @Override
    public List<Place> getAll() throws SQLException {
        List<Place> places = new ArrayList<>();

        try (PreparedStatement statement = requireConnection().prepareStatement(SELECT_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                places.add(mapPlace(resultSet));
            }
        }

        return places;
    }

    @Override
    public void update(Place place) throws SQLException {
        validatePlace(place, true);

        try (PreparedStatement statement = requireConnection().prepareStatement(UPDATE_SQL)) {
            fillStatement(statement, place);
            statement.setInt(6, place.getIdPlace());

            if (statement.executeUpdate() == 0) {
                throw new SQLException("Aucune place trouvee avec l'id " + place.getIdPlace() + ".");
            }
        }
    }

    @Override
    public void delete(Place place) throws SQLException {
        if (place == null) {
            throw new IllegalArgumentException("La place est obligatoire.");
        }
        deleteById(place.getIdPlace());
    }

    private void deleteById(int idPlace) throws SQLException {
        if (idPlace <= 0) {
            throw new IllegalArgumentException("L'id de la place doit etre positif.");
        }

        try (PreparedStatement statement = requireConnection().prepareStatement(DELETE_SQL)) {
            statement.setInt(1, idPlace);

            if (statement.executeUpdate() == 0) {
                throw new SQLException("Aucune place trouvee avec l'id " + idPlace + ".");
            }
        }
    }

    public Place recupererParId(int idPlace) throws SQLException {
        if (idPlace <= 0) {
            throw new IllegalArgumentException("L'id de la place doit etre positif.");
        }

        try (PreparedStatement statement = requireConnection().prepareStatement(SELECT_BY_ID_SQL)) {
            statement.setInt(1, idPlace);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapPlace(resultSet);
                }
            }
        }

        return null;
    }

    public List<Place> getBySalle(int idSalle) throws SQLException {
        if (idSalle <= 0) {
            throw new IllegalArgumentException("L'id de la salle doit etre positif.");
        }

        List<Place> places = new ArrayList<>();

        try (PreparedStatement statement = requireConnection().prepareStatement(SELECT_BY_SALLE_SQL)) {
            statement.setInt(1, idSalle);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    places.add(mapPlace(resultSet));
                }
            }
        }

        return places;
    }

    public List<String> getAvailableEtats() {
        return List.of("disponible", "en maintenance", "indisponible");
    }

    public void updateEtat(int idPlace, String nouvelEtat) throws SQLException {
        if (idPlace <= 0) {
            throw new IllegalArgumentException("L'id de la place doit etre positif.");
        }

        String etatNormalise = normalizeEtat(nouvelEtat);
        if (!getAvailableEtats().contains(etatNormalise)) {
            throw new IllegalArgumentException("L'etat de la place est invalide.");
        }

        try (PreparedStatement statement = requireConnection().prepareStatement(UPDATE_ETAT_SQL)) {
            statement.setString(1, etatNormalise);
            statement.setInt(2, idPlace);

            if (statement.executeUpdate() == 0) {
                throw new SQLException("Aucune place trouvee avec l'id " + idPlace + ".");
            }
        }
    }

    private void fillStatement(PreparedStatement statement, Place place) throws SQLException {
        statement.setInt(1, place.getNumero());
        statement.setInt(2, place.getRang());
        statement.setInt(3, place.getColonne());
        statement.setString(4, normalizeEtat(place.getEtat()));
        statement.setInt(5, place.getIdSalle());
    }

    private Place mapPlace(ResultSet resultSet) throws SQLException {
        return new Place(
            resultSet.getInt("idPlace"),
            resultSet.getInt("numero"),
            resultSet.getInt("rang"),
            resultSet.getInt("colonne"),
            resultSet.getString("etat"),
            resultSet.getInt("idSalle")
        );
    }

    private void validatePlace(Place place, boolean requireId) {
        if (place == null) {
            throw new IllegalArgumentException("La place est obligatoire.");
        }
        if (requireId && place.getIdPlace() <= 0) {
            throw new IllegalArgumentException("L'id de la place est obligatoire pour la modification.");
        }
        if (place.getNumero() <= 0) {
            throw new IllegalArgumentException("Le numero de la place doit etre superieur a zero.");
        }
        if (place.getRang() <= 0) {
            throw new IllegalArgumentException("Le rang de la place doit etre superieur a zero.");
        }
        if (place.getColonne() <= 0) {
            throw new IllegalArgumentException("La colonne de la place doit etre superieure a zero.");
        }
        if (isBlank(place.getEtat())) {
            throw new IllegalArgumentException("L'etat de la place est obligatoire.");
        }
        if (place.getIdSalle() <= 0) {
            throw new IllegalArgumentException("L'id de la salle est obligatoire.");
        }
    }

    private Connection requireConnection() {
        if (cnx == null) {
            throw new IllegalStateException("Connexion a la base de donnees indisponible.");
        }
        return cnx;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizeEtat(String etat) {
        if (isBlank(etat)) {
            throw new IllegalArgumentException("L'etat de la place est obligatoire.");
        }

        return etat.trim().toLowerCase();
    }
}
