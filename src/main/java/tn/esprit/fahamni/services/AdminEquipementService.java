package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.Equipement;
import tn.esprit.fahamni.interfaces.IServices;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class AdminEquipementService implements IServices<Equipement> {

    private static final String INSERT_SQL =
        "INSERT INTO equipement (nom, typeEquipement, quantiteDisponible, etat, description) VALUES (?, ?, ?, ?, ?)";
    private static final String UPDATE_SQL =
        "UPDATE equipement SET nom = ?, typeEquipement = ?, quantiteDisponible = ?, etat = ?, description = ? WHERE idEquipement = ?";
    private static final String DELETE_SQL = "DELETE FROM equipement WHERE idEquipement = ?";
    private static final String SELECT_ALL_SQL =
        "SELECT idEquipement, nom, typeEquipement, quantiteDisponible, etat, description FROM equipement ORDER BY idEquipement DESC";
    private static final String SELECT_BY_ID_SQL =
        "SELECT idEquipement, nom, typeEquipement, quantiteDisponible, etat, description FROM equipement WHERE idEquipement = ?";

    private final Connection cnx;

    public AdminEquipementService() {
        this.cnx = MyDataBase.getInstance().getCnx();
    }

    @Override
    public void add(Equipement equipement) throws SQLException {
        validateEquipement(equipement, false);

        try (PreparedStatement statement = requireConnection().prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            fillStatement(statement, equipement);
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    equipement.setIdEquipement(generatedKeys.getInt(1));// remplit l'attribut id de l'equipement par l'id généré automatiquement par MySQL
                }
            }
        }
    }

    @Override
    public List<Equipement> getAll() throws SQLException {
        List<Equipement> equipements = new ArrayList<>();

        try (PreparedStatement statement = requireConnection().prepareStatement(SELECT_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                equipements.add(mapEquipement(resultSet));
            }
        }

        return equipements;
    }

    @Override
    public void update(Equipement equipement) throws SQLException {
        validateEquipement(equipement, true);

        try (PreparedStatement statement = requireConnection().prepareStatement(UPDATE_SQL)) {
            fillStatement(statement, equipement);
            statement.setInt(6, equipement.getIdEquipement());

            if (statement.executeUpdate() == 0) {
                throw new SQLException("Aucun equipement trouve avec l'id " + equipement.getIdEquipement() + ".");
            }
        }
    }

    @Override
    public void delete(Equipement equipement) throws SQLException {
        if (equipement == null) {
            throw new IllegalArgumentException("L'equipement est obligatoire.");
        }
        deleteById(equipement.getIdEquipement());
    }

    private void deleteById(int idEquipement) throws SQLException {
        if (idEquipement <= 0) {
            throw new IllegalArgumentException("L'id de l'equipement doit etre positif.");
        }

        try (PreparedStatement statement = requireConnection().prepareStatement(DELETE_SQL)) {
            statement.setInt(1, idEquipement);

            if (statement.executeUpdate() == 0) {
                throw new SQLException("Aucun equipement trouve avec l'id " + idEquipement + ".");
            }
        }
    }

    public Equipement recupererParId(int idEquipement) throws SQLException {
        if (idEquipement <= 0) {
            throw new IllegalArgumentException("L'id de l'equipement doit etre positif.");
        }

        try (PreparedStatement statement = requireConnection().prepareStatement(SELECT_BY_ID_SQL)) {
            statement.setInt(1, idEquipement);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapEquipement(resultSet);
                }
            }
        }

        return null;
    }

    public List<String> getAvailableTypes() {
        return List.of("projecteur", "ordinateur", "tableau interactif", "camera");
    }

    public List<String> getAvailableEtats() {
        return List.of("disponible", "en maintenance", "indisponible");
    }

    private void fillStatement(PreparedStatement statement, Equipement equipement) throws SQLException {
        statement.setString(1, equipement.getNom().trim());
        statement.setString(2, equipement.getTypeEquipement().trim());
        statement.setInt(3, equipement.getQuantiteDisponible());
        statement.setString(4, equipement.getEtat().trim());
        statement.setString(5, normalizeText(equipement.getDescription()));
    }


    //mapEquipement sert à transformer une ligne venant de la base de données en objet java Equipement
    private Equipement mapEquipement(ResultSet resultSet) throws SQLException {
        return new Equipement(
            resultSet.getInt("idEquipement"),
            resultSet.getString("nom"),
            resultSet.getString("typeEquipement"),
            resultSet.getInt("quantiteDisponible"),
            resultSet.getString("etat"),
            resultSet.getString("description")
        );
    }

    private void validateEquipement(Equipement equipement, boolean requireId) {
        if (equipement == null) {
            throw new IllegalArgumentException("L'equipement est obligatoire.");
        }
        if (requireId && equipement.getIdEquipement() <= 0) {
            throw new IllegalArgumentException("L'id de l'equipement est obligatoire pour la modification.");
        }
        if (isBlank(equipement.getNom())) {
            throw new IllegalArgumentException("Le nom de l'equipement est obligatoire.");
        }
        if (isBlank(equipement.getTypeEquipement())) {
            throw new IllegalArgumentException("Le type d'equipement est obligatoire.");
        }
        if (equipement.getQuantiteDisponible() <= 0) {
            throw new IllegalArgumentException("La quantite disponible doit etre strictement positive.");
        }
        if (isBlank(equipement.getEtat())) {
            throw new IllegalArgumentException("L'etat de l'equipement est obligatoire.");
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
