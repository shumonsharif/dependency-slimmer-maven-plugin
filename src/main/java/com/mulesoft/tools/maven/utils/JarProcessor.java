package com.mulesoft.tools.maven.utils;

import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.logging.Log;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class JarProcessor {
    private final Log log;
    private final boolean verbose;

    public JarProcessor(Log log, boolean verbose) {
        this.log = log;
        this.verbose = verbose;
    }

    public void processJar(File artifact, Set<Artifact> excludedArtifacts) throws IOException {
        File tempFile = new File(artifact.getParentFile(), "temp-" + artifact.getName());
        
        try (JarFile sourceJar = new JarFile(artifact);
             JarOutputStream destJar = new JarOutputStream(new FileOutputStream(tempFile))) {
            
            int totalEntries = 0;
            int excludedEntries = 0;
            long totalSize = 0;
            long excludedSize = 0;

            for (JarEntry entry : java.util.Collections.list(sourceJar.entries())) {
                try {
                    totalEntries++;
                    totalSize += entry.getSize();
                    
                    if (shouldSkipEntry(entry, excludedArtifacts)) {
                        excludedEntries++;
                        excludedSize += entry.getSize();
                        if (verbose) {
                            log.info("Excluding: " + entry.getName());
                        }
                        continue;
                    }

                    // Copy entry to new jar
                    try (InputStream inputStream = sourceJar.getInputStream(entry)) {
                        destJar.putNextEntry(new JarEntry(entry.getName()));
                        IOUtils.copy(inputStream, destJar);
                        destJar.closeEntry();
                    }
                } catch (IOException e) {
                    log.error("Error processing entry: " + entry.getName(), e);
                }
            }

            log.info(String.format("Processed %d entries, excluded %d entries", 
                totalEntries, excludedEntries));
            log.info(String.format("Excluded %s of content", formatBytes(excludedSize)));
        }

        // Replace original with processed jar
        if (!artifact.delete()) {
            throw new IOException("Could not delete original artifact: " + artifact);
        }
        if (!tempFile.renameTo(artifact)) {
            throw new IOException("Could not rename processed artifact");
        }
    }

    public void analyzeDependencies(File artifact, Set<Artifact> excludedArtifacts) throws IOException {
        Set<String> includedDeps = new HashSet<>();
        Set<String> excludedDeps = new HashSet<>();
        long totalSize = 0;
        long excludedSize = 0;
        
        try (JarFile sourceJar = new JarFile(artifact)) {
            for (JarEntry entry : java.util.Collections.list(sourceJar.entries())) {
                totalSize += entry.getSize();
                
                if (shouldSkipEntry(entry, excludedArtifacts)) {
                    excludedDeps.add(extractDependencyName(entry.getName()));
                    excludedSize += entry.getSize();
                } else {
                    includedDeps.add(extractDependencyName(entry.getName()));
                }
            }
        }

        log.info("=== JAR Content Analysis ===");
        log.info("Total artifact size: " + formatBytes(totalSize));
        log.info("Size to be excluded: " + formatBytes(excludedSize));
        log.info("Estimated size reduction: " + String.format("%.1f%%", (excludedSize * 100.0 / totalSize)));
        
        log.info("\nDependencies to be INCLUDED:");
        includedDeps.stream().sorted().forEach(dep -> log.info("  + " + dep));
        
        log.info("\nDependencies to be EXCLUDED:");
        excludedDeps.stream().sorted().forEach(dep -> log.info("  - " + dep));
    }

    private boolean shouldSkipEntry(JarEntry entry, Set<Artifact> excludedArtifacts) {
        String entryName = entry.getName();
        
        // Always preserve manifest and critical files
        if (entryName.startsWith("META-INF/MANIFEST.MF") ||
            entryName.startsWith("META-INF/maven/") ||
            entryName.equals("META-INF/") ||
            entryName.startsWith("BOOT-INF/classes/") ||
            entryName.startsWith("WEB-INF/classes/") ||
            entryName.startsWith("org/springframework/boot/loader/")) {
            return false;
        }

        return PatternMatcher.shouldExclude(entryName, excludedArtifacts);
    }

    private String extractDependencyName(String entryPath) {
        if (entryPath.contains("/lib/")) {
            String fileName = entryPath.substring(entryPath.lastIndexOf("/") + 1);
            if (fileName.endsWith(".jar")) {
                // Extract artifact name from jar file name
                // Example: langchain4j-core-0.35.0.jar -> langchain4j-core
                String nameWithoutExtension = fileName.substring(0, fileName.lastIndexOf(".jar"));
                int lastDash = nameWithoutExtension.lastIndexOf("-");
                if (lastDash > 0) {
                    return nameWithoutExtension.substring(0, lastDash);
                }
                return nameWithoutExtension;
            }
        }
        return entryPath.contains("/") ? entryPath.substring(0, entryPath.indexOf("/")) : entryPath;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
