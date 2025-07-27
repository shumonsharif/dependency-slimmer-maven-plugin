package com.mulesoft.tools.maven.utils;

import com.mulesoft.tools.maven.config.DependencyFilter;
import com.mulesoft.tools.maven.config.SlimmingConfiguration;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

import java.util.*;

public class DependencyAnalyzer {
    private final MavenProject project;
    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession repositorySession;
    private final List<RemoteRepository> remoteRepositories;
    private final Log log;
    private final boolean verbose;

    public DependencyAnalyzer(MavenProject project, RepositorySystem repositorySystem,
                             RepositorySystemSession repositorySession,
                             List<RemoteRepository> remoteRepositories, Log log, boolean verbose) {
        this.project = project;
        this.repositorySystem = repositorySystem;
        this.repositorySession = repositorySession;
        this.remoteRepositories = remoteRepositories;
        this.log = log;
        this.verbose = verbose;
    }

    public Set<Artifact> analyzeDependencies(SlimmingConfiguration config) throws DependencyCollectionException {
        Set<Artifact> excludedArtifacts = new HashSet<>();
        Set<Artifact> includedArtifacts = new HashSet<>();
        
        // Get all project dependencies
        Set<Artifact> allDependencies = project.getArtifacts();
        
        if (verbose) {
            log.info("Analyzing " + allDependencies.size() + " project dependencies...");
        }

        // First pass: identify directly excluded/included artifacts
        for (Artifact artifact : allDependencies) {
            if (shouldExcludeArtifact(artifact, config)) {
                excludedArtifacts.add(artifact);
                if (verbose) {
                    log.info("Direct exclusion: " + getArtifactKey(artifact));
                }
            } else if (shouldIncludeArtifact(artifact, config)) {
                includedArtifacts.add(artifact);
                if (verbose) {
                    log.info("Direct inclusion: " + getArtifactKey(artifact));
                }
            }
        }

        // Second pass: analyze transitive dependencies of excluded artifacts
        Set<Artifact> transitiveExclusions = new HashSet<>();
        for (Artifact excludedArtifact : excludedArtifacts) {
            Set<Artifact> transitives = getTransitiveDependencies(excludedArtifact);
            transitiveExclusions.addAll(transitives);
            
            if (verbose && !transitives.isEmpty()) {
                log.info("Transitive exclusions for " + getArtifactKey(excludedArtifact) + ":");
                for (Artifact transitive : transitives) {
                    log.info("  -> " + getArtifactKey(transitive));
                }
            }
        }

        // Third pass: handle include-only mode
        if (!config.getIncludes().isEmpty()) {
            // In include-only mode, exclude everything not explicitly included
            for (Artifact artifact : allDependencies) {
                if (!includedArtifacts.contains(artifact) && !isTransitiveOfIncluded(artifact, includedArtifacts)) {
                    excludedArtifacts.add(artifact);
                    if (verbose) {
                        log.info("Include-only exclusion: " + getArtifactKey(artifact));
                    }
                }
            }
        }

        // Combine direct and transitive exclusions
        excludedArtifacts.addAll(transitiveExclusions);

        // Remove any artifacts that are explicitly included (includes override excludes)
        excludedArtifacts.removeAll(includedArtifacts);

        return excludedArtifacts;
    }

    private boolean shouldExcludeArtifact(Artifact artifact, SlimmingConfiguration config) {
        for (DependencyFilter exclude : config.getExcludes()) {
            if (matchesFilter(artifact, exclude)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldIncludeArtifact(Artifact artifact, SlimmingConfiguration config) {
        for (DependencyFilter include : config.getIncludes()) {
            if (matchesFilter(artifact, include)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesFilter(Artifact artifact, DependencyFilter filter) {
        return matchesPattern(artifact.getGroupId(), filter.getGroupId()) &&
               matchesPattern(artifact.getArtifactId(), filter.getArtifactId()) &&
               matchesPattern(artifact.getVersion(), filter.getVersion()) &&
               matchesPattern(artifact.getType(), filter.getType());
    }

    private boolean matchesPattern(String value, String pattern) {
        if (pattern == null || pattern.isEmpty() || "*".equals(pattern)) {
            return true;
        }

        if (pattern.contains("*")) {
            // Convert wildcard to regex
            String regex = pattern.replace(".", "\\.")
                                 .replace("*", ".*");
            return value.matches(regex);
        }

        return value.equals(pattern);
    }

    private Set<Artifact> getTransitiveDependencies(Artifact rootArtifact) {
        Set<Artifact> transitives = new HashSet<>();
        
        try {
            // Create Aether artifact from Maven artifact
            org.eclipse.aether.artifact.Artifact aetherArtifact = new DefaultArtifact(
                rootArtifact.getGroupId(),
                rootArtifact.getArtifactId(),
                rootArtifact.getClassifier(),
                rootArtifact.getType(),
                rootArtifact.getVersion()
            );

            // Collect dependencies
            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRoot(new Dependency(aetherArtifact, "compile"));
            collectRequest.setRepositories(remoteRepositories);

            CollectResult collectResult = repositorySystem.collectDependencies(repositorySession, collectRequest);
            
            // Extract all transitive dependencies
            PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
            collectResult.getRoot().accept(nlg);
            
            for (DependencyNode node : nlg.getNodes()) {
                if (node.getDependency() != null && node != collectResult.getRoot()) {
                    org.eclipse.aether.artifact.Artifact dep = node.getDependency().getArtifact();
                    
                    // Find corresponding Maven artifact in project dependencies
                    for (Artifact projectArtifact : project.getArtifacts()) {
                        if (projectArtifact.getGroupId().equals(dep.getGroupId()) &&
                            projectArtifact.getArtifactId().equals(dep.getArtifactId()) &&
                            projectArtifact.getVersion().equals(dep.getVersion())) {
                            transitives.add(projectArtifact);
                            break;
                        }
                    }
                }
            }
            
        } catch (DependencyCollectionException e) {
            log.warn("Could not resolve transitive dependencies for " + getArtifactKey(rootArtifact) + ": " + e.getMessage());
        }
        
        return transitives;
    }

    private boolean isTransitiveOfIncluded(Artifact artifact, Set<Artifact> includedArtifacts) {
        // Check if this artifact is a transitive dependency of any included artifact
        for (Artifact included : includedArtifacts) {
            Set<Artifact> transitives = getTransitiveDependencies(included);
            if (transitives.contains(artifact)) {
                return true;
            }
        }
        return false;
    }

    private String getArtifactKey(Artifact artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
    }
}
