package com.mulesoft.tools.maven.config;

import java.util.ArrayList;
import java.util.List;

public class SlimmingConfiguration {
    private List<DependencyFilter> includes = new ArrayList<>();
    private List<DependencyFilter> excludes = new ArrayList<>();
    private boolean preserveManifest = true;
    private boolean removeEmptyDirectories = true;

    public List<DependencyFilter> getIncludes() {
        return includes;
    }

    public void setIncludes(List<DependencyFilter> includes) {
        this.includes = includes != null ? includes : new ArrayList<>();
    }

    public List<DependencyFilter> getExcludes() {
        return excludes;
    }

    public void setExcludes(List<DependencyFilter> excludes) {
        this.excludes = excludes != null ? excludes : new ArrayList<>();
    }

    public boolean isPreserveManifest() {
        return preserveManifest;
    }

    public void setPreserveManifest(boolean preserveManifest) {
        this.preserveManifest = preserveManifest;
    }

    public boolean isRemoveEmptyDirectories() {
        return removeEmptyDirectories;
    }

    public void setRemoveEmptyDirectories(boolean removeEmptyDirectories) {
        this.removeEmptyDirectories = removeEmptyDirectories;
    }
}
