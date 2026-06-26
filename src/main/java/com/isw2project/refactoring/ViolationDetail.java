package com.isw2project.refactoring;

public record ViolationDetail(
        String ruleName,
        String ruleSetName,
        int beginLine,
        int endLine,
        String description
) {}
