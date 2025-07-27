package com.mulesoft.tools.maven.config;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SlimmingProfile {
    private static final Map<String, SlimmingProfile> PROFILES = new HashMap<>();
    
    static {
        // Ollama-only profile
        SlimmingProfile ollamaProfile = new SlimmingProfile("ollama-only");
        ollamaProfile.addInclude("dev.langchain4j", "langchain4j-core", null);
        ollamaProfile.addInclude("dev.langchain4j", "langchain4j-ollama", null);
        ollamaProfile.addExclude("com.microsoft.*", "*", null);
        ollamaProfile.addExclude("com.amazon.*", "*", null);
        ollamaProfile.addExclude("org.apache.hadoop.*", "*", null);
        ollamaProfile.addExclude("org.apache.tika.*", "*", null);
        PROFILES.put("ollama-only", ollamaProfile);

        // OpenAI-only profile
        SlimmingProfile openaiProfile = new SlimmingProfile("openai-only");
        openaiProfile.addInclude("dev.langchain4j", "langchain4j-core", null);
        openaiProfile.addInclude("dev.langchain4j", "langchain4j-open-ai", null);
        openaiProfile.addExclude("dev.langchain4j", "langchain4j-ollama", null);
        openaiProfile.addExclude("com.microsoft.*", "*", null);
        openaiProfile.addExclude("com.amazon.*", "*", null);
        openaiProfile.addExclude("org.apache.hadoop.*", "*", null);
        PROFILES.put("openai-only", openaiProfile);

        // Minimal profile - removes most heavy dependencies
        SlimmingProfile minimalProfile = new SlimmingProfile("minimal");
        minimalProfile.addExclude("org.apache.hadoop.*", "*", null);
        minimalProfile.addExclude("org.apache.tika.*", "*", null);
        minimalProfile.addExclude("com.microsoft.*", "*", null);
        minimalProfile.addExclude("com.amazon.*", "*", null);
        minimalProfile.addExclude("org.apache.spark.*", "*", null);
        PROFILES.put("minimal", minimalProfile);
    }

    private final String name;
    private final SlimmingConfiguration configuration;

    private SlimmingProfile(String name) {
        this.name = name;
        this.configuration = new SlimmingConfiguration();
    }

    private void addInclude(String groupId, String artifactId, String version) {
        DependencyFilter filter = new DependencyFilter();
        filter.setGroupId(groupId);
        filter.setArtifactId(artifactId);
        filter.setVersion(version);
        configuration.getIncludes().add(filter);
    }

    private void addExclude(String groupId, String artifactId, String version) {
        DependencyFilter filter = new DependencyFilter();
        filter.setGroupId(groupId);
        filter.setArtifactId(artifactId);
        filter.setVersion(version);
        configuration.getExcludes().add(filter);
    }

    public SlimmingConfiguration applyTo(SlimmingConfiguration baseConfig) {
        if (baseConfig == null) {
            baseConfig = new SlimmingConfiguration();
        }

        // Merge includes
        baseConfig.getIncludes().addAll(configuration.getIncludes());
        
        // Merge excludes
        baseConfig.getExcludes().addAll(configuration.getExcludes());

        return baseConfig;
    }

    public static SlimmingProfile getProfile(String name) {
        return PROFILES.get(name.toLowerCase());
    }

    public static String[] getAvailableProfiles() {
        return PROFILES.keySet().toArray(new String[0]);
    }
}
