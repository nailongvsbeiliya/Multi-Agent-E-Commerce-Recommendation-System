package com.ecommerce.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "recommendation.nlp")
public class NaturalLanguageParsingProperties {

    private boolean llmFirst = true;
    private boolean fallbackEnabled = true;
    private String defaultScene = "homepage";
    private int defaultNumItems = 5;
    private int maxNumItems = 20;
    private List<String> sceneCampaignKeywords = new ArrayList<>();
    private List<String> sceneDetailKeywords = new ArrayList<>();
    private List<String> stopWords = new ArrayList<>();
    private Map<String, List<String>> synonymDictionary = new LinkedHashMap<>();

    public boolean isLlmFirst() {
        return llmFirst;
    }

    public void setLlmFirst(boolean llmFirst) {
        this.llmFirst = llmFirst;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    public String getDefaultScene() {
        return defaultScene;
    }

    public void setDefaultScene(String defaultScene) {
        this.defaultScene = defaultScene;
    }

    public int getDefaultNumItems() {
        return defaultNumItems;
    }

    public void setDefaultNumItems(int defaultNumItems) {
        this.defaultNumItems = defaultNumItems;
    }

    public int getMaxNumItems() {
        return maxNumItems;
    }

    public void setMaxNumItems(int maxNumItems) {
        this.maxNumItems = maxNumItems;
    }

    public List<String> getSceneCampaignKeywords() {
        return sceneCampaignKeywords;
    }

    public void setSceneCampaignKeywords(List<String> sceneCampaignKeywords) {
        this.sceneCampaignKeywords = sceneCampaignKeywords;
    }

    public List<String> getSceneDetailKeywords() {
        return sceneDetailKeywords;
    }

    public void setSceneDetailKeywords(List<String> sceneDetailKeywords) {
        this.sceneDetailKeywords = sceneDetailKeywords;
    }

    public List<String> getStopWords() {
        return stopWords;
    }

    public void setStopWords(List<String> stopWords) {
        this.stopWords = stopWords;
    }

    public Map<String, List<String>> getSynonymDictionary() {
        return synonymDictionary;
    }

    public void setSynonymDictionary(Map<String, List<String>> synonymDictionary) {
        this.synonymDictionary = synonymDictionary;
    }
}
