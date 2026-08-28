package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.RuntimeCommandException;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.ui.MessagePriority;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Local model configuration. API keys are deliberately never accepted from chat. */
public final class AIConfigCommand extends Command {
    public AIConfigCommand() {
        super("ai_config", "View or update the local agent model configuration.",
                new StringArg("action"), new StringArg("value", ""));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String action = parser.get(String.class).toLowerCase();
        String value = parser.get(String.class);
        Path file = mod.getAgentStore().root().resolve("llm.properties");
        Properties props = new Properties();
        try {
            if (Files.exists(file)) {
                try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { props.load(reader); }
            }
            switch (action) {
                case "show" -> mod.log("AI config: model=" + props.getProperty("model", "")
                        + ", url=" + props.getProperty("url", "")
                        + ", key=" + (props.getProperty("key", "").isBlank() ? "not set" : "set"), MessagePriority.TIMELY);
                case "model", "url" -> {
                    if (value.isBlank()) throw new RuntimeCommandException("Value cannot be empty.");
                    if (action.equals("url") && !(value.startsWith("http://") || value.startsWith("https://")))
                        throw new RuntimeCommandException("URL must start with http:// or https://.");
                    props.setProperty(action, value);
                    write(file, props);
                    mod.log("AI " + action + " updated. Restart the agent bridge to apply it.");
                }
                case "key" -> throw new RuntimeCommandException("For security, API keys cannot be entered in Minecraft chat. Edit " + file + " locally, then restart the bridge.");
                default -> throw new RuntimeCommandException("Usage: ai_config show | ai_config model <name> | ai_config url <http(s) URL>");
            }
        } catch (RuntimeCommandException e) { throw e; }
        catch (Exception e) { throw new RuntimeCommandException("Cannot update AI config: " + e.getMessage()); }
        finish();
    }

    private static void write(Path file, Properties props) throws Exception {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (var writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            props.store(writer, "Minecraft AI Companion local agent configuration");
        }
        Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
