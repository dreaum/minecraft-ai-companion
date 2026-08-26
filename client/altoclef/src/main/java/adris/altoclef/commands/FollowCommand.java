package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.commandsystem.exception.RuntimeCommandException;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.tasks.movement.FollowPlayerTask;

public class FollowCommand extends Command {
    public FollowCommand() {
        super("follow", "Follows you or someone else",
                new StringArg("username", null)
        );
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String username = parser.get(String.class);

        if (mod.getButler().hasCurrentUser()) {
            String sender = mod.getButler().getCurrentUser();
            if (username != null && !username.equalsIgnoreCase(sender)) {
                throw new RuntimeCommandException("In companion mode, follow can only follow the player who sent the private message.");
            }
            username = sender;
        } else if (username == null) {
            mod.logWarning("No butler user currently present. Running this command with no user argument can ONLY be done via butler.");
            finish();
            return;
        }

        mod.getCompanionSession().startFollowing(username);
        mod.runUserTask(new FollowPlayerTask(username), this::finish);
    }
}
