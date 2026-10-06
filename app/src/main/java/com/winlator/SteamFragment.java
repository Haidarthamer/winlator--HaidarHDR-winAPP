package com.winlator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.FileUtils;
import com.winlator.core.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SteamFragment extends Fragment {
    private static final int OPEN_STEAM_INSTALLER = 601;
    private static final int PAD = 18;

    private ContainerManager manager;
    private Spinner containerSpinner;
    private LinearLayout gameList;
    private TextView installState;
    private TextView accountState;
    private EditText speedLimit;
    private Spinner downloadsDuringGameplay;
    private Container selectedContainer;
    private File steamDir;
    private File steamExe;

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        manager = new ContainerManager(context);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(PAD), dp(12), dp(PAD), dp(28));
        scrollView.addView(root);

        TextView title = text("STEAM", 28, Color.WHITE);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title, lp(-1, -2));

        TextView subtitle = text("Real Steam client integration for Winlator", 14, 0xFF9DA7B3);
        root.addView(subtitle, lp(-1, -2));

        root.addView(space(14), lp(1, dp(1)));

        LinearLayout containerCard = card(context);
        containerCard.addView(sectionTitle("Steam Container"));
        containerSpinner = new Spinner(context);
        containerCard.addView(containerSpinner, lp(-1, dp(52)));
        Button refresh = button("Refresh library");
        containerCard.addView(refresh, lp(-1, dp(48)));
        installState = text("Scanning…", 13, 0xFF9DA7B3);
        installState.setPadding(0, dp(8), 0, 0);
        containerCard.addView(installState, lp(-1, -2));
        root.addView(containerCard, lp(-1, -2));

        LinearLayout accountCard = card(context);
        accountCard.addView(sectionTitle("Account"));
        accountState = text("Steam account is managed by the official Steam client", 14, 0xFFD7DCE2);
        accountCard.addView(accountState, lp(-1, -2));
        Button launchSteam = button("Launch Steam");
        Button switchAccount = button("Switch account in Steam");
        accountCard.addView(launchSteam, lp(-1, dp(48)));
        accountCard.addView(switchAccount, lp(-1, dp(48)));
        root.addView(accountCard, lp(-1, -2));

        LinearLayout libraryCard = card(context);
        libraryCard.addView(sectionTitle("Library"));
        gameList = new LinearLayout(context);
        gameList.setOrientation(LinearLayout.VERTICAL);
        libraryCard.addView(gameList, lp(-1, -2));
        root.addView(libraryCard, lp(-1, -2));

        LinearLayout settingsCard = card(context);
        settingsCard.addView(sectionTitle("Steam settings"));
        settingsCard.addView(text("Download region", 15, Color.WHITE), lp(-1, -2));
        Button regionButton = button("Open Steam Downloads settings");
        settingsCard.addView(regionButton, lp(-1, dp(48)));

        LinearLayout speedRow = new LinearLayout(context);
        speedRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView speedLabel = text("Download limit (KB/s)", 15, Color.WHITE);
        speedRow.addView(speedLabel, new LinearLayout.LayoutParams(0, dp(52), 1));
        speedLimit = new EditText(context);
        speedLimit.setSingleLine(true);
        speedLimit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        speedLimit.setHint("0 = unlimited");
        speedLimit.setText(PreferenceManager.getDefaultSharedPreferences(context).getString("steam_speed_limit_kbs", "0"));
        speedRow.addView(speedLimit, lp(dp(150), dp(52)));
        settingsCard.addView(speedRow, lp(-1, dp(52)));

        Button applySpeed = button("Apply download limit");
        settingsCard.addView(applySpeed, lp(-1, dp(48)));

        settingsCard.addView(text("Downloads while playing", 15, Color.WHITE), lp(-1, dp(30)));
        downloadsDuringGameplay = new Spinner(context);
        downloadsDuringGameplay.setAdapter(new ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item,
            new String[]{"Blocked while playing", "Allowed while playing"}));
        settingsCard.addView(downloadsDuringGameplay, lp(-1, dp(52)));

        Button applyGameplay = button("Apply Steam download settings");
        settingsCard.addView(applyGameplay, lp(-1, dp(48)));

        TextView cloud = text("Cloud saves: handled by Steam. Open the real Steam settings to change account/library/cloud behavior.", 13, 0xFF9DA7B3);
        cloud.setPadding(0, dp(8), 0, dp(8));
        settingsCard.addView(cloud, lp(-1, -2));

        Button openSteamSettings = button("Open Steam settings");
        settingsCard.addView(openSteamSettings, lp(-1, dp(48)));

        root.addView(settingsCard, lp(-1, -2));

        LinearLayout installCard = card(context);
        installCard.addView(sectionTitle("Steam installation"));
        installCard.addView(text("Steam is the actual Windows client inside the selected container. Your password and Steam Guard stay inside Steam; Winlator never stores them.", 13, 0xFF9DA7B3), lp(-1, -2));
        Button install = button("Import SteamSetup.exe");
        Button download = button("Open official Steam download page");
        installCard.addView(install, lp(-1, dp(48)));
        installCard.addView(download, lp(-1, dp(48)));
        root.addView(installCard, lp(-1, -2));

        containerSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (manager == null || manager.getContainers().isEmpty()) return;
                selectedContainer = manager.getContainers().get(pos);
                PreferenceManager.getDefaultSharedPreferences(context).edit().putInt("steam_container_id", selectedContainer.id).apply();
                refreshSteamState();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        refresh.setOnClickListener(v -> refreshSteamState());
        launchSteam.setOnClickListener(v -> launchSteamClient());
        switchAccount.setOnClickListener(v -> launchSteamClient());
        regionButton.setOnClickListener(v -> launchSteamClient());
        openSteamSettings.setOnClickListener(v -> launchSteamClient());
        applySpeed.setOnClickListener(v -> applySteamSettings());
        applyGameplay.setOnClickListener(v -> applySteamSettings());
        install.setOnClickListener(v -> chooseSteamInstaller());
        download.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://store.steampowered.com/about/download"));
            startActivity(intent);
        });

        loadContainers();
        return scrollView;
    }

    private void loadContainers() {
        ArrayList<Container> containers = manager.getContainers();
        ArrayList<String> names = new ArrayList<>();
        for (Container c : containers) names.add(c.getName());
        containerSpinner.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, names));

        if (!containers.isEmpty()) {
            int wanted = PreferenceManager.getDefaultSharedPreferences(requireContext()).getInt("steam_container_id", containers.get(0).id);
            int index = 0;
            for (int i = 0; i < containers.size(); i++) if (containers.get(i).id == wanted) { index = i; break; }
            containerSpinner.setSelection(index, false);
            selectedContainer = containers.get(index);
            refreshSteamState();
        }
        else {
            installState.setText("Create a container first.");
            gameList.removeAllViews();
        }
    }

    private void refreshSteamState() {
        if (selectedContainer == null) return;
        steamExe = findSteamExe(selectedContainer);
        steamDir = steamExe != null ? steamExe.getParentFile() : null;
        boolean installed = steamExe != null && steamExe.isFile();
        installState.setText(installed
            ? "Steam detected: " + steamExe.getPath()
            : "Steam is not installed in this container.");
        accountState.setText(installed ? "Steam is ready. Login and account switching use the real Steam client." : "Install SteamSetup.exe into this container.");
        renderGames(installed ? discoverGames(selectedContainer, steamDir) : new ArrayList<>());
    }

    private void renderGames(ArrayList<GameInfo> games) {
        gameList.removeAllViews();
        if (games.isEmpty()) {
            gameList.addView(text("No installed Steam games detected in this container yet.", 14, 0xFF9DA7B3), lp(-1, dp(52)));
            return;
        }

        for (GameInfo game : games) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(7), 0, dp(7));

            LinearLayout labels = new LinearLayout(requireContext());
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView name = text(game.name, 15, Color.WHITE);
            TextView id = text("AppID " + game.appId, 12, 0xFF8E98A4);
            labels.addView(name, lp(-1, -2));
            labels.addView(id, lp(-1, -2));
            row.addView(labels, new LinearLayout.LayoutParams(0, dp(58), 1));

            Button play = button("Play");
            play.setOnClickListener(v -> launchSteamGame(game.appId));
            row.addView(play, lp(dp(96), dp(44)));
            gameList.addView(row, lp(-1, -2));
        }
    }

    private void launchSteamClient() {
        if (selectedContainer == null) return;
        steamExe = findSteamExe(selectedContainer);
        if (steamExe == null) {
            Toast.makeText(requireContext(), "Steam is not installed in this container.", Toast.LENGTH_LONG).show();
            return;
        }
        launchViaWinlator(steamExe, "");
    }

    private void launchSteamGame(String appId) {
        if (selectedContainer == null || steamExe == null) return;
        launchViaWinlator(steamExe, "-applaunch " + appId);
    }

    private void launchViaWinlator(File exe, String args) {
        try {
            File launchDir = new File(selectedContainer.getRootDir(), ".winlator-steam");
            if (!launchDir.exists() && !launchDir.mkdirs()) throw new Exception("Unable to create launcher directory");

            String windowsPath = toWindowsPath(selectedContainer, exe);
            String safeName = args.isEmpty() ? "Steam" : "Steam-App-" + args.replaceAll("[^0-9]", "");
            File desktop = new File(launchDir, StringUtils.clearReservedChars(safeName) + ".desktop");
            String content = "[Desktop Entry]\nName=" + safeName + "\nExec=wine " + StringUtils.escapeDOSPath(windowsPath) + (args.isEmpty() ? "" : " " + args) + "\n";
            FileUtils.writeString(desktop, content);

            Intent intent = new Intent(requireActivity(), XServerDisplayActivity.class);
            intent.putExtra("container_id", selectedContainer.id);
            intent.putExtra("shortcut_path", desktop.getPath());
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Unable to start Steam: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void chooseSteamInstaller() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/octet-stream");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, OPEN_STEAM_INSTALLER);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != OPEN_STEAM_INSTALLER || resultCode != Activity.RESULT_OK || data == null || selectedContainer == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        try {
            File launcherDir = new File(selectedContainer.getRootDir(), ".winlator-steam");
            if (!launcherDir.exists()) launcherDir.mkdirs();
            File installer = new File(launcherDir, "SteamSetup.exe");

            try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(installer)) {
                if (in == null) throw new Exception("Unable to open installer");
                byte[] buffer = new byte[1024 * 1024];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            }

            Toast.makeText(requireContext(), "Launching Steam installer…", Toast.LENGTH_SHORT).show();
            launchViaWinlator(installer, "");
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Unable to import Steam installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void applySteamSettings() {
        if (selectedContainer == null) return;
        steamExe = findSteamExe(selectedContainer);
        if (steamExe == null) {
            Toast.makeText(requireContext(), "Install Steam first.", Toast.LENGTH_LONG).show();
            return;
        }

        File config = new File(steamExe.getParentFile(), "config/config.vdf");
        if (!config.isFile()) {
            Toast.makeText(requireContext(), "Steam config.vdf was not found yet. Launch Steam once first.", Toast.LENGTH_LONG).show();
            return;
        }

        String limitText = speedLimit.getText().toString().trim();
        int kbPerSecond;
        try {
            kbPerSecond = Math.max(0, Integer.parseInt(limitText.isEmpty() ? "0" : limitText));
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "Enter a valid KB/s value.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean allowDuringGameplay = downloadsDuringGameplay.getSelectedItemPosition() == 1;
        String data = FileUtils.readString(config);
        data = replaceVdfNumber(data, "DownloadThrottleKbps", kbPerSecond == 0 ? 0 : kbPerSecond * 8);
        data = replaceVdfNumber(data, "AllowDownloadsDuringGameplay", allowDuringGameplay ? 1 : 0);
        File backup = new File(config.getParentFile(), "config.vdf.winlator-backup");
        try {
            FileUtils.writeString(backup, FileUtils.readString(config));
            FileUtils.writeString(config, data);
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                .putString("steam_speed_limit_kbs", String.valueOf(kbPerSecond)).apply();
            Toast.makeText(requireContext(), "Steam download settings applied. Restart Steam to reload them.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Could not update Steam settings.", Toast.LENGTH_LONG).show();
        }
    }

    private String replaceVdfNumber(String data, String key, int value) {
        Pattern pattern = Pattern.compile("(?m)^(\\s*)\"" + Pattern.quote(key) + "\"\\s+\"[^\"]*\"");
        Matcher matcher = pattern.matcher(data);
        String replacement = "$1\"" + key + "\" \"" + value + "\"";
        return matcher.find() ? matcher.replaceFirst(Matcher.quoteReplacement(replacement)) : data;
    }

    private File findSteamExe(Container c) {
        File[] candidates = new File[]{
            new File(c.getRootDir(), ".wine/drive_c/Program Files (x86)/Steam/steam.exe"),
            new File(c.getRootDir(), ".wine/drive_c/Program Files/Steam/steam.exe"),
            new File(c.getRootDir(), ".wine/drive_c/Steam/steam.exe")
        };
        for (File f : candidates) if (f.isFile()) return f;
        return null;
    }

    private ArrayList<GameInfo> discoverGames(Container c, File steamInstallDir) {
        LinkedHashSet<String> libraryPaths = new LinkedHashSet<>();
        File defaultSteamApps = new File(steamInstallDir, "steamapps");
        if (defaultSteamApps.isDirectory()) libraryPaths.add(defaultSteamApps.getPath());

        File libraries = new File(steamInstallDir, "config/libraryfolders.vdf");
        if (libraries.isFile()) {
            String vdf = FileUtils.readString(libraries);
            Matcher m = Pattern.compile("\"path\"\\s+\"([^\"]+)\"").matcher(vdf);
            while (m.find()) {
                String path = m.group(1).replace("\\\\", "\\");
                File host = windowsLibraryPath(c, path);
                if (host != null) libraryPaths.add(new File(host, "steamapps").getPath());
            }
        }

        ArrayList<GameInfo> result = new ArrayList<>();
        for (String libraryPath : libraryPaths) {
            File dir = new File(libraryPath);
            File[] manifests = dir.listFiles((d, n) -> n.startsWith("appmanifest_") && n.endsWith(".acf"));
            if (manifests == null) continue;

            for (File manifest : manifests) {
                String body = FileUtils.readString(manifest);
                String appId = extractVdfString(body, "appid");
                String name = extractVdfString(body, "name");
                String state = extractVdfString(body, "StateFlags");
                if (!appId.isEmpty() && !name.isEmpty() && (state.isEmpty() || "4".equals(state))) result.add(new GameInfo(appId, name));
            }
        }

        result.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return result;
    }

    private File windowsLibraryPath(Container c, String windowsPath) {
        if (windowsPath == null || windowsPath.length() < 2 || windowsPath.charAt(1) != ':') return null;
        char drive = Character.toUpperCase(windowsPath.charAt(0));
        String relative = windowsPath.substring(2).replace('\\', File.separatorChar).replace('/', File.separatorChar);
        for (com.winlator.container.Drive d : c.drivesIterator()) {
            if (!d.letter.isEmpty() && Character.toUpperCase(d.letter.charAt(0)) == drive) return new File(d.path, relative);
        }
        if (drive == 'C') return new File(c.getRootDir(), ".wine/drive_c" + relative);
        return null;
    }

    private String extractVdfString(String body, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s+\"([^\"]*)\"").matcher(body);
        return m.find() ? m.group(1) : "";
    }

    private String toWindowsPath(Container c, File file) {
        String root = new File(c.getRootDir(), ".wine/drive_c").getPath();
        String path = file.getPath();
        if (path.startsWith(root)) {
            String rel = path.substring(root.length()).replace(File.separatorChar, '\\');
            return "C:" + rel;
        }
        for (com.winlator.container.Drive d : c.drivesIterator()) {
            String driveRoot = new File(d.path).getPath();
            if (path.startsWith(driveRoot)) {
                String rel = path.substring(driveRoot.length()).replace(File.separatorChar, '\\');
                return d.letter + ":" + rel;
            }
        }
        return path.replace(File.separatorChar, '\\');
    }

    private LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFF242830);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0xFF353C46);
        card.setBackground(bg);
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(p);
        return card;
    }

    private TextView sectionTitle(String value) {
        TextView v = text(value, 14, 0xFF66B7FF);
        v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
        v.setPadding(0, 0, 0, dp(10));
        return v;
    }

    private Button button(String label) {
        Button b = new Button(requireContext());
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setMinHeight(0);
        b.setPadding(dp(10), 0, dp(10), 0);
        return b;
    }

    private TextView text(String value, float size, int color) {
        TextView v = new TextView(requireContext());
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private View space(int w) {
        View v = new View(requireContext());
        v.setLayoutParams(new ViewGroup.LayoutParams(w, dp(1)));
        return v;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w == -1 || w == 1 ? w : dp(w), h == -1 || h == -2 ? h : dp(h));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class GameInfo {
        final String appId;
        final String name;
        GameInfo(String appId, String name) { this.appId = appId; this.name = name; }
    }
}
