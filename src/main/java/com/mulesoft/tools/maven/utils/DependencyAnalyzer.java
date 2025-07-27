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
import java.util.stream.Collectors;

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
        Set<Artifact> allDependencies = project.getArtifacts();
        if (verbose) {
            log.info("Analyzing " + allDependencies.size() + " project dependencies...");
        }

        boolean hasIncludes = !config.getIncludes().isEmpty();
        boolean hasExcludes = !config.getExcludes().isEmpty();

        if (hasIncludes && hasExcludes) {
            if (verbose) log.info("Running in mixed-mode (includes and excludes).");
            return analyzeMixedMode(config, allDependencies);
        } else if (hasIncludes) {
            if (verbose) log.info("Running in include-only mode.");
            return analyzeIncludeOnlyMode(config, allDependencies);
        } else if (hasExcludes) {
            if (verbose) log.info("Running in exclude-only mode.");
            return analyzeExcludeOnlyMode(config, allDependencies);
        } else {
            return Collections.emptySet();
        }
    }

    private Set<Artifact> analyzeMixedMode(SlimmingConfiguration config, Set<Artifact> allDependencies) {
        // Get all artifacts that would be excluded
        Set<Artifact> directlyExcluded = getMatchingArtifacts(allDependencies, config.getExcludes());
        if (verbose && !directlyExcluded.isEmpty()) {
            log.info("Directly excluded artifacts: " + getArtifactKeys(directlyExcluded));
        }
        Set<Artifact> transitivelyExcluded = getAllTransitiveDependencies(directlyExcluded);
        Set<Artifact> allToExclude = new HashSet<>(directlyExcluded);
        allToExclude.addAll(transitivelyExcluded);

        // Get all artifacts that must be included
        Set<Artifact> directlyIncluded = getMatchingArtifacts(allDependencies, config.getIncludes());
        if (verbose && !directlyIncluded.isEmpty()) {
            log.info("Directly included artifacts: " + getArtifactKeys(directlyIncluded));
        }
        Set<Artifact> transitivelyIncluded = getAllTransitiveDependencies(directlyIncluded);
        Set<Artifact> allToInclude = new HashSet<>(directlyIncluded);
        allToInclude.addAll(transitivelyIncluded);
        if (verbose && !allToInclude.isEmpty()) {
            log.info("Including artifacts and their transitives: " + getArtifactKeys(allToInclude));
        }

        // Excludes are removed if they are part of an include set (inclusions take precedence)
        allToExclude.removeAll(allToInclude);
        if (verbose) {
            log.info("Final exclusion set (mixed-mode): " + getArtifactKeys(allToExclude));
        }
        return allToExclude;
    }

    private Set<Artifact> analyzeIncludeOnlyMode(SlimmingConfiguration config, Set<Artifact> allDependencies) {
        Set<Artifact> directlyIncluded = getMatchingArtifacts(allDependencies, config.getIncludes());
        if (verbose && !directlyIncluded.isEmpty()) {
            log.info("Directly included artifacts: " + getArtifactKeys(directlyIncluded));
        }
        Set<Artifact> transitivelyIncluded = getAllTransitiveDependencies(directlyIncluded);
        Set<Artifact> keeperSet = new HashSet<>(directlyIncluded);
        keeperSet.addAll(transitivelyIncluded);
        if (verbose && !keeperSet.isEmpty()) {
            log.info("Keeping artifacts and their transitives: " + getArtifactKeys(keeperSet));
        }

        Set<Artifact> finalExclusions = new HashSet<>(allDependencies);
        finalExclusions.removeAll(keeperSet);
        if (verbose) {
            log.info("Final exclusion set (include-only): " + getArtifactKeys(finalExclusions));
        }
        return finalExclusions;
    }

    private Set<Artifact> analyzeExcludeOnlyMode(SlimmingConfiguration config, Set<Artifact> allDependencies) {
        Set<Artifact> directlyExcluded = getMatchingArtifacts(allDependencies, config.getExcludes());
        if (verbose && !directlyExcluded.isEmpty()) {
            log.info("Directly excluded artifacts: " + getArtifactKeys(directlyExcluded));
        }
        Set<Artifact> transitivelyExcluded = getAllTransitiveDependencies(directlyExcluded);
        Set<Artifact> finalExclusions = new HashSet<>(directlyExcluded);
        finalExclusions.addAll(transitivelyExcluded);
        if (verbose) {
            log.info("Final exclusion set (exclude-only): " + getArtifactKeys(finalExclusions));
        }
        return finalExclusions;
    }

    private Set<Artifact> getMatchingArtifacts(Set<Artifact> allDependencies, List<DependencyFilter> filters) {
        Set<Artifact> matching = new HashSet<>();
        for (Artifact artifact : allDependencies) {
            for (DependencyFilter filter : filters) {
                if (matchesFilter(artifact, filter)) {
                    matching.add(artifact);
                    break;
                }
            }
        }
        return matching;
    }

    private Set<Artifact> getAllTransitiveDependencies(Set<Artifact> rootArtifacts) {
        Set<Artifact> allTransitives = new HashSet<>();
        for (Artifact root : rootArtifacts) {
            Set<Artifact> transitives = getTransitiveDependencies(root);
            allTransitives.addAll(transitives);
            if (verbose && !transitives.isEmpty()) {
                log.info("Transitive dependencies for " + getArtifactKey(root) + ": " + getArtifactKeys(transitives));
            }
        }
        return allTransitives;
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
            org.eclipse.aether.artifact.Artifact aetherArtifact = new DefaultArtifact(
                rootArtifact.getGroupId(),
                rootArtifact.getArtifactId(),
                rootArtifact.getClassifier(),
                rootArtifact.getType(),
                rootArtifact.getVersion()
            );

            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRoot(new Dependency(aetherArtifact, "compile"));
            collectRequest.setRepositories(remoteRepositories);

            CollectResult collectResult = repositorySystem.collectDependencies(repositorySession, collectRequest);
            
            PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
            collectResult.getRoot().accept(nlg);
            
            for (DependencyNode node : nlg.getNodes()) {
                if (node.getDependency() != null && node != collectResult.getRoot()) {
                    org.eclipse.aether.artifact.Artifact dep = node.getDependency().getArtifact();
                    
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

    private String getArtifactKey(Artifact artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
    }

    private String getArtifactKeys(Set<Artifact> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            return "[]";
        }
        return artifacts.stream().map(this::getArtifactKey).collect(Collectors.joining(", "));
    }
}
