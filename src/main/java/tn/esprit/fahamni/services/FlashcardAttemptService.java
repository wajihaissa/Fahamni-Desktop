package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.FlashcardAttempt;
import tn.esprit.fahamni.interfaces.IServices;
import tn.esprit.fahamni.utils.MyDataBase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class FlashcardAttemptService implements IServices<FlashcardAttempt> {

    private final Connection cnx;

    public FlashcardAttemptService() {
        this.cnx = MyDataBase.getInstance().getCnx();
    }

    public void ajouter(FlashcardAttempt attempt) {
        String req = "INSERT INTO flashcard_attempt (question, user_answer, ai_feedback, is_correct, matiere_id, section_id, expected_answer) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pst = cnx.prepareStatement(req, Statement.RETURN_GENERATED_KEYS)) {
            pst.setString(1, attempt.getQuestion());
            pst.setString(2, attempt.getUserAnswer());
            pst.setString(3, attempt.getAiFeedback());
            pst.setBoolean(4, attempt.getIsCorrect());
            pst.setInt(5, attempt.getSubjectId());
            pst.setInt(6, attempt.getSectionId());
            pst.setString(7, attempt.getExpectedAnswer());
            pst.executeUpdate();

            try (ResultSet rs = pst.getGeneratedKeys()) {
                if (rs.next()) {
                    attempt.setId(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void modifier(FlashcardAttempt attempt) {
        String req = "UPDATE flashcard_attempt SET question = ?, user_answer = ?, ai_feedback = ?, is_correct = ?, matiere_id = ?, section_id = ?, expected_answer = ? WHERE id = ?";

        try (PreparedStatement pst = cnx.prepareStatement(req)) {
            pst.setString(1, attempt.getQuestion());
            pst.setString(2, attempt.getUserAnswer());
            pst.setString(3, attempt.getAiFeedback());
            pst.setBoolean(4, attempt.getIsCorrect());
            pst.setInt(5, attempt.getSubjectId());
            pst.setInt(6, attempt.getSectionId());
            pst.setString(7, attempt.getExpectedAnswer());
            pst.setInt(8, attempt.getId());
            pst.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void supprimer(int id) {
        String req = "DELETE FROM flashcard_attempt WHERE id = ?";

        try (PreparedStatement pst = cnx.prepareStatement(req)) {
            pst.setInt(1, id);
            pst.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public List<FlashcardAttempt> afficherList() {
        List<FlashcardAttempt> attempts = new ArrayList<>();
        String req = "SELECT * FROM flashcard_attempt";

        try (Statement st = cnx.createStatement();
             ResultSet rs = st.executeQuery(req)) {

            while (rs.next()) {
                FlashcardAttempt attempt = new FlashcardAttempt(
                    rs.getInt("id"),
                    rs.getString("question"),
                    rs.getString("user_answer"),
                    rs.getString("ai_feedback"),
                    rs.getBoolean("is_correct"),
                    rs.getInt("matiere_id"),
                    rs.getInt("section_id"),
                    rs.getString("expected_answer")
                );
                attempts.add(attempt);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return attempts;
    }

    public FlashcardAttempt getOneById(int id) {
        String req = "SELECT * FROM flashcard_attempt WHERE id = ?";

        try (PreparedStatement pst = cnx.prepareStatement(req)) {
            pst.setInt(1, id);

            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    return new FlashcardAttempt(
                        rs.getInt("id"),
                        rs.getString("question"),
                        rs.getString("user_answer"),
                        rs.getString("ai_feedback"),
                        rs.getBoolean("is_correct"),
                        rs.getInt("matiere_id"),
                        rs.getInt("section_id"),
                        rs.getString("expected_answer")
                    );
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return null;
    }
}
