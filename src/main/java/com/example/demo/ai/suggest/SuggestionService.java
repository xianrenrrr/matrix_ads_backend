package com.example.demo.ai.suggest;

import java.util.List;
import java.util.Map;

public interface SuggestionService {
    SuggestionsResult suggestCn(Map<String, Object> comparisonFacts);
    
    record SuggestionsResult(
        List<String> suggestionsZh,
        List<String> nextActionsZh
    ) {}
}