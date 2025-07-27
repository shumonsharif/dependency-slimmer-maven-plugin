package com.mulesoft.tools.maven.utils;

import org.apache.maven.artifact.Artifact;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;
import java.util.regex.Pattern;

public class PatternMatcher {
    
    public static boolean shouldExclude(String jarEntryPath, Set<Artifact> excludedArtifacts) {
        if (excludedArtifacts == null || excludedArtifacts.isEmpty()) {
            return false;
        }

        // Extract artifact information from jar entry path
        String artifactInfo = extractArtifactInfo(jarEntryPath);
        
        // Check if this entry belongs to any excluded artifact
        for (Artifact excludedArtifact : excludedArtifacts) {
            if (belongsToArtifact(artifactInfo, excludedArtifact)) {
                return true;
            }
        }
        
        return false;
    }

    private static String extractArtifactInfo(String jarEntryPath) {
        // Handle different jar entry formats
        // Examples:
        // BOOT-INF/lib/langchain4j-core-0.35.0.jar
        // BOOT-INF/lib/microsoft-cognitiveservices-speech-1.24.2.jar
        // WEB-INF/lib/hadoop-common-3.3.4.jar
        
        if (jarEntryPath.contains("/lib/")) {
            String libPath = jarEntryPath.substring(jarEntryPath.indexOf("/lib/") + 5);
            if (libPath.endsWith(".jar")) {
                return libPath.substring(0, libPath.length() - 4);
            }
        }
        
        return jarEntryPath;
    }

    private static boolean belongsToArtifact(String jarFileName, Artifact artifact) {
        if (StringUtils.isEmpty(jarFileName)) {
            return false;
        }

        // Build expected jar name patterns
        String basePattern = artifact.getArtifactId() + "-" + artifact.getVersion();
        String classifierPattern = artifact.getClassifier() != null ? 
            artifact.getArtifactId() + "-" + artifact.getVersion() + "-" + artifact.getClassifier() :
            null;

        // Check exact matches
        if (jarFileName.equals(basePattern) || 
            (classifierPattern != null && jarFileName.equals(classifierPattern))) {
            return true;
        }

        // Check if jar name starts with artifact pattern (handles cases with additional suffixes)
        if (jarFileName.startsWith(basePattern + "-") || jarFileName.startsWith(basePattern + ".")) {
            return true;
        }

        if (classifierPattern != null && 
            (jarFileName.startsWith(classifierPattern + "-") || jarFileName.startsWith(classifierPattern + "."))) {
            return true;
        }

        // Fallback: check if artifactId is contained in the jar name
        // This handles cases where versioning schemes don't match exactly
        return jarFileName.contains(artifact.getArtifactId());
    }
}
