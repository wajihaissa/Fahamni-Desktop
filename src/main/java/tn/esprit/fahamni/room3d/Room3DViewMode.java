package tn.esprit.fahamni.room3d;

public enum Room3DViewMode {
    PREVIEW,
    SELECTION;

    public boolean supportsSeatSelection() {
        return this == SELECTION;
    }
}
