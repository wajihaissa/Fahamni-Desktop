package tn.esprit.fahamni.room3d;

public enum Room3DViewMode {
    PREVIEW,
    SELECTION,
    DESIGN_REVIEW;

    public boolean supportsSeatSelection() {
        return this == SELECTION;
    }

    public boolean isDesignReview() {
        return this == DESIGN_REVIEW;
    }
}
