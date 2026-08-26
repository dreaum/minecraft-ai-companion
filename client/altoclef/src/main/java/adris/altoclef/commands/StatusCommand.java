package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
public class StatusCommand extends Command {
    public StatusCommand() {
        super("status", "Get status of currently executing command");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) {
        int health = (int) Math.ceil(mod.getPlayer().getHealth());
        mod.log(mod.getCompanionSession().describe() + " Health: " + health + "/20.");
        finish();
    }
}
