package com.ethanlally.skyblocknexus.skyblock;

import java.util.List;

public record SkyBlockProfileProgress(
        String profileId,
        List<SkyBlockSkillProgress> skills,
        List<SkyBlockCollectionProgress> collections) {}
