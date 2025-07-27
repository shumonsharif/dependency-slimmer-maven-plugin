package com.mulesoft.tools.maven;

import com.mulesoft.tools.maven.config.*;
import com.mulesoft.tools.maven.utils.JarProcessor;
import com.mulesoft.tools.maven.utils.DependencyAnalyzer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.artifact.Artifact;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Mojo(
    name = "slim",
    defaultPhase = LifecyclePhase.PACKAGE,
    requiresDependencyResolution = ResolutionScope.RUNTIME
)
public class DependencySlimmerMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Component
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repositorySession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepositories;

    @Parameter
    private List<DependencyFilter> includes = new ArrayList<>();

    @Parameter
    private List<DependencyFilter> excludes = new ArrayList<>();

    @Parameter(property = "slim.preserveManifest", defaultValue = "true")
    private boolean preserveManifest;

    @Parameter(property = "slim.removeEmptyDirectories", defaultValue = "true")
    private boolean removeEmptyDirectories;

    @Parameter(property = "slim.profile")
    private String profile;

    @Parameter(property = "slim.enabled", defaultValue = "true")
    private boolean enabled;

    @Parameter(property = "slim.verbose", defaultValue = "false")
    private boolean verbose;

    @Parameter(property = "slim.dryRun", defaultValue = "false")
    private boolean dryRun;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!enabled) {
            getLog().info("Dependency slimming is disabled");
            return;
        }

        File artifact = project.getArtifact().getFile();
        if (artifact == null || !artifact.exists()) {
            throw new MojoExecutionException("Project artifact not found: " + artifact);
        }

        try {
            // Initialize configuration
            SlimmingConfiguration config = initializeConfiguration();
            
            // Analyze dependencies to build exclusion set
            DependencyAnalyzer analyzer = new DependencyAnalyzer(
                project, repositorySystem, repositorySession, remoteRepositories, getLog(), verbose);
            
            Set<Artifact> dependenciesToExclude = analyzer.analyzeDependencies(config);
            
            if (verbose) {
                getLog().info("=== Dependency Analysis Results ===");
                getLog().info("Total project dependencies: " + project.getArtifacts().size());
                getLog().info("Dependencies to exclude: " + dependenciesToExclude.size());
                for (Artifact dep : dependenciesToExclude) {
                    getLog().info("  - " + dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion());
                }
            }
            
            // Process the JAR file
            JarProcessor processor = new JarProcessor(getLog(), verbose);
            long originalSize = artifact.length();
            
            if (dryRun) {
                getLog().info("DRY RUN: Would process " + artifact.getName());
                processor.analyzeDependencies(artifact, dependenciesToExclude);
            } else {
                processor.processJar(artifact, dependenciesToExclude);
                long newSize = artifact.length();
                long saved = originalSize - newSize;
                
                getLog().info(String.format("Slimming complete! Reduced size by %s (%.1f%% reduction)",
                    formatBytes(saved), (saved * 100.0 / originalSize)));
            }
            
        } catch (Exception e) {
            throw new MojoExecutionException("Error during dependency slimming", e);
        }
    }

    private SlimmingConfiguration initializeConfiguration() throws MojoExecutionException {
        SlimmingConfiguration config = new SlimmingConfiguration();
        config.setIncludes(includes);
        config.setExcludes(excludes);
        config.setPreserveManifest(preserveManifest);
        config.setRemoveEmptyDirectories(removeEmptyDirectories);

        // Apply predefined profile if specified
        if (profile != null && !profile.trim().isEmpty()) {
            SlimmingProfile profileConfig = SlimmingProfile.getProfile(profile);
            if (profileConfig == null) {
                throw new MojoExecutionException("Unknown slimming profile: " + profile);
            }
            config = profileConfig.applyTo(config);
            getLog().info("Applied slimming profile: " + profile);
        }

        // Validate configuration
        if (config.getExcludes().isEmpty() && config.getIncludes().isEmpty()) {
            getLog().warn("No includes or excludes configured. No slimming will be performed.");
        }

        return config;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
