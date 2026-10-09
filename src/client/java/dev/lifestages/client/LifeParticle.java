package dev.lifestages.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/** One class for the mod's six particles; each kind moves differently. */
public class LifeParticle extends SingleQuadParticle {
	public enum Kind { TEAR, FART, FLY, STINK, SPLAT, SPARKLE }

	private final Kind kind;
	private final SpriteSet sprites;
	private final double ox, oz;

	LifeParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, Kind kind, SpriteSet sprites, RandomSource random) {
		super(level, x, y, z, dx, dy, dz, sprites.get(random));
		this.kind = kind;
		this.sprites = sprites;
		this.ox = x;
		this.oz = z;
		this.hasPhysics = kind == Kind.TEAR || kind == Kind.SPLAT;
		switch (kind) {
			case TEAR -> {
				lifetime = 20 + random.nextInt(10);
				gravity = 0.9f;
				quadSize *= 0.6f;
			}
			case FART -> {
				lifetime = 40 + random.nextInt(20);
				gravity = -0.01f;
				friction = 0.92f;
				quadSize *= 1.3f;
				xd *= 0.3;
				yd = 0.01;
				zd *= 0.3;
				setSpriteFromAge(sprites);
			}
			case FLY -> {
				lifetime = 30;
				gravity = 0f;
				quadSize *= 0.5f;
			}
			case STINK -> {
				lifetime = 30 + random.nextInt(10);
				gravity = -0.02f;
				quadSize *= 1.2f;
				xd = 0;
				zd = 0;
			}
			case SPLAT -> {
				lifetime = 25;
				gravity = 1.0f;
				quadSize *= 0.7f;
				yd = 0.15 + random.nextDouble() * 0.1;
			}
			case SPARKLE -> {
				lifetime = 12 + random.nextInt(8);
				gravity = 0f;
				quadSize *= 0.6f;
				xd = yd = zd = 0;
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		switch (kind) {
			case FART -> {
				setSpriteFromAge(sprites);
				alpha = 0.8f * (1.0f - (float) age / lifetime);
			}
			case FLY -> {
				double a = age * 0.6;
				x = ox + Math.cos(a) * 0.35;
				z = oz + Math.sin(a) * 0.35;
				setSprite(sprites.get(age % 2, 2));
			}
			case STINK -> setSprite(sprites.get((age / 4) % 2, 2));
			case SPARKLE -> alpha = 1.0f - (float) age / lifetime;
			default -> {
			}
		}
	}

	@Override
	public Layer getLayer() {
		return Layer.TRANSLUCENT;
	}

	public static ParticleProvider<SimpleParticleType> provider(Kind kind, SpriteSet sprites) {
		return (type, level, x, y, z, dx, dy, dz, random) -> new LifeParticle(level, x, y, z, dx, dy, dz, kind, sprites, random);
	}

}
