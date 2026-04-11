package tn.esprit.fahamni.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import javafx.scene.Scene;

public final class UiTheme {

    private static final String STYLES = """
        .root {
            -fx-font-family: "Segoe UI", "Helvetica Neue", Arial, sans-serif;
            -fx-base: #ffffff;
            -fx-control-inner-background: #ffffff;
            -fx-accent: #5a80f4;
        }

        .main-root, .backoffice-root {
            -fx-background-color:
                radial-gradient(center 18% 0%, radius 55%, rgba(126, 182, 255, 0.24) 0%, rgba(126, 182, 255, 0.0) 72%),
                linear-gradient(to bottom, #f5f8ff 0%, #edf3ff 46%, #e7eefc 100%);
        }

        .content-pane, .backoffice-content-pane {
            -fx-background-color:
                radial-gradient(center 24% 0%, radius 62%, rgba(126, 182, 255, 0.16) 0%, rgba(126, 182, 255, 0.0) 68%),
                linear-gradient(to bottom, #f6f9ff 0%, #eef4ff 52%, #e9f0fd 100%);
        }

        .sidebar, .backoffice-sidebar {
            -fx-background-color:
                radial-gradient(center 72% 16%, radius 62%, rgba(126, 182, 255, 0.18) 0%, rgba(126, 182, 255, 0.0) 100%),
                linear-gradient(to bottom, #2d61b9 0%, #1f4f9f 56%, #183f82 100%);
        }

        .sidebar {
            -fx-pref-width: 268px;
            -fx-min-width: 268px;
        }

        .backoffice-sidebar {
            -fx-pref-width: 276px;
            -fx-min-width: 276px;
        }

        .sidebar-header, .backoffice-sidebar-header {
            -fx-padding: 0 24px 18px 24px;
            -fx-border-color: rgba(255, 255, 255, 0.12);
            -fx-border-width: 0 0 1 0;
        }

        .sidebar-title, .backoffice-sidebar-title {
            -fx-font-size: 25px;
            -fx-font-weight: bold;
            -fx-text-fill: white;
        }

        .backoffice-sidebar-subtitle, .backoffice-footer-copy {
            -fx-font-size: 12px;
            -fx-text-fill: rgba(222, 233, 248, 0.76);
        }

        .sidebar-menu, .backoffice-sidebar-menu {
            -fx-padding: 14px 18px 0 18px;
        }

        .sidebar-button, .backoffice-nav-button {
            -fx-background-color: transparent;
            -fx-text-fill: rgba(231, 239, 251, 0.80);
            -fx-font-size: 14px;
            -fx-font-weight: bold;
            -fx-padding: 14px 18px;
            -fx-background-radius: 12px;
            -fx-alignment: CENTER_LEFT;
            -fx-cursor: hand;
        }

        .sidebar-button:hover, .backoffice-nav-button:hover {
            -fx-background-color: rgba(255, 255, 255, 0.10);
            -fx-text-fill: white;
        }

        .sidebar-button.active, .backoffice-nav-button.active {
            -fx-background-color: linear-gradient(to bottom, #6e97ff 0%, #5a80f4 100%);
            -fx-text-fill: white;
            -fx-effect: dropshadow(gaussian, rgba(23, 52, 110, 0.22), 12, 0.18, 0, 4);
        }

        .sidebar-footer, .backoffice-sidebar-footer {
            -fx-padding: 16px 24px 0 24px;
            -fx-border-color: rgba(255, 255, 255, 0.10);
            -fx-border-width: 1 0 0 0;
        }

        .logout-button, .backoffice-logout-button {
            -fx-background-color: rgba(255, 255, 255, 0.10);
            -fx-border-color: rgba(255, 255, 255, 0.18);
            -fx-border-width: 1px;
            -fx-border-radius: 12px;
            -fx-background-radius: 12px;
            -fx-text-fill: white;
            -fx-font-size: 13px;
            -fx-font-weight: bold;
            -fx-padding: 12px 16px;
            -fx-cursor: hand;
        }

        .logout-button:hover, .backoffice-logout-button:hover {
            -fx-background-color: rgba(255, 255, 255, 0.18);
        }

        .topbar, .backoffice-topbar {
            -fx-background-color: rgba(255, 255, 255, 0.90);
            -fx-border-color: rgba(205, 219, 244, 0.92);
            -fx-border-width: 0 0 1 0;
            -fx-effect: dropshadow(gaussian, rgba(24, 63, 130, 0.08), 14, 0.10, 0, 4);
        }

        .page-title, .backoffice-page-title {
            -fx-font-size: 25px;
            -fx-font-weight: bold;
            -fx-text-fill: #183f82;
        }

        .page-subtitle {
            -fx-font-size: 12px;
            -fx-text-fill: #7487a3;
        }

        .user-label, .backoffice-page-subtitle {
            -fx-font-size: 13px;
            -fx-text-fill: #697f9f;
        }

        .topbar-actions {
            -fx-padding: 2px 0 2px 0;
        }

        .account-menu-wrapper {
            -fx-padding: 0;
        }

        .account-menu-panel {
            -fx-background-color: rgba(255, 255, 255, 0.98);
            -fx-border-color: #d8e3f8;
            -fx-border-width: 1px;
            -fx-background-radius: 16px;
            -fx-border-radius: 16px;
            -fx-padding: 10px;
            -fx-min-width: 190px;
            -fx-effect: dropshadow(gaussian, rgba(24, 63, 130, 0.16), 18, 0.18, 0, 8);
        }

        .account-menu-item {
            -fx-background-color: transparent;
            -fx-text-fill: #21406d;
            -fx-font-size: 12px;
            -fx-font-weight: bold;
            -fx-padding: 10px 12px;
            -fx-background-radius: 12px;
            -fx-alignment: CENTER_LEFT;
            -fx-cursor: hand;
        }

        .account-menu-item-danger {
            -fx-background-color: transparent;
            -fx-text-fill: #ba4658;
            -fx-font-size: 12px;
            -fx-font-weight: bold;
            -fx-padding: 10px 12px;
            -fx-background-radius: 12px;
            -fx-alignment: CENTER_LEFT;
            -fx-cursor: hand;
        }

        .account-menu-item:hover {
            -fx-background-color: #eef4ff;
        }

        .account-menu-item-danger:hover {
            -fx-background-color: #ffe8ec;
        }

        .topbar-button {
            -fx-background-color: white;
            -fx-border-color: #d8e3f8;
            -fx-border-width: 1px;
            -fx-border-radius: 14px;
            -fx-background-radius: 14px;
            -fx-text-fill: #214f93;
            -fx-font-size: 16px;
            -fx-padding: 0;
            -fx-min-width: 38px;
            -fx-min-height: 38px;
            -fx-cursor: hand;
        }

        .topbar-button:hover {
            -fx-background-color: #eff5ff;
            -fx-border-color: #a8c2f7;
        }

        .topbar-pill-button {
            -fx-background-color: #f8fbff;
            -fx-border-color: #d8e3f8;
            -fx-border-width: 1px;
            -fx-border-radius: 999px;
            -fx-background-radius: 999px;
            -fx-text-fill: #214f93;
            -fx-font-size: 12px;
            -fx-font-weight: bold;
            -fx-padding: 9px 14px;
            -fx-cursor: hand;
        }

        .topbar-pill-button:hover {
            -fx-background-color: #edf4ff;
            -fx-border-color: #aac4f6;
        }

        .topbar-profile-card {
            -fx-background-color: linear-gradient(to bottom, #ffffff 0%, #f6faff 100%);
            -fx-border-color: #d8e3f8;
            -fx-border-width: 1px;
            -fx-border-radius: 16px;
            -fx-background-radius: 16px;
            -fx-padding: 8px 12px;
        }

        .profile-avatar {
            -fx-min-width: 34px;
            -fx-min-height: 34px;
            -fx-alignment: center;
            -fx-background-color: linear-gradient(to bottom, #6e97ff 0%, #5a80f4 100%);
            -fx-background-radius: 999px;
            -fx-text-fill: white;
            -fx-font-size: 12px;
            -fx-font-weight: bold;
        }

        .profile-name {
            -fx-font-size: 12px;
            -fx-font-weight: bold;
            -fx-text-fill: #21406d;
        }

        .profile-role {
            -fx-font-size: 11px;
            -fx-text-fill: #7b8ea9;
        }

        .profile-avatar-large {
            -fx-alignment: center;
            -fx-background-color: linear-gradient(to bottom, #6e97ff 0%, #5a80f4 100%);
            -fx-background-radius: 999px;
            -fx-text-fill: white;
            -fx-font-weight: bold;
            -fx-min-width: 56px;
            -fx-min-height: 56px;
            -fx-font-size: 18px;
        }

        .profile-role-badge {
            -fx-background-color: #edf4ff;
            -fx-text-fill: #29589f;
            -fx-font-size: 11px;
            -fx-font-weight: bold;
            -fx-padding: 6px 10px;
            -fx-background-radius: 999px;
        }

        .backoffice-badge {
            -fx-background-color: linear-gradient(to bottom, #6e97ff 0%, #5a80f4 100%);
            -fx-text-fill: white;
            -fx-font-size: 12px;
            -fx-font-weight: bold;
            -fx-padding: 8px 14px;
            -fx-background-radius: 999px;
        }

        .profile-settings-shell {
            -fx-background-color: rgba(255, 255, 255, 0.95);
            -fx-border-color: #dbe5f6;
            -fx-border-width: 1px;
            -fx-background-radius: 24px;
            -fx-border-radius: 24px;
            -fx-effect: dropshadow(gaussian, rgba(24, 63, 130, 0.10), 22, 0.18, 0, 8);
        }

        .profile-settings-sidebar {
            -fx-background-color: linear-gradient(to bottom, #fbfcff 0%, #f4f7fd 100%);
            -fx-border-color: #e2e9f7;
            -fx-border-width: 0 1 0 0;
            -fx-background-radius: 24px 0 0 24px;
            -fx-border-radius: 24px 0 0 24px;
        }

        .profile-settings-sidebar-header {
            -fx-padding: 28px 28px 22px 28px;
            -fx-border-color: #e8eef9;
            -fx-border-width: 0 0 1 0;
        }

        .profile-sidebar-title {
            -fx-font-size: 20px;
            -fx-font-weight: bold;
            -fx-text-fill: #1a3864;
        }

        .profile-sidebar-copy, .profile-sidebar-user-email, .profile-inline-note {
            -fx-font-size: 12px;
            -fx-text-fill: #7085a4;
        }

        .profile-sidebar-user-card {
            -fx-padding: 6px 0 0 0;
        }

        .profile-sidebar-user-name {
            -fx-font-size: 13px;
            -fx-font-weight: bold;
            -fx-text-fill: #243c59;
        }

        .profile-nav-list {
            -fx-padding: 22px 18px 18px 18px;
        }

        .profile-nav-button {
            -fx-background-color: transparent;
            -fx-text-fill: #294365;
            -fx-font-size: 14px;
            -fx-font-weight: bold;
            -fx-padding: 14px 18px;
            -fx-background-radius: 14px;
            -fx-border-color: transparent;
            -fx-border-width: 0 0 0 4px;
            -fx-alignment: CENTER_LEFT;
            -fx-cursor: hand;
        }

        .profile-nav-button:hover {
            -fx-background-color: #eef3ff;
            -fx-text-fill: #1f4f9f;
        }

        .profile-nav-button.active {
            -fx-background-color: linear-gradient(to right, #617cff 0%, #4e67f0 100%);
            -fx-text-fill: white;
            -fx-border-color: #4b67f2;
            -fx-effect: dropshadow(gaussian, rgba(47, 66, 151, 0.18), 16, 0.18, 0, 4);
        }

        .profile-sidebar-footer {
            -fx-padding: 18px 24px 26px 24px;
            -fx-border-color: #e8eef9;
            -fx-border-width: 1px 0 0 0;
        }

        .profile-settings-content {
            -fx-padding: 30px 34px 34px 34px;
        }

        .profile-content-title {
            -fx-font-size: 22px;
            -fx-font-weight: bold;
            -fx-text-fill: #1c3155;
        }

        .profile-section-panel {
            -fx-background-color: transparent;
            -fx-padding: 8px 0 0 0;
        }

        .profile-upload-row {
            -fx-background-color: rgba(248, 251, 255, 0.92);
            -fx-border-color: #d5e1f5;
            -fx-border-width: 1px;
            -fx-border-radius: 14px;
            -fx-background-radius: 14px;
            -fx-padding: 12px 14px;
        }

        .profile-inline-button, .profile-soft-button, .profile-outline-danger-button, .profile-danger-inline-button {
            -fx-font-weight: bold;
            -fx-background-radius: 12px;
            -fx-border-radius: 12px;
            -fx-cursor: hand;
            -fx-padding: 10px 16px;
        }

        .profile-inline-button, .profile-soft-button {
            -fx-background-color: rgba(236, 242, 255, 0.95);
            -fx-border-color: #d5e1f5;
            -fx-border-width: 1px;
            -fx-text-fill: #294a78;
        }

        .profile-inline-button:hover, .profile-soft-button:hover {
            -fx-background-color: #eaf1ff;
        }

        .profile-accent-button {
            -fx-background-color: linear-gradient(to right, #4d6fff 0%, #7b1fd3 100%);
            -fx-text-fill: white;
            -fx-font-weight: bold;
            -fx-cursor: hand;
            -fx-background-radius: 12px;
            -fx-padding: 11px 18px;
            -fx-effect: dropshadow(gaussian, rgba(58, 57, 160, 0.20), 14, 0.18, 0, 4);
        }

        .profile-accent-button:hover {
            -fx-background-color: linear-gradient(to right, #5b7cff 0%, #8b2be0 100%);
        }

        .profile-danger-inline-button, .profile-outline-danger-button {
            -fx-background-color: #fff1f2;
            -fx-border-color: #ffc9cf;
            -fx-border-width: 1px;
            -fx-text-fill: #c94252;
        }

        .profile-danger-inline-button:hover, .profile-outline-danger-button:hover {
            -fx-background-color: #ffe5e8;
        }

        .profile-readonly-field {
            -fx-background-color: #f8fbff;
            -fx-control-inner-background: #f8fbff;
            -fx-text-fill: #465d7c;
        }

        .content-placeholder, .backoffice-empty-state {
            -fx-font-size: 16px;
            -fx-text-fill: #5f7595;
            -fx-padding: 24px;
        }

        .dashboard-scroll, .seance-scroll, .reservation-scroll, .planner-scroll, .messages-scroll, .quiz-scroll, .blog-scroll, .backoffice-scroll {
            -fx-background-color: transparent;
            -fx-background-insets: 0;
            -fx-padding: 0;
        }

        .dashboard-scroll > .viewport, .seance-scroll > .viewport, .reservation-scroll > .viewport, .planner-scroll > .viewport,
        .messages-scroll > .viewport, .quiz-scroll > .viewport, .blog-scroll > .viewport, .backoffice-scroll > .viewport {
            -fx-background-color: transparent;
        }

        .dashboard-content, .seance-content, .reservation-content, .planner-content, .messages-container, .quiz-content, .blog-content, .backoffice-page, .messenger-root {
            -fx-background-color: transparent;
        }

        .stat-card, .upcoming-sessions, .learning-progress, .recommended-tutors, .seance-header, .seance-card, .reservation-header,
        .tab-content, .planner-header, .planner-form, .current-plan, .previous-plans, .quiz-header, .available-quizzes, .recent-results,
        .blog-header, .featured-article, .recent-articles, .categories-section, .blog-controls, .conversations-list, .chat-area,
        .backoffice-panel, .backoffice-stat-card, .backoffice-mini-card, .profile-hero-card, .profile-form-card, .profile-danger-card {
            -fx-background-color: rgba(255, 255, 255, 0.92);
            -fx-border-color: #dbe5f6;
            -fx-border-width: 1px;
            -fx-background-radius: 18px;
            -fx-border-radius: 18px;
            -fx-effect: dropshadow(gaussian, rgba(24, 63, 130, 0.08), 18, 0.18, 0, 6);
            -fx-padding: 20px;
        }

        .session-card, .tutor-card, .reservation-card, .plan-day, .previous-plan-item, .quiz-card, .result-card, .article-card, .category-card,
        .backoffice-feed-card, .backoffice-alert-row {
            -fx-background-color: linear-gradient(to bottom, #ffffff 0%, #f8fbff 100%);
            -fx-border-color: #dce7f8;
            -fx-border-width: 1px;
            -fx-background-radius: 16px;
            -fx-border-radius: 16px;
            -fx-padding: 16px;
        }

        .welcome-title {
            -fx-font-size: 29px;
            -fx-font-weight: bold;
            -fx-text-fill: #183f82;
        }

        .welcome-section, .backoffice-header-block, .conversations-header, .login-brand-copy, .login-fields-stack, .login-field-group, .login-helper-row {
            -fx-padding: 0 0 4px 0;
        }

        .section-title, .subsection-title, .form-title, .plan-title, .day-title, .plan-item-title, .conversations-title, .chat-partner, .quiz-title,
        .result-title, .article-title, .category-name, .session-subject, .seance-title, .reservation-title, .tutor-name, .backoffice-panel-title,
        .backoffice-section-title, .backoffice-mini-title, .backoffice-feed-title {
            -fx-font-size: 18px;
            -fx-font-weight: bold;
            -fx-text-fill: #1a3864;
        }

        .section-title {
            -fx-font-size: 20px;
        }

        .welcome-subtitle, .section-subtitle, .stat-label, .session-time, .seance-info, .seance-description, .reservation-info, .result-date,
        .quiz-info, .quiz-description, .article-date, .article-views, .article-excerpt, .category-count, .plan-item-date, .tutor-subject,
        .tutor-rating, .backoffice-panel-copy, .backoffice-mini-copy, .backoffice-feed-copy, .backoffice-stat-copy {
            -fx-font-size: 13px;
            -fx-text-fill: #6d82a0;
        }

        .stat-value, .session-price, .seance-price, .reservation-price, .progress-percentage, .plan-subtitle, .task-duration, .result-score,
        .article-author, .session-tutor, .seance-tutor, .reservation-tutor, .backoffice-stat-value, .backoffice-alert-text {
            -fx-font-weight: bold;
            -fx-text-fill: #2b5fb7;
        }

        .progress-subject, .backoffice-stat-title, .backoffice-form-label, .form-label {
            -fx-font-size: 13px;
            -fx-font-weight: bold;
            -fx-text-fill: #4a6284;
        }

        .seance-rating, .reservation-rating, .quiz-rating {
            -fx-font-size: 13px;
            -fx-font-weight: bold;
            -fx-text-fill: #f0a84d;
        }

        .search-field, .message-input, .form-field, .backoffice-form-input, .filter-combo, .combo-box-base, .date-picker, .text-area,
        .login-input {
            -fx-background-color: rgba(255, 255, 255, 0.96);
            -fx-control-inner-background: white;
            -fx-border-color: #d5e1f5;
            -fx-border-width: 1px;
            -fx-border-radius: 12px;
            -fx-background-radius: 12px;
            -fx-text-fill: #243c59;
            -fx-prompt-text-fill: #8da0b8;
        }

        .search-field, .message-input, .form-field, .backoffice-form-input, .filter-combo, .combo-box-base, .date-picker {
            -fx-pref-height: 40px;
            -fx-padding: 0 12px;
        }

        .search-field:focused, .message-input:focused, .form-field:focused, .backoffice-form-input:focused, .combo-box-base:focused,
        .date-picker:focused, .text-area:focused, .login-input:focused {
            -fx-border-color: #7fb0ff;
            -fx-effect: dropshadow(gaussian, rgba(17, 67, 140, 0.16), 10, 0.22, 0, 3);
        }

        .contact-button, .session-button, .search-button, .reserve-button, .generate-button, .send-button, .start-quiz-button,
        .read-more-button, .view-button, .backoffice-primary-button, .action-button.primary, .login-primary-button {
            -fx-background-color: linear-gradient(to bottom, #6e97ff 0%, #5a80f4 100%);
            -fx-text-fill: white;
            -fx-font-weight: bold;
            -fx-cursor: hand;
            -fx-background-radius: 12px;
            -fx-effect: dropshadow(gaussian, rgba(23, 52, 110, 0.20), 12, 0.18, 0, 4);
        }

        .contact-button:hover, .session-button:hover, .search-button:hover, .reserve-button:hover, .generate-button:hover, .send-button:hover,
        .start-quiz-button:hover, .read-more-button:hover, .view-button:hover, .backoffice-primary-button:hover, .action-button.primary:hover,
        .login-primary-button:hover {
            -fx-background-color: linear-gradient(to bottom, #7ca2ff 0%, #6388ff 100%);
        }

        .action-button.secondary, .review-button, .like-button, .comment-button, .backoffice-secondary-button {
            -fx-background-color: rgba(231, 239, 251, 0.90);
            -fx-border-color: #d4e0f5;
            -fx-border-width: 1px;
            -fx-text-fill: #294a78;
            -fx-font-weight: bold;
            -fx-background-radius: 12px;
            -fx-border-radius: 12px;
            -fx-cursor: hand;
        }

        .action-button.danger {
            -fx-background-color: #ffe8ec;
            -fx-border-color: #ffcbd3;
            -fx-border-width: 1px;
            -fx-text-fill: #ba4658;
            -fx-font-weight: bold;
            -fx-background-radius: 12px;
            -fx-border-radius: 12px;
            -fx-cursor: hand;
        }

        .action-button.secondary:hover, .review-button:hover, .like-button:hover, .comment-button:hover, .backoffice-secondary-button:hover {
            -fx-background-color: #ecf3ff;
        }

        .action-button.danger:hover {
            -fx-background-color: #ffdce3;
        }

        .profile-form-actions {
            -fx-padding: 6px 0 0 0;
        }

        .profile-save-button {
            -fx-background-color: linear-gradient(to bottom, #6e97ff 0%, #5a80f4 100%);
            -fx-text-fill: white;
            -fx-font-weight: bold;
            -fx-cursor: hand;
            -fx-background-radius: 12px;
            -fx-effect: dropshadow(gaussian, rgba(23, 52, 110, 0.20), 12, 0.18, 0, 4);
        }

        .profile-save-button:hover {
            -fx-background-color: linear-gradient(to bottom, #7ca2ff 0%, #6388ff 100%);
        }

        .profile-reset-button {
            -fx-background-color: rgba(231, 239, 251, 0.90);
            -fx-border-color: #d4e0f5;
            -fx-border-width: 1px;
            -fx-text-fill: #294a78;
            -fx-font-weight: bold;
            -fx-background-radius: 12px;
            -fx-border-radius: 12px;
            -fx-cursor: hand;
        }

        .profile-reset-button:hover {
            -fx-background-color: #ecf3ff;
        }

        .profile-delete-button {
            -fx-background-color: #ffe8ec;
            -fx-border-color: #ffcbd3;
            -fx-border-width: 1px;
            -fx-text-fill: #ba4658;
            -fx-font-weight: bold;
            -fx-background-radius: 12px;
            -fx-border-radius: 12px;
            -fx-cursor: hand;
        }

        .profile-delete-button:hover {
            -fx-background-color: #ffdce3;
        }

        .progress-bar > .track {
            -fx-background-color: #dfe8f8;
            -fx-background-radius: 999px;
        }

        .progress-bar > .bar {
            -fx-background-color: linear-gradient(to right, #6e97ff 0%, #5a80f4 100%);
            -fx-background-radius: 999px;
        }

        .reservation-tabs .tab-header-area .tab-header-background {
            -fx-background-color: transparent;
        }

        .reservation-tabs .tab {
            -fx-background-color: rgba(255, 255, 255, 0.88);
            -fx-border-color: #dbe5f6;
            -fx-border-width: 1px 1px 0 1px;
            -fx-background-radius: 14px 14px 0 0;
            -fx-border-radius: 14px 14px 0 0;
        }

        .reservation-tabs .tab:selected {
            -fx-background-color: linear-gradient(to bottom, #6e97ff 0%, #5a80f4 100%);
            -fx-border-color: #6a91f2;
        }

        .reservation-tabs .tab .tab-label {
            -fx-font-size: 13px;
            -fx-font-weight: bold;
            -fx-text-fill: #264b87;
        }

        .reservation-tabs .tab:selected .tab-label {
            -fx-text-fill: white;
        }

        .reservation-status, .result-grade {
            -fx-font-size: 12px;
            -fx-font-weight: bold;
            -fx-padding: 5px 10px;
            -fx-background-radius: 999px;
        }

        .confirmed, .good {
            -fx-background-color: #e6eeff;
            -fx-text-fill: #3158a8;
        }

        .pending {
            -fx-background-color: #fff1d8;
            -fx-text-fill: #a26a08;
        }

        .completed, .excellent {
            -fx-background-color: #e2f5e8;
            -fx-text-fill: #1f7a45;
        }

        .conversations-list-view, .messages-scroll, .backoffice-table {
            -fx-background-color: transparent;
            -fx-border-color: transparent;
        }

        .conversations-list-view .list-cell {
            -fx-background-color: transparent;
            -fx-border-color: transparent;
        }

        .conversations-list-view .list-cell.conversation-cell {
            -fx-padding: 12px 14px;
            -fx-background-radius: 14px;
            -fx-font-size: 13px;
            -fx-font-weight: bold;
            -fx-text-fill: #294365;
        }

        .conversations-list-view .list-cell.conversation-cell:selected {
            -fx-background-color: #edf4ff;
            -fx-text-fill: #214f93;
        }

        .chat-header, .message-input-area {
            -fx-background-color: rgba(248, 251, 255, 0.94);
            -fx-border-color: #dbe5f6;
        }

        .message-bubble.message-sent {
            -fx-background-color: linear-gradient(to bottom, #6e97ff 0%, #5a80f4 100%);
            -fx-background-radius: 16px;
            -fx-padding: 10px 14px;
        }

        .message-bubble.message-received {
            -fx-background-color: #eef4ff;
            -fx-background-radius: 16px;
            -fx-padding: 10px 14px;
        }

        .message-text.message-text-sent {
            -fx-fill: white;
        }

        .message-text.message-text-received {
            -fx-fill: #243c59;
        }

        .backoffice-table {
            -fx-border-color: #dbe5f6;
            -fx-border-width: 1px;
            -fx-border-radius: 14px;
            -fx-background-radius: 14px;
        }

        .backoffice-table .column-header-background, .backoffice-table .filler {
            -fx-background-color: linear-gradient(to bottom, #f7faff 0%, #eef4ff 100%);
        }

        .backoffice-table .column-header .label {
            -fx-text-fill: #3f587a;
            -fx-font-size: 12px;
            -fx-font-weight: bold;
        }

        .backoffice-table .table-row-cell:selected {
            -fx-background-color: #eaf1ff;
        }

        .backoffice-feedback.success {
            -fx-background-color: #e2f5e8;
            -fx-text-fill: #1d7a46;
            -fx-background-radius: 12px;
            -fx-padding: 10px 12px;
        }

        .backoffice-feedback.error {
            -fx-background-color: #ffe8ec;
            -fx-text-fill: #c43b49;
            -fx-background-radius: 12px;
            -fx-padding: 10px 12px;
        }

        .login-root {
            -fx-background-color:
                radial-gradient(center 68% 20%, radius 58%, rgba(126, 182, 255, 0.22) 0%, rgba(126, 182, 255, 0.0) 100%),
                linear-gradient(to bottom right, #2d61b9 0%, #1f4f9f 56%, #183f82 100%);
        }

        .login-brand-block {
            -fx-padding: 24px 30px 24px 8px;
        }

        .login-panel {
            -fx-padding: 18px 62px 18px 28px;
        }

        .login-form-card {
            -fx-background-color: transparent;
            -fx-border-color: transparent;
            -fx-padding: 28px;
            -fx-spacing: 18px;
            -fx-effect: none;
        }

        .login-brand-title {
            -fx-font-size: 34px;
            -fx-font-weight: bold;
            -fx-text-fill: #204f93;
        }

        .login-brand-subtitle, .login-copy, .login-helper-text {
            -fx-font-size: 13px;
            -fx-text-fill: rgba(230, 239, 255, 0.84);
        }

        .login-eyebrow, .login-input-label {
            -fx-font-size: 12px;
            -fx-font-weight: bold;
            -fx-text-fill: rgba(245, 248, 255, 0.92);
        }

        .login-input {
            -fx-pref-width: 320px;
            -fx-pref-height: 44px;
            -fx-padding: 0 14px;
            -fx-highlight-fill: #5f88ff;
            -fx-highlight-text-fill: white;
        }

        .login-heading {
            -fx-font-size: 32px;
            -fx-font-weight: bold;
            -fx-text-fill: white;
        }

        .login-options-row {
            -fx-padding: 2px 0 6px 0;
        }

        .login-link {
            -fx-padding: 0;
            -fx-border-color: transparent;
            -fx-font-size: 11px;
            -fx-text-fill: rgba(211, 225, 255, 0.96);
        }

        .login-link:hover {
            -fx-text-fill: white;
        }

        .login-divider .line {
            -fx-border-color: rgba(255, 255, 255, 0.16);
            -fx-border-width: 1px 0 0 0;
        }

        .login-check {
            -fx-font-size: 11px;
            -fx-font-weight: bold;
            -fx-text-fill: rgba(234, 241, 255, 0.88);
        }

        .login-check .box {
            -fx-background-color: rgba(255, 255, 255, 0.08);
            -fx-border-color: rgba(255, 255, 255, 0.55);
            -fx-border-radius: 4px;
            -fx-background-radius: 4px;
            -fx-padding: 3px;
        }

        .login-check:selected .box {
            -fx-background-color: rgba(255, 255, 255, 0.18);
        }

        .login-check:selected .mark {
            -fx-background-color: white;
        }

        .login-message-label {
            -fx-font-size: 12px;
            -fx-padding: 2px 0 0 0;
        }

        .login-message-label.error {
            -fx-text-fill: #ffd9dc;
        }

        .login-message-label.success {
            -fx-text-fill: #d6ffe1;
        }
        """;

    private static final String STYLESHEET_URI = createStylesheetUri();

    private UiTheme() {
    }

    public static void apply(Scene scene) {
        if (scene == null) {
            return;
        }
        if (!scene.getStylesheets().contains(STYLESHEET_URI)) {
            scene.getStylesheets().add(STYLESHEET_URI);
        }
    }

    private static String createStylesheetUri() {
        try {
            Path tempFile = Files.createTempFile("fahamni-theme-", ".css");
            Files.writeString(tempFile, STYLES, StandardCharsets.UTF_8);
            tempFile.toFile().deleteOnExit();
            return tempFile.toUri().toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize the embedded UI theme.", exception);
        }
    }
}

