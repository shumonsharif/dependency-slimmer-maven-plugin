package com.mulesoft.tools.maven.config;

public class DependencyFilter {
    private String groupId;
    private String artifactId;
    private String version;
    private String classifier;
    private String type = "jar";

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getClassifier() {
        return classifier;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return String.format("%s:%s:%s:%s:%s", 
            groupId != null ? groupId : "*",
            artifactId != null ? artifactId : "*",
            type != null ? type : "*",
            classifier != null ? classifier : "",
            version != null ? version : "*"
        );
    }
}
