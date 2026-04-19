package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.SalleEquipement;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SalleEquipementService {

    private static final String SELECT_BY_SALLE_SQL = """
        SELECT se.idSalle,
               se.idEquipement,
               se.quantite,
               e.nom,
               e.typeEquipement,
               e.quantiteDisponible,
               e.etat,
               e.description
        FROM salle_equipement se
        INNER JOIN equipement e ON e.idEquipement = se.idEquipement
        WHERE se.idSalle = ?
        ORDER BY e.nom ASC, e.idEquipement ASC
        """;

    private static final String SELECT_EQUIPMENT_SQL = """
        SELECT idEquipement, nom, quantiteDisponible
        FROM equipement
        WHERE idEquipement = ?
        """;
    private static final String SELECT_ALLOCATED_TOTAL_SQL = """
        SELECT COALESCE(SUM(quantite), 0) AS totalAlloue
        FROM salle_equipement
        WHERE idEquipement = ?
        """;
    private static final String SELECT_ALLOCATED_EXCLUDING_SALLE_SQL = """
        SELECT COALESCE(SUM(quantite), 0) AS totalAlloue
        FROM salle_equipement
        WHERE idEquipement = ? AND idSalle <> ?
        """;

    private static final String DELETE_BY_SALLE_SQL = "DELETE FROM salle_equipement WHERE idSalle = ?";
    private static final String INSERT_SQL = "INSERT INTO salle_equipement (idSalle, idEquipement, quantite) VALUES (?, ?, ?)";

    private final Connection cnx;

    public SalleEquipementService() {
        this.cnx = MyDataBase.getInstance().getCnx();
    }

    public List<SalleEquipement> getEquipementsBySalleId(int idSalle) throws SQLException {
        if (idSalle <= 0) {
            return List.of();
        }

        List<SalleEquipement> equipements = new ArrayList<>();
        try (PreparedStatement statement = requireConnection().prepareStatement(SELECT_BY_SALLE_SQL)) {
            statement.setInt(1, idSalle);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    equipements.add(new SalleEquipement(
                        resultSet.getInt("idSalle"),
                        resultSet.getInt("idEquipement"),
                        resultSet.getString("nom"),
                        resultSet.getString("typeEquipement"),
                        Math.max(1, resultSet.getInt("quantite")),
                        Math.max(0, resultSet.getInt("quantiteDisponible")),
                        resultSet.getString("etat"),
                        resultSet.getString("description")
                    ));
                }
            }
        }

        return equipements;
    }

    public Map<Integer, Integer> getEquipementQuantitesBySalleId(int idSalle) throws SQLException {
        LinkedHashMap<Integer, Integer> quantites = new LinkedHashMap<>();
        for (SalleEquipement equipement : getEquipementsBySalleId(idSalle)) {
            quantites.put(equipement.getIdEquipement(), equipement.getQuantite());
        }
        return quantites;
    }

    public void replaceEquipementsForSalle(int idSalle, Map<Integer, Integer> equipementQuantites) throws SQLException {
        if (idSalle <= 0) {
            throw new IllegalArgumentException("La salle doit etre enregistree avant d'affecter du materiel.");
        }

        LinkedHashMap<Integer, Integer> normalizedQuantities = normalizeEquipementQuantites(idSalle, equipementQuantites);

        try (PreparedStatement deleteStatement = requireConnection().prepareStatement(DELETE_BY_SALLE_SQL)) {
            deleteStatement.setInt(1, idSalle);
            deleteStatement.executeUpdate();
        }

        if (normalizedQuantities.isEmpty()) {
            return;
        }

        try (PreparedStatement insertStatement = requireConnection().prepareStatement(INSERT_SQL)) {
            for (Map.Entry<Integer, Integer> entry : normalizedQuantities.entrySet()) {
                insertStatement.setInt(1, idSalle);
                insertStatement.setInt(2, entry.getKey());
                insertStatement.setInt(3, entry.getValue());
                insertStatement.addBatch();
            }
            insertStatement.executeBatch();
        }
    }

    public int getRemainingQuantiteForEquipement(int equipementId) throws SQLException {
        return getRemainingQuantiteForEquipement(equipementId, null);
    }

    public int getRemainingQuantiteForEquipement(int equipementId, Integer excludedSalleId) throws SQLException {
        EquipementStock stock = loadEquipementStock(equipementId);
        int totalAllocated = getAllocatedQuantite(equipementId, excludedSalleId);
        return Math.max(0, stock.quantiteDisponible() - totalAllocated);
    }

    private LinkedHashMap<Integer, Integer> normalizeEquipementQuantites(int idSalle, Map<Integer, Integer> equipementQuantites) throws SQLException {
        LinkedHashMap<Integer, Integer> normalizedQuantities = new LinkedHashMap<>();
        if (equipementQuantites == null || equipementQuantites.isEmpty()) {
            return normalizedQuantities;
        }

        for (Map.Entry<Integer, Integer> entry : equipementQuantites.entrySet()) {
            Integer equipementId = entry.getKey();
            int quantite = entry.getValue() == null ? 0 : entry.getValue();
            if (equipementId == null || equipementId <= 0 || quantite <= 0) {
                continue;
            }

            validateEquipementAssignment(idSalle, equipementId, quantite);
            normalizedQuantities.put(equipementId, quantite);
        }

        return normalizedQuantities;
    }

    private void validateEquipementAssignment(int idSalle, int equipementId, int quantiteDemandee) throws SQLException {
        EquipementStock stock = loadEquipementStock(equipementId);
        int quantiteRestante = getRemainingQuantiteForEquipement(equipementId, idSalle > 0 ? idSalle : null);
        if (quantiteDemandee > quantiteRestante) {
            throw new IllegalArgumentException(
                "Le materiel \"" + stock.nom() + "\" est limite a " + quantiteRestante
                    + " unite(s) libre(s) apres affectation aux autres salles."
            );
        }
    }

    private EquipementStock loadEquipementStock(int equipementId) throws SQLException {
        try (PreparedStatement statement = requireConnection().prepareStatement(SELECT_EQUIPMENT_SQL)) {
            statement.setInt(1, equipementId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException("Le materiel #" + equipementId + " est introuvable.");
                }

                return new EquipementStock(
                    resultSet.getString("nom"),
                    Math.max(0, resultSet.getInt("quantiteDisponible"))
                );
            }
        }
    }

    private int getAllocatedQuantite(int equipementId, Integer excludedSalleId) throws SQLException {
        String sql = excludedSalleId != null && excludedSalleId > 0
            ? SELECT_ALLOCATED_EXCLUDING_SALLE_SQL
            : SELECT_ALLOCATED_TOTAL_SQL;

        try (PreparedStatement statement = requireConnection().prepareStatement(sql)) {
            statement.setInt(1, equipementId);
            if (excludedSalleId != null && excludedSalleId > 0) {
                statement.setInt(2, excludedSalleId);
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Math.max(0, resultSet.getInt("totalAlloue")) : 0;
            }
        }
    }

    private Connection requireConnection() {
        if (cnx == null) {
            throw new IllegalStateException("Connexion a la base de donnees indisponible.");
        }
        return cnx;
    }

    private record EquipementStock(String nom, int quantiteDisponible) {
    }
}
