package com.jagent.desktop.models.git;

import java.util.Map;

public record WorktreeStatus(WorktreeStatusSummary summary, Map<String, String> files) {}
