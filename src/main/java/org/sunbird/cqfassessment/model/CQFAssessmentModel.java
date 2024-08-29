package org.sunbird.cqfassessment.model;

/**
 * @author mahesh.vakkund
 */
public class CQFAssessmentModel {
    private final String userId;
    private final String assessmentIdentifier;
    private final String contentId;
    private final String versionKey;

    public CQFAssessmentModel(String userId, String assessmentIdentifier, String contentId, String versionKey) {
        this.userId = userId;
        this.assessmentIdentifier = assessmentIdentifier;
        this.contentId = contentId;
        this.versionKey = versionKey;
    }

    public String getUserId() {
        return userId;
    }

    public String getAssessmentIdentifier() {
        return assessmentIdentifier;
    }

    public String getContentId() {
        return contentId;
    }

    public String getVersionKey() {
        return versionKey;
    }
}
