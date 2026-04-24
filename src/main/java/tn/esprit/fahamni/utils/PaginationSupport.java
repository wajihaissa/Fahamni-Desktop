package tn.esprit.fahamni.utils;

import javafx.scene.control.Button;
import javafx.scene.layout.HBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public final class PaginationSupport {

    private static final int DEFAULT_MAX_VISIBLE_PAGES = 5;

    private PaginationSupport() {
    }

    public record PageSlice(
        int currentPage,
        int totalPages,
        int pageSize,
        int totalItems,
        int fromIndex,
        int toIndex
    ) {
        public boolean isEmpty() {
            return totalItems <= 0 || fromIndex >= toIndex;
        }

        public int fromItem() {
            return isEmpty() ? 0 : fromIndex + 1;
        }

        public int toItem() {
            return isEmpty() ? 0 : toIndex;
        }
    }

    public static int calculateTotalPages(int totalItems, int pageSize) {
        if (totalItems <= 0 || pageSize <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalItems / pageSize);
    }

    public static int clampPage(int currentPage, int totalPages) {
        if (totalPages <= 0) {
            return 1;
        }
        return Math.max(1, Math.min(currentPage, totalPages));
    }

    public static PageSlice slice(int currentPage, int totalItems, int pageSize) {
        int effectivePageSize = Math.max(1, pageSize);
        int totalPages = calculateTotalPages(totalItems, effectivePageSize);
        int effectiveCurrentPage = clampPage(currentPage, totalPages);

        if (totalItems <= 0 || totalPages <= 0) {
            return new PageSlice(1, 0, effectivePageSize, Math.max(0, totalItems), 0, 0);
        }

        int fromIndex = (effectiveCurrentPage - 1) * effectivePageSize;
        int toIndex = Math.min(fromIndex + effectivePageSize, totalItems);
        return new PageSlice(effectiveCurrentPage, totalPages, effectivePageSize, totalItems, fromIndex, toIndex);
    }

    public static String buildRangeSummary(
        PageSlice pageSlice,
        String singularLabel,
        String pluralLabel,
        String singularSuffix,
        String pluralSuffix
    ) {
        if (pageSlice == null || pageSlice.totalItems() <= 0) {
            return "Aucun " + singularLabel + " a afficher";
        }
        if (pageSlice.totalItems() == 1) {
            return "1 " + singularLabel + " " + singularSuffix;
        }
        return pageSlice.fromItem()
            + "-"
            + pageSlice.toItem()
            + " sur "
            + pageSlice.totalItems()
            + " "
            + pluralLabel
            + " "
            + pluralSuffix;
    }

    public static List<Integer> buildVisiblePageNumbers(int currentPage, int totalPages) {
        return buildVisiblePageNumbers(currentPage, totalPages, DEFAULT_MAX_VISIBLE_PAGES);
    }

    public static List<Integer> buildVisiblePageNumbers(int currentPage, int totalPages, int maxVisiblePages) {
        ArrayList<Integer> pages = new ArrayList<>();
        if (totalPages <= 0 || maxVisiblePages <= 0) {
            return pages;
        }

        int effectiveCurrentPage = clampPage(currentPage, totalPages);
        int effectiveMaxVisiblePages = Math.max(1, maxVisiblePages);
        int firstPage = Math.max(1, effectiveCurrentPage - (effectiveMaxVisiblePages / 2));
        int lastPage = Math.min(totalPages, firstPage + effectiveMaxVisiblePages - 1);
        firstPage = Math.max(1, lastPage - effectiveMaxVisiblePages + 1);

        for (int page = firstPage; page <= lastPage; page++) {
            pages.add(page);
        }
        return pages;
    }

    public static void populatePageButtons(
        HBox container,
        int currentPage,
        int totalPages,
        IntConsumer onPageSelected
    ) {
        populatePageButtons(container, currentPage, totalPages, DEFAULT_MAX_VISIBLE_PAGES, onPageSelected);
    }

    public static void populatePageButtons(
        HBox container,
        int currentPage,
        int totalPages,
        int maxVisiblePages,
        IntConsumer onPageSelected
    ) {
        if (container == null) {
            return;
        }

        container.getChildren().clear();
        if (totalPages <= 0) {
            return;
        }

        for (int page : buildVisiblePageNumbers(currentPage, totalPages, maxVisiblePages)) {
            Button pageButton = new Button(String.valueOf(page));
            pageButton.getStyleClass().setAll("backoffice-page-button");
            if (page == currentPage) {
                pageButton.getStyleClass().add("active-page");
                pageButton.setDisable(true);
            } else if (onPageSelected != null) {
                pageButton.setOnAction(event -> onPageSelected.accept(page));
            }
            container.getChildren().add(pageButton);
        }
    }
}
