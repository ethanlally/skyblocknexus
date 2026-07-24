package com.ethanlally.skyblocknexus.skyblock;

import java.util.List;

public record SkyBlockProfileProgress(
        String profileId,
        SkyBlockCurrencySummary currencies,
        List<SkyBlockEquipmentItem> equipment,
        List<SkyBlockSkillProgress> skills,
        List<SkyBlockCollectionProgress> collections) {}
