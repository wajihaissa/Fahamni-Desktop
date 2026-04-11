package tn.esprit.fahamni.Models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Seance {

    public static final String MODE_ONLINE = "en_ligne";
    public static final String MODE_ONSITE = "presentiel";

    private static final DateTimeFormatter SCHEDULE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private int id;
    private String matiere;
    private LocalDateTime startAt;
    private int durationMin;
    private int maxParticipants;
    private int status;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int tuteurId;
    private String mode = MODE_ONLINE;
    private Integer salleId;
    private List<Integer> equipementIds = new ArrayList<>();
    private Map<Integer, Integer> equipementQuantites = new LinkedHashMap<>();

    // Optional display helpers kept for current mock views.
    private String displayTitle;
    private String displayTutorName;
    private String displaySchedule;
    private Integer availableSpots;
    private Double price;
    private Double rating;

    public Seance() {
    }

    public Seance(int id, String matiere, LocalDateTime startAt, int durationMin,
                  int maxParticipants, int status, String description,
                  LocalDateTime createdAt, LocalDateTime updatedAt, int tuteurId) {
        this.id = id;
        this.matiere = matiere;
        this.startAt = startAt;
        this.durationMin = durationMin;
        this.maxParticipants = maxParticipants;
        this.status = status;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.tuteurId = tuteurId;
    }

    public Seance(String title, String subject, String tutorName, String schedule, int durationMinutes,
                  int availableSpots, int totalSpots, double price, double rating, String description) {
        this.displayTitle = title;
        this.matiere = subject;
        this.displayTutorName = tutorName;
        this.displaySchedule = schedule;
        this.durationMin = durationMinutes;
        this.availableSpots = availableSpots;
        this.maxParticipants = totalSpots;
        this.price = price;
        this.rating = rating;
        this.description = description;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getMatiere() {
        return matiere;
    }

    public void setMatiere(String matiere) {
        this.matiere = matiere;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public int getDurationMin() {
        return durationMin;
    }

    public void setDurationMin(int durationMin) {
        this.durationMin = durationMin;
    }

    public int getMaxParticipants() {
        return maxParticipants;
    }

    public void setMaxParticipants(int maxParticipants) {
        this.maxParticipants = maxParticipants;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getTuteurId() {
        return tuteurId;
    }

    public void setTuteurId(int tuteurId) {
        this.tuteurId = tuteurId;
    }

    public String getMode() {
        return mode == null || mode.isBlank() ? MODE_ONLINE : mode;
    }

    public void setMode(String mode) {
        this.mode = mode == null || mode.isBlank() ? MODE_ONLINE : mode;
    }

    public boolean isPresentiel() {
        return MODE_ONSITE.equals(getMode());
    }

    public Integer getSalleId() {
        return salleId;
    }

    public void setSalleId(Integer salleId) {
        this.salleId = salleId;
    }

    public List<Integer> getEquipementIds() {
        return new ArrayList<>(equipementIds);
    }

    public void setEquipementIds(List<Integer> equipementIds) {
        this.equipementIds = equipementIds == null ? new ArrayList<>() : new ArrayList<>(equipementIds);
        LinkedHashMap<Integer, Integer> normalizedQuantities = new LinkedHashMap<>();
        for (Integer equipementId : this.equipementIds) {
            if (equipementId == null || equipementId <= 0) {
                continue;
            }
            normalizedQuantities.put(equipementId, getEquipementQuantite(equipementId));
        }
        this.equipementIds = new ArrayList<>(normalizedQuantities.keySet());
        this.equipementQuantites = normalizedQuantities;
    }

    public Map<Integer, Integer> getEquipementQuantites() {
        LinkedHashMap<Integer, Integer> copy = new LinkedHashMap<>();
        for (Integer equipementId : equipementIds) {
            if (equipementId == null || equipementId <= 0) {
                continue;
            }
            copy.put(equipementId, getEquipementQuantite(equipementId));
        }
        return copy;
    }

    public void setEquipementQuantites(Map<Integer, Integer> equipementQuantites) {
        LinkedHashMap<Integer, Integer> normalized = new LinkedHashMap<>();
        if (equipementQuantites != null) {
            for (Map.Entry<Integer, Integer> entry : equipementQuantites.entrySet()) {
                Integer equipementId = entry.getKey();
                Integer quantite = entry.getValue();
                if (equipementId == null || equipementId <= 0) {
                    continue;
                }
                normalized.put(equipementId, quantite == null || quantite <= 0 ? 1 : quantite);
            }
        }
        this.equipementQuantites = normalized;
        this.equipementIds = new ArrayList<>(normalized.keySet());
    }

    public int getEquipementQuantite(int equipementId) {
        if (equipementId <= 0) {
            return 1;
        }
        Integer quantite = equipementQuantites.get(equipementId);
        return quantite == null || quantite <= 0 ? 1 : quantite;
    }

    public String getTitle() {
        return displayTitle != null ? displayTitle : matiere;
    }

    public void setTitle(String title) {
        this.displayTitle = title;
    }

    public String getSubject() {
        return matiere;
    }

    public void setSubject(String subject) {
        this.matiere = subject;
    }

    public String getTutorName() {
        if (displayTutorName != null && !displayTutorName.isBlank()) {
            return displayTutorName;
        }
        return tuteurId > 0 ? "Tuteur #" + tuteurId : "Tuteur non defini";
    }

    public void setTutorName(String tutorName) {
        this.displayTutorName = tutorName;
    }

    public String getSchedule() {
        if (displaySchedule != null && !displaySchedule.isBlank()) {
            return displaySchedule;
        }
        return startAt != null ? startAt.format(SCHEDULE_FORMATTER) : "";
    }

    public void setSchedule(String schedule) {
        this.displaySchedule = schedule;
    }

    public int getDurationMinutes() {
        return durationMin;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMin = durationMinutes;
    }

    public int getAvailableSpots() {
        return availableSpots != null ? availableSpots : maxParticipants;
    }

    public void setAvailableSpots(int availableSpots) {
        this.availableSpots = availableSpots;
    }

    public int getTotalSpots() {
        return maxParticipants;
    }

    public void setTotalSpots(int totalSpots) {
        this.maxParticipants = totalSpots;
    }

    public double getPrice() {
        return price != null ? price : 0.0;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getRating() {
        return rating != null ? rating : 0.0;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }
}
