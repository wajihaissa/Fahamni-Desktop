package tn.esprit.fahamni.services;

import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.utils.MyDataBase;
import tn.esprit.fahamni.utils.OperationResult;
import tn.esprit.fahamni.utils.UserInputValidator;
import tn.esprit.fahamni.utils.UserSession;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserAccountService {

    private static final long MAX_AVATAR_SIZE_BYTES = 5L * 1024 * 1024;
    private static final String[] ALLOWED_AVATAR_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"};
    private static final Path AVATAR_ROOT = Paths.get("uploads", "avatars");

    public record AccountOverview(
        String accountStatus,
        String roleLabel,
        String validationStatus,
        String createdAt
    ) {
    }

    private final Connection connection;

    public UserAccountService() {
        connection = MyDataBase.getInstance().getCnx();
    }

    public OperationResult updateCurrentAvatar(File selectedFile) {
        User currentUser = UserSession.getCurrentUser();
        if (currentUser == null) {
            return OperationResult.failure("Aucun utilisateur connecte.");
        }

        if (currentUser.getId() <= 0) {
            return OperationResult.failure("Ce compte n'est pas synchronise avec la base de donnees.");
        }

        if (selectedFile == null || !selectedFile.exists() || !selectedFile.isFile()) {
            return OperationResult.failure("Veuillez choisir un fichier image valide.");
        }

        String extension = extractAllowedAvatarExtension(selectedFile.getName());
        if (extension == null) {
            return OperationResult.failure("Format invalide. Utilisez uniquement JPG, PNG ou WEBP.");
        }

        if (selectedFile.length() > MAX_AVATAR_SIZE_BYTES) {
            return OperationResult.failure("La photo de profil ne doit pas depasser 5 MB.");
        }

        if (connection == null) {
            return OperationResult.failure("Connexion a la base indisponible. Televersement impossible.");
        }

        try {
            ensureAvatarColumn();

            String currentAvatarPath = loadCurrentAvatarPath(currentUser.getId());
            Path userAvatarDirectory = AVATAR_ROOT.resolve("user-" + currentUser.getId());
            Files.createDirectories(userAvatarDirectory);

            deletePreviousAvatarFile(currentAvatarPath);
            deleteUserAvatarVariants(userAvatarDirectory);

            Path targetPath = userAvatarDirectory.resolve("avatar" + extension);
            Files.copy(selectedFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            String relativeAvatarPath = userAvatarDirectory.resolve("avatar" + extension)
                .toString()
                .replace('\\', '/');

            saveCurrentAvatarPath(currentUser.getId(), relativeAvatarPath);
            return OperationResult.success("Photo de profil mise a jour avec succes.");
        } catch (SQLException e) {
            System.out.println("Error updating avatar path: " + e.getMessage());
            return OperationResult.failure("Erreur lors de l'enregistrement de la photo : " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Error storing avatar file: " + e.getMessage());
            return OperationResult.failure("Erreur lors du stockage de la photo : " + e.getMessage());
        }
    }

    public OperationResult updateCurrentUser(String fullName, String email, String password, String confirmPassword) {
        User currentUser = UserSession.getCurrentUser();
        if (currentUser == null) {
            return OperationResult.failure("Aucun utilisateur connecte.");
        }

        if (currentUser.getId() <= 0) {
            return OperationResult.failure("Ce compte n'est pas synchronise avec la base de donnees.");
        }

        String fullNameError = UserInputValidator.validateFullName(fullName);
        if (fullNameError != null) {
            return OperationResult.failure(fullNameError);
        }

        String emailError = UserInputValidator.validateEmail(email);
        if (emailError != null) {
            return OperationResult.failure(emailError);
        }

        String passwordError = UserInputValidator.validatePassword(password, confirmPassword, false);
        if (passwordError != null) {
            return OperationResult.failure(passwordError);
        }

        String normalizedEmail = UserInputValidator.normalizeEmail(email);
        String normalizedFullName = UserInputValidator.normalizeFullName(fullName);

        if (emailAlreadyUsedByAnotherUser(normalizedEmail, currentUser.getId())) {
            return OperationResult.failure("Un autre compte utilise deja cette adresse email.");
        }

        if (connection == null) {
            return OperationResult.failure("Connexion a la base indisponible. Mise a jour impossible.");
        }

        String updatedPassword = isBlank(password) ? currentUser.getPassword() : password;
        String query = "UPDATE `user` SET `full_name` = ?, `email` = ?, `password` = ? WHERE `id` = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, normalizedFullName);
            statement.setString(2, normalizedEmail);
            statement.setString(3, updatedPassword);
            statement.setInt(4, currentUser.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error updating current user: " + e.getMessage());
            return OperationResult.failure("Erreur lors de la mise a jour du compte : " + e.getMessage());
        }

        UserSession.setCurrentUser(new User(
            currentUser.getId(),
            normalizedFullName,
            normalizedEmail,
            updatedPassword,
            currentUser.getRole()
        ));

        return OperationResult.success("Profil mis a jour avec succes.");
    }

    public OperationResult deleteCurrentUser() {
        User currentUser = UserSession.getCurrentUser();
        if (currentUser == null) {
            return OperationResult.failure("Aucun utilisateur connecte.");
        }

        if (currentUser.getId() <= 0) {
            return OperationResult.failure("Ce compte n'est pas synchronise avec la base de donnees.");
        }

        if (connection == null) {
            return OperationResult.failure("Connexion a la base indisponible. Suppression impossible.");
        }

        String query = "DELETE FROM `user` WHERE `id` = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, currentUser.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error deleting current user: " + e.getMessage());
            return OperationResult.failure("Erreur lors de la suppression du compte : " + e.getMessage());
        }

        UserSession.clear();
        return OperationResult.success("Compte supprime avec succes.");
    }

    public AccountOverview getCurrentAccountOverview() {
        User currentUser = UserSession.getCurrentUser();
        if (currentUser == null) {
            return new AccountOverview("Inconnu", "Aucun role", "Non disponible", "Non disponible");
        }

        String accountStatus = "Active";
        String validationStatus = "Approved";
        String createdAt = "Non disponible";

        if (connection != null && currentUser.getId() > 0) {
            String query = "SELECT `status`, `created_at` FROM `user` WHERE `id` = ? LIMIT 1";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setInt(1, currentUser.getId());

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        boolean active = resultSet.getBoolean("status");
                        accountStatus = active ? "Active" : "Suspended";
                        validationStatus = active ? "Approved" : "Restricted";
                        String createdValue = resultSet.getString("created_at");
                        if (!isBlank(createdValue)) {
                            createdAt = createdValue;
                        }
                    }
                }
            } catch (SQLException e) {
                System.out.println("Error loading current account overview: " + e.getMessage());
            }
        }

        return new AccountOverview(accountStatus, UserSession.getRoleLabel(), validationStatus, createdAt);
    }

    public String getCurrentAvatarStatus() {
        User currentUser = UserSession.getCurrentUser();
        if (currentUser == null || currentUser.getId() <= 0 || connection == null) {
            return "Aucun fichier choisi";
        }

        try {
            ensureAvatarColumn();
            String avatarPath = loadCurrentAvatarPath(currentUser.getId());
            if (isBlank(avatarPath)) {
                return "Aucun fichier choisi";
            }
            Path path = Paths.get(avatarPath);
            return "Avatar actuel : " + path.getFileName();
        } catch (SQLException e) {
            System.out.println("Error loading avatar status: " + e.getMessage());
            return "Aucun fichier choisi";
        }
    }

    public Path getCurrentAvatarPath() {
        User currentUser = UserSession.getCurrentUser();
        if (currentUser == null || currentUser.getId() <= 0 || connection == null) {
            return null;
        }

        try {
            ensureAvatarColumn();
            String avatarPath = loadCurrentAvatarPath(currentUser.getId());
            if (isBlank(avatarPath)) {
                return null;
            }

            Path candidate = Paths.get(avatarPath).normalize();
            if (!Files.exists(candidate)) {
                return null;
            }
            return candidate;
        } catch (SQLException e) {
            System.out.println("Error loading current avatar path: " + e.getMessage());
            return null;
        }
    }

    private boolean emailAlreadyUsedByAnotherUser(String email, int currentUserId) {
        if (connection == null) {
            return false;
        }

        String query = "SELECT 1 FROM `user` WHERE LOWER(email) = LOWER(?) AND id <> ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, email.trim());
            statement.setInt(2, currentUserId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            System.out.println("Error checking duplicate account email: " + e.getMessage());
            return false;
        }
    }

    private void ensureAvatarColumn() throws SQLException {
        String query = "ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `avatar_path` VARCHAR(255) NULL";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.execute();
        }
    }

    private String loadCurrentAvatarPath(int userId) throws SQLException {
        String query = "SELECT `avatar_path` FROM `user` WHERE `id` = ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return resultSet.getString("avatar_path");
            }
        }
    }

    private void saveCurrentAvatarPath(int userId, String avatarPath) throws SQLException {
        String query = "UPDATE `user` SET `avatar_path` = ? WHERE `id` = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, avatarPath);
            statement.setInt(2, userId);
            statement.executeUpdate();
        }
    }

    private void deletePreviousAvatarFile(String avatarPath) throws IOException {
        if (isBlank(avatarPath)) {
            return;
        }

        Path candidate = Paths.get(avatarPath).normalize();
        if (!candidate.startsWith(AVATAR_ROOT)) {
            return;
        }

        Files.deleteIfExists(candidate);
    }

    private void deleteUserAvatarVariants(Path userAvatarDirectory) throws IOException {
        if (!Files.exists(userAvatarDirectory)) {
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(userAvatarDirectory, "avatar.*")) {
            for (Path path : stream) {
                Files.deleteIfExists(path);
            }
        }
    }

    private String extractAllowedAvatarExtension(String fileName) {
        if (isBlank(fileName)) {
            return null;
        }

        String lower = fileName.trim().toLowerCase();
        for (String extension : ALLOWED_AVATAR_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return extension;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
