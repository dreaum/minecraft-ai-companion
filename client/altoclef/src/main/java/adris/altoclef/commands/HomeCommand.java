package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.tasks.movement.GetToBlockTask;

public class HomeCommand extends Command {
    public HomeCommand() {
        super("home", "Returns to the configured home position");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) {
        mod.getCompanionSession().startReturningHome();
        mod.runUserTask(new GetToBlockTask(mod.getModSettings().getHomeBasePosition()), () -> {
            mod.getCompanionSession().completeMovementIfActive();
            finish();
        });
    }
}
