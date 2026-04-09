package tn.esprit.fahamni.interfaces;

import tn.esprit.fahamni.Models.Seance;

import java.util.List;

public interface ISeanceSearchService {

    List<String> getAvailableSearchStatuses();

    List<Seance> search(String keyword, String statusFilter, int limit);
}
