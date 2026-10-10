package dev.lifestages.net;

import dev.lifestages.LifeStages;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Every packet the mod sends. S2C = server to client, C2S = client to server. */
public final class Payloads {
	private Payloads() {
	}

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
		return new CustomPacketPayload.Type<>(LifeStages.id(name));
	}

	public record ParentOption(UUID id, String name) {
		public static final StreamCodec<ByteBuf, ParentOption> CODEC = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, ParentOption::id, ByteBufCodecs.STRING_UTF8, ParentOption::name, ParentOption::new);
	}

	/** S2C: show the life choice screen. mode 1 = first join (Adult, Baby), 2 = after death (1-4). */
	public record OpenChoice(int mode, boolean ghostLocked, List<ParentOption> parents) implements CustomPacketPayload {
		public static final Type<OpenChoice> TYPE = typeOf("open_choice");
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenChoice> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, OpenChoice::mode, ByteBufCodecs.BOOL, OpenChoice::ghostLocked,
				ParentOption.CODEC.apply(ByteBufCodecs.list()), OpenChoice::parents, OpenChoice::new);

		@Override
		public Type<OpenChoice> type() {
			return TYPE;
		}
	}

	/** S2C: a child of yours is crying; point at them. */
	/** S2C: a baby just hatched from your egg; open the screen to name them and choose their look. */
	public record OpenBabyDesigner(int entityId, String name, int look) implements CustomPacketPayload {
		public static final Type<OpenBabyDesigner> TYPE = typeOf("open_baby_designer");
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenBabyDesigner> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, OpenBabyDesigner::entityId, ByteBufCodecs.STRING_UTF8, OpenBabyDesigner::name,
				ByteBufCodecs.VAR_INT, OpenBabyDesigner::look, OpenBabyDesigner::new);

		@Override
		public Type<OpenBabyDesigner> type() {
			return TYPE;
		}
	}

	/** C2S: the name and look chosen for a new baby. */
	public record NameBaby(int entityId, String name, int look) implements CustomPacketPayload {
		public static final Type<NameBaby> TYPE = typeOf("name_baby");
		public static final StreamCodec<RegistryFriendlyByteBuf, NameBaby> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, NameBaby::entityId, ByteBufCodecs.STRING_UTF8, NameBaby::name,
				ByteBufCodecs.VAR_INT, NameBaby::look, NameBaby::new);

		@Override
		public Type<NameBaby> type() {
			return TYPE;
		}
	}

	public record CryArrow(int entityId) implements CustomPacketPayload {
		public static final Type<CryArrow> TYPE = typeOf("cry_arrow");
		public static final StreamCodec<RegistryFriendlyByteBuf, CryArrow> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, CryArrow::entityId, CryArrow::new);

		@Override
		public Type<CryArrow> type() {
			return TYPE;
		}
	}

	/** S2C: someone asked to become family; show a popup. */
	public record PairPrompt(String fromName, boolean fromIsParent) implements CustomPacketPayload {
		public static final Type<PairPrompt> TYPE = typeOf("pair_prompt");
		public static final StreamCodec<RegistryFriendlyByteBuf, PairPrompt> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, PairPrompt::fromName, ByteBufCodecs.BOOL, PairPrompt::fromIsParent, PairPrompt::new);

		@Override
		public Type<PairPrompt> type() {
			return TYPE;
		}
	}

	/** S2C: server settings the client needs to show ages correctly. */
	public record ConfigSync(double lifeLengthMultiplier, int carryAgeLimit) implements CustomPacketPayload {
		public static final Type<ConfigSync> TYPE = typeOf("config_sync");
		public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSync> CODEC = StreamCodec.composite(
				ByteBufCodecs.DOUBLE, ConfigSync::lifeLengthMultiplier, ByteBufCodecs.VAR_INT, ConfigSync::carryAgeLimit, ConfigSync::new);

		@Override
		public Type<ConfigSync> type() {
			return TYPE;
		}
	}

	/** C2S: the player's choice. parent is empty for "any parent / NPC Mom". */
	public record Choose(int option, Optional<UUID> parent, int momLook) implements CustomPacketPayload {
		public static final Type<Choose> TYPE = typeOf("choose");
		public static final StreamCodec<RegistryFriendlyByteBuf, Choose> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Choose::option, UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional), Choose::parent,
				ByteBufCodecs.VAR_INT, Choose::momLook, Choose::new);

		public Choose(int option, Optional<UUID> parent) {
			this(option, parent, 0);
		}

		@Override
		public Type<Choose> type() {
			return TYPE;
		}
	}

	/** C2S: an emote from the wheel (0 cry, 1 fart, 2 cute, 3 poop). */
	public record Emote(int kind) implements CustomPacketPayload {
		public static final Type<Emote> TYPE = typeOf("emote");
		public static final StreamCodec<RegistryFriendlyByteBuf, Emote> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Emote::kind, Emote::new);

		@Override
		public Type<Emote> type() {
			return TYPE;
		}
	}

	/** C2S: simple actions. See {@link Action}. */
	public record Act(int action, int target) implements CustomPacketPayload {
		public static final Type<Act> TYPE = typeOf("act");
		public static final StreamCodec<RegistryFriendlyByteBuf, Act> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Act::action, ByteBufCodecs.VAR_INT, Act::target, Act::new);

		@Override
		public Type<Act> type() {
			return TYPE;
		}
	}

	public static final class Action {
		public static final int PAIR = 0, WRIGGLE = 1, PUT_DOWN = 2, BOO = 3, FLICKER = 4, REBORN = 5,
				ALLOW_CARRY_ON = 6, ALLOW_CARRY_OFF = 7, LOOKING_ON = 8, LOOKING_OFF = 9, PAIR_ACCEPT = 10, PAIR_DECLINE = 11,
				SEARCH_PARENTS = 12;

		private Action() {
		}
	}
}
