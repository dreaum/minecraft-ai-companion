package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.commandsystem.exception.RuntimeCommandException;
import adris.altoclef.companion.ComeToPlayerTask;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Optional;

public class ComeCommand extends Command {
    public ComeCommand() {
        super("come", "Approaches the player who sent the private message");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        if (!mod.getButler().hasCurrentUser()) {
            throw new RuntimeCommandException("The come command can only be used through a private message.");
        }

        String username = mod.getButler().getCurrentUser();
        Optional<PlayerEntity> target = mod.getEntityTracker().getPlayerEntity(username);
        if (target.isEmpty()) {
            throw new RuntimeCommandException("I cannot see " + username + " yet. Move within my render distance and try again.");
        }

        mod.getCompanionSession().startApproaching(username);
        mod.runUserTask(new ComeToPlayerTask(target.get(), 2), () -> {
            mod.getCompanionSession().completeMovementIfActive();
            finish();
        });
    }
}
