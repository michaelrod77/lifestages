package dev.lifestages.block;

import dev.lifestages.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class GravestoneBlockEntity extends BlockEntity {
	private String name = "Someone";
	private int age;

	public GravestoneBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.GRAVESTONE_ENTITY, pos, state);
	}

	public void set(String name, int age) {
		this.name = name;
		this.age = age;
		setChanged();
	}

	public String epitaph() {
		return age > 0 ? "Here lies " + name + ", who lived to " + age + "." : "Here lies " + name + ".";
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		out.putString("name", name);
		out.putInt("age", age);
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		name = in.getStringOr("name", "Someone");
		age = in.getIntOr("age", 0);
	}
}
