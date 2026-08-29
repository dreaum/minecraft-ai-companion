package adris.altoclef.butler;

import adris.altoclef.AltoClef;
import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.ChatMessageEvent;
import adris.altoclef.ui.ChatMessageSanitizer;
import adris.altoclef.ui.MessagePriority;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Lets whitelisted players drive the companion through public chat.
 * <p>
 * The companion no longer reads private whispers. A player whose exact name is on
 * "altoclef_butler_whitelist.txt" (and not on the blacklist) may simply talk in the
 * public chat channel and the message is forwarded to the local AI bridge.
 * <p>
 * Authorization depends on the "useButlerWhitelist" and "useButlerBlacklist" settings
 * in "altoclef_settings.json".
 */
public class Butler {

    private static final String BUTLER_MESSAGE_START = "` ";

    private final AltoClef mod;
    private final UserAuth userAuth;

    private static final long LOG_ECHO_WINDOW_MS = 30_000L;

    private String currentUser = null;
    private long lastRequestAt = 0L;
    private final Deque<String> recentPublicMessages = new ArrayDeque<>();
    private final Map<String, Long> recentPublicMessageTimes = new HashMap<>();

    public Butler(AltoClef mod) {
        this.mod = mod;
        userAuth = new UserAuth(mod);

        // Whitelisted players talk to the companion directly in public chat.
        EventBus.subscribe(ChatMessageEvent.class, evt -> {
            String message = evt.messageContent();
            String sender = evt.senderName();
            String receiver = mod.getPlayer().getName().getString();
            if (sender == null || sender.equalsIgnoreCase(receiver)) return;
            if (message == null || message.startsWith(BUTLER_MESSAGE_START)) return;
            if (isRecentPublicMessage(message)) return;

            if (userAuth.isUserAuthorized(sender)) {
                receiveAgentRequest(sender, message.trim());
            } else if (ButlerConfig.getInstance().sendAuthorizationResponse) {
                sendPublic(ButlerConfig.getInstance().failedAuthorizationResposne.replace("{from}", sender), MessagePriority.UNAUTHORIZED);
            }
        });
    }

    private void receiveAgentRequest(String username, String request) {
        if (request.isBlank()) return;
        // Keeps the owner context used by bridge tools (observe_world, altoclef_task).
        currentUser = username;
        lastRequestAt = System.currentTimeMillis();
        if (mod.getAgentBridge() == null || !mod.getAgentBridge().isConnected()) {
            sendPublic("AI backend is not connected.", MessagePriority.ASAP);
            return;
        }
        mod.getAgentBridge().submitUserRequest(username, request);
    }

    public boolean isUserAuthorized(String username) {
        return userAuth.isUserAuthorized(username);
    }

    public void onLog(String message, MessagePriority priority) {
        if (currentUser != null && System.currentTimeMillis() - lastRequestAt < LOG_ECHO_WINDOW_MS) {
            sendPublic(message, priority);
        }
    }

    public void onLogWarning(String message, MessagePriority priority) {
        if (currentUser != null && System.currentTimeMillis() - lastRequestAt < LOG_ECHO_WINDOW_MS) {
            sendPublic("[WARNING:] " + message, priority);
        }
    }

    public String getCurrentUser() {
        return currentUser;
    }

    public boolean hasCurrentUser() {
        return currentUser != null;
    }

    public void sendPublic(String message, MessagePriority priority) {
        String safe = truncateForChat(ChatMessageSanitizer.sanitize(message), 240);
        if (safe.isBlank()) return;
        synchronized (recentPublicMessages) {
            recentPublicMessages.addLast(safe);
            recentPublicMessageTimes.put(safe, System.currentTimeMillis());
            while (recentPublicMessages.size() > 8) recentPublicMessages.removeFirst();
        }
        mod.getMessageSender().enqueueChat(safe, priority);
    }

    private boolean isRecentPublicMessage(String message) {
        synchronized (recentPublicMessages) {
            long now = System.currentTimeMillis();
            recentPublicMessageTimes.entrySet().removeIf(e -> now - e.getValue() > 15000L);
            Long sentAt = recentPublicMessageTimes.get(message);
            return sentAt != null && now - sentAt <= 15000L;
        }
    }

    private static String truncateForChat(String message, int maxUtf8Bytes) {
        if (message == null || message.isEmpty()) return "";
        String suffix = "...";
        if (message.getBytes(StandardCharsets.UTF_8).length <= maxUtf8Bytes) return message;
        int limit = maxUtf8Bytes - suffix.length();
        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < message.length();) {
            int codePoint = message.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            if (result.toString().getBytes(StandardCharsets.UTF_8).length + character.getBytes(StandardCharsets.UTF_8).length > limit) break;
            result.append(character);
            offset += Character.charCount(codePoint);
        }
        return result + suffix;
    }
}
