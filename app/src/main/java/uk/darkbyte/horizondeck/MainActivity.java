package uk.darkbyte.horizondeck;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String UI_PREFS = "ui_state";
    private static final String KEY_LAST_SOURCE = "last_source";
    private static final String[] INTERVAL_LABELS = {
            "Manual", "1 minute", "1 hour", "6 hours", "1 day"
    };
    private static final long[] INTERVAL_VALUES = {
            0L, 60_000L, 3_600_000L, 21_600_000L, 86_400_000L
    };

    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final List<RepositorySource> sources = new ArrayList<>();
    private final List<CatalogItem> rootCategories = new ArrayList<>();

    private SourceStore sourceStore;
    private GitHubCatalogClient catalogClient;
    private PreviewCache previewCache;
    private SourceListAdapter sourceAdapter;
    private WallpaperGridAdapter gridAdapter;
    private RepositorySource activeSource;
    private String currentPath = "";
    private boolean allMode;
    private int requestGeneration;

    private LinearLayout categoryStrip;
    private HorizontalScrollView categoryScroll;
    private GridView grid;
    private TextView breadcrumb;
    private TextView status;
    private TextView activeIndicator;
    private EditText search;
    private ProgressBar progress;
    private Button backButton;
    private Button activateButton;
    private Button openSourceButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setNavigationBarColor(Ui.NAV);
        sourceStore = new SourceStore(this);
        catalogClient = new GitHubCatalogClient(this);
        previewCache = new PreviewCache(this);
        setContentView(buildUi());
        reloadSources();
        RepositorySource initial = lastSource();
        if (initial != null) selectSource(initial);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateActiveState();
    }

    @Override
    protected void onDestroy() {
        requestGeneration++;
        io.shutdownNow();
        previewCache.close();
        super.onDestroy();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BACKGROUND);

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 64)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        body.addView(buildSourceRail(), new LinearLayout.LayoutParams(Ui.dp(this, 218),
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = buildContent();
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        contentParams.leftMargin = Ui.dp(this, 12);
        contentParams.rightMargin = Ui.dp(this, 12);
        body.addView(content, contentParams);
        return root;
    }

    private View buildTopBar() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 16), Ui.dp(this, 6),
                Ui.dp(this, 16), Ui.dp(this, 6));
        top.setBackgroundColor(Ui.NAV);

        ImageView mark = new ImageView(this);
        mark.setImageResource(uk.darkbyte.horizondeck.R.drawable.horizondeck_mark);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        top.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 12), 0);
        brand.addView(Ui.title(this, "HorizonDeck", 22));
        top.addView(brand, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        activeIndicator = Ui.title(this, "CHECKING", 12);
        activeIndicator.setGravity(Gravity.CENTER);
        activeIndicator.setMinHeight(Ui.dp(this, 48));
        activeIndicator.setMinimumHeight(Ui.dp(this, 48));
        activeIndicator.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        top.addView(activeIndicator, new LinearLayout.LayoutParams(
                Ui.dp(this, 132), Ui.dp(this, 48)));

        Spinner interval = new Spinner(this);
        ArrayAdapter<String> intervals = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, INTERVAL_LABELS) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = Ui.text(MainActivity.this,
                        "↻  " + getItem(position), 14, Ui.TEXT);
                view.setPadding(Ui.dp(MainActivity.this, 12), 0, Ui.dp(MainActivity.this, 8), 0);
                return view;
            }
        };
        intervals.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        interval.setAdapter(intervals);
        interval.setBackground(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(this, 12), Ui.DIVIDER, 1));
        LinearLayout.LayoutParams intervalParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 148), Ui.dp(this, 48));
        intervalParams.leftMargin = Ui.dp(this, 8);
        top.addView(interval, intervalParams);
        configureInterval(interval);

        Button next = Ui.button(this, "▷  Next", false);
        next.setOnClickListener(view -> {
            sendBroadcast(new Intent(WallpaperEngineService.ACTION_NEXT).setPackage(getPackageName()));
            setStatus("Advanced to the next downloaded wallpaper.");
            next.postDelayed(() -> gridAdapter.refreshLibraryState(isWallpaperActive()), 500);
        });
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 96), Ui.dp(this, 48));
        actionParams.leftMargin = Ui.dp(this, 8);
        top.addView(next, actionParams);

        activateButton = Ui.button(this, "Activate", true);
        activateButton.setOnClickListener(view -> activateWallpaper());
        LinearLayout.LayoutParams activateParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 112), Ui.dp(this, 48));
        activateParams.leftMargin = Ui.dp(this, 8);
        top.addView(activateButton, activateParams);

        Button info = Ui.button(this, "Info", false);
        info.setOnClickListener(view -> showInfo());
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 64), Ui.dp(this, 48));
        infoParams.leftMargin = Ui.dp(this, 8);
        top.addView(info, infoParams);
        return top;
    }

    private View buildSourceRail() {
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setPadding(Ui.dp(this, 12), Ui.dp(this, 14),
                Ui.dp(this, 12), Ui.dp(this, 12));
        rail.setBackgroundColor(Ui.NAV);

        TextView heading = Ui.title(this, "Sources", 18);
        rail.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 38)));

        ListView sourceList = new ListView(this);
        sourceList.setDivider(null);
        sourceList.setSelector(android.R.color.transparent);
        sourceAdapter = new SourceListAdapter(this);
        sourceList.setAdapter(sourceAdapter);
        sourceList.setOnItemClickListener((parent, view, position, id) ->
                selectSource(sourceAdapter.getItem(position)));
        sourceList.setOnItemLongClickListener((parent, view, position, id) -> {
            promptRemoveSource(sourceAdapter.getItem(position));
            return true;
        });
        rail.addView(sourceList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        openSourceButton = Ui.button(this, "↗  View on GitHub", false);
        openSourceButton.setOnClickListener(view -> openSelectedSource());
        rail.addView(openSourceButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));

        Button add = Ui.button(this, "＋  Add source", false);
        add.setTextColor(Ui.CYAN);
        add.setBackground(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(this, 12),
                Ui.DIVIDER, Ui.dp(this, 1)));
        add.setOnClickListener(view -> showAddSourceDialog());
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48));
        addParams.topMargin = Ui.dp(this, 8);
        rail.addView(add, addParams);
        return rail;
    }

    private LinearLayout buildContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 2));
        backButton = Ui.button(this, "‹", false);
        backButton.setTextSize(26);
        backButton.setContentDescription("Back");
        backButton.setOnClickListener(view -> navigateUp());
        toolbar.addView(backButton, new LinearLayout.LayoutParams(
                Ui.dp(this, 44), Ui.dp(this, 48)));

        LinearLayout location = new LinearLayout(this);
        location.setOrientation(LinearLayout.VERTICAL);
        location.setGravity(Gravity.CENTER_VERTICAL);
        location.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        breadcrumb = Ui.title(this, "Choose a source", 19);
        breadcrumb.setSingleLine(true);
        location.addView(breadcrumb, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 28)));
        status = Ui.text(this, "Starting HorizonDeck…", 11, Ui.MUTED);
        status.setSingleLine(true);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        location.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 20)));
        toolbar.addView(location, new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1f));

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        toolbar.addView(progress, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));

        search = new EditText(this);
        search.setHint("Search wallpapers");
        search.setHintTextColor(Ui.MUTED);
        search.setTextColor(Ui.TEXT);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        search.setBackground(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(this, 12), Ui.DIVIDER, 1));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                gridAdapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 228), Ui.dp(this, 48));
        searchParams.leftMargin = Ui.dp(this, 8);
        toolbar.addView(search, searchParams);

        Button refresh = Ui.button(this, "↻", false);
        refresh.setTextSize(20);
        refresh.setContentDescription("Refresh wallpapers");
        refresh.setOnClickListener(view -> {
            catalogClient.clearCache();
            previewCache.clear();
            if (allMode) loadAll(); else loadDirectory(currentPath);
        });
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 48), Ui.dp(this, 48));
        refreshParams.leftMargin = Ui.dp(this, 8);
        toolbar.addView(refresh, refreshParams);
        content.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 62)));

        categoryScroll = new HorizontalScrollView(this);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        categoryStrip = new LinearLayout(this);
        categoryStrip.setOrientation(LinearLayout.HORIZONTAL);
        categoryStrip.setGravity(Gravity.CENTER_VERTICAL);
        categoryScroll.addView(categoryStrip, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(categoryScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 50)));

        grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setColumnWidth(Ui.dp(this, 210));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(Ui.dp(this, 8));
        grid.setVerticalSpacing(Ui.dp(this, 8));
        grid.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        grid.setClipToPadding(false);
        grid.setSelector(android.R.color.transparent);
        gridAdapter = new WallpaperGridAdapter(this, previewCache, this::handleItemAction);
        grid.setAdapter(gridAdapter);
        content.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return content;
    }

    private void configureInterval(Spinner spinner) {
        SharedPreferences preferences = getSharedPreferences(WallpaperEngineService.PREFS, MODE_PRIVATE);
        long saved = preferences.getLong(WallpaperEngineService.PREF_INTERVAL,
                WallpaperEngineService.DEFAULT_INTERVAL);
        int selected = 0;
        for (int i = 0; i < INTERVAL_VALUES.length; i++) {
            if (INTERVAL_VALUES[i] == saved) selected = i;
        }
        spinner.setSelection(selected);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                preferences.edit().putLong(WallpaperEngineService.PREF_INTERVAL,
                        INTERVAL_VALUES[position]).apply();
                sendBroadcast(new Intent(WallpaperEngineService.ACTION_LIBRARY_CHANGED)
                        .setPackage(getPackageName()));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void reloadSources() {
        sources.clear();
        sources.addAll(sourceStore.list());
        sourceAdapter.setSources(sources);
    }

    private void selectSource(RepositorySource source) {
        activeSource = source;
        getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit()
                .putString(KEY_LAST_SOURCE, source.id()).apply();
        currentPath = "";
        allMode = false;
        rootCategories.clear();
        search.setText("");
        sourceAdapter.setSelected(source);
        rebuildCategoryStrip();
        setStatus("Loading " + source.displayName + "…");
        loadDirectory("");
    }

    private RepositorySource lastSource() {
        if (sources.isEmpty()) return null;
        String saved = getSharedPreferences(UI_PREFS, MODE_PRIVATE)
                .getString(KEY_LAST_SOURCE, "");
        for (RepositorySource source : sources) {
            if (source.id().equals(saved)) return source;
        }
        return sources.get(0);
    }

    private void loadDirectory(String relativePath) {
        if (activeSource == null) return;
        RepositorySource source = activeSource;
        String requestedPath = RepositorySource.normalizePath(relativePath);
        int generation = ++requestGeneration;
        setLoading(true);
        setStatus("Loading " + (requestedPath.isEmpty() ? activeSource.displayName : requestedPath) + "…");
        io.execute(() -> {
            try {
                CatalogPage page = catalogClient.list(source, requestedPath);
                runOnUiThread(() -> {
                    if (generation != requestGeneration) return;
                    currentPath = requestedPath;
                    allMode = false;
                    if (requestedPath.isEmpty()) {
                        rootCategories.clear();
                        for (CatalogItem item : page.items) {
                            if (item.isDirectory()) rootCategories.add(item);
                        }
                    }
                    gridAdapter.setData(source, page.items);
                    rebuildCategoryStrip();
                    updateBreadcrumb();
                    setLoading(false);
                    String note = page.staleCache ? " • offline cache" : "";
                    setStatus(page.items.size() + " items" + note
                            + " • previews load only when visible");
                });
            } catch (Exception exception) {
                showLoadError(generation, exception);
            }
        });
    }

    private void loadAll() {
        if (activeSource == null) return;
        RepositorySource source = activeSource;
        int generation = ++requestGeneration;
        setLoading(true);
        setStatus("Building the complete " + activeSource.displayName + " index…");
        io.execute(() -> {
            try {
                CatalogPage page = catalogClient.listAll(source);
                runOnUiThread(() -> {
                    if (generation != requestGeneration) return;
                    allMode = true;
                    currentPath = "";
                    gridAdapter.setData(source, page.items);
                    rebuildCategoryStrip();
                    updateBreadcrumb();
                    setLoading(false);
                    String note = page.truncated ? " • GitHub index truncated" : "";
                    if (page.staleCache) note += " • offline cache";
                    setStatus(page.items.size() + " compatible wallpapers" + note);
                });
            } catch (Exception exception) {
                showLoadError(generation, exception);
            }
        });
    }

    private void showLoadError(int generation, Exception exception) {
        runOnUiThread(() -> {
            if (generation != requestGeneration) return;
            setLoading(false);
            setStatus("Could not load source: " + readableMessage(exception));
            Toast.makeText(this, readableMessage(exception), Toast.LENGTH_LONG).show();
        });
    }

    private void rebuildCategoryStrip() {
        categoryStrip.removeAllViews();
        addCategoryButton("Overview", !allMode && currentPath.isEmpty(),
                () -> loadDirectory(""));
        addCategoryButton("All wallpapers", allMode, this::loadAll);
        for (CatalogItem category : rootCategories) {
            String relativePath = activeSource.relativePath(category.path);
            boolean selected = !allMode && (currentPath.equals(relativePath)
                    || currentPath.startsWith(relativePath + "/"));
            addCategoryButton(category.name, selected,
                    () -> loadDirectory(activeSource.relativePath(category.path)));
        }
    }

    private void addCategoryButton(String label, boolean selected, Runnable action) {
        Button button = Ui.chip(this, label, selected);
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40));
        params.rightMargin = Ui.dp(this, 8);
        categoryStrip.addView(button, params);
        if (selected) {
            categoryScroll.post(() -> categoryScroll.smoothScrollTo(
                    Math.max(0, button.getLeft() - Ui.dp(this, 12)), 0));
        }
    }

    private void handleItemAction(CatalogItem item) {
        if (item.isDirectory()) {
            loadDirectory(activeSource.relativePath(item.path));
            return;
        }
        if (!WallpaperRules.canInstall(item)) {
            Toast.makeText(this, "This file exceeds HorizonDeck's safe size limit.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        RepositorySource source = activeSource;
        setStatus("Preparing " + item.name + "…");
        gridAdapter.setDownloadProgress(source, item, -1);
        io.execute(() -> {
            try {
                File file = WallpaperStore.installedFile(this, source, item);
                if (file == null) {
                    runOnUiThread(() -> gridAdapter.setDownloadProgress(source, item, 0));
                    int[] lastPercent = {-1};
                    file = WallpaperStore.install(this, source, item, (downloaded, total) -> {
                        int percent = total > 0 ? (int) Math.min(100, downloaded * 100 / total) : 0;
                        if (percent == lastPercent[0]) return;
                        lastPercent[0] = percent;
                        runOnUiThread(() -> {
                            gridAdapter.setDownloadProgress(source, item, percent);
                            setStatus("Downloading " + item.name + " • " + percent + "%");
                        });
                    });
                }
                WallpaperStore.select(this, file);
                sendBroadcast(new Intent(WallpaperEngineService.ACTION_LIBRARY_CHANGED)
                        .setPackage(getPackageName()));
                boolean active = isWallpaperActive();
                runOnUiThread(() -> {
                    gridAdapter.clearDownloadProgress(source, item);
                    gridAdapter.refreshLibraryState(active);
                    if (active) {
                        setStatus("Applied " + item.name + ".");
                        Toast.makeText(this, "Wallpaper applied", Toast.LENGTH_SHORT).show();
                    } else {
                        setStatus("Downloaded. Confirm HorizonDeck in Android's wallpaper screen.");
                        activateWallpaper();
                    }
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    gridAdapter.clearDownloadProgress(source, item);
                    setStatus("Could not apply wallpaper: " + readableMessage(exception));
                    Toast.makeText(this, readableMessage(exception), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void navigateUp() {
        if (allMode || currentPath.isEmpty()) {
            loadDirectory("");
            return;
        }
        int slash = currentPath.lastIndexOf('/');
        loadDirectory(slash < 0 ? "" : currentPath.substring(0, slash));
    }

    private void updateBreadcrumb() {
        String location = allMode ? "All wallpapers"
                : currentPath.isEmpty() ? "Overview" : currentPath;
        String value = activeSource.displayName + "  ›  " + location;
        SpannableString styled = new SpannableString(value);
        styled.setSpan(new ForegroundColorSpan(Ui.CYAN), 0,
                activeSource.displayName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        breadcrumb.setText(styled);
        boolean canGoBack = allMode || !currentPath.isEmpty();
        backButton.setEnabled(canGoBack);
        backButton.setVisibility(canGoBack ? View.VISIBLE : View.GONE);
    }

    private void updateActiveState() {
        boolean active = isWallpaperActive();
        activeIndicator.setText(active ? "WALLPAPER ON" : "WALLPAPER OFF");
        activeIndicator.setTextColor(active ? Ui.CYAN : Ui.CORAL);
        activeIndicator.setBackground(Ui.rounded(active ? Ui.CYAN_DARK : Ui.SURFACE_HIGH,
                Ui.dp(this, 12), active ? Ui.CYAN : Ui.CORAL, Ui.dp(this, 1)));
        activateButton.setText(active ? "Active" : "Activate");
        activateButton.setEnabled(!active);
        if (gridAdapter != null) gridAdapter.refreshLibraryState(active);
    }

    private boolean isWallpaperActive() {
        WallpaperInfo info = WallpaperManager.getInstance(this).getWallpaperInfo();
        return info != null && new ComponentName(this, WallpaperEngineService.class)
                .equals(info.getComponent());
    }

    private void activateWallpaper() {
        ComponentName component = new ComponentName(this, WallpaperEngineService.class);
        Intent change = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        change.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component);
        try {
            startActivity(change);
        } catch (Exception exception) {
            try {
                startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
            } catch (Exception fallback) {
                Toast.makeText(this, "Android's wallpaper picker is unavailable.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openSelectedSource() {
        if (activeSource == null) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(activeSource.repositoryUrl())));
        } catch (Exception exception) {
            Toast.makeText(this, "No browser is available.", Toast.LENGTH_LONG).show();
        }
    }

    private void showAddSourceDialog() {
        int pad = Ui.dp(this, 24);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(pad, Ui.dp(this, 20), pad, Ui.dp(this, 18));
        panel.setBackground(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(this, 18),
                Ui.DIVIDER, Ui.dp(this, 1)));

        panel.addView(Ui.title(this, "Add wallpaper source", 22),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 36)));
        TextView description = Ui.text(this,
                "Connect a public GitHub repository. Folders become categories automatically.",
                13, Ui.MUTED);
        description.setMaxLines(2);
        panel.addView(description, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46)));

        EditText repository = dialogField("GitHub URL or owner/repository");
        EditText folder = dialogField("Starting folder (optional)");
        EditText name = dialogField("Display name (optional)");
        addDialogField(panel, repository);
        addDialogField(panel, folder);
        addDialogField(panel, name);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Button cancel = Ui.button(this, "Cancel", false);
        Button add = Ui.button(this, "Add source", true);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 106), Ui.dp(this, 46));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 122), Ui.dp(this, 46));
        addParams.leftMargin = Ui.dp(this, 10);
        actions.addView(cancel, actionParams);
        actions.addView(add, addParams);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 58));
        actionsParams.topMargin = Ui.dp(this, 8);
        panel.addView(actions, actionsParams);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).create();
        cancel.setOnClickListener(view -> dialog.dismiss());
        add.setOnClickListener(view -> {
            add.setEnabled(false);
            setStatus("Validating GitHub repository…");
            io.execute(() -> {
                try {
                    RepositorySourceParser.ParsedSource parsed =
                            RepositorySourceParser.parse(repository.getText().toString());
                    RepositorySource source = catalogClient.resolveSource(parsed,
                            name.getText().toString(), folder.getText().toString());
                    sourceStore.add(source);
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        reloadSources();
                        selectSource(source);
                        hideKeyboard();
                    });
                } catch (Exception exception) {
                    runOnUiThread(() -> {
                        add.setEnabled(true);
                        repository.setError(readableMessage(exception));
                        setStatus("Repository not added: " + readableMessage(exception));
                    });
                }
            });
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(Ui.dp(this, 570), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private EditText dialogField(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setHintTextColor(Ui.MUTED);
        field.setTextColor(Ui.TEXT);
        field.setSingleLine(true);
        field.setTextSize(15);
        field.setPadding(Ui.dp(this, 14), 0,
                Ui.dp(this, 12), 0);
        field.setMinHeight(Ui.dp(this, 52));
        field.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 10),
                Ui.DIVIDER, Ui.dp(this, 1)));
        return field;
    }

    private void addDialogField(LinearLayout panel, EditText field) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52));
        params.topMargin = Ui.dp(this, 10);
        panel.addView(field, params);
    }

    private void promptRemoveSource(RepositorySource source) {
        if (source.builtIn) {
            Toast.makeText(this, "Curated sources remain available by default.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Remove " + source.displayName + "?")
                .setMessage("Downloaded wallpapers remain in your local library.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (ignoredDialog, which) -> {
                    sourceStore.remove(source);
                    reloadSources();
                    if (!sources.isEmpty()) selectSource(sources.get(0));
                })
                .create();
        dialog.show();
        styleDialog(dialog);
    }

    private void showInfo() {
        double previewMb = previewCache.diskBytes() / (1024.0 * 1024.0);
        double libraryMb = WallpaperStore.totalBytes(this) / (1024.0 * 1024.0);
        String message = String.format(Locale.ROOT,
                "HorizonDeck 1.2.1\n\n"
                        + "• Public GitHub repositories only\n"
                        + "• Folder categories use a representative wallpaper cover\n"
                        + "• Download and active states stay visible on each card\n"
                        + "• 480×270 previews are cached locally\n"
                        + "• GIF animation pauses behind other apps\n\n"
                        + "Preview cache: %.1f MB / 96 MB\n"
                        + "Downloaded library: %.1f MB\n\n"
                        + "Data saver requests reduced previews from wsrv.nl and falls back to the "
                        + "original GitHub image when needed. Turn it off to use GitHub directly. "
                        + "Artwork remains subject to each source and creator's terms.",
                previewMb, libraryMb);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("About HorizonDeck")
                .setMessage(message)
                .setNegativeButton("Clear previews", (ignoredDialog, which) -> {
                    previewCache.clear();
                    gridAdapter.notifyDataSetChanged();
                    setStatus("Preview cache cleared.");
                })
                .setNeutralButton(previewCache.isDataSaverEnabled()
                        ? "Data saver: on" : "Data saver: off", null)
                .setPositiveButton("Done", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            styleDialog(dialog);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                    boolean enabled = !previewCache.isDataSaverEnabled();
                    previewCache.setDataSaverEnabled(enabled);
                    dialog.dismiss();
                    setStatus("Preview data saver " + (enabled ? "enabled." : "disabled."));
                    showInfo();
                });
        });
        dialog.show();
    }

    private void styleDialog(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(this, 18),
                    Ui.DIVIDER, Ui.dp(this, 1)));
        }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) message.setTextColor(Ui.MUTED);
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (positive != null) positive.setTextColor(Ui.CYAN);
        if (negative != null) negative.setTextColor(Ui.MUTED);
        if (neutral != null) neutral.setTextColor(Ui.CYAN);
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void setStatus(String message) {
        if (status != null) status.setText(message);
    }

    private void hideKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View focused = getCurrentFocus();
        if (manager != null && focused != null) manager.hideSoftInputFromWindow(focused.getWindowToken(), 0);
    }

    private static String readableMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? "Unexpected error" : message;
    }
}
