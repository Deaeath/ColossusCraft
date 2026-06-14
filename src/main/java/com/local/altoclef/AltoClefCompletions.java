package com.local.altoclef;

import adris.altoclef.TaskCatalogue;
import adris.altoclef.util.ItemTarget;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class AltoClefCompletions {
    private static final int LIMIT = 80;
    private static List<ItemCandidate> itemCandidates;
    private static List<EntityCandidate> entityCandidates;
    private static List<String> itemSuggestions;
    private static List<String> entitySuggestions;
    private static final List<String> LOCATIONS = List.of(
            "overworld", "nether", "end", "stronghold",
            "ruined_portals", "desert_temples", "elytra",
            "allthemodium:mining", "allthemodium:the_other", "allthemodium:the_beyond",
            "twilightforest:twilight_forest", "ad_astra:moon", "ad_astra:mars",
            "deeperdarker:otherside", "undergarden:undergarden",
            "blue_skies:everbright", "blue_skies:everdawn", "aether:the_aether"
    );

    private AltoClefCompletions() {
    }

    public static CompletableFuture<Suggestions> suggestItems(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggest(builder, itemSuggestionNames());
    }

    public static CompletableFuture<Suggestions> suggestEntities(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggest(builder, entitySuggestionNames());
    }

    public static CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                names.add(info.getProfile().getName());
            }
        }
        return suggest(builder, names);
    }

    public static CompletableFuture<Suggestions> suggestArmor(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.add("netherite");
        names.add("diamond");
        names.add("iron");
        names.add("gold");
        names.add("leather");
        for (ItemCandidate candidate : itemCandidateList()) {
            if (candidate.item() != null && candidate.item() instanceof net.minecraft.world.item.ArmorItem) {
                names.add(candidate.name());
            }
        }
        return suggest(builder, names);
    }

    public static CompletableFuture<Suggestions> suggestLocations(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggest(builder, LOCATIONS);
    }

    public static CompletableFuture<Suggestions> suggestBlocks(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggest(builder, blockSuggestionNames());
    }

    /** Fuzzy-rank all registered blocks and return the best match, or null. */
    public static net.minecraft.world.level.block.Block resolveBlock(String input) {
        String query = normalizeKey(normalizeItemName(input));
        // Fast path: if no namespace given, try minecraft:<name> before fuzzy search so that
        // vanilla names like "oak_log" are not beaten by modded blocks that happen to share the path.
        if (!input.contains(":")) {
            ResourceLocation minecraftLoc = ResourceLocation.tryParse("minecraft:" + query);
            if (minecraftLoc != null) {
                net.minecraft.world.level.block.Block direct = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(minecraftLoc);
                if (direct != null && direct != net.minecraft.world.level.block.Blocks.AIR) return direct;
            }
        }
        net.minecraft.world.level.block.Block best = null;
        int bestScore = Integer.MAX_VALUE;
        for (ResourceLocation key : net.minecraft.core.registries.BuiltInRegistries.BLOCK.keySet()) {
            net.minecraft.world.level.block.Block b = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(key);
            if (b == null || b == net.minecraft.world.level.block.Blocks.AIR) continue;
            String path = key.getPath();
            String full = key.toString();
            int score = Math.min(rank(query, path), rank(query, full));
            // Prefer non-minecraft namespace for ambiguous short names
            if (key.getNamespace().equals("minecraft")) score += 50;
            if (score < bestScore) {
                bestScore = score;
                best = b;
            }
        }
        return bestScore < 5000 ? best : null;
    }

    static ItemMatch resolveItem(String input) {
        String query = normalizeKey(normalizeItemName(input));
        ItemCandidate best = null;
        int bestRank = Integer.MAX_VALUE;
        for (ItemCandidate candidate : itemCandidateList()) {
            int rank = rank(query, candidate.name());
            ResourceLocation id = candidate.id();
            if (id != null) {
                rank = Math.min(rank, rank(query, id.toString()));
                rank = Math.min(rank, rank(query, id.getPath()));
            }
            if (rank < bestRank) {
                best = candidate;
                bestRank = rank;
            }
        }
        if (best == null || bestRank >= 5000) {
            return null;
        }
        return new ItemMatch(best.catalogueName(), best.id(), best.item());
    }

    static ItemTarget resolveItemTarget(String input, int count) {
        ItemMatch match = resolveItem(input);
        if (match == null) {
            return ItemTarget.EMPTY;
        }
        if (match.catalogueName() != null) {
            return TaskCatalogue.getItemTarget(match.catalogueName(), count);
        }
        return new ItemTarget(match.item(), count);
    }

    static String resolveEntityId(String input) {
        String query = normalizeKey(normalizeItemName(input));
        EntityCandidate best = null;
        int bestRank = Integer.MAX_VALUE;
        for (EntityCandidate candidate : entityCandidateList()) {
            int rank = Math.min(rank(query, candidate.id().toString()), rank(query, candidate.id().getPath()));
            if (rank < bestRank) {
                best = candidate;
                bestRank = rank;
            }
        }
        return best == null || bestRank >= 5000 ? null : best.id().toString();
    }

    static String resolveLocation(String input) {
        String query = normalizeKey(normalizeItemName(input));
        String best = null;
        int bestRank = Integer.MAX_VALUE;
        for (String location : LOCATIONS) {
            int rank = Math.min(rank(query, location), rank(query, normalizeItemName(location)));
            if (rank < bestRank) {
                best = location;
                bestRank = rank;
            }
        }
        return best == null || bestRank >= 5000 ? null : best;
    }

    static String normalizeItemName(String name) {
        String result = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        int variant = result.indexOf('#');
        if (variant >= 0) {
            result = result.substring(0, variant);
        }
        if (result.startsWith("minecraft:")) {
            result = result.substring("minecraft:".length());
        }
        return result;
    }

    private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, Collection<String> candidates) {
        String query = normalizeKey(builder.getRemaining());
        candidates.stream()
                .map(value -> new Scored(value, rank(query, value)))
                .filter(scored -> scored.rank() < 5000)
                .sorted(Comparator.comparingInt(Scored::rank).thenComparing(Scored::value))
                .limit(LIMIT)
                .forEach(scored -> builder.suggest(scored.value()));
        return builder.buildFuture();
    }

    private static List<String> itemSuggestionNames() {
        if (itemSuggestions == null) {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            for (ItemCandidate candidate : itemCandidateList()) {
                names.add(candidate.name());
            }
            itemSuggestions = List.copyOf(names);
        }
        return itemSuggestions;
    }

    private static List<String> entitySuggestionNames() {
        if (entitySuggestions == null) {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            for (EntityCandidate candidate : entityCandidateList()) {
                names.add(candidate.id().toString());
                names.add(candidate.id().getPath());
            }
            entitySuggestions = List.copyOf(names);
        }
        return entitySuggestions;
    }

    private static List<String> blockSuggestions;

    private static List<String> blockSuggestionNames() {
        if (blockSuggestions == null) {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            for (ResourceLocation key : net.minecraft.core.registries.BuiltInRegistries.BLOCK.keySet()) {
                net.minecraft.world.level.block.Block b = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(key);
                if (b == null || b == net.minecraft.world.level.block.Blocks.AIR) continue;
                names.add(key.toString());  // full namespace:path
                names.add(key.getPath());   // short path
            }
            blockSuggestions = List.copyOf(names);
        }
        return blockSuggestions;
    }

    private static List<ItemCandidate> itemCandidateList() {
        if (itemCandidates != null) {
            return itemCandidates;
        }
        Map<String, ItemCandidate> result = new LinkedHashMap<>();
        for (String name : TaskCatalogue.resourceNames()) {
            ItemTarget target = TaskCatalogue.getItemTarget(name, 1);
            Item item = target.isEmpty() ? null : target.getMatches()[0];
            ResourceLocation id = item == null ? null : BuiltInRegistries.ITEM.getKey(item);
            result.putIfAbsent(normalizeKey(name), new ItemCandidate(name, name, id, item));
        }
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == null || item == Items.AIR) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) {
                continue;
            }
            String full = id.toString();
            String path = id.getPath();
            result.putIfAbsent(normalizeKey(full), new ItemCandidate(full, null, id, item));
            result.putIfAbsent(normalizeKey(path), new ItemCandidate(path, null, id, item));
        }
        itemCandidates = new ArrayList<>(result.values());
        return itemCandidates;
    }

    private static List<EntityCandidate> entityCandidateList() {
        if (entityCandidates != null) {
            return entityCandidates;
        }
        List<EntityCandidate> result = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id != null) {
                result.add(new EntityCandidate(id));
            }
        }
        entityCandidates = result;
        return entityCandidates;
    }

    private static int rank(String query, String candidate) {
        String key = normalizeKey(candidate);
        if (query.isEmpty()) {
            return 0;
        }
        if (key.equals(query)) {
            return 0;
        }
        if (key.startsWith(query)) {
            return 10 + key.length() - query.length();
        }
        int contains = key.indexOf(query);
        if (contains >= 0) {
            return 100 + contains + key.length() - query.length();
        }
        int gap = subsequenceGap(query, key);
        return gap < 0 ? 9999 : 1000 + gap + key.length();
    }

    private static int subsequenceGap(String query, String candidate) {
        int last = -1;
        int gap = 0;
        for (int qi = 0, ci = 0; qi < query.length(); qi++) {
            char want = query.charAt(qi);
            int found = -1;
            while (ci < candidate.length()) {
                if (candidate.charAt(ci) == want) {
                    found = ci++;
                    break;
                }
                ci++;
            }
            if (found < 0) {
                return -1;
            }
            if (last >= 0) {
                gap += found - last - 1;
            }
            last = found;
        }
        return gap;
    }

    private static String normalizeKey(String value) {
        return (value == null ? "" : value)
                .toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');
    }

    record ItemMatch(String catalogueName, ResourceLocation id, Item item) {
    }

    private record ItemCandidate(String name, String catalogueName, ResourceLocation id, Item item) {
    }

    private record EntityCandidate(ResourceLocation id) {
    }

    private record Scored(String value, int rank) {
    }
}
