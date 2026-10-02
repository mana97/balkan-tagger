package com.kevin.tiertagger.tierlist;

import com.kevin.tiertagger.model.PlayerInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.uku3lig.ukulib.config.screen.CloseableScreen;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * {@code /bktiers <player>}: shows "Loading X's profile..." while the Balkan Tiers search and the skin load,
 * then switches to the {@link PlayerInfoScreen}. On failure it tells whether the player isn't on Balkan Tiers or the
 * API is unreachable, with a Retry button - like the Tiers mod's profile screen.
 */
public class ProfileLoadingScreen extends CloseableScreen {
    private final String username;
    private Component status = Component.empty();
    private Component hint = null;
    private boolean failed = false;
    private CompletableFuture<?> future = null;
    private Button retryButton;

    public ProfileLoadingScreen(Screen parent, String username) {
        super(Component.literal(username), parent);
        this.username = username;
    }

    @Override
    protected void init() {
        this.retryButton = this.addRenderableWidget(Button.builder(Component.translatable("tiertagger.profile.retry"), b -> this.load())
                .bounds(this.width / 2 - 100, this.height / 2 + 30, 98, 20)
                .build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(this.width / 2 + 2, this.height / 2 + 30, 98, 20)
                .build());
        this.retryButton.visible = this.failed;

        if (this.future == null) {
            this.load();
        }
    }

    private void load() {
        this.failed = false;
        this.hint = null;
        this.status = Component.translatable("tiertagger.profile.loading", this.username).withStyle(ChatFormatting.GREEN);
        this.retryButton.visible = false;

        // the profile's Done button goes back to where /bktiers was typed (the game), not to this loading screen
        this.future = PlayerSearchScreen.loadProfile(this.parent, this.username, this.width, this.height)
                .whenComplete((screen, t) -> Minecraft.getInstance().execute(() -> {
                    if (Minecraft.getInstance().screen != this) return; // closed while loading
                    if (t == null) {
                        Minecraft.getInstance().setScreen(screen);
                    } else {
                        this.fail(t);
                    }
                }));
    }

    private void fail(Throwable t) {
        Throwable cause = t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
        boolean notFound = cause instanceof PlayerInfo.SearchException e && e.isNotFound();

        this.failed = true;
        this.status = Component.translatable(notFound ? "tiertagger.profile.notfound" : "tiertagger.profile.apifail", this.username)
                .withStyle(ChatFormatting.RED);
        this.hint = Component.translatable(notFound ? "tiertagger.profile.notfound.hint" : "tiertagger.profile.apifail.hint")
                .withStyle(ChatFormatting.YELLOW);
        this.retryButton.visible = true;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, this.status, this.width / 2, this.height / 2 - 14, 0xFFFFFFFF);
        if (this.hint != null) {
            graphics.drawCenteredString(this.font, this.hint, this.width / 2, this.height / 2, 0xFFFFFFFF);
        }
    }
}
