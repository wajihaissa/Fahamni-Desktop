package tn.esprit.fahamni.Models;

import java.time.LocalDateTime;

public class AdminCustomizationRequestItem {

    private final int requestId;
    private final int sessionId;
    private final String sessionSubject;
    private final String tutor;
    private final String roomName;
    private final String status;
    private final String requestDate;
    private final String reviewDate;
    private final String summary;
    private final LocalDateTime requestDateTime;
    private final SessionRoomCustomizationRequest request;

    public AdminCustomizationRequestItem(
        int requestId,
        int sessionId,
        String sessionSubject,
        String tutor,
        String roomName,
        String status,
        String requestDate,
        String reviewDate,
        String summary,
        LocalDateTime requestDateTime,
        SessionRoomCustomizationRequest request
    ) {
        this.requestId = requestId;
        this.sessionId = sessionId;
        this.sessionSubject = sessionSubject;
        this.tutor = tutor;
        this.roomName = roomName;
        this.status = status;
        this.requestDate = requestDate;
        this.reviewDate = reviewDate;
        this.summary = summary;
        this.requestDateTime = requestDateTime;
        this.request = request;
    }

    public int getRequestId() {
        return requestId;
    }

    public int getSessionId() {
        return sessionId;
    }

    public String getSessionSubject() {
        return sessionSubject;
    }

    public String getTutor() {
        return tutor;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getStatus() {
        return status;
    }

    public String getRequestDate() {
        return requestDate;
    }

    public String getReviewDate() {
        return reviewDate;
    }

    public String getSummary() {
        return summary;
    }

    public LocalDateTime getRequestDateTime() {
        return requestDateTime;
    }

    public SessionRoomCustomizationRequest getRequest() {
        return request;
    }
}
