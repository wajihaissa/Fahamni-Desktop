-- Demo seed for intelligent tutor recommendation.
-- Uses dedicated IDs so the script can be replayed safely.

DELETE FROM reservation WHERE id BETWEEN 1001 AND 1100;
DELETE FROM seance WHERE id BETWEEN 101 AND 130;

INSERT INTO seance (
    id, matiere, start_at, duration_min, max_participants, status,
    description, created_at, updated_at, tuteur_id, mode_seance, salle_id
) VALUES
    (101, 'Analyse Numerique', '2026-04-20 18:00:00', 90, 6, 1,
     'Seance de revision orientee exercices pour consolider les methodes numeriques.', '2026-04-18 09:00:00', NULL, 9, 'en_ligne', NULL),
    (102, 'Algorithmique', '2026-04-21 17:30:00', 90, 6, 1,
     'Atelier de resolution de problemes pour renforcer les bases en algorithmique.', '2026-04-18 09:15:00', NULL, 8, 'en_ligne', NULL),
    (103, 'SGBD', '2026-04-22 18:00:00', 120, 6, 1,
     'Preparation guidee avec schemas relationnels, normalisation et SQL applique.', '2026-04-18 09:30:00', NULL, 3, 'en_ligne', NULL),
    (104, 'GL', '2026-04-20 15:00:00', 120, 8, 1,
     'Introduction au genie logiciel et a l organisation d un projet equipe.', '2026-04-18 09:45:00', NULL, 33, 'en_ligne', NULL),
    (111, 'Analyse Numerique', '2026-04-09 18:00:00', 90, 6, 1,
     'Seance terminee pour creer un historique fiable autour de la matiere.', '2026-03-28 08:00:00', NULL, 9, 'en_ligne', NULL),
    (112, 'Analyse Numerique', '2026-04-07 18:00:00', 90, 6, 1,
     'Seance terminee pour enrichir la recommandation par historique de reservations.', '2026-03-26 08:00:00', NULL, 9, 'en_ligne', NULL),
    (113, 'Analyse Numerique', '2026-04-04 18:00:00', 90, 6, 1,
     'Seance terminee avec exercices progressifs et mise en confiance.', '2026-03-23 08:00:00', NULL, 9, 'en_ligne', NULL),
    (114, 'Anglais Technique', '2026-04-02 17:30:00', 60, 6, 1,
     'Seance terminee en communication technique et vocabulaire metier.', '2026-03-20 08:00:00', NULL, 9, 'en_ligne', NULL),
    (115, 'Java', '2026-03-30 18:00:00', 90, 6, 1,
     'Seance terminee sur la programmation orientee objet et les bonnes pratiques.', '2026-03-17 08:00:00', NULL, 9, 'en_ligne', NULL),
    (116, 'Analyse Numerique', '2026-03-27 18:00:00', 90, 6, 1,
     'Seance terminee pour renforcer encore la confiance sur cette matiere.', '2026-03-14 08:00:00', NULL, 9, 'en_ligne', NULL),
    (117, 'Algorithmique', '2026-04-08 17:00:00', 90, 6, 1,
     'Seance terminee sur les tableaux, fonctions et raisonnement algorithmique.', '2026-03-27 08:15:00', NULL, 8, 'en_ligne', NULL),
    (118, 'Algorithmique', '2026-04-05 17:00:00', 90, 6, 1,
     'Seance terminee avec exercices de complexite et structures de controle.', '2026-03-24 08:15:00', NULL, 8, 'en_ligne', NULL),
    (119, 'Algorithmique', '2026-04-01 17:00:00', 90, 6, 1,
     'Seance terminee pour construire une reputation fiable sur ce tuteur.', '2026-03-20 08:15:00', NULL, 8, 'en_ligne', NULL),
    (120, 'Java Avance', '2026-03-29 17:30:00', 90, 6, 1,
     'Seance terminee autour des classes, collections et methodes utilitaires.', '2026-03-16 08:15:00', NULL, 8, 'en_ligne', NULL),
    (121, 'SGBD', '2026-04-06 16:00:00', 120, 6, 1,
     'Seance terminee sur la modelisation relationnelle et la normalisation.', '2026-03-25 08:30:00', NULL, 3, 'en_ligne', NULL),
    (122, 'SGBD', '2026-04-03 16:00:00', 120, 6, 1,
     'Seance terminee avec requetes SQL et travail sur les jointures.', '2026-03-22 08:30:00', NULL, 3, 'en_ligne', NULL),
    (123, 'SGBD', '2026-03-31 16:00:00', 120, 6, 1,
     'Seance terminee pour donner de la profondeur au score de recommandation.', '2026-03-19 08:30:00', NULL, 3, 'en_ligne', NULL)
ON DUPLICATE KEY UPDATE
    matiere = VALUES(matiere),
    start_at = VALUES(start_at),
    duration_min = VALUES(duration_min),
    max_participants = VALUES(max_participants),
    status = VALUES(status),
    description = VALUES(description),
    created_at = VALUES(created_at),
    updated_at = VALUES(updated_at),
    tuteur_id = VALUES(tuteur_id),
    mode_seance = VALUES(mode_seance),
    salle_id = VALUES(salle_id);

INSERT INTO reservation (
    id, status, reserved_at, cancell_at, notes, seance_id, participant_id,
    confirmation_email_sent_at, acceptance_email_sent_at, reminder_email_sent_at,
    student_rating, student_review, rated_at
) VALUES
    (1001, 1, '2026-04-05 09:00:00', NULL, 'Participation reguliere.', 111, 5, NULL, '2026-04-05 09:05:00', NULL, NULL, NULL, NULL),
    (1002, 1, '2026-04-03 09:00:00', NULL, 'Participation reguliere.', 112, 5, NULL, '2026-04-03 09:05:00', NULL, NULL, NULL, NULL),
    (1003, 1, '2026-03-31 09:00:00', NULL, 'Participation reguliere.', 113, 5, NULL, '2026-03-31 09:05:00', NULL, NULL, NULL, NULL),
    (1004, 1, '2026-03-29 10:00:00', NULL, 'Presence confirmee.', 114, 2, NULL, '2026-03-29 10:05:00', NULL, NULL, NULL, NULL),
    (1005, 1, '2026-03-26 10:00:00', NULL, 'Presence confirmee.', 115, 4, NULL, '2026-03-26 10:05:00', NULL, NULL, NULL, NULL),
    (1006, 1, '2026-03-23 10:00:00', NULL, 'Presence confirmee.', 116, 6, NULL, '2026-03-23 10:05:00', NULL, NULL, NULL, NULL),
    (1007, 1, '2026-04-05 11:00:00', NULL, 'Presence confirmee.', 111, 7, NULL, '2026-04-05 11:05:00', NULL, NULL, NULL, NULL),
    (1008, 1, '2026-04-03 11:00:00', NULL, 'Presence confirmee.', 112, 10, NULL, '2026-04-03 11:05:00', NULL, NULL, NULL, NULL),
    (1009, 1, '2026-04-18 12:00:00', NULL, 'Reservation future acceptee.', 101, 11, NULL, '2026-04-18 12:05:00', NULL, NULL, NULL, NULL),
    (1010, 1, '2026-04-18 12:15:00', NULL, 'Reservation future acceptee.', 101, 12, NULL, '2026-04-18 12:20:00', NULL, NULL, NULL, NULL),
    (1011, 1, '2026-04-02 09:30:00', NULL, 'Participation reguliere.', 117, 5, NULL, '2026-04-02 09:35:00', NULL, NULL, NULL, NULL),
    (1012, 1, '2026-03-30 09:30:00', NULL, 'Presence confirmee.', 118, 13, NULL, '2026-03-30 09:35:00', NULL, NULL, NULL, NULL),
    (1013, 1, '2026-03-27 09:30:00', NULL, 'Presence confirmee.', 119, 14, NULL, '2026-03-27 09:35:00', NULL, NULL, NULL, NULL),
    (1014, 1, '2026-03-24 09:30:00', NULL, 'Presence confirmee.', 120, 15, NULL, '2026-03-24 09:35:00', NULL, NULL, NULL, NULL),
    (1015, 1, '2026-04-02 11:30:00', NULL, 'Presence confirmee.', 117, 16, NULL, '2026-04-02 11:35:00', NULL, NULL, NULL, NULL),
    (1016, 1, '2026-04-18 13:00:00', NULL, 'Reservation future acceptee.', 102, 17, NULL, '2026-04-18 13:05:00', NULL, NULL, NULL, NULL),
    (1017, 1, '2026-03-31 10:30:00', NULL, 'Presence confirmee.', 121, 5, NULL, '2026-03-31 10:35:00', NULL, NULL, NULL, NULL),
    (1018, 1, '2026-03-28 10:30:00', NULL, 'Presence confirmee.', 121, 18, NULL, '2026-03-28 10:35:00', NULL, NULL, NULL, NULL),
    (1019, 1, '2026-03-25 10:30:00', NULL, 'Presence confirmee.', 122, 19, NULL, '2026-03-25 10:35:00', NULL, NULL, NULL, NULL),
    (1020, 1, '2026-03-22 10:30:00', NULL, 'Presence confirmee.', 123, 20, NULL, '2026-03-22 10:35:00', NULL, NULL, NULL, NULL),
    (1021, 1, '2026-04-18 13:30:00', NULL, 'Reservation future acceptee.', 103, 6, NULL, '2026-04-18 13:35:00', NULL, NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    reserved_at = VALUES(reserved_at),
    cancell_at = VALUES(cancell_at),
    notes = VALUES(notes),
    seance_id = VALUES(seance_id),
    participant_id = VALUES(participant_id),
    confirmation_email_sent_at = VALUES(confirmation_email_sent_at),
    acceptance_email_sent_at = VALUES(acceptance_email_sent_at),
    reminder_email_sent_at = VALUES(reminder_email_sent_at),
    student_rating = VALUES(student_rating),
    student_review = VALUES(student_review),
    rated_at = VALUES(rated_at);
