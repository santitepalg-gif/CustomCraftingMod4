package com.crafteos;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraftforge.common.crafting.StrictNBTIngredient;

public final class Rule {
    public static final int GRID = 9;
    public static final ResourceLocation AIR_ID = new ResourceLocation("minecraft", "air");

    public final ResourceLocation result;
    /** NBT del resultado (items custom). null = item normal. */
    @Nullable
    public final CompoundTag resultTag;
    public final int count;
    public final Station station;
    public final List<ResourceLocation> inputs;
    /** NBT de cada ingrediente (items custom del inventario). null = item normal. */
    public final List<CompoundTag> inputTags;
    public final int cookTicks;
    /** Regla activada/desactivada desde el panel de recetas. */
    public boolean enabled = true;

    public Rule(ResourceLocation result, @Nullable CompoundTag resultTag, int count, Station station,
                List<ResourceLocation> inputs, List<CompoundTag> inputTags, int cookTicks) {
        this.result = result;
        this.resultTag = clean(resultTag);
        this.count = Math.max(1, Math.min(64, count));
        this.station = station;
        List<ResourceLocation> list = new ArrayList<>(GRID);
        List<CompoundTag> tags = new ArrayList<>(GRID);
        for (int i = 0; i < GRID; i++) {
            list.add(i < inputs.size() ? inputs.get(i) : AIR_ID);
            tags.add(inputTags != null && i < inputTags.size() ? clean(inputTags.get(i)) : null);
        }
        this.inputs = list;
        this.inputTags = tags;
        this.cookTicks = Math.max(1, Math.min(72000, cookTicks));
    }

    /** Compatibilidad: regla sin NBT. */
    public Rule(ResourceLocation result, int count, Station station, List<ResourceLocation> inputs, int cookTicks) {
        this(result, null, count, station, inputs, null, cookTicks);
    }

    @Nullable
    private static CompoundTag clean(@Nullable CompoundTag t) {
        return t == null || t.isEmpty() ? null : t.copy();
    }

    public static Item item(ResourceLocation id) {
        return BuiltInRegistries.ITEM.get(id);
    }

    public static ItemStack stackOf(ResourceLocation id, @Nullable CompoundTag tag, int count) {
        ItemStack s = new ItemStack(item(id), count);
        if (tag != null) s.setTag(tag.copy());
        return s;
    }

    public ItemStack resultStack() {
        return stackOf(result, resultTag, count);
    }

    public ItemStack inputStack(int i) {
        return stackOf(inputs.get(i), inputTags.get(i), 1);
    }

    private Ingredient ing(int i) {
        Item it = item(inputs.get(i));
        if (it == Items.AIR) return Ingredient.EMPTY;
        CompoundTag tag = inputTags.get(i);
        if (tag != null) {
            return StrictNBTIngredient.of(stackOf(inputs.get(i), tag, 1));
        }
        return Ingredient.of(it);
    }

    public String validate() {
        if (!BuiltInRegistries.ITEM.containsKey(result) || item(result) == Items.AIR) {
            return "El objeto resultado no existe.";
        }
        boolean any = false;
        for (int i = 0; i < station.inputCount(); i++) {
            ResourceLocation id = inputs.get(i);
            if (id.equals(AIR_ID)) continue;
            if (!BuiltInRegistries.ITEM.containsKey(id)) {
                return "Objeto desconocido: " + id;
            }
            any = true;
        }
        if (station == Station.SMITHING_TABLE) {
            if (item(inputs.get(1)) == Items.AIR) {
                return "La mesa de mejora necesita al menos el objeto base.";
            }
        } else if (!any) {
            return "Falta al menos un ingrediente.";
        }
        return null;
    }

    public Recipe<?> toRecipe(ResourceLocation id) {
        Item resItem = item(result);
        if (resItem == Items.AIR) return null;
        ItemStack out = resultStack();
        switch (station) {
            case CRAFTING_TABLE:
                return shaped(id, out);
            case FURNACE:
                return new SmeltingRecipe(id, "", CookingBookCategory.MISC, ing(0), out, 0.35f, cookTicks);
            case BLAST_FURNACE:
                return new BlastingRecipe(id, "", CookingBookCategory.MISC, ing(0), out, 0.35f, cookTicks);
            case SMOKER:
                return new SmokingRecipe(id, "", CookingBookCategory.MISC, ing(0), out, 0.35f, cookTicks);
            case CAMPFIRE:
                return new CampfireCookingRecipe(id, "", CookingBookCategory.MISC, ing(0), out, 0.35f, cookTicks);
            case SMITHING_TABLE:
                return new SmithingTransformRecipe(id, ing(0), ing(1), ing(2), out);
        }
        return null;
    }

    private Recipe<?> shaped(ResourceLocation id, ItemStack out) {
        int minR = 3, maxR = -1, minC = 3, maxC = -1;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (item(inputs.get(r * 3 + c)) == Items.AIR) continue;
                minR = Math.min(minR, r);
                maxR = Math.max(maxR, r);
                minC = Math.min(minC, c);
                maxC = Math.max(maxC, c);
            }
        }
        if (maxR < 0) return null;
        int w = maxC - minC + 1;
        int h = maxR - minR + 1;
        NonNullList<Ingredient> list = NonNullList.withSize(w * h, Ingredient.EMPTY);
        for (int r = minR; r <= maxR; r++) {
            for (int c = minC; c <= maxC; c++) {
                list.set((r - minR) * w + (c - minC), ing(r * 3 + c));
            }
        }
        return new ShapedRecipe(id, "", CraftingBookCategory.MISC, w, h, list, out);
    }

    // ---------- red ----------
    public void write(FriendlyByteBuf b) {
        b.writeResourceLocation(result);
        b.writeVarInt(count);
        b.writeByte(station.ordinal());
        for (int i = 0; i < GRID; i++) b.writeResourceLocation(inputs.get(i));
        b.writeVarInt(cookTicks);
        // NBT de items custom (campo nuevo)
        b.writeNbt(resultTag);
        for (int i = 0; i < GRID; i++) b.writeNbt(inputTags.get(i));
        b.writeBoolean(enabled);
    }

    public static Rule read(FriendlyByteBuf b) {
        ResourceLocation result = b.readResourceLocation();
        int count = b.readVarInt();
        Station st = Station.byOrdinal(b.readByte());
        List<ResourceLocation> in = new ArrayList<>(GRID);
        for (int i = 0; i < GRID; i++) in.add(b.readResourceLocation());
        int ticks = b.readVarInt();
        CompoundTag resTag = b.readNbt();
        List<CompoundTag> tags = new ArrayList<>(GRID);
        for (int i = 0; i < GRID; i++) tags.add(b.readNbt());
        Rule rule = new Rule(result, resTag, count, st, in, tags, ticks);
        rule.enabled = b.readBoolean();
        return rule;
    }

    // ---------- guardado ----------
    public CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.putString("result", result.toString());
        t.putInt("count", count);
        t.putString("station", station.name());
        ListTag l = new ListTag();
        for (ResourceLocation r : inputs) l.add(StringTag.valueOf(r.toString()));
        t.put("inputs", l);
        t.putInt("cook", cookTicks);
        if (resultTag != null) t.put("resultTag", resultTag.copy());
        ListTag tl = new ListTag();
        for (CompoundTag c : inputTags) tl.add(c == null ? new CompoundTag() : c.copy());
        t.put("inputTags", tl);
        t.putBoolean("enabled", enabled);
        return t;
    }

    public static Rule fromTag(CompoundTag t) {
        ResourceLocation result = new ResourceLocation(t.getString("result"));
        Station st;
        try {
            st = Station.valueOf(t.getString("station"));
        } catch (IllegalArgumentException e) {
            st = Station.CRAFTING_TABLE;
        }
        List<ResourceLocation> in = new ArrayList<>();
        ListTag l = t.getList("inputs", 8);
        for (int i = 0; i < l.size(); i++) in.add(new ResourceLocation(l.getString(i)));
        CompoundTag resTag = t.contains("resultTag", 10) ? t.getCompound("resultTag") : null;
        List<CompoundTag> tags = new ArrayList<>();
        if (t.contains("inputTags", 9)) {
            ListTag tl = t.getList("inputTags", 10);
            for (int i = 0; i < tl.size(); i++) tags.add(tl.getCompound(i));
        }
        Rule rule = new Rule(result, resTag, t.getInt("count"), st, in, tags, t.getInt("cook"));
        rule.enabled = !t.contains("enabled") || t.getBoolean("enabled");
        return rule;
    }
}
