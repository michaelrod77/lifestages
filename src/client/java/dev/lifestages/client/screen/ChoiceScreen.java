package dev.lifestages.client.screen;

import dev.lifestages.net.Payloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Choose how to begin (Adult or Baby) or, after a death, how to come back (1-4). Cannot be closed without choosing. */
public class ChoiceScreen extends Screen {
	private record Card(int option, String title, String age, String[] lines, int color) {
	}

	private final int mode;
	private final boolean ghostLocked;
	private List<Payloads.ParentOption> parents;
	private int parentIndex = -1;
	/** 0 not searched yet, 1 searching, 2 searched (and found nobody, if the list is still empty). */
	private int search;
	private long searchStarted;
	private final List<Card> cards = new ArrayList<>();

	public ChoiceScreen(int mode, boolean ghostLocked, List<Payloads.ParentOption> parents) {
		super(Component.literal(mode == 1 ? "Begin your life" : "Choose your next life"));
		this.mode = mode;
		this.ghostLocked = ghostLocked;
		this.parents = parents;
		cards.add(new Card(1, "1  Adult", "Young Adult, age 18", new String[]{"Full size, full life ahead.", "Build, explore, raise a family."}, 0xFF7FB8E6));
		cards.add(new Card(2, "2  Baby", "Newborn", new String[]{"Tiny, carried and fed.", "Grow up with a parent", "or an NPC Mom."}, 0xFFF2A2BE));
		if (mode == 2) {
			cards.add(new Card(3, "3  Elderly", "Elder, age 65", new String[]{"About 30 minutes left.", "Starts with 5 XP levels", "and a Walking Cane."}, 0xFFC9B8E8));
			cards.add(new Card(4, "4  Ghost", ghostLocked ? "Not twice in a row" : "Up to 10 minutes", new String[]{"Float through walls.", "Boo! Flicker lamps.", "Then choose 1, 2 or 3."}, 0xFFB9D7C8));
		}
	}

	@Override
	protected void init() {
		int n = cards.size();
		int w = cardWidth();
		int total = n * w + (n - 1) * 8;
		int x0 = (width - total) / 2;
		int y = height / 2 + 40;
		for (int i = 0; i < n; i++) {
			Card c = cards.get(i);
			boolean locked = c.option == 4 && ghostLocked;
			Button b = Button.builder(Component.literal("Choose"), btn -> choose(c.option)).bounds(x0 + i * (w + 8) + 6, y, w - 12, 20).build();
			b.active = !locked;
			addRenderableWidget(b);
		}
		if (!parents.isEmpty()) {
			addRenderableWidget(Button.builder(parentLabel(), btn -> {
				parentIndex = parentIndex + 1 >= parents.size() ? -1 : parentIndex + 1;
				btn.setMessage(parentLabel());
			}).bounds(width / 2 - 100, height / 2 + 72, 200, 20).build());
		} else {
			// Nobody is looking for a baby yet: give a grown-up time to switch it on, then search again.
			Button b = Button.builder(searchLabel(), btn -> {
				search = 1;
				searchStarted = System.currentTimeMillis();
				btn.setMessage(searchLabel());
				btn.active = false;
				ClientPlayNetworking.send(new Payloads.Act(Payloads.Action.SEARCH_PARENTS, 0));
			}).bounds(width / 2 - 100, height / 2 + 72, 200, 20).build();
			b.active = search != 1;
			addRenderableWidget(b);
		}
	}

	private Component searchLabel() {
		return Component.literal(search == 1 ? "Searching for parents..." : search == 2 ? "No parents found. Search again" : "Search for parents");
	}

	/** The server's latest list of grown-ups looking for a baby (sent every couple of seconds while choosing). */
	public void updateParents(List<Payloads.ParentOption> latest) {
		boolean changed = !latest.equals(parents);
		boolean finishSearch = search == 1 && System.currentTimeMillis() - searchStarted > 600;
		if (!changed && !finishSearch) {
			return;
		}
		if (finishSearch) {
			search = 2;
		}
		parents = latest;
		if (parentIndex >= parents.size()) {
			parentIndex = -1;
		}
		rebuildWidgets();
	}

	private Component parentLabel() {
		return Component.literal("Baby's parent: " + (parentIndex < 0 ? "Anyone (or NPC Mom)" : parents.get(parentIndex).name()));
	}

	private void choose(int option) {
		Optional<java.util.UUID> parent = option == 2 && parentIndex >= 0 ? Optional.of(parents.get(parentIndex).id()) : Optional.empty();
		if (option == 2 && parent.isEmpty()) {
			minecraft.setScreenAndShow(new MomPickerScreen(parent)); // a Baby with no chosen parent picks their Mom
			return;
		}
		ClientPlayNetworking.send(new Payloads.Choose(option, parent));
		onClose();
	}

	private int cardWidth() {
		int n = cards.size();
		return Math.min(130, (width - 30) / n - 8);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		super.extractBackground(g, mouseX, mouseY, partial);
		g.centeredText(font, title, width / 2, height / 2 - 95, 0xFFFFFFFF);
		g.centeredText(font, Component.literal(mode == 1 ? "You can come back as something new each time you die." : "Every life ends. What will you be next?"),
				width / 2, height / 2 - 82, 0xFFB8B8C8);
		int n = cards.size();
		int w = cardWidth();
		int total = n * w + (n - 1) * 8;
		int x0 = (width - total) / 2;
		int top = height / 2 - 66;
		for (int i = 0; i < n; i++) {
			Card c = cards.get(i);
			int x = x0 + i * (w + 8);
			boolean locked = c.option == 4 && ghostLocked;
			g.fill(x, top, x + w, height / 2 + 66, 0xD0101018);
			g.fill(x, top, x + w, top + 3, locked ? 0xFF555560 : c.color);
			g.centeredText(font, c.title, x + w / 2, top + 10, locked ? 0xFF808088 : 0xFFFFFFFF);
			g.centeredText(font, c.age, x + w / 2, top + 24, locked ? 0xFF808088 : c.color);
			int ly = top + 42;
			for (String line : c.lines) {
				for (var part : font.split(Component.literal(line), w - 8)) {
					g.centeredText(font, part, x + w / 2, ly, 0xFFD0D0DA);
					ly += 10;
				}
			}
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
