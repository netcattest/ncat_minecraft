package com.netcattest.ncatminecraft.client.sftp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.netcattest.ncatminecraft.client.ssh.SshClientSessions;
import com.netcattest.ncatminecraft.client.rack.RackClientApps;
import com.netcattest.ncatminecraft.entity.RackModuleType;
import com.netcattest.ncatminecraft.utilities.DataCableService;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.net.server_bound.C2SMessageDataActivity;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.utilities.browser.WDBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SftpClientSessions {
    private static final String PAGE = "mod://ncat_minecraft/sftp.html";
    private static final int MAX_TEXT_BYTES = 256 * 1024;
    private static final int MAX_ENTRIES = 500;
    private static final Map<CefBrowser, Explorer> EXPLORERS = new ConcurrentHashMap<>();

    private SftpClientSessions() { }

    public static boolean accepts(CefBrowser browser, CefFrame frame) {
        if (frame == null || !frame.isMain() || !PAGE.equals(frame.getURL()) || !PAGE.equals(browser.getURL()))
            return false;
        if (RackClientApps.accepts(browser, RackModuleType.SFTP)) return true;
        if (!(browser instanceof WDBrowser view) || view.getBe() == null || view.getSide() == null) return false;
        if (!BlockRegistry.isSftpScreen(view.getBe().getBlockState().getBlock())) return false;
        ScreenData screen = view.getBe().getScreen(view.getSide());
        return screen != null && screen.browser == browser;
    }

    public static void handle(CefBrowser browser, JsonObject data, CefQueryCallback callback) {
        String action = string(data, "action", 24);
        if (action == null) {
            callback.failure(400, "invalidAction");
            return;
        }
        Explorer explorer = EXPLORERS.computeIfAbsent(browser, Explorer::new);
        Session ssh = sshSession(browser);
        boolean hasLink = linked(browser);
        explorer.authorized = ssh;
        if ("status".equals(action)) {
            callback.success(explorer.status(hasLink, ssh).toString());
            return;
        }
        explorer.worker.execute(() -> {
            try {
                JsonObject result = explorer.run(action, data, ssh);
                Minecraft.getInstance().execute(() -> {
                    callback.success(result.toString());
                    if (!"disconnect".equals(action)) pulseLinked(browser);
                });
            } catch (SftpFailure failure) {
                Minecraft.getInstance().execute(() -> callback.failure(400, failure.code));
            } catch (Exception error) {
                Minecraft.getInstance().execute(() -> callback.failure(500, "remoteError"));
            }
        });
    }

    public static void tick() {
        for (Map.Entry<CefBrowser, Explorer> entry : EXPLORERS.entrySet()) {
            CefBrowser browser = entry.getKey();
            Explorer explorer = entry.getValue();
            if (!RackClientApps.accepts(browser, RackModuleType.SFTP) &&
                    (!(browser instanceof WDBrowser view) || view.getBe() == null || view.getSide() == null ||
                    view.getBe().getLevel() == null || view.getBe().getScreen(view.getSide()) == null ||
                    view.getBe().getScreen(view.getSide()).browser != browser)) {
                if (EXPLORERS.remove(browser, explorer)) explorer.close();
            } else {
                explorer.dropDisconnected();
            }
        }
    }

    public static void closeAll() {
        for (CefBrowser browser : EXPLORERS.keySet()) {
            Explorer explorer = EXPLORERS.remove(browser);
            if (explorer != null) explorer.close();
        }
    }

    private static Session sshSession(CefBrowser browser) {
        if (RackClientApps.accepts(browser, RackModuleType.SFTP)) {
            CefBrowser source = RackClientApps.linkedBrowser(browser, RackModuleType.SSH);
            return source == null ? null : SshClientSessions.connectedSession(source);
        }
        if (!(browser instanceof WDBrowser view) || view.getBe() == null || view.getSide() == null) return null;
        if (view.getBe().getLevel() != null) {
            for (DataCableService.RackModuleEndpoint target : DataCableService.connectedRackModules(
                    view.getBe().getLevel(), view.getBe(), view.getSide())) {
                if (target.module().type() != RackModuleType.SSH || !target.module().powered()) continue;
                CefBrowser source = RackClientApps.browser(target, 1280, 720);
                Session session = source == null ? null : SshClientSessions.connectedSession(source);
                if (session != null) return session;
            }
        }
        ScreenData sftp = view.getBe().getScreen(view.getSide());
        if (sftp == null || sftp.logSourcePos == null || sftp.logSourceSide == null) return null;
        Level level = view.getBe().getLevel();
        if (level == null || !level.hasChunkAt(sftp.logSourcePos.toBlock())) return null;
        if (!(level.getBlockEntity(sftp.logSourcePos.toBlock()) instanceof ScreenBlockEntity source) ||
                !BlockRegistry.isSshScreen(source.getBlockState().getBlock())) return null;
        ScreenData ssh = source.getScreen(sftp.logSourceSide);
        return ssh == null || ssh.browser == null ? null : SshClientSessions.connectedSession(ssh.browser);
    }

    private static boolean linked(CefBrowser browser) {
        if (RackClientApps.accepts(browser, RackModuleType.SFTP))
            return RackClientApps.linkedBrowser(browser, RackModuleType.SSH) != null;
        if (!(browser instanceof WDBrowser view) || view.getBe() == null || view.getSide() == null) return false;
        if (view.getBe().getLevel() != null)
            for (DataCableService.RackModuleEndpoint target : DataCableService.connectedRackModules(
                    view.getBe().getLevel(), view.getBe(), view.getSide()))
                if (target.module().type() == RackModuleType.SSH && target.module().powered()) return true;
        ScreenData sftp = view.getBe().getScreen(view.getSide());
        return sftp != null && sftp.logSourcePos != null && sftp.logSourceSide != null;
    }

    private static void pulseLinked(CefBrowser browser) {
        if (RackClientApps.accepts(browser, RackModuleType.SFTP)) {
            RackClientApps.sendFlow(browser, RackClientApps.linkedBrowser(browser, RackModuleType.SSH));
            return;
        }
        if (!(browser instanceof WDBrowser view) || view.getBe() == null || view.getSide() == null) return;
        if (view.getBe().getLevel() != null) {
            for (DataCableService.RackModuleEndpoint target : DataCableService.connectedRackModules(
                    view.getBe().getLevel(), view.getBe(), view.getSide())) {
                if (target.module().type() != RackModuleType.SSH || !target.module().powered()) continue;
                CefBrowser source = RackClientApps.browser(target, 1280, 720);
                if (source != null) { RackClientApps.sendFlow(browser, source); return; }
            }
        }
        ScreenData sftp = view.getBe().getScreen(view.getSide());
        Level level = view.getBe().getLevel();
        if (sftp == null || sftp.logSourcePos == null || sftp.logSourceSide == null || level == null ||
                !level.hasChunkAt(sftp.logSourcePos.toBlock())) return;
        if (level.getBlockEntity(sftp.logSourcePos.toBlock()) instanceof ScreenBlockEntity ssh &&
                BlockRegistry.isSshScreen(ssh.getBlockState().getBlock()))
            C2SMessageDataActivity.send(view.getBe(), view.getSide(), ssh, sftp.logSourceSide);
    }

    private static String string(JsonObject data, String key, int max) {
        try {
            if (data == null || !data.has(key) || !data.get(key).isJsonPrimitive()) return null;
            String value = data.get(key).getAsString();
            return value.length() > max || value.chars().anyMatch(Character::isISOControl) ? null : value;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static String path(JsonObject data, String key) throws SftpFailure {
        String value = string(data, key, 4096);
        if (value == null || value.isBlank()) throw new SftpFailure("invalidPath");
        return value;
    }

    private static JsonObject result(String key, String value) {
        JsonObject object = new JsonObject();
        object.addProperty(key, value);
        return object;
    }

    private static final class Explorer {
        private final CefBrowser browser;
        private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "NCAT-SFTP");
            thread.setDaemon(true);
            return thread;
        });
        private volatile ChannelSftp channel;
        private volatile Session parent;
        private volatile Session authorized;
        private volatile boolean disconnectQueued;

        private Explorer(CefBrowser browser) { this.browser = browser; }

        private JsonObject status(boolean hasLink, Session ssh) {
            JsonObject response = new JsonObject();
            response.addProperty("linked", hasLink);
            response.addProperty("sshReady", ssh != null);
            response.addProperty("sftpReady", ssh != null && ssh == parent && channel != null && channel.isConnected());
            return response;
        }

        private void dropDisconnected() {
            authorized = sshSession(browser);
            if (parent != null && parent != authorized && !disconnectQueued) {
                disconnectQueued = true;
                worker.execute(() -> {
                    disconnect();
                    disconnectQueued = false;
                });
            }
        }

        private JsonObject run(String action, JsonObject data, Session ssh) throws Exception {
            if ("disconnect".equals(action)) {
                disconnect();
                return result("status", "closed");
            }
            if (ssh == null || ssh != authorized || !ssh.isConnected()) {
                disconnect();
                throw new SftpFailure("sshUnavailable");
            }
            if ("connect".equals(action)) {
                if (channel == null || parent != ssh || !channel.isConnected()) {
                    disconnect();
                    ChannelSftp opened = (ChannelSftp) ssh.openChannel("sftp");
                    opened.connect(10000);
                    parent = ssh;
                    channel = opened;
                }
                return result("cwd", channel.pwd());
            }
            ChannelSftp sftp = channel;
            if (sftp == null || parent != ssh || !sftp.isConnected()) {
                disconnect();
                throw new SftpFailure("sftpUnavailable");
            }
            return switch (action) {
                case "list" -> list(sftp, path(data, "path"));
                case "read" -> read(sftp, path(data, "path"));
                case "save" -> save(sftp, path(data, "path"), data);
                case "mkdir" -> { sftp.mkdir(path(data, "path")); yield result("status", "created"); }
                case "create" -> create(sftp, path(data, "path"));
                case "rename" -> rename(sftp, path(data, "path"), path(data, "target"));
                case "delete" -> remove(sftp, path(data, "path"));
                case "chmod" -> chmod(sftp, path(data, "path"), data);
                default -> throw new SftpFailure("invalidAction");
            };
        }

        private JsonObject list(ChannelSftp sftp, String path) throws SftpException {
            String canonical = sftp.realpath(path);
            ArrayList<ChannelSftp.LsEntry> sorted = new ArrayList<>();
            sftp.ls(canonical, entry -> {
                if (!".".equals(entry.getFilename()) && !"..".equals(entry.getFilename())) sorted.add(entry);
                return sorted.size() > MAX_ENTRIES ? ChannelSftp.LsEntrySelector.BREAK : ChannelSftp.LsEntrySelector.CONTINUE;
            });
            sorted.sort(Comparator.comparing((ChannelSftp.LsEntry entry) -> !entry.getAttrs().isDir())
                    .thenComparing(ChannelSftp.LsEntry::getFilename, String.CASE_INSENSITIVE_ORDER));
            JsonArray files = new JsonArray();
            for (int i = 0; i < Math.min(sorted.size(), MAX_ENTRIES); i++) {
                ChannelSftp.LsEntry entry = sorted.get(i);
                SftpATTRS attrs = entry.getAttrs();
                JsonObject file = new JsonObject();
                file.addProperty("name", entry.getFilename());
                file.addProperty("directory", attrs.isDir());
                file.addProperty("link", attrs.isLink());
                file.addProperty("size", attrs.getSize());
                file.addProperty("mode", String.format("%04o", attrs.getPermissions() & 07777));
                file.addProperty("modified", attrs.getMTime());
                files.add(file);
            }
            JsonObject response = new JsonObject();
            response.addProperty("path", canonical);
            response.addProperty("truncated", sorted.size() > MAX_ENTRIES);
            response.add("entries", files);
            return response;
        }

        private JsonObject read(ChannelSftp sftp, String path) throws Exception {
            SftpATTRS attrs = sftp.stat(path);
            if (!attrs.isReg()) throw new SftpFailure("notRegularFile");
            if (attrs.getSize() > MAX_TEXT_BYTES) throw new SftpFailure("fileTooLarge");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) attrs.getSize());
            try (InputStream input = sftp.get(path)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > MAX_TEXT_BYTES) throw new SftpFailure("fileTooLarge");
                    bytes.write(buffer, 0, count);
                }
            }
            String content;
            try {
                content = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
            } catch (CharacterCodingException invalid) {
                throw new SftpFailure("binaryFile");
            }
            if (content.indexOf('\0') >= 0) throw new SftpFailure("binaryFile");
            JsonObject response = result("path", path);
            response.addProperty("content", content);
            response.addProperty("mode", String.format("%04o", attrs.getPermissions() & 07777));
            response.addProperty("size", attrs.getSize());
            response.addProperty("modified", attrs.getMTime());
            return response;
        }

        private JsonObject save(ChannelSftp sftp, String path, JsonObject data) throws Exception {
            if (data == null || !data.has("content") || !data.get("content").isJsonPrimitive())
                throw new SftpFailure("invalidData");
            String content = data.get("content").getAsString();
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_TEXT_BYTES) throw new SftpFailure("fileTooLarge");
            SftpATTRS attrs = sftp.lstat(path);
            if (!attrs.isReg()) throw new SftpFailure("notRegularFile");
            if (data == null || !data.has("expectedSize") || !data.has("expectedModified") ||
                    attrs.getSize() != data.get("expectedSize").getAsLong() ||
                    attrs.getMTime() != data.get("expectedModified").getAsInt())
                throw new SftpFailure("fileChanged");
            String suffix = ".ncat-" + UUID.randomUUID().toString().substring(0, 12);
            String temporary = path + suffix + ".tmp";
            String backup = path + suffix + ".bak";
            boolean movedOriginal = false;
            try {
                try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
                    sftp.put(input, temporary, ChannelSftp.OVERWRITE);
                }
                sftp.chmod(attrs.getPermissions() & 07777, temporary);
                try { sftp.chown(attrs.getUId(), temporary); } catch (SftpException ignored) { }
                try { sftp.chgrp(attrs.getGId(), temporary); } catch (SftpException ignored) { }
                sftp.rename(path, backup);
                movedOriginal = true;
                sftp.rename(temporary, path);
                movedOriginal = false;
            } catch (Exception error) {
                if (movedOriginal) {
                    try { sftp.rename(backup, path); } catch (SftpException ignored) { }
                }
                try { sftp.rm(temporary); } catch (SftpException ignored) { }
                throw error;
            }
            boolean backupRetained = false;
            try { sftp.rm(backup); } catch (SftpException ignored) { backupRetained = true; }
            SftpATTRS updated = sftp.stat(path);
            JsonObject response = result("status", "saved");
            response.addProperty("size", updated.getSize());
            response.addProperty("modified", updated.getMTime());
            if (backupRetained) response.addProperty("backup", backup);
            return response;
        }

        private JsonObject create(ChannelSftp sftp, String path) throws Exception {
            try {
                sftp.lstat(path);
                throw new SftpFailure("alreadyExists");
            } catch (SftpException absent) {
                if (absent.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw absent;
            }
            try (ByteArrayInputStream input = new ByteArrayInputStream(new byte[0])) {
                sftp.put(input, path, ChannelSftp.OVERWRITE);
            }
            return result("status", "created");
        }

        private JsonObject rename(ChannelSftp sftp, String path, String target) throws Exception {
            if (path.equals(target)) return result("status", "renamed");
            try {
                sftp.lstat(target);
                throw new SftpFailure("alreadyExists");
            } catch (SftpException absent) {
                if (absent.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw absent;
            }
            sftp.rename(path, target);
            return result("status", "renamed");
        }

        private JsonObject remove(ChannelSftp sftp, String path) throws SftpException {
            if (sftp.lstat(path).isDir()) sftp.rmdir(path);
            else sftp.rm(path);
            return result("status", "deleted");
        }

        private JsonObject chmod(ChannelSftp sftp, String path, JsonObject data) throws Exception {
            String mode = string(data, "mode", 4);
            if (mode == null || !mode.matches("[0-7]{3,4}")) throw new SftpFailure("invalidMode");
            sftp.chmod(Integer.parseInt(mode, 8), path);
            return result("status", "permissionsChanged");
        }

        private void disconnect() {
            ChannelSftp active = channel;
            channel = null;
            parent = null;
            if (active != null) active.disconnect();
        }

        private void close() {
            worker.execute(this::disconnect);
            worker.shutdown();
        }
    }

    private static final class SftpFailure extends Exception {
        private final String code;
        private SftpFailure(String code) { this.code = code; }
    }
}
