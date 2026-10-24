package com.crafteos.client;

import com.crafteos.CrafteosMod;
import com.crafteos.RecipeEntry;
import com.crafteos.Rule;
import com.crafteos.Station;
import com.crafteos.net.AddRulePacket;
import com.crafteos.net.Network;
import com.crafteos.net.RecipeTogglePacket;
import com.crafteos.net.RemoveRulePacket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;

public class CrafteosScreen extends Screen {
    private static final int RESULT = 99;
    private static final int ROW_H = 24;
    private static List<Entry> ALL;

    private List<Rule> rules;
    private List<RecipeEntry> disabledEntries;
    // ---- panel de recetas ----
    private boolean recipeMode = false;
    private List<Row> allRows = new ArrayList<>();
    private List<Row> shownRows = new ArrayList<>();
    private List<String> namespaces = new ArrayList<>();
    private int nsIndex = 0;      // 0 = todos los mods
    private int stateFilter = 0;  // 0 todas, 1 activas, 2 desactivadas
    private int recScroll = 0;
    private String recSearchText = "";
    private EditBox recSearch;
    private Button modeButton;
    private Button stateButton;
    private Button nsButton;
    private Button disAllButton;
    private Button enAllButton;
    private final List<AbstractWidget> editorWidgets = new ArrayList<>();
    private final List<AbstractWidget> recipeWidgets = new ArrayList<>();
    private Station station = Station.CRAFTING_TABLE;
    private ItemStack result = ItemStack.EMPTY;
    private int count = 1;
    private final ItemStack[] inputs = new ItemStack[9];
    private int active = RESULT;
    private int ruleScroll = 0;
    private int pickScroll = 0;
    private String searchText = "";
    private String cookText = "10";
    private String status = "";
    private int statusColor = -1;
    /** Toggle: mostrar los items del inventario del jugador en vez del registro completo. */
    private boolean useInventory = false;
    private EditBox search;
    private EditBox cookBox;
    private Button invButton;
    private final List<Button> stationButtons = new ArrayList<>();
    private List<Entry> filtered = new ArrayList<>();

    private int lx, ly, lw, lh, mx0, px, pw, gridY, cols, rows;

    public CrafteosScreen(List<Rule> rules, List<RecipeEntry> disabled) {
        super(Component.literal("Crafteos Reemplazo"));
        this.rules = new ArrayList<>(rules);
        this.disabledEntries = new ArrayList<>(disabled);
        for (int i = 0; i < inputs.length; i++) inputs[i] = ItemStack.EMPTY;
        ensureAll();
    }

    public void setRules(List<Rule> newRules, List<RecipeEntry> disabled) {
        this.rules = new ArrayList<>(newRules);
        this.disabledEntries = new ArrayList<>(disabled);
        int max = Math.max(0, rules.size() - 1);
        ruleScroll = Mth.clamp(ruleScroll, 0, max);
        rebuildRows();
    }

    private static void ensureAll() {
        if (ALL != null) return;
        List<Entry> list = new ArrayList<>();
        for (Item it : BuiltInRegistries.ITEM) {
            if (it == Items.AIR) continue;
            ItemStack st = new ItemStack(it);
            list.add(new Entry(st, keyOf(st)));
        }
        ALL = list;
    }

    private static String keyOf(ItemStack st) {
        String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
        return (id + " " + st.getHoverName().getString()).toLowerCase(Locale.ROOT);
    }

    /** Lista los items del inventario del jugador (sin repetidos, con su NBT). */
    private static List<Entry> inventoryEntries() {
        List<Entry> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return out;
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            ItemStack one = s.copy();
            one.setCount(1);
            boolean dup = false;
            for (Entry e : out) {
                if (ItemStack.isSameItemSameTags(e.stack(), one)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) out.add(new Entry(one, keyOf(one)));
        }
        return out;
    }

    private <T extends AbstractWidget> T addEditor(T widget) {
        editorWidgets.add(widget);
        return addRenderableWidget(widget);
    }

    private <T extends AbstractWidget> T addRecipe(T widget) {
        recipeWidgets.add(widget);
        return addRenderableWidget(widget);
    }

    private Component modeLabel() {
        return Component.literal(recipeMode ? "<< Volver al editor" : "Panel de recetas >>");
    }

    private Component stateLabel() {
        String[] names = {"Estado: todas", "Estado: activas", "Estado: desactivadas"};
        return Component.literal(names[stateFilter]);
    }

    private String currentNamespace() {
        return nsIndex == 0 || nsIndex > namespaces.size() ? "" : namespaces.get(nsIndex - 1);
    }

    private Component nsLabel() {
        String ns = currentNamespace();
        return Component.literal("Mod: " + (ns.isEmpty() ? "todos" : ns));
    }

    @Override
    protected void init() {
        lx = 8;
        ly = 24;
        lw = 140;
        lh = height - ly - 8;
        mx0 = 156;
        px = 354;
        pw = Math.max(90, width - px - 8);
        gridY = 58;
        cols = Math.max(1, pw / 18);
        rows = Math.max(1, (height - gridY - 8) / 18);
        stationButtons.clear();
        editorWidgets.clear();
        recipeWidgets.clear();
        Station[] all = Station.values();
        for (int i = 0; i < all.length; i++) {
            final Station s2 = all[i];
            Button b2 = Button.builder(Component.literal(s2.label), btn -> setStation(s2))
                    .bounds(mx0 + (i % 2) * 96, 24 + (i / 2) * 20, 94, 18).build();
            stationButtons.add(b2);
            addEditor(b2);
        }
        addEditor(Button.builder(Component.literal("-"), b -> changeCount(-1)).bounds(mx0 + 30, 96, 16, 18).build());
        addEditor(Button.builder(Component.literal("+"), b -> changeCount(1)).bounds(mx0 + 76, 96, 16, 18).build());
        cookBox = new EditBox(font, mx0 + 84, 200, 40, 16, Component.literal("seg"));
        cookBox.setMaxLength(4);
        cookBox.setFilter(s -> s.matches("\\d*"));
        cookBox.setValue(cookText);
        cookBox.setResponder(s -> cookText = s);
        addEditor(cookBox);
        addEditor(Button.builder(Component.literal("Agregar regla"), b -> submit()).bounds(mx0, 222, 126, 20).build());
        addEditor(Button.builder(Component.literal("Limpiar"), b -> clearEditor()).bounds(mx0 + 130, 222, 60, 20).build());

        search = new EditBox(font, px, 22, pw, 16, Component.literal("Buscar"));
        search.setHint(Component.literal("Buscar objeto..."));
        search.setMaxLength(50);
        search.setValue(searchText);
        search.setResponder(s -> {
            searchText = s;
            pickScroll = 0;
            refilter();
        });
        addEditor(search);

        // Toggle de inventario
        invButton = Button.builder(invLabel(), b -> {
            useInventory = !useInventory;
            pickScroll = 0;
            b.setMessage(invLabel());
            refilter();
        }).bounds(px, 40, pw, 14).build();
        addEditor(invButton);

        // ---- boton de modo + widgets del panel de recetas ----
        modeButton = Button.builder(modeLabel(), b -> {
            recipeMode = !recipeMode;
            modeButton.setMessage(modeLabel());
            updateVisibility();
        }).bounds(8, 2, 140, 18).build();
        addRenderableWidget(modeButton);

        int w = width - 16;
        recSearch = new EditBox(font, 8, 24, Math.max(60, w - 130), 16, Component.literal("Buscar receta"));
        recSearch.setHint(Component.literal("Buscar receta o item..."));
        recSearch.setMaxLength(60);
        recSearch.setValue(recSearchText);
        recSearch.setResponder(t -> {
            recSearchText = t;
            recScroll = 0;
            refilterRows();
        });
        addRecipe(recSearch);
        stateButton = Button.builder(stateLabel(), b -> {
            stateFilter = (stateFilter + 1) % 3;
            stateButton.setMessage(stateLabel());
            recScroll = 0;
            refilterRows();
        }).bounds(8 + Math.max(60, w - 130) + 4, 22, 122, 20).build();
        addRecipe(stateButton);
        int third = Math.max(40, (w - 8) / 3);
        nsButton = Button.builder(nsLabel(), b -> {
            nsIndex = (nsIndex + 1) % (namespaces.size() + 1);
            nsButton.setMessage(nsLabel());
            recScroll = 0;
            refilterRows();
        }).bounds(8, 44, third, 18).build();
        addRecipe(nsButton);
        disAllButton = Button.builder(Component.literal("Desactivar todas (mod)"), b ->
                Network.CHANNEL.sendToServer(RecipeTogglePacket.bulk(currentNamespace(), false)))
                .bounds(8 + third + 4, 44, third, 18).build();
        addRecipe(disAllButton);
        enAllButton = Button.builder(Component.literal("Activar todas (mod)"), b ->
                Network.CHANNEL.sendToServer(RecipeTogglePacket.bulk(currentNamespace(), true)))
                .bounds(8 + (third + 4) * 2, 44, third, 18).build();
        addRecipe(enAllButton);

        rebuildRows();
        refilter();
        updateVisibility();
    }

    private Component invLabel() {
        return Component.literal("Mi inventario: " + (useInventory ? "SI" : "NO"));
    }

    private void refilter() {
        String q = searchText.toLowerCase(Locale.ROOT).trim();
        List<Entry> source = useInventory ? inventoryEntries() : ALL;
        List<Entry> out = new ArrayList<>();
        for (Entry e : source) {
            if (!q.isEmpty() && !e.key().contains(q)) continue;
            out.add(e);
        }
        filtered = out;
    }

    private void updateVisibility() {
        for (AbstractWidget w : editorWidgets) w.visible = !recipeMode;
        for (AbstractWidget w : recipeWidgets) w.visible = recipeMode;
        if (cookBox != null) cookBox.visible = !recipeMode && station.isCooking();
        if (recSearch != null && !recipeMode) recSearch.setFocused(false);
        if (search != null && recipeMode) search.setFocused(false);
    }

    private void setStation(Station s) {
        station = s;
        if (s.isCooking()) {
            cookText = String.valueOf(Math.max(1, s.defaultTicks / 20));
            if (cookBox != null) cookBox.setValue(cookText);
        }
        if (active != RESULT && active >= s.inputCount()) active = 0;
        updateVisibility();
    }

    private void changeCount(int dir) {
        int step = hasShiftDown() ? 10 : 1;
        count = Mth.clamp(count + dir * step, 1, 64);
    }

    private void clearEditor() {
        result = ItemStack.EMPTY;
        count = 1;
        for (int i = 0; i < inputs.length; i++) inputs[i] = ItemStack.EMPTY;
        active = RESULT;
        setStatus("", -1);
    }

    private void loadRule(Rule r) {
        station = r.station;
        result = Rule.item(r.result) == Items.AIR ? ItemStack.EMPTY : Rule.stackOf(r.result, r.resultTag, 1);
        count = r.count;
        for (int i = 0; i < 9; i++) {
            inputs[i] = Rule.item(r.inputs.get(i)) == Items.AIR ? ItemStack.EMPTY : r.inputStack(i);
        }
        cookText = String.valueOf(Math.max(1, r.cookTicks / 20));
        if (cookBox != null) cookBox.setValue(cookText);
        active = RESULT;
        updateVisibility();
        setStatus("Regla cargada. Edita y pulsa Agregar (la anterior sigue activa).", 0xFFFFFF55);
    }

    private void setStatus(String s, int color) {
        status = s;
        statusColor = color;
    }

    private void submit() {
        if (result.isEmpty()) {
            setStatus("Elige el objeto resultado.", 0xFFFF5555);
            return;
        }
        boolean any = false;
        for (int i = 0; i < station.inputCount(); i++) {
            if (!inputs[i].isEmpty()) any = true;
        }
        if (station == Station.SMITHING_TABLE) {
            if (inputs[1].isEmpty()) {
                setStatus("La mesa de mejora necesita el objeto base.", 0xFFFF5555);
                return;
            }
        } else if (!any) {
            setStatus("Pon al menos un ingrediente.", 0xFFFF5555);
            return;
        }
        List<ResourceLocation> ids = new ArrayList<>();
        List<CompoundTag> tags = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            if (i < station.inputCount() && !inputs[i].isEmpty()) {
                ids.add(BuiltInRegistries.ITEM.getKey(inputs[i].getItem()));
                tags.add(inputs[i].getTag());
            } else {
                ids.add(Rule.AIR_ID);
                tags.add(null);
            }
        }
        int seconds = 1;
        try {
            seconds = cookText.isEmpty() ? 1 : Integer.parseInt(cookText);
        } catch (NumberFormatException ignored) {
        }
        Rule rule = new Rule(BuiltInRegistries.ITEM.getKey(result.getItem()), result.getTag(), count, station,
                ids, tags, Math.max(1, seconds) * 20);
        Network.CHANNEL.sendToServer(new AddRulePacket(rule));
        setStatus("Regla enviada.", 0xFF55FF55);
    }

    /** Copia del stack elegido; si viene del inventario se ignora el desgaste (Damage). */
    private static ItemStack toPicked(ItemStack src) {
        ItemStack s = src.copy();
        s.setCount(1);
        CompoundTag t = s.getTag();
        if (t != null) {
            t.remove("Damage");
            if (t.isEmpty()) s.setTag(null);
        }
        return s;
    }

    private void pick(ItemStack stack) {
        ItemStack picked = toPicked(stack);
        if (active == RESULT) {
            result = picked;
            active = 0;
        } else if (active >= 0 && active < station.inputCount()) {
            inputs[active] = picked;
            if (active + 1 < station.inputCount()) active++;
        }
    }

    private int inX(int i) {
        return station == Station.CRAFTING_TABLE ? mx0 + 6 + (i % 3) * 20 : mx0 + 6;
    }

    private int inY(int i) {
        if (station == Station.CRAFTING_TABLE) return 136 + (i / 3) * 20;
        if (station == Station.SMITHING_TABLE) return 136 + i * 20;
        return 136;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int pickerIndexAt(double mx, double my) {
        if (!inside(mx, my, px, gridY, cols * 18, rows * 18)) return -1;
        int r = (int) ((my - gridY) / 18.0);
        int c = (int) ((mx - px) / 18.0);
        int idx = (pickScroll + r) * cols + c;
        return idx >= 0 && idx < filtered.size() ? idx : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        setFocused(null);
        if (recipeMode) return recipeClick(mx, my);
        if (inside(mx, my, mx0 + 6, 96, 18, 18)) {
            if (button == 1) result = ItemStack.EMPTY;
            else active = RESULT;
            return true;
        }
        for (int i = 0; i < station.inputCount(); i++) {
            if (!inside(mx, my, inX(i), inY(i), 18, 18)) continue;
            if (button == 1) inputs[i] = ItemStack.EMPTY;
            else active = i;
            return true;
        }
        int idx = pickerIndexAt(mx, my);
        if (idx >= 0) {
            pick(filtered.get(idx).stack());
            return true;
        }
        if (inside(mx, my, lx, ly, lw, lh)) {
            int ri = ruleScroll + (int) ((my - ly) / ROW_H);
            if (ri >= 0 && ri < rules.size()) {
                if (mx >= lx + lw - 14) {
                    Network.CHANNEL.sendToServer(new RemoveRulePacket(ri));
                } else {
                    loadRule(rules.get(ri));
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int dir = delta > 0 ? -1 : 1;
        if (recipeMode) {
            int step = hasShiftDown() ? 10 : 3;
            int max = Math.max(0, shownRows.size() - recVisibleRows());
            recScroll = Mth.clamp(recScroll + dir * step, 0, max);
            return true;
        }
        if (mx >= px) {
            int totalRows = (filtered.size() + cols - 1) / cols;
            int max = Math.max(0, totalRows - rows);
            pickScroll = Mth.clamp(pickScroll + dir, 0, max);
            return true;
        }
        if (mx < lx + lw + 4) {
            int visible = Math.max(1, lh / ROW_H);
            int max = Math.max(0, rules.size() - visible);
            ruleScroll = Mth.clamp(ruleScroll + dir, 0, max);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void drawSlot(GuiGraphics g, int x, int y, ItemStack item, boolean isActive) {
        g.fill(x, y, x + 18, y + 18, 0xFF373737);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
        if (!item.isEmpty()) g.renderItem(item, x + 1, y + 1);
        if (isActive) outline(g, x - 1, y - 1, 20, 20, 0xFFFFFF55);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        if (recipeMode) {
            renderRecipes(g, mouseX, mouseY, partial);
            return;
        }
        renderBackground(g);
        g.fill(lx - 2, ly - 2, lx + lw + 2, ly + lh + 2, 0x90000000);
        if (rules.isEmpty()) {
            g.drawString(font, "Sin reglas todavia.", lx + 4, ly + 6, 0xFFAAAAAA);
        }
        g.enableScissor(lx, ly, lx + lw, ly + lh);
        int visible = lh / ROW_H + 1;
        for (int i = 0; i < visible; i++) {
            int idx2 = ruleScroll + i;
            if (idx2 >= rules.size()) break;
            Rule r = rules.get(idx2);
            int y = ly + i * ROW_H;
            boolean hover = inside(mouseX, mouseY, lx, y, lw, ROW_H);
            g.fill(lx, y, lx + lw, y + ROW_H - 2, hover ? 0x80FFFFFF : 0x40FFFFFF);
            ItemStack st = r.resultStack();
            g.renderItem(st, lx + 3, y + 3);
            String name = st.getHoverName().getString() + " x" + r.count;
            g.drawString(font, font.plainSubstrByWidth(name, lw - 40), lx + 24, y + 3, 0xFFFFFFFF);
            g.drawString(font, font.plainSubstrByWidth((r.enabled ? "" : "[OFF] ") + r.station.label, lw - 40), lx + 24, y + 13, 0xFFAAAAAA);
            g.fill(lx + lw - 14, y + 5, lx + lw - 2, y + 17, 0xFFAA2222);
            g.drawString(font, "x", lx + lw - 10, y + 7, 0xFFFFFFFF);
        }
        g.disableScissor();

        g.drawString(font, Component.literal("Estacion de reemplazo").withStyle(ChatFormatting.GOLD), mx0, 10, 0xFFFFFFFF);
        g.drawString(font, "Resultado", mx0 + 6, 86, 0xFFFFFFFF);
        drawSlot(g, mx0 + 6, 96, result, active == RESULT);
        g.drawCenteredString(font, "x" + count, mx0 + 61, 101, 0xFFFFFFFF);
        String label = switch (station) {
            case CRAFTING_TABLE -> "Receta (forma 3x3)";
            case SMITHING_TABLE -> "Mejora (plantilla / base / adicion)";
            default -> "Ingrediente";
        };
        g.drawString(font, label, mx0 + 6, 124, 0xFFFFFFFF);
        for (int i = 0; i < station.inputCount(); i++) {
            drawSlot(g, inX(i), inY(i), inputs[i], active == i);
        }
        if (station == Station.SMITHING_TABLE) {
            String[] labels = {"Plantilla (opcional)", "Objeto base", "Adicion (opcional)"};
            for (int i = 0; i < 3; i++) {
                g.drawString(font, labels[i], mx0 + 30, inY(i) + 5, 0xFFAAAAAA);
            }
        } else if (station.isCooking()) {
            g.drawString(font, "Tiempo (seg):", mx0 + 6, 204, 0xFFFFFFFF);
        }
        g.drawString(font, font.plainSubstrByWidth(status, 190), mx0, 248, statusColor);
        g.drawString(font, "Clic en un espacio y luego en un objeto.", mx0, 262, 0xFF888888);
        g.drawString(font, "Clic derecho en un espacio: vaciarlo.", mx0, 272, 0xFF888888);
        g.drawString(font, "Boton 'Mi inventario': usa tus items custom.", mx0, 282, 0xFF888888);

        String title = useInventory ? "Mi inventario (rueda: desplazar)" : "Objetos (rueda: desplazar)";
        g.drawString(font, Component.literal(title).withStyle(ChatFormatting.GOLD), px, 10, 0xFFFFFFFF);
        g.fill(px - 2, gridY - 2, px + cols * 18 + 2, gridY + rows * 18 + 2, 0x90000000);
        if (useInventory && filtered.isEmpty()) {
            g.drawString(font, "Inventario vacio.", px + 4, gridY + 4, 0xFFAAAAAA);
        }
        g.enableScissor(px, gridY, px + cols * 18, gridY + rows * 18);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx3 = (pickScroll + r) * cols + c;
                if (idx3 >= filtered.size()) break;
                int x = px + c * 18;
                int y = gridY + r * 18;
                g.renderItem(filtered.get(idx3).stack(), x + 1, y + 1);
                if (inside(mouseX, mouseY, x, y, 18, 18)) {
                    g.fill(x, y, x + 18, y + 18, 0x80FFFFFF);
                }
            }
        }
        g.disableScissor();

        super.render(g, mouseX, mouseY, partial);

        for (int i = 0; i < stationButtons.size(); i++) {
            if (Station.values()[i] != station) continue;
            Button b = stationButtons.get(i);
            outline(g, b.getX() - 1, b.getY() - 1, b.getWidth() + 2, b.getHeight() + 2, 0xFFFFFF55);
        }

        ItemStack tip = ItemStack.EMPTY;
        if (inside(mouseX, mouseY, mx0 + 6, 96, 18, 18) && !result.isEmpty()) tip = result;
        for (int i = 0; i < station.inputCount() && tip.isEmpty(); i++) {
            if (!inside(mouseX, mouseY, inX(i), inY(i), 18, 18) || inputs[i].isEmpty()) continue;
            tip = inputs[i];
        }
        int idx = pickerIndexAt(mouseX, mouseY);
        if (tip.isEmpty() && idx >= 0) tip = filtered.get(idx).stack();
        if (tip.isEmpty() && inside(mouseX, mouseY, lx, ly, lw, lh)) {
            int ri = ruleScroll + (mouseY - ly) / ROW_H;
            if (ri >= 0 && ri < rules.size()) tip = rules.get(ri).resultStack();
        }
        if (!tip.isEmpty()) g.renderTooltip(font, tip, mouseX, mouseY);
    }

    // ------------------------------------------------------------------
    //  Panel de recetas
    // ------------------------------------------------------------------
    private static final int REC_ROW_H = 22;
    private static final int REC_TOP = 68;

    private static final class Row {
        final ResourceLocation id;
        final ItemStack result;
        final int station;
        final boolean enabled;
        final int ruleIndex; // -1 = receta existente (vanilla / otro mod)
        final String name;
        final String key;

        Row(ResourceLocation id, ItemStack result, int station, boolean enabled, int ruleIndex) {
            this.id = id;
            this.result = result;
            this.station = station;
            this.enabled = enabled;
            this.ruleIndex = ruleIndex;
            this.name = result.getHoverName().getString();
            this.key = (id.toString() + " " + name + " " + RecipeEntry.label(station)).toLowerCase(Locale.ROOT);
        }
    }

    private void rebuildRows() {
        List<Row> list = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            RegistryAccess access = mc.level.registryAccess();
            for (Recipe<?> r : mc.level.getRecipeManager().getRecipes()) {
                ResourceLocation id = r.getId();
                if (id.getNamespace().equals(CrafteosMod.MODID)) continue;
                ItemStack res;
                try {
                    res = r.getResultItem(access);
                } catch (RuntimeException e) {
                    continue;
                }
                if (res.isEmpty()) continue;
                list.add(new Row(id, res.copy(), RecipeEntry.stationOf(r), true, -1));
            }
        }
        for (RecipeEntry e : disabledEntries) {
            list.add(new Row(e.id, e.result, e.station, false, -1));
        }
        for (int i = 0; i < rules.size(); i++) {
            Rule r = rules.get(i);
            ItemStack res = r.resultStack();
            if (res.isEmpty()) continue;
            list.add(new Row(new ResourceLocation(CrafteosMod.MODID, "regla_" + i), res, r.station.ordinal(), r.enabled, i));
        }
        list.sort(Comparator
                .comparing((Row r) -> r.ruleIndex < 0)
                .thenComparing(r -> r.id.getNamespace())
                .thenComparing(r -> r.name.toLowerCase(Locale.ROOT)));
        allRows = list;

        TreeSet<String> ns = new TreeSet<>();
        for (Row r : list) {
            if (r.ruleIndex < 0) ns.add(r.id.getNamespace());
        }
        String keep = currentNamespace();
        namespaces = new ArrayList<>(ns);
        nsIndex = keep.isEmpty() ? 0 : namespaces.indexOf(keep) + 1;
        if (nsIndex < 0) nsIndex = 0;
        if (nsButton != null) nsButton.setMessage(nsLabel());
        refilterRows();
    }

    private void refilterRows() {
        String q = recSearchText.toLowerCase(Locale.ROOT).trim();
        String ns = currentNamespace();
        List<Row> out = new ArrayList<>();
        for (Row r : allRows) {
            if (stateFilter == 1 && !r.enabled) continue;
            if (stateFilter == 2 && r.enabled) continue;
            if (!ns.isEmpty() && !(r.ruleIndex < 0 && r.id.getNamespace().equals(ns))) continue;
            if (!q.isEmpty() && !r.key.contains(q)) continue;
            out.add(r);
        }
        shownRows = out;
        recScroll = Mth.clamp(recScroll, 0, Math.max(0, out.size() - recVisibleRows()));
    }

    private int recVisibleRows() {
        return Math.max(1, (height - REC_TOP - 8) / REC_ROW_H);
    }

    private int recToggleX() {
        return width - 8 - 86;
    }

    private Row recRowAt(double mx, double my) {
        if (mx < 8 || mx >= width - 8 || my < REC_TOP) return null;
        int idx = recScroll + (int) ((my - REC_TOP) / REC_ROW_H);
        if (idx < 0 || idx >= shownRows.size()) return null;
        if (my - REC_TOP >= recVisibleRows() * REC_ROW_H) return null;
        return shownRows.get(idx);
    }

    private boolean recipeClick(double mx, double my) {
        Row row = recRowAt(mx, my);
        if (row == null) return false;
        if (mx >= recToggleX()) {
            if (row.ruleIndex >= 0) Network.CHANNEL.sendToServer(RecipeTogglePacket.rule(row.ruleIndex));
            else Network.CHANNEL.sendToServer(RecipeTogglePacket.recipe(row.id));
            return true;
        }
        return false;
    }

    private void renderRecipes(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        int off = 0;
        for (Row r : allRows) {
            if (!r.enabled) off++;
        }
        g.drawString(font, Component.literal("Recetas: " + shownRows.size() + " / " + allRows.size()
                + "   desactivadas: " + off).withStyle(ChatFormatting.GOLD), 156, 8, 0xFFFFFFFF);
        int visible = recVisibleRows();
        int x0 = 8;
        int w = width - 16;
        g.fill(x0 - 2, REC_TOP - 2, x0 + w + 2, REC_TOP + visible * REC_ROW_H + 2, 0x90000000);
        if (shownRows.isEmpty()) {
            g.drawString(font, "No hay recetas con ese filtro.", x0 + 6, REC_TOP + 6, 0xFFAAAAAA);
        }
        g.enableScissor(x0, REC_TOP, x0 + w, REC_TOP + visible * REC_ROW_H);
        for (int i = 0; i < visible; i++) {
            int idx = recScroll + i;
            if (idx >= shownRows.size()) break;
            Row r = shownRows.get(idx);
            int y = REC_TOP + i * REC_ROW_H;
            boolean hover = inside(mouseX, mouseY, x0, y, w, REC_ROW_H);
            g.fill(x0, y, x0 + w, y + REC_ROW_H - 2, hover ? 0x80FFFFFF : 0x40FFFFFF);
            g.renderItem(r.result, x0 + 3, y + 2);
            int textW = recToggleX() - (x0 + 26) - 4;
            String title = (r.ruleIndex >= 0 ? "[mia] " : "") + r.name + (r.result.getCount() > 1 ? " x" + r.result.getCount() : "");
            g.drawString(font, font.plainSubstrByWidth(title, textW), x0 + 24, y + 2, r.enabled ? 0xFFFFFFFF : 0xFF999999);
            g.drawString(font, font.plainSubstrByWidth(r.id + "  -  " + RecipeEntry.label(r.station), textW),
                    x0 + 24, y + 11, 0xFFAAAAAA);
            int bx = recToggleX();
            g.fill(bx, y + 2, bx + 80, y + REC_ROW_H - 4, r.enabled ? 0xFF2E7D32 : 0xFFB71C1C);
            g.drawCenteredString(font, r.enabled ? "ACTIVA" : "DESACTIVADA", bx + 40, y + 6, 0xFFFFFFFF);
        }
        g.disableScissor();

        super.render(g, mouseX, mouseY, partial);

        Row hov = recRowAt(mouseX, mouseY);
        if (hov != null && mouseX < recToggleX()) {
            g.renderTooltip(font, hov.result, mouseX, mouseY);
        }
    }

    private record Entry(ItemStack stack, String key) {
    }
}
