package com.ethanlally.skyblocknexus.skyblock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.cloudburstmc.nbt.NBTInputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.nbt.NbtUtils;

public class SkyBlockItemDecoder {

    private static final long MAX_NBT_BYTES = 2_000_000;
    private static final Pattern MINECRAFT_COLOR_CODE =
            Pattern.compile("§[0-9A-FK-ORa-fk-or]");

    public List<SkyBlockEquipmentItem> decodeItems(String encodedData, String category) {
        if (encodedData == null || encodedData.isBlank()) {
            return List.of();
        }

        try {
            byte[] compressedData = Base64.getDecoder().decode(encodedData);
            try (NBTInputStream input = NbtUtils.createGZIPReader(
                    new ByteArrayInputStream(compressedData), MAX_NBT_BYTES)) {
                Object rootTag = input.readTag();
                if (!(rootTag instanceof NbtMap root)) {
                    return List.of();
                }

                List<SkyBlockEquipmentItem> items = new ArrayList<>();
                for (NbtMap item : root.getList("i", NbtType.COMPOUND)) {
                    NbtMap tag = item.getCompound("tag");
                    String itemId = tag.getCompound("ExtraAttributes").getString("id", "");
                    if (itemId.isBlank()) {
                        continue;
                    }

                    String displayName = tag.getCompound("display").getString("Name", "");
                    String name = MINECRAFT_COLOR_CODE.matcher(displayName).replaceAll("").strip();
                    items.add(new SkyBlockEquipmentItem(
                            category,
                            itemId,
                            name.isBlank() ? formatIdentifier(itemId) : name));
                }
                return List.copyOf(items);
            }
        } catch (IllegalArgumentException | IOException exception) {
            return List.of();
        }
    }

    private String formatIdentifier(String identifier) {
        String[] words = identifier.toLowerCase(Locale.ROOT).split("_");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!formatted.isEmpty()) {
                formatted.append(' ');
            }
            formatted.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return formatted.toString();
    }
}
