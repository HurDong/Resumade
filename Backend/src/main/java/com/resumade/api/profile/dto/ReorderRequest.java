package com.resumade.api.profile.dto;

import java.util.List;

public record ReorderRequest(String category, List<String> ids) {}
