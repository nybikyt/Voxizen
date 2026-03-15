package dev.nybikyt.voxizen.commands;

import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.BinaryTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.generator.*;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import de.maxhenkel.voicechat.api.VolumeCategory;
import dev.nybikyt.voxizen.VoiceAddon;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class VolumeCategoryCommand extends AbstractCommand {

    private static final int ICON_SIZE = 16;
    private static final String ID_PATTERN = "[a-z_]{1,16}";

    public static final Map<String, VolumeCategory> categories = new ConcurrentHashMap<>();

    public enum Instruction { CREATE, DELETE }

    public VolumeCategoryCommand() {
        setName("volumecategory");
        setSyntax("volumecategory [create/delete] [id:<id>] (name:<n>) (icon:<binary>/<base64>) (description:<text>) (name_translation_key:<key>) (description_translation_key:<key>)");
        setRequiredArguments(2, 7);
        isProcedural = false;
        autoCompile();
    }

    // <--[command]
    // @Name volumecategory
    // @Syntax volumecategory [create/delete] [id:<id>] (name:<n>) (icon:<binary/base64>) (description:<text>) (name_translation_key:<key>) (description_translation_key:<key>)
    // @Required 2
    // @Maximum 7
    // @Short Creates or deletes a Simple Voice Chat volume category.
    // @Group Voxizen
    //
    // @Description
    // Creates or deletes a volume category for Simple Voice Chat.
    // Volume categories let players control the volume of specific audio sources
    // separately in the Voice Chat GUI.
    //
    // id          — unique identifier, 1–16 chars, only lowercase a-z and _.
    // name        — display name shown in the GUI.
    // icon        — BinaryTag or base64-encoded image. Automatically scaled to 16x16.
    // description — optional tooltip text.
    // name_translation_key / description_translation_key — i18n keys (optional).
    //
    // Use <@link command voicesource> with the category argument to assign a source to a category.
    //
    // @Usage
    // - volumecategory create id:music name:Music description:"Background music volume"
    //
    // @Usage
    // # From a file
    // - ~fileread path:data/icon.png save:file
    // - volumecategory create id:ambient name:Ambient icon:<entry[file].data>
    //
    // @Usage
    // - volumecategory delete id:music
    //
    // -->

    public static void autoExecute(
            ScriptEntry scriptEntry,
            @ArgName("instruction") Instruction instruction,
            @ArgPrefixed @ArgName("id") ElementTag id,
            @ArgPrefixed @ArgDefaultNull @ArgName("name") ElementTag name,
            @ArgPrefixed @ArgDefaultNull @ArgName("icon") ObjectTag icon,
            @ArgPrefixed @ArgDefaultNull @ArgName("description") ElementTag description,
            @ArgPrefixed @ArgDefaultNull @ArgName("name_translation_key") ElementTag nameTranslationKey,
            @ArgPrefixed @ArgDefaultNull @ArgName("description_translation_key") ElementTag descriptionTranslationKey
    ) {
        switch (instruction) {
            case CREATE -> handleCreate(scriptEntry, id, name, icon, description, nameTranslationKey, descriptionTranslationKey);
            case DELETE -> handleDelete(scriptEntry, id);
        }
    }

    private static void handleCreate(
            ScriptEntry scriptEntry,
            ElementTag id,
            ElementTag name,
            ObjectTag icon,
            ElementTag description,
            ElementTag nameTranslationKey,
            ElementTag descriptionTranslationKey
    ) {
        String categoryId = id.asString();

        if (!categoryId.matches(ID_PATTERN)) {
            Debug.echoError(scriptEntry, "Volume category id must be 1–16 characters, only lowercase a-z and _.");
            return;
        }

        if (categories.containsKey(categoryId)) {
            Debug.echoError(scriptEntry, "Volume category '%s' already exists!".formatted(categoryId));
            return;
        }

        if (name == null) {
            Debug.echoError(scriptEntry, "Volume category requires 'name'");
            return;
        }

        VolumeCategory.Builder builder = VoiceAddon.getApi().volumeCategoryBuilder()
                .setId(categoryId)
                .setName(name.asString());

        if (nameTranslationKey != null) builder.setNameTranslationKey(nameTranslationKey.asString());
        if (description != null) builder.setDescription(description.asString());
        if (descriptionTranslationKey != null) builder.setDescriptionTranslationKey(descriptionTranslationKey.asString());

        if (icon != null) {
            try {
                builder.setIcon(resolveIcon(icon));
            } catch (Exception e) {
                Debug.echoError(scriptEntry, "Failed to parse icon for volume category '%s': %s".formatted(categoryId, e.getMessage()));
                return;
            }
        }

        VolumeCategory category = builder.build();
        VoiceAddon.getApi().registerVolumeCategory(category);
        categories.put(categoryId, category);

        scriptEntry.saveObject("id", id);
        scriptEntry.saveObject("category", new ElementTag(categoryId));
    }

    private static void handleDelete(ScriptEntry scriptEntry, ElementTag id) {
        String categoryId = id.asString();
        VolumeCategory category = categories.remove(categoryId);

        if (category == null) {
            Debug.echoError(scriptEntry, "Volume category '%s' not found!".formatted(categoryId));
            return;
        }

        VoiceAddon.getApi().unregisterVolumeCategory(category);
    }


    private static int[][] resolveIcon(ObjectTag icon) throws IOException {
        byte[] bytes;
        if (Objects.requireNonNull(icon) instanceof BinaryTag binary) {
            bytes = binary.data;
        } else {
            bytes = Base64.getDecoder().decode(icon.toString());
        }
        return toIconGrid(bytes);
    }

    private static int[][] toIconGrid(byte[] imageBytes) throws IOException {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));

        if (original == null) {
            throw new IOException("Could not decode image from provided bytes.");
        }

        BufferedImage scaled = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaled.createGraphics();

        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(original, 0, 0, ICON_SIZE, ICON_SIZE, null);
        } finally {
            g2d.dispose();
        }

        int[][] grid = new int[ICON_SIZE][ICON_SIZE];
        for (int x = 0; x < ICON_SIZE; x++) {
            for (int y = 0; y < ICON_SIZE; y++) {
                grid[x][y] = scaled.getRGB(x, y);
            }
        }

        return grid;
    }
}