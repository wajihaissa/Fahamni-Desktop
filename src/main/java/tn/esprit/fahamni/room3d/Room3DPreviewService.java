package tn.esprit.fahamni.room3d;

import tn.esprit.fahamni.Models.Place;
import tn.esprit.fahamni.Models.Salle;
import tn.esprit.fahamni.services.AdminPlaceService;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Room3DPreviewService {

    private final AdminPlaceService placeService = new AdminPlaceService();

    public Room3DPreviewData buildPreview(Salle salle, boolean preferGeneratedLayout) {
        return buildPreview(salle, preferGeneratedLayout, Room3DViewMode.PREVIEW);
    }

    public Room3DPreviewData buildPreview(Salle salle, boolean preferGeneratedLayout, Room3DViewMode viewMode) {
        validateSalle(salle);

        List<Room3DPreviewData.SeatPreview> seats = preferGeneratedLayout
            ? generateVirtualSeats(salle)
            : loadPersistedSeats(salle);

        if (seats.isEmpty()) {
            seats = generateVirtualSeats(salle);
        }

        return createPreviewData(salle, viewMode, seats);
    }

    public Room3DPreviewData buildPreviewFromSeats(
        Salle salle,
        Room3DViewMode viewMode,
        List<Room3DPreviewData.SeatPreview> seats
    ) {
        return buildPreviewFromSeats(salle, viewMode, null, seats);
    }

    public Room3DPreviewData buildPreviewFromSeats(
        Salle salle,
        Room3DViewMode viewMode,
        String dispositionOverride,
        List<Room3DPreviewData.SeatPreview> seats
    ) {
        validateSalle(salle);

        List<Room3DPreviewData.SeatPreview> effectiveSeats = seats == null || seats.isEmpty()
            ? generateVirtualSeats(salle)
            : List.copyOf(seats);

        return createPreviewData(salle, viewMode, dispositionOverride, effectiveSeats);
    }

    private Room3DPreviewData createPreviewData(
        Salle salle,
        Room3DViewMode viewMode,
        List<Room3DPreviewData.SeatPreview> seats
    ) {
        return createPreviewData(salle, viewMode, null, seats);
    }

    private Room3DPreviewData createPreviewData(
        Salle salle,
        Room3DViewMode viewMode,
        String dispositionOverride,
        List<Room3DPreviewData.SeatPreview> seats
    ) {
        return new Room3DPreviewData(
            salle.getNom(),
            salle.getBatiment(),
            salle.getLocalisation(),
            salle.getTypeSalle(),
            resolveDisposition(dispositionOverride, salle.getTypeDisposition()),
            salle.getEtat(),
            salle.getCapacite(),
            salle.isAccesHandicape(),
            viewMode,
            seats
        );
    }

    private String resolveDisposition(String dispositionOverride, String fallbackDisposition) {
        if (dispositionOverride == null || dispositionOverride.isBlank()) {
            return fallbackDisposition;
        }
        return dispositionOverride.trim();
    }

    private List<Room3DPreviewData.SeatPreview> loadPersistedSeats(Salle salle) {
        if (salle == null || salle.getIdSalle() <= 0) {
            return List.of();
        }

        try {
            List<Place> places = placeService.getBySalle(salle.getIdSalle());
            if (places.isEmpty()) {
                return List.of();
            }

            return places.stream()
                .map(place -> new Room3DPreviewData.SeatPreview(
                    place.getIdPlace(),
                    place.getNumero(),
                    place.getRang(),
                    place.getColonne(),
                    RoomSeatVisualState.fromPlaceStatus(place.getEtat()),
                    false
                ))
                .toList();
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            return List.of();
        }
    }

    private List<Room3DPreviewData.SeatPreview> generateVirtualSeats(Salle salle) {
        int capacity = Math.max(1, salle.getCapacite());
        String normalizedDisposition = normalize(salle.getTypeDisposition());

        return switch (normalizedDisposition) {
            case "u" -> generateUShapeSeats(capacity);
            case "reunion" -> generateRectanglePerimeterSeats(capacity);
            default -> generateGridSeats(capacity, resolvePreferredColumnCount(normalizedDisposition, capacity));
        };
    }

    private List<Room3DPreviewData.SeatPreview> generateGridSeats(int capacity, int preferredColumns) {
        List<Room3DPreviewData.SeatPreview> seats = new ArrayList<>(capacity);
        int columns = Math.max(1, preferredColumns);

        for (int index = 0; index < capacity; index++) {
            int row = (index / columns) + 1;
            int column = (index % columns) + 1;
            seats.add(new Room3DPreviewData.SeatPreview(
                0,
                index + 1,
                row,
                column,
                RoomSeatVisualState.AVAILABLE,
                false
            ));
        }

        return seats;
    }

    private List<Room3DPreviewData.SeatPreview> generateRectanglePerimeterSeats(int capacity) {
        int rows = 4;
        int columns = 4;
        while (calculatePerimeterSeatCount(rows, columns) < capacity) {
            if (columns <= rows) {
                columns++;
            } else {
                rows++;
            }
        }

        List<int[]> positions = new ArrayList<>(calculatePerimeterSeatCount(rows, columns));
        for (int column = 1; column <= columns; column++) {
            positions.add(new int[] {1, column});
        }
        for (int row = 2; row < rows; row++) {
            positions.add(new int[] {row, columns});
        }
        for (int column = columns; column >= 1; column--) {
            positions.add(new int[] {rows, column});
        }
        for (int row = rows - 1; row >= 2; row--) {
            positions.add(new int[] {row, 1});
        }

        return mapPositionsToSeats(positions, capacity);
    }

    private List<Room3DPreviewData.SeatPreview> generateUShapeSeats(int capacity) {
        int rows = 2;
        int columns = 5;
        while (calculateUShapeSeatCount(rows, columns) < capacity) {
            if (rows <= columns) {
                rows++;
            } else {
                columns++;
            }
        }

        List<int[]> positions = new ArrayList<>(calculateUShapeSeatCount(rows, columns));
        for (int row = 1; row <= rows; row++) {
            positions.add(new int[] {row, 1});
        }
        for (int column = 2; column < columns; column++) {
            positions.add(new int[] {rows, column});
        }
        for (int row = rows; row >= 1; row--) {
            positions.add(new int[] {row, columns});
        }

        return mapPositionsToSeats(positions, capacity);
    }

    private List<Room3DPreviewData.SeatPreview> mapPositionsToSeats(List<int[]> positions, int capacity) {
        List<Room3DPreviewData.SeatPreview> seats = new ArrayList<>(capacity);
        int maxSeats = Math.min(capacity, positions.size());

        for (int index = 0; index < maxSeats; index++) {
            int[] position = positions.get(index);
            seats.add(new Room3DPreviewData.SeatPreview(
                0,
                index + 1,
                position[0],
                position[1],
                RoomSeatVisualState.AVAILABLE,
                false
            ));
        }

        return seats;
    }

    private int calculatePerimeterSeatCount(int rows, int columns) {
        if (rows <= 0 || columns <= 0) {
            return 0;
        }
        if (rows == 1) {
            return columns;
        }
        if (columns == 1) {
            return rows;
        }
        return (rows * 2) + (columns * 2) - 4;
    }

    private int calculateUShapeSeatCount(int rows, int columns) {
        if (rows <= 0 || columns <= 0) {
            return 0;
        }
        if (columns == 1) {
            return rows;
        }
        return (rows * 2) + Math.max(0, columns - 2);
    }

    private int resolvePreferredColumnCount(String disposition, int capacity) {
        if (capacity <= 1) {
            return 1;
        }

        int maxColumns = switch (disposition) {
            case "conference" -> 12;
            case "informatique", "atelier" -> 8;
            default -> 10;
        };

        int suggestedColumns = (int) Math.ceil(Math.sqrt(capacity));
        return Math.max(1, Math.min(Math.min(capacity, maxColumns), suggestedColumns));
    }

    private void validateSalle(Salle salle) {
        if (salle == null) {
            throw new IllegalArgumentException("La salle a previsualiser est obligatoire.");
        }
        if (salle.getCapacite() <= 0) {
            throw new IllegalArgumentException("La capacite doit etre superieure a zero pour l'apercu 3D.");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }
}
