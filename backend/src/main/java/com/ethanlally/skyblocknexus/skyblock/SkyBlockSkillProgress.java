package com.ethanlally.skyblocknexus.skyblock;

public record SkyBlockSkillProgress(
        String name,
        int level,
        double totalExperience,
        double experienceIntoLevel,
        Double experienceForNextLevel) {}
