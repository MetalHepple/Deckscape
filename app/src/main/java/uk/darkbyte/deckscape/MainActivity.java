package uk.darkbyte.deckscape;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
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
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Landscape-first catalog browser and controller for Deckscape's live-wallpaper service.
 * Network and disk work is delegated to a bounded executor; UI mutations return to the main
 * thread and are guarded by request generations when navigation supersedes an older request.
 */
public final class MainActivity extends Activity {
    private static final int REQUEST_FOREGROUND_LOCATION = 41;
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
    private UpdateManager updateManager;
    private GitHubMetadataClient metadataClient;
    private RepositoryMetadata aboutMetadata;
    private DayNightSettings dayNightSettings;
    private CoarseLocationClient locationClient;
    private UpdateManager.State updateState;
    private SourceListAdapter sourceAdapter;
    private WallpaperGridAdapter gridAdapter;
    private RepositorySource activeSource;
    private String currentPath = "";
    private boolean allMode;
    private boolean activationGuideShown;
    private boolean pendingUpdateInstall;
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
    private Button modeButton;
    private Button openSourceButton;
    private Button infoButton;
    private Button settingsButton;
    private AlertDialog settingsDialog;
    private Button settingsDayNightToggle;
    private TextView settingsDayNightStatus;
    private Spinner settingsScheduleMode;
    private TextView settingsDayTimeLabel;
    private TextView settingsNightTimeLabel;
    private Spinner settingsDayTimeSpinner;
    private Spinner settingsNightTimeSpinner;
    private TextView settingsDayCalculatedTime;
    private TextView settingsNightCalculatedTime;
    private Button settingsLocationButton;
    private boolean pendingEnableDayNight;
    private AlertDialog infoDialog;
    private AlertDialog updateDialog;
    private TextView infoUpdateStatus;
    private TextView updateDialogStatus;
    private ProgressBar updateDialogProgress;
    private Button updateDialogAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setNavigationBarColor(Ui.NAV);
        sourceStore = new SourceStore(this);
        catalogClient = new GitHubCatalogClient(this);
        metadataClient = new GitHubMetadataClient(this);
        previewCache = new PreviewCache(this);
        dayNightSettings = new DayNightSettings(this);
        locationClient = new CoarseLocationClient(this);
        setContentView(buildUi());
        updateManager = new UpdateManager(this, this::onUpdateStateChanged);
        updateState = updateManager.state();
        updateManager.start();
        reloadSources();
        RepositorySource initial = lastSource();
        if (initial != null) selectSource(initial);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateActiveState();
        if (pendingUpdateInstall) {
            if (UpdateInstaller.hasInstallPermission(this)) {
                pendingUpdateInstall = false;
                launchVerifiedUpdate();
            } else {
                pendingUpdateInstall = false;
                Toast.makeText(this, "Install permission was not enabled.",
                        Toast.LENGTH_LONG).show();
            }
        }
        if (!activationGuideShown && !isWallpaperActive()) {
            activationGuideShown = true;
            modeButton.post(this::showActivationGuide);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_FOREGROUND_LOCATION) return;
        if (hasUsableForegroundLocationPermission()) {
            resolveAutomaticLocation();
        } else {
            boolean shouldEnable = pendingEnableDayNight;
            pendingEnableDayNight = false;
            refreshSettingsStatus();
            Toast.makeText(this,
                    shouldEnable
                            ? "GPS access was not enabled. Day & Night remains off; choose Manual or try again."
                            : "GPS access was not enabled. Choose Manual times or try again.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onStop() {
        if (locationClient != null && locationClient.isRequesting()) {
            cancelAutomaticLocation(false);
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        requestGeneration++;
        io.shutdownNow();
        previewCache.close();
        locationClient.cancel();
        if (updateManager != null) updateManager.close();
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
        mark.setImageResource(R.drawable.deckscape_mark);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        top.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 12), 0);
        brand.addView(Ui.title(this, getString(R.string.app_name), 22));
        top.addView(brand, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        activeIndicator = Ui.title(this, "CHECKING", 12);
        activeIndicator.setGravity(Gravity.CENTER);
        activeIndicator.setMinHeight(Ui.dp(this, 48));
        activeIndicator.setMinimumHeight(Ui.dp(this, 48));
        activeIndicator.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        activeIndicator.setFocusable(true);
        activeIndicator.setOnClickListener(view -> {
            if (isWallpaperActive()) showSlideshowLibrary();
            else showActivationGuide();
        });
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
            setStatus("Advanced to the next slideshow wallpaper.");
            next.postDelayed(() -> gridAdapter.refreshLibraryState(isWallpaperActive()), 500);
        });
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 96), Ui.dp(this, 48));
        actionParams.leftMargin = Ui.dp(this, 8);
        top.addView(next, actionParams);

        modeButton = Ui.button(this, "Library", true);
        modeButton.setOnClickListener(view -> showSlideshowLibrary());
        LinearLayout.LayoutParams activateParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 112), Ui.dp(this, 48));
        activateParams.leftMargin = Ui.dp(this, 8);
        top.addView(modeButton, activateParams);

        settingsButton = Ui.button(this, "Settings", false);
        settingsButton.setSingleLine(true);
        settingsButton.setOnClickListener(view -> showSettings());
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 98), Ui.dp(this, 48));
        settingsParams.leftMargin = Ui.dp(this, 8);
        top.addView(settingsButton, settingsParams);

        infoButton = Ui.button(this, "About", false);
        infoButton.setSingleLine(true);
        infoButton.setOnClickListener(view -> {
            if (updateState != null && updateState.release != null
                    && (updateState.phase == UpdateManager.Phase.DOWNLOADING
                    || updateState.phase == UpdateManager.Phase.READY
                    || updateState.phase == UpdateManager.Phase.ERROR)) {
                showUpdateDialog();
            } else {
                showInfo();
            }
        });
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 88), Ui.dp(this, 48));
        infoParams.leftMargin = Ui.dp(this, 8);
        top.addView(infoButton, infoParams);
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
        status = Ui.text(this, "Starting Deckscape…", 11, Ui.MUTED);
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
        gridAdapter = new WallpaperGridAdapter(this, previewCache,
                new WallpaperGridAdapter.Listener() {
                    @Override
                    public void onAction(CatalogItem item) {
                        handleItemAction(item);
                    }

                    @Override
                    public void onPreview(CatalogItem item) {
                        showWallpaperPreview(item);
                    }

                    @Override
                    public void onOptions(CatalogItem item) {
                        File file = WallpaperStore.installedFile(
                                MainActivity.this, activeSource, item);
                        if (file != null) showWallpaperOptions(file, null);
                    }
                });
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
        RepositorySource source = activeSource;
        if (WallpaperStore.installedFile(this, source, item) == null) {
            getWallpaper(source, item, null);
        } else {
            setWallpaper(source, item, null);
        }
    }

    /** Downloads an original into the local library without selecting or rotating it. */
    private void getWallpaper(RepositorySource source, CatalogItem item,
                              WallpaperPreviewDialog.ActionCallback callback) {
        if (!WallpaperRules.canInstall(item)) {
            Toast.makeText(this, "This file exceeds Deckscape's safe size limit.",
                    Toast.LENGTH_LONG).show();
            if (callback != null) callback.onComplete(false);
            return;
        }
        String displayName = WallpaperStore.displayName(item.name);
        File existing = WallpaperStore.installedFile(this, source, item);
        if (existing != null) {
            if (callback != null) callback.onComplete(true);
            return;
        }
        setStatus("Preparing " + displayName + "…");
        gridAdapter.setDownloadProgress(source, item, -1);
        io.execute(() -> {
            try {
                runOnUiThread(() -> gridAdapter.setDownloadProgress(source, item, 0));
                int[] lastPercent = {-1};
                File file = WallpaperStore.install(this, source, item, (downloaded, total) -> {
                    int percent = total > 0
                            ? (int) Math.min(100, downloaded * 100 / total) : 0;
                    if (percent == lastPercent[0]) return;
                    lastPercent[0] = percent;
                    runOnUiThread(() -> {
                        gridAdapter.setDownloadProgress(source, item, percent);
                        setStatus("Getting " + displayName + " • " + percent + "%");
                        if (callback != null) callback.onProgress(percent);
                    });
                });
                WallpaperStore.removeFromSlideshow(this, file);
                sendBroadcast(new Intent(WallpaperEngineService.ACTION_LIBRARY_CHANGED)
                        .setPackage(getPackageName()));
                runOnUiThread(() -> {
                    gridAdapter.clearDownloadProgress(source, item);
                    gridAdapter.refreshLibraryState(isWallpaperActive());
                    setStatus("Saved " + displayName
                            + " to Library. Wallpaper and slideshow unchanged.");
                    Toast.makeText(this, "Saved to Library • wallpaper unchanged",
                            Toast.LENGTH_SHORT).show();
                    if (callback != null) callback.onComplete(true);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    gridAdapter.clearDownloadProgress(source, item);
                    setStatus("Could not get wallpaper: " + readableMessage(exception));
                    Toast.makeText(this, readableMessage(exception), Toast.LENGTH_LONG).show();
                    if (callback != null) callback.onComplete(false);
                });
            }
        });
    }

    /** Includes an on-device original in the slideshow and makes it current. */
    private void setWallpaper(RepositorySource source, CatalogItem item,
                              WallpaperPreviewDialog.ActionCallback callback) {
        String displayName = WallpaperStore.displayName(item.name);
        File file = WallpaperStore.installedFile(this, source, item);
        if (file == null) {
            Toast.makeText(this, "Choose Get before setting this wallpaper.",
                    Toast.LENGTH_SHORT).show();
            if (callback != null) callback.onComplete(false);
            return;
        }
        setStatus("Setting " + displayName + "…");
        io.execute(() -> {
            try {
                WallpaperStore.include(this, file);
                WallpaperStore.select(this, file);
                sendBroadcast(new Intent(WallpaperEngineService.ACTION_LIBRARY_CHANGED)
                        .setPackage(getPackageName()));
                runOnUiThread(() -> {
                    boolean active = isWallpaperActive();
                    gridAdapter.refreshLibraryState(active);
                    if (active) {
                        setStatus("Now showing " + displayName
                                + ". Other included wallpapers remain in the slideshow.");
                        Toast.makeText(this, "Now showing • added to slideshow",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        setStatus("Selected " + displayName
                                + ". Tap Setup needed to enable Deckscape.");
                        Toast.makeText(this, "Selected • setup still needed",
                                Toast.LENGTH_SHORT).show();
                    }
                    if (callback != null) callback.onComplete(true);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    setStatus("Could not set wallpaper: " + readableMessage(exception));
                    Toast.makeText(this, readableMessage(exception), Toast.LENGTH_LONG).show();
                    if (callback != null) callback.onComplete(false);
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
        activeIndicator.setText(active ? "DECKSCAPE ON" : "SETUP NEEDED");
        activeIndicator.setTextColor(active ? Ui.CYAN : Ui.CORAL);
        activeIndicator.setBackground(Ui.rounded(active ? Ui.CYAN_DARK : Ui.SURFACE_HIGH,
                Ui.dp(this, 12), active ? Ui.CYAN : Ui.CORAL, Ui.dp(this, 1)));
        activeIndicator.setContentDescription(active
                ? "Deckscape is active. Open wallpaper library"
                : "Deckscape setup is needed. Open setup guide");
        modeButton.setEnabled(true);
        if (gridAdapter != null) gridAdapter.refreshLibraryState(active);
    }

    private boolean isWallpaperActive() {
        WallpaperInfo info = WallpaperManager.getInstance(this).getWallpaperInfo();
        return info != null && new ComponentName(this, WallpaperEngineService.class)
                .equals(info.getComponent());
    }

    /** Explains Android's one-time live-wallpaper confirmation before opening it. */
    private void showActivationGuide() {
        if (isFinishing() || isWallpaperActive()) return;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(this, 24), Ui.dp(this, 20),
                Ui.dp(this, 24), Ui.dp(this, 16));

        panel.addView(Ui.title(this, "Finish setting up Deckscape", 22),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 42)));

        TextView introduction = Ui.text(this,
                "Android needs one confirmation before Deckscape can control your wallpaper.",
                14, Ui.MUTED);
        introduction.setMaxLines(2);
        panel.addView(introduction, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));

        TextView steps = Ui.text(this,
                "1   Deckscape opens Android's wallpaper screen\n"
                        + "2   Some head units may briefly rotate that screen to portrait\n"
                        + "3   Tap ‘Set wallpaper’ to return here in landscape",
                15, Ui.TEXT);
        steps.setGravity(Gravity.CENTER_VERTICAL);
        steps.setLineSpacing(Ui.dp(this, 5), 1f);
        steps.setPadding(Ui.dp(this, 16), Ui.dp(this, 8),
                Ui.dp(this, 16), Ui.dp(this, 8));
        steps.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 12),
                Ui.DIVIDER, Ui.dp(this, 1)));
        panel.addView(steps, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 116)));

        TextView reassurance = Ui.text(this,
                "After activation, previews and wallpaper changes stay inside Deckscape.",
                13, Ui.CYAN);
        reassurance.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(reassurance, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Button later = Ui.button(this, "Not now", false);
        Button activate = Ui.button(this, "Activate Deckscape", true);
        actions.addView(later, new LinearLayout.LayoutParams(
                Ui.dp(this, 110), Ui.dp(this, 46)));
        LinearLayout.LayoutParams activateParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 166), Ui.dp(this, 46));
        activateParams.leftMargin = Ui.dp(this, 10);
        actions.addView(activate, activateParams);
        panel.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 58)));

        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).create();
        later.setOnClickListener(view -> dialog.dismiss());
        activate.setOnClickListener(view -> {
            dialog.dismiss();
            activateWallpaper();
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(this, 18),
                    Ui.DIVIDER, Ui.dp(this, 1)));
            int available = getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 48);
            window.setLayout(Math.min(available, Ui.dp(this, 700)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /** Shows every downloaded wallpaper with independent slideshow and deletion controls. */
    private void showSlideshowLibrary() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(this, 22), Ui.dp(this, 18),
                Ui.dp(this, 22), Ui.dp(this, 12));

        panel.addView(Ui.title(this, "Wallpaper library", 22),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 42)));

        TextView explanation = Ui.text(this, "", 13, Ui.MUTED);
        explanation.setMaxLines(2);
        panel.addView(explanation, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 50)));

        LinearLayout groups = new LinearLayout(this);
        groups.setOrientation(LinearLayout.HORIZONTAL);
        Button allGroup = Ui.button(this, "All", false);
        Button dayGroup = Ui.button(this, "Day", false);
        Button nightGroup = Ui.button(this, "Night", false);
        Button[] groupButtons = {allGroup, dayGroup, nightGroup};
        for (int index = 0; index < groupButtons.length; index++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, Ui.dp(this, 42), 1f);
            if (index > 0) params.leftMargin = Ui.dp(this, 8);
            groups.addView(groupButtons[index], params);
        }
        panel.addView(groups, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46)));

        FrameLayout content = new FrameLayout(this);
        GridView slideshow = new GridView(this);
        slideshow.setNumColumns(3);
        slideshow.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        slideshow.setHorizontalSpacing(Ui.dp(this, 8));
        slideshow.setVerticalSpacing(Ui.dp(this, 8));
        slideshow.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        slideshow.setClipToPadding(false);
        slideshow.setSelector(android.R.color.transparent);
        content.addView(slideshow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView empty = Ui.title(this, "NO WALLPAPERS DOWNLOADED", 13);
        empty.setTextColor(Ui.MUTED);
        empty.setGravity(Gravity.CENTER);
        content.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        slideshow.setEmptyView(empty);

        SlideshowGridAdapter[] adapterHolder = new SlideshowGridAdapter[1];
        Runnable[] refreshHolder = new Runnable[1];
        SlideshowGridAdapter adapter = new SlideshowGridAdapter(
                this, previewCache, new SlideshowGridAdapter.Listener() {
            @Override
            public void onSet(File file) {
                try {
                    WallpaperStore.include(MainActivity.this, file);
                    WallpaperStore.select(MainActivity.this, file);
                    refreshHolder[0].run();
                    setStatus("Now showing " + WallpaperStore.displayName(file)
                            + ". It is included in the slideshow.");
                    Toast.makeText(MainActivity.this, "Now showing • added to slideshow",
                            Toast.LENGTH_SHORT).show();
                } catch (Exception exception) {
                    showWallpaperLibraryError(exception);
                }
            }

            @Override
            public void onSetIncluded(File file, boolean included) {
                try {
                    if (included) WallpaperStore.include(MainActivity.this, file);
                    else WallpaperStore.removeFromSlideshow(MainActivity.this, file);
                    boolean scheduleDisabled = dayNightSettings.disableIfIncomplete();
                    refreshHolder[0].run();
                    String name = WallpaperStore.displayName(file);
                    int count = adapterHolder[0].includedCount();
                    if (included) {
                        setStatus("Added " + name + " to the slideshow.");
                        Toast.makeText(MainActivity.this, "Added to slideshow",
                                Toast.LENGTH_SHORT).show();
                    } else if (count == 0) {
                        setStatus("Removed " + name
                                + ". The slideshow is empty; the download remains on device.");
                        Toast.makeText(MainActivity.this, "Slideshow empty • file kept on device",
                                Toast.LENGTH_LONG).show();
                    } else {
                        setStatus("Removed " + name
                                + " from the slideshow; the download remains on device.");
                        Toast.makeText(MainActivity.this, "Removed from slideshow • file kept",
                                Toast.LENGTH_SHORT).show();
                    }
                    if (scheduleDisabled) showScheduleDisabledMessage();
                } catch (Exception exception) {
                    showWallpaperLibraryError(exception);
                }
            }

            @Override
            public void onCycleRole(File file, DayNightRole currentRole) {
                DayNightRole nextRole = currentRole.next();
                WallpaperProfileStore profiles = new WallpaperProfileStore(MainActivity.this);
                profiles.put(file, profiles.get(file).withRole(nextRole));
                boolean scheduleDisabled = dayNightSettings.disableIfIncomplete();
                refreshHolder[0].run();
                String name = WallpaperStore.displayName(file);
                setStatus(name + " is now " + nextRole.label + ".");
                Toast.makeText(MainActivity.this, nextRole.label,
                        Toast.LENGTH_SHORT).show();
                if (scheduleDisabled) showScheduleDisabledMessage();
            }

            @Override
            public void onOptions(File file) {
                showWallpaperOptions(file, refreshHolder[0]);
            }
        });
        adapterHolder[0] = adapter;
        refreshHolder[0] = () -> refreshWallpaperLibrary(adapterHolder[0], explanation,
                allGroup, dayGroup, nightGroup, empty);
        allGroup.setOnClickListener(view -> {
            adapter.setGroup(LibraryGroup.ALL);
            updateLibraryGroupControls(adapter, allGroup, dayGroup, nightGroup, empty);
            updateSlideshowSummary(explanation, adapter);
        });
        dayGroup.setOnClickListener(view -> {
            adapter.setGroup(LibraryGroup.DAY);
            updateLibraryGroupControls(adapter, allGroup, dayGroup, nightGroup, empty);
            updateSlideshowSummary(explanation, adapter);
        });
        nightGroup.setOnClickListener(view -> {
            adapter.setGroup(LibraryGroup.NIGHT);
            updateLibraryGroupControls(adapter, allGroup, dayGroup, nightGroup, empty);
            updateSlideshowSummary(explanation, adapter);
        });
        updateSlideshowSummary(explanation, adapter);
        updateLibraryGroupControls(adapter, allGroup, dayGroup, nightGroup, empty);
        slideshow.setAdapter(adapter);
        panel.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 350)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Button close = Ui.button(this, "Close", false);
        actions.addView(close, new LinearLayout.LayoutParams(
                Ui.dp(this, 104), Ui.dp(this, 46)));
        panel.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 58)));

        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).create();
        close.setOnClickListener(view -> dialog.dismiss());
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(this, 18),
                    Ui.DIVIDER, Ui.dp(this, 1)));
            int available = getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 48);
            window.setLayout(Math.min(available, Ui.dp(this, 900)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void refreshWallpaperLibrary(SlideshowGridAdapter adapter, TextView explanation,
                                         Button allGroup, Button dayGroup, Button nightGroup,
                                         TextView empty) {
        sendBroadcast(new Intent(WallpaperEngineService.ACTION_LIBRARY_CHANGED)
                .setPackage(getPackageName()));
        adapter.refresh();
        updateSlideshowSummary(explanation, adapter);
        updateLibraryGroupControls(adapter, allGroup, dayGroup, nightGroup, empty);
        gridAdapter.refreshLibraryState(isWallpaperActive());
    }

    private void updateSlideshowSummary(TextView explanation, SlideshowGridAdapter adapter) {
        int downloaded = adapter.downloadedCount();
        int included = adapter.includedCount();
        String summary = downloaded == 0
                ? "Use Get to save wallpapers on this device. Choose Set or Add when you "
                + "want one in the slideshow."
                : included + " of " + downloaded + " downloaded wallpaper"
                + (downloaded == 1 ? "" : "s") + (included == 1 ? " is" : " are")
                + " in the slideshow. "
                + (adapter.group() == LibraryGroup.ALL
                ? "Remove keeps the file; Delete removes it from this device."
                : adapter.group().label + " view shows " + adapter.getCount()
                + "; Both wallpapers appear in Day and Night.");
        explanation.setText(summary);
    }

    private void updateLibraryGroupControls(SlideshowGridAdapter adapter,
                                            Button allGroup, Button dayGroup,
                                            Button nightGroup, TextView empty) {
        styleLibraryGroupButton(allGroup, LibraryGroup.ALL, adapter);
        styleLibraryGroupButton(dayGroup, LibraryGroup.DAY, adapter);
        styleLibraryGroupButton(nightGroup, LibraryGroup.NIGHT, adapter);
        if (adapter.downloadedCount() == 0) {
            empty.setText(R.string.library_empty);
        } else {
            empty.setText(getString(R.string.library_group_empty,
                    adapter.group().label.toUpperCase(Locale.ROOT)));
        }
    }

    private void styleLibraryGroupButton(Button button, LibraryGroup group,
                                         SlideshowGridAdapter adapter) {
        boolean selected = adapter.group() == group;
        button.setText(getString(R.string.library_group_count,
                group.label, adapter.groupCount(group)));
        button.setTextColor(selected ? Ui.NAV : Ui.TEXT);
        button.setBackground(Ui.rounded(selected ? Ui.CYAN : Ui.SURFACE_HIGH,
                Ui.dp(this, 10), selected ? Ui.CYAN : Ui.DIVIDER,
                Ui.dp(this, selected ? 2 : 1)));
    }

    private void confirmWallpaperDeletion(File file, Runnable onConfirmed) {
        AlertDialog confirmation = new AlertDialog.Builder(this)
                .setTitle("Delete " + WallpaperStore.displayName(file) + "?")
                .setMessage("This removes the downloaded wallpaper from this device and the "
                        + "slideshow. You can download it again from its source.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (ignored, which) -> onConfirmed.run())
                .create();
        confirmation.show();
        styleDialog(confirmation);
        Button delete = confirmation.getButton(AlertDialog.BUTTON_POSITIVE);
        if (delete != null) delete.setTextColor(Ui.CORAL);
    }

    private void showWallpaperLibraryError(Exception exception) {
        String message = readableMessage(exception);
        setStatus("Could not update wallpaper library: " + message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /** Opens one wallpaper's crop, schedule, membership, selection, and deletion controls. */
    private void showWallpaperOptions(File file, Runnable refreshLibraryDialog) {
        WallpaperOptionsDialog.show(this, io, file, new WallpaperOptionsDialog.Listener() {
            @Override
            public void onProfileSaved(File changed) {
                boolean disabled = new DayNightSettings(MainActivity.this)
                        .disableIfIncomplete();
                notifyWallpaperConfigurationChanged(refreshLibraryDialog);
                setStatus("Saved options for " + WallpaperStore.displayName(changed) + ".");
                if (disabled) showScheduleDisabledMessage();
            }

            @Override
            public void onSetNow(File selected) {
                try {
                    WallpaperStore.include(MainActivity.this, selected);
                    WallpaperStore.select(MainActivity.this, selected);
                    notifyWallpaperConfigurationChanged(refreshLibraryDialog);
                    setStatus("Now showing " + WallpaperStore.displayName(selected)
                            + ". The slideshow remains enabled.");
                    Toast.makeText(MainActivity.this, "Now showing • slideshow unchanged",
                            Toast.LENGTH_SHORT).show();
                } catch (Exception exception) {
                    showWallpaperLibraryError(exception);
                }
            }

            @Override
            public void onSetIncluded(File changed, boolean included) {
                try {
                    if (included) WallpaperStore.include(MainActivity.this, changed);
                    else WallpaperStore.removeFromSlideshow(MainActivity.this, changed);
                    boolean disabled = new DayNightSettings(MainActivity.this)
                            .disableIfIncomplete();
                    notifyWallpaperConfigurationChanged(refreshLibraryDialog);
                    setStatus((included ? "Added " : "Removed ")
                            + WallpaperStore.displayName(changed)
                            + (included ? " to the slideshow." : " from the slideshow."));
                    if (disabled) showScheduleDisabledMessage();
                } catch (Exception exception) {
                    showWallpaperLibraryError(exception);
                }
            }

            @Override
            public void onDelete(File deleted) {
                try {
                    String name = WallpaperStore.displayName(deleted);
                    WallpaperStore.delete(MainActivity.this, deleted);
                    boolean disabled = new DayNightSettings(MainActivity.this)
                            .disableIfIncomplete();
                    notifyWallpaperConfigurationChanged(refreshLibraryDialog);
                    setStatus("Deleted " + name + " from this device.");
                    Toast.makeText(MainActivity.this, "Deleted from device",
                            Toast.LENGTH_SHORT).show();
                    if (disabled) showScheduleDisabledMessage();
                } catch (Exception exception) {
                    showWallpaperLibraryError(exception);
                }
            }
        });
    }

    private void notifyWallpaperConfigurationChanged(Runnable refreshLibraryDialog) {
        sendBroadcast(new Intent(WallpaperEngineService.ACTION_LIBRARY_CHANGED)
                .setPackage(getPackageName()));
        if (refreshLibraryDialog != null) refreshLibraryDialog.run();
        gridAdapter.refreshLibraryState(isWallpaperActive());
    }

    private void showScheduleDisabledMessage() {
        Toast.makeText(this,
                "Day & Night was disabled because one period has no slideshow wallpapers.",
                Toast.LENGTH_LONG).show();
    }

    /** Opens a cached preview with explicit Get and Set actions for the original. */
    private void showWallpaperPreview(CatalogItem item) {
        RepositorySource source = activeSource;
        if (source == null) return;
        PreviewSequence sequence = new PreviewSequence(
                gridAdapter.visibleItemsSnapshot(), item);
        new WallpaperPreviewDialog(this, previewCache, source, sequence,
                new WallpaperPreviewDialog.Listener() {
                    @Override
                    public void onGet(CatalogItem selected,
                                      WallpaperPreviewDialog.ActionCallback callback) {
                        getWallpaper(source, selected, callback);
                    }

                    @Override
                    public void onSet(CatalogItem selected,
                                      WallpaperPreviewDialog.ActionCallback callback) {
                        setWallpaper(source, selected, callback);
                    }
                }).show();
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

    /** Shows global day/night, display, rotation, and cache settings. */
    private void showSettings() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(this, 22), Ui.dp(this, 16),
                Ui.dp(this, 22), Ui.dp(this, 12));
        panel.addView(Ui.title(this, "Settings", 22), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 42)));

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(columns, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 405)));

        LinearLayout schedule = settingsColumn();
        columns.addView(schedule, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        schedule.addView(settingsHeading("DAY & NIGHT"), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 30)));
        settingsDayNightStatus = Ui.text(this, "", 13, Ui.MUTED);
        settingsDayNightStatus.setGravity(Gravity.TOP);
        settingsDayNightStatus.setLineSpacing(Ui.dp(this, 2), 1f);
        schedule.addView(settingsDayNightStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 78)));

        settingsDayNightToggle = Ui.button(this, "", true);
        settingsDayNightToggle.setOnClickListener(view -> {
            if (dayNightSettings.isEnabled()) {
                if (locationClient.isRequesting()) cancelAutomaticLocation(false);
                dayNightSettings.setEnabled(false);
                pendingEnableDayNight = false;
                broadcastConfigurationChanged();
                refreshSettingsStatus();
                return;
            }
            enableDayNightFromSettings();
        });
        schedule.addView(settingsDayNightToggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));

        schedule.addView(settingsLabel("SCHEDULE METHOD"), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 30)));
        settingsScheduleMode = settingsSpinner(scheduleModeLabels());
        settingsScheduleMode.setSelection(dayNightSettings.mode().ordinal());
        schedule.addView(settingsScheduleMode, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));

        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout dayTime = timeControl(true, dayNightSettings.dayMinute());
        LinearLayout nightTime = timeControl(false, dayNightSettings.nightMinute());
        times.addView(dayTime, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        LinearLayout.LayoutParams nightParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        nightParams.leftMargin = Ui.dp(this, 8);
        times.addView(nightTime, nightParams);
        schedule.addView(times, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 82)));

        settingsLocationButton = Ui.actionButton(this, "Refresh automatic location", false);
        settingsLocationButton.setSingleLine(true);
        settingsLocationButton.setOnClickListener(view -> {
            if (locationClient.isRequesting()) cancelAutomaticLocation(true);
            else requestAutomaticLocation(false);
        });
        schedule.addView(settingsLocationButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));

        LinearLayout right = settingsColumn();
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        rightParams.leftMargin = Ui.dp(this, 22);
        columns.addView(right, rightParams);
        right.addView(settingsHeading("DISPLAY"), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 30)));
        right.addView(settingsLabel("DEFAULT WALLPAPER FIT"), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 28)));
        ScaleMode[] defaults = {ScaleMode.FILL, ScaleMode.FIT, ScaleMode.STRETCH};
        Spinner defaultScale = settingsSpinner(new String[]{
                defaults[0].label, defaults[1].label, defaults[2].label});
        int selectedScale = dayNightSettings.defaultScaleMode() == ScaleMode.FIT ? 1
                : dayNightSettings.defaultScaleMode() == ScaleMode.STRETCH ? 2 : 0;
        defaultScale.setSelection(selectedScale);
        right.addView(defaultScale, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));

        right.addView(settingsLabel("SLIDESHOW INTERVAL"), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 34)));
        Spinner interval = settingsSpinner(INTERVAL_LABELS);
        configureInterval(interval);
        right.addView(interval, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));

        right.addView(settingsHeading("DATA & STORAGE"), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 42)));
        TextView storage = Ui.text(this, storageSummary(), 13, Ui.MUTED);
        storage.setLineSpacing(Ui.dp(this, 2), 1f);
        right.addView(storage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 60)));

        LinearLayout storageActions = new LinearLayout(this);
        Button dataSaver = Ui.actionButton(this, previewCache.isDataSaverEnabled()
                ? "Data saver: on" : "Data saver: off", false);
        Button clear = Ui.actionButton(this, "Clear previews", false);
        storageActions.addView(dataSaver, new LinearLayout.LayoutParams(
                0, Ui.dp(this, 48), 1f));
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                0, Ui.dp(this, 48), 1f);
        clearParams.leftMargin = Ui.dp(this, 8);
        storageActions.addView(clear, clearParams);
        right.addView(storageActions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Button done = Ui.button(this, "Done", true);
        actions.addView(done, new LinearLayout.LayoutParams(
                Ui.dp(this, 118), Ui.dp(this, 46)));
        panel.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 58)));

        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).create();
        settingsDialog = dialog;
        boolean[] initializing = {true};
        settingsScheduleMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ScheduleMode selected = ScheduleMode.values()[position];
                if (selected != ScheduleMode.AUTO && locationClient.isRequesting()) {
                    cancelAutomaticLocation(false);
                }
                dayNightSettings.setMode(selected);
                if (!initializing[0] && selected == ScheduleMode.AUTO
                        && dayNightSettings.isEnabled() && !hasAmbientLightSensor()
                        && !dayNightSettings.hasCoarseLocation()) {
                    requestAutomaticLocation(false);
                }
                broadcastConfigurationChanged();
                refreshSettingsStatus();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        defaultScale.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                dayNightSettings.setDefaultScaleMode(defaults[position]);
                if (!initializing[0]) broadcastConfigurationChanged();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        dataSaver.setOnClickListener(view -> {
            boolean enabled = !previewCache.isDataSaverEnabled();
            previewCache.setDataSaverEnabled(enabled);
            dataSaver.setText(enabled ? "Data saver: on" : "Data saver: off");
            setStatus("Preview data saver " + (enabled ? "enabled." : "disabled."));
        });
        clear.setOnClickListener(view -> {
            previewCache.clear();
            catalogClient.clearCache();
            gridAdapter.notifyDataSetChanged();
            storage.setText(storageSummary());
            setStatus("Preview and catalog caches cleared.");
        });
        done.setOnClickListener(view -> dialog.dismiss());
        dialog.setOnDismissListener(ignored -> {
            if (locationClient.isRequesting()) cancelAutomaticLocation(false);
            if (settingsDialog == dialog) settingsDialog = null;
            settingsDayNightToggle = null;
            settingsDayNightStatus = null;
            settingsScheduleMode = null;
            settingsDayTimeLabel = null;
            settingsNightTimeLabel = null;
            settingsDayTimeSpinner = null;
            settingsNightTimeSpinner = null;
            settingsDayCalculatedTime = null;
            settingsNightCalculatedTime = null;
            settingsLocationButton = null;
        });
        dialog.show();
        styleWideDialog(dialog, 1_050);
        initializing[0] = false;
        refreshSettingsStatus();
    }

    private LinearLayout settingsColumn() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Ui.dp(this, 14), Ui.dp(this, 10),
                Ui.dp(this, 14), Ui.dp(this, 10));
        column.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 12),
                Ui.DIVIDER, Ui.dp(this, 1)));
        return column;
    }

    private TextView settingsHeading(String text) {
        TextView view = Ui.title(this, text, 12);
        view.setTextColor(Ui.CYAN);
        return view;
    }

    private TextView settingsLabel(String text) {
        TextView view = Ui.title(this, text, 10);
        view.setTextColor(Ui.MUTED);
        return view;
    }

    private Spinner settingsSpinner(String[] labels) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setBackground(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(this, 10),
                Ui.DIVIDER, Ui.dp(this, 1)));
        return spinner;
    }

    private LinearLayout timeControl(boolean day, int selectedMinute) {
        LinearLayout control = new LinearLayout(this);
        control.setOrientation(LinearLayout.VERTICAL);
        TextView label = settingsLabel(getString(day
                ? R.string.manual_day_begins : R.string.manual_night_begins));
        if (day) settingsDayTimeLabel = label;
        else settingsNightTimeLabel = label;
        control.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 28)));

        FrameLayout value = new FrameLayout(this);
        Spinner spinner = settingsSpinner(timeLabels());
        if (day) settingsDayTimeSpinner = spinner;
        else settingsNightTimeSpinner = spinner;
        spinner.setSelection(Math.floorMod(selectedMinute, 24 * 60) / 30);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int dayMinute = day ? position * 30 : dayNightSettings.dayMinute();
                int nightMinute = day ? dayNightSettings.nightMinute()
                        : position * 30;
                dayNightSettings.setManualTimes(dayMinute, nightMinute);
                broadcastConfigurationChanged();
                refreshSettingsStatus();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        value.addView(spinner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView calculated = Ui.text(this, "", 15, Ui.TEXT);
        calculated.setGravity(Gravity.CENTER_VERTICAL);
        calculated.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        calculated.setBackground(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(this, 10),
                Ui.DIVIDER, Ui.dp(this, 1)));
        calculated.setVisibility(View.GONE);
        if (day) settingsDayCalculatedTime = calculated;
        else settingsNightCalculatedTime = calculated;
        value.addView(calculated, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        control.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));
        return control;
    }

    private void enableDayNightFromSettings() {
        if (!dayNightSettings.hasCompletePools()) {
            Toast.makeText(this,
                    "Add slideshow wallpapers for both Day and Night before enabling the schedule.",
                    Toast.LENGTH_LONG).show();
            refreshSettingsStatus();
            return;
        }
        if (dayNightSettings.mode() == ScheduleMode.AUTO && !hasAmbientLightSensor()
                && !dayNightSettings.hasCoarseLocation()) {
            requestAutomaticLocation(true);
            return;
        }
        dayNightSettings.setEnabled(true);
        broadcastConfigurationChanged();
        refreshSettingsStatus();
    }

    private void requestAutomaticLocation(boolean enableAfter) {
        pendingEnableDayNight = enableAfter;
        if (!hasUsableForegroundLocationPermission()) {
            showLocationPermissionExplanation();
            return;
        }
        resolveAutomaticLocation();
    }

    private void showLocationPermissionExplanation() {
        AlertDialog explanation = new AlertDialog.Builder(this)
                .setTitle(R.string.location_permission_title)
                .setMessage(R.string.location_permission_message)
                .setNegativeButton("Not now", (ignored, which) -> {
                    boolean shouldEnable = pendingEnableDayNight;
                    pendingEnableDayNight = false;
                    refreshSettingsStatus();
                    if (shouldEnable) {
                        Toast.makeText(this,
                                "Day & Night remains off. Choose Manual or enable GPS access.",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setPositiveButton("Continue", (ignored, which) -> requestPermissions(
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION},
                        REQUEST_FOREGROUND_LOCATION))
                .create();
        explanation.setOnCancelListener(ignored -> {
            pendingEnableDayNight = false;
            refreshSettingsStatus();
        });
        explanation.show();
        styleDialog(explanation);
    }

    private boolean hasUsableForegroundLocationPermission() {
        boolean fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        return LocationPermissionPolicy.isSufficient(Build.VERSION.SDK_INT, fine, coarse);
    }

    private void resolveAutomaticLocation() {
        if (settingsLocationButton != null) {
            settingsLocationButton.setText(R.string.cancel_location_search);
            settingsLocationButton.setEnabled(true);
        }
        locationClient.request(new CoarseLocationClient.Callback() {
            @Override
            public void onLocation(Location location, boolean cached) {
                dayNightSettings.setCoarseLocation(location.getLatitude(),
                        location.getLongitude(), location.getTime() > 0
                                ? location.getTime() : System.currentTimeMillis());
                boolean shouldEnable = pendingEnableDayNight;
                pendingEnableDayNight = false;
                if (shouldEnable) dayNightSettings.setEnabled(true);
                broadcastConfigurationChanged();
                refreshSettingsStatus();
                Toast.makeText(MainActivity.this,
                        cached
                                ? "Updated from a recent on-device location; no new GPS fix was needed."
                                : "Location updated; today's sunrise and sunset are ready.",
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                boolean shouldEnable = pendingEnableDayNight;
                pendingEnableDayNight = false;
                refreshSettingsStatus();
                Toast.makeText(MainActivity.this,
                        shouldEnable ? message + ". Day & Night remains off." : message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void cancelAutomaticLocation(boolean notify) {
        if (!locationClient.isRequesting()) return;
        locationClient.cancel();
        pendingEnableDayNight = false;
        refreshSettingsStatus();
        if (notify) {
            Toast.makeText(this, "Location search cancelled. Your schedule is unchanged.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshSettingsStatus() {
        if (settingsDayNightStatus == null || settingsDayNightToggle == null) return;
        int dayCount = dayNightSettings.eligibleCount(DayPhase.DAY);
        int nightCount = dayNightSettings.eligibleCount(DayPhase.NIGHT);
        DayPhase phase = dayNightSettings.currentPhase(System.currentTimeMillis(), null);
        String method;
        if (dayNightSettings.mode() == ScheduleMode.MANUAL) {
            method = "Manual " + DayPhaseResolver.formatMinute(dayNightSettings.dayMinute())
                    + " / " + DayPhaseResolver.formatMinute(dayNightSettings.nightMinute());
        } else if (hasAmbientLightSensor()) {
            method = "Automatic ambient-light sensor";
        } else if (dayNightSettings.hasCoarseLocation()) {
            method = "Automatic on-device sunrise/sunset";
        } else {
            method = "Automatic needs location • manual times used for now";
        }
        settingsDayNightStatus.setText(getString(R.string.day_night_status,
                dayNightSettings.isEnabled() ? "ON" : "OFF", phase.label,
                getResources().getQuantityString(R.plurals.day_wallpapers, dayCount, dayCount),
                getResources().getQuantityString(
                        R.plurals.night_wallpapers, nightCount, nightCount), method));
        settingsDayNightStatus.setTextColor(dayNightSettings.isEnabled() ? Ui.GREEN : Ui.MUTED);
        settingsDayNightToggle.setText(dayNightSettings.isEnabled()
                ? "Turn Day & Night off" : "Turn Day & Night on");
        settingsDayNightToggle.setTextColor(dayNightSettings.isEnabled() ? Ui.CORAL : Ui.NAV);
        settingsDayNightToggle.setBackground(Ui.rounded(dayNightSettings.isEnabled()
                        ? Ui.SURFACE_HIGH : Ui.CYAN, Ui.dp(this, 12),
                dayNightSettings.isEnabled() ? Ui.CORAL : Ui.CYAN, Ui.dp(this, 1)));
        if (settingsScheduleMode != null
                && settingsScheduleMode.getSelectedItemPosition()
                != dayNightSettings.mode().ordinal()) {
            settingsScheduleMode.setSelection(dayNightSettings.mode().ordinal());
        }
        refreshScheduleTimeControls();
        if (settingsLocationButton != null) {
            if (locationClient.isRequesting()) {
                settingsLocationButton.setText(R.string.cancel_location_search);
            } else if (dayNightSettings.hasCoarseLocation()) {
                settingsLocationButton.setText(getString(R.string.refresh_location_saved,
                        android.text.format.DateFormat.format(
                                "dd MMM", new Date(dayNightSettings.locationTime()))));
            } else {
                settingsLocationButton.setText(R.string.refresh_location);
            }
            settingsLocationButton.setEnabled(true);
            settingsLocationButton.setVisibility(hasAmbientLightSensor() ? View.GONE : View.VISIBLE);
        }
    }

    private void refreshScheduleTimeControls() {
        if (settingsDayTimeLabel == null || settingsNightTimeLabel == null
                || settingsDayTimeSpinner == null || settingsNightTimeSpinner == null
                || settingsDayCalculatedTime == null || settingsNightCalculatedTime == null) {
            return;
        }
        boolean manual = dayNightSettings.mode() == ScheduleMode.MANUAL;
        settingsDayTimeSpinner.setVisibility(manual ? View.VISIBLE : View.GONE);
        settingsNightTimeSpinner.setVisibility(manual ? View.VISIBLE : View.GONE);
        settingsDayCalculatedTime.setVisibility(manual ? View.GONE : View.VISIBLE);
        settingsNightCalculatedTime.setVisibility(manual ? View.GONE : View.VISIBLE);
        if (manual) {
            settingsDayTimeLabel.setText(R.string.manual_day_begins);
            settingsNightTimeLabel.setText(R.string.manual_night_begins);
            return;
        }
        if (hasAmbientLightSensor()) {
            settingsDayTimeLabel.setText(R.string.automatic_control);
            settingsNightTimeLabel.setText(R.string.fixed_times);
            settingsDayCalculatedTime.setText(R.string.ambient_light);
            settingsNightCalculatedTime.setText(R.string.not_used);
            return;
        }
        settingsDayTimeLabel.setText(R.string.todays_sunrise);
        settingsNightTimeLabel.setText(R.string.todays_sunset);
        DayPhaseResolver.SolarTimes times = dayNightSettings.solarTimes(
                System.currentTimeMillis());
        if (times == null) {
            int unavailable = dayNightSettings.hasCoarseLocation()
                    ? R.string.solar_time_unavailable : R.string.location_needed;
            settingsDayCalculatedTime.setText(unavailable);
            settingsNightCalculatedTime.setText(unavailable);
        } else {
            settingsDayCalculatedTime.setText(
                    DayPhaseResolver.formatMinute(times.sunriseMinute));
            settingsNightCalculatedTime.setText(
                    DayPhaseResolver.formatMinute(times.sunsetMinute));
        }
    }

    private String storageSummary() {
        return String.format(Locale.ROOT,
                "Preview cache: %.1f MB / 96 MB\nDownloaded library: %.1f MB",
                previewCache.diskBytes() / (1024.0 * 1024.0),
                WallpaperStore.totalBytes(this) / (1024.0 * 1024.0));
    }

    private boolean hasAmbientLightSensor() {
        SensorManager manager = (SensorManager) getSystemService(SENSOR_SERVICE);
        return manager != null && manager.getDefaultSensor(Sensor.TYPE_LIGHT) != null;
    }

    private void broadcastConfigurationChanged() {
        sendBroadcast(new Intent(WallpaperEngineService.ACTION_LIBRARY_CHANGED)
                .setPackage(getPackageName()));
    }

    private static String[] scheduleModeLabels() {
        ScheduleMode[] values = ScheduleMode.values();
        String[] labels = new String[values.length];
        for (int index = 0; index < values.length; index++) labels[index] = values[index].label;
        return labels;
    }

    private static String[] timeLabels() {
        String[] labels = new String[48];
        for (int index = 0; index < labels.length; index++) {
            labels[index] = DayPhaseResolver.formatMinute(index * 30);
        }
        return labels;
    }

    private void showInfo() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(this, 24), Ui.dp(this, 20),
                Ui.dp(this, 24), Ui.dp(this, 14));
        panel.addView(Ui.title(this, "About " + AppMetadata.DISPLAY_NAME, 22),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 42)));

        String message = AppMetadata.versionLabel() + "\n"
                + "Landscape wallpapers for Android head units\n\n"
                + "Created by Paul Hepple (@MetalHepple) and released under the MIT Licence.\n"
                + "No accounts, advertising, analytics or tracking. A one-time foreground "
                + "location fix is rounded and kept only on-device when you enable automatic "
                + "Day & Night scheduling.";
        TextView copy = Ui.text(this, message, 13, Ui.MUTED);
        copy.setLineSpacing(Ui.dp(this, 3), 1f);
        panel.addView(copy, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 128)));

        LinearLayout contributors = new LinearLayout(this);
        contributors.setOrientation(LinearLayout.HORIZONTAL);
        contributors.setPadding(Ui.dp(this, 14), Ui.dp(this, 8),
                Ui.dp(this, 14), Ui.dp(this, 8));
        contributors.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 10),
                Ui.DIVIDER, Ui.dp(this, 1)));
        renderContributors(contributors, aboutMetadata);
        panel.addView(contributors, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 76)));

        infoUpdateStatus = Ui.text(this, updateSummary(), 13,
                updateState != null && updateState.phase == UpdateManager.Phase.READY
                        ? Ui.CYAN : Ui.MUTED);
        infoUpdateStatus.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        infoUpdateStatus.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 10),
                Ui.DIVIDER, Ui.dp(this, 1)));
        panel.addView(infoUpdateStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Button licences = Ui.actionButton(this, "Licences", false);
        Button source = Ui.actionButton(this, "GitHub", false);
        Button support = Ui.actionButton(this, "Support", false);
        Button check = Ui.actionButton(this, updateState != null && updateState.release != null
                ? "View update" : "Check updates", true);
        Button done = Ui.button(this, "Done", true);
        addEqualDialogAction(actions, licences, false);
        addEqualDialogAction(actions, source, true);
        addEqualDialogAction(actions, support, true);
        addEqualDialogAction(actions, check, true);
        addEqualDialogAction(actions, done, true);
        panel.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 58)));

        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).create();
        infoDialog = dialog;
        licences.setOnClickListener(view -> showLicences());
        source.setOnClickListener(view -> openExternal(AppMetadata.REPOSITORY_URL));
        support.setOnClickListener(view -> openExternal("https://ko-fi.com/metalhepple"));
        check.setOnClickListener(view -> {
            if (updateState != null && updateState.release != null) {
                dialog.dismiss();
                showUpdateDialog();
            } else {
                updateManager.checkNow();
            }
        });
        done.setOnClickListener(view -> dialog.dismiss());
        dialog.setOnDismissListener(ignored -> {
            if (infoDialog == dialog) infoDialog = null;
            infoUpdateStatus = null;
        });
        dialog.show();
        styleWideDialog(dialog, 1_000);

        io.execute(() -> {
            RepositoryMetadata loaded = metadataClient.load(new ArrayList<>(sources));
            runOnUiThread(() -> {
                aboutMetadata = loaded;
                if (dialog.isShowing()) {
                    renderContributors(contributors, loaded);
                    loadContributorAvatars(contributors, loaded, dialog);
                }
            });
        });
    }

    /** Rebuilds the contributor strip with friendly, deduplicated profile chips. */
    private void renderContributors(LinearLayout panel, RepositoryMetadata metadata) {
        panel.removeAllViews();
        TextView heading = Ui.title(this, metadata != null && metadata.stale
                ? "CONTRIBUTORS\nSAVED OFFLINE" : "CONTRIBUTORS", 10);
        heading.setTextColor(metadata != null && metadata.stale ? Ui.MUTED : Ui.CYAN);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(heading, new LinearLayout.LayoutParams(
                Ui.dp(this, 138), ViewGroup.LayoutParams.MATCH_PARENT));

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        LinearLayout people = new LinearLayout(this);
        people.setOrientation(LinearLayout.HORIZONTAL);
        people.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(people, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        if (metadata == null) {
            TextView loading = Ui.text(this, "Loading from GitHub…", 13, Ui.MUTED);
            loading.setGravity(Gravity.CENTER_VERTICAL);
            people.addView(loading, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return;
        }

        List<RepositoryMetadata.Contributor> values = contributorProfiles(metadata);
        for (RepositoryMetadata.Contributor contributor : values) {
            people.addView(contributorChip(contributor));
        }
    }

    private View contributorChip(RepositoryMetadata.Contributor contributor) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(Ui.dp(this, 7), Ui.dp(this, 4),
                Ui.dp(this, 12), Ui.dp(this, 4));
        chip.setBackground(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(this, 24),
                Ui.DIVIDER, Ui.dp(this, 1)));

        FrameLayout avatar = new FrameLayout(this);
        TextView initial = Ui.title(this, contributor.displayName.substring(0, 1)
                .toUpperCase(Locale.ROOT), 13);
        initial.setGravity(Gravity.CENTER);
        initial.setTextColor(Ui.NAV);
        initial.setBackground(contributorAvatarBackground(Ui.CYAN));
        avatar.addView(initial, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(contributorAvatarBackground(Ui.SURFACE_HIGH));
        image.setClipToOutline(true);
        image.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        image.setVisibility(View.INVISIBLE);
        image.setTag(contributorAvatarTag(contributor));
        avatar.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 38), Ui.dp(this, 38));
        chip.addView(avatar, avatarParams);

        TextView label = Ui.text(this, contributor.displayLabel(), 13, Ui.TEXT);
        label.setSingleLine(true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        labelParams.leftMargin = Ui.dp(this, 9);
        chip.addView(label, labelParams);

        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 50));
        chipParams.rightMargin = Ui.dp(this, 8);
        chip.setLayoutParams(chipParams);
        if (!contributor.pageUrl.isEmpty()) {
            chip.setClickable(true);
            chip.setFocusable(true);
            chip.setContentDescription("Open GitHub profile for " + contributor.displayLabel());
            chip.setOnClickListener(view -> openExternal(contributor.pageUrl));
        }
        return chip;
    }

    private android.graphics.drawable.GradientDrawable contributorAvatarBackground(int color) {
        android.graphics.drawable.GradientDrawable background =
                new android.graphics.drawable.GradientDrawable();
        background.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        background.setColor(color);
        return background;
    }

    private static String contributorAvatarTag(RepositoryMetadata.Contributor contributor) {
        return "contributor-avatar:" + contributor.login + ":" + contributor.pageUrl;
    }

    private static List<RepositoryMetadata.Contributor> contributorProfiles(
            RepositoryMetadata metadata) {
        List<RepositoryMetadata.Contributor> contributors =
                new ArrayList<>(metadata.contributors);
        if (contributors.isEmpty()) {
            contributors.add(new RepositoryMetadata.Contributor(AppMetadata.CREATOR_NAME,
                    AppMetadata.CREATOR_LOGIN, AppMetadata.CREATOR_URL,
                    AppMetadata.CREATOR_AVATAR_URL));
        }
        return contributors;
    }

    /** Loads bounded profile images after labels are visible, retaining initials offline. */
    private void loadContributorAvatars(LinearLayout panel, RepositoryMetadata metadata,
                                        AlertDialog dialog) {
        List<RepositoryMetadata.Contributor> contributors = contributorProfiles(metadata);
        io.execute(() -> {
            List<Bitmap> avatars = new ArrayList<>();
            for (RepositoryMetadata.Contributor contributor : contributors) {
                avatars.add(metadataClient.loadAvatar(contributor));
            }
            runOnUiThread(() -> {
                if (!dialog.isShowing()) return;
                for (int index = 0; index < contributors.size(); index++) {
                    Bitmap avatar = avatars.get(index);
                    if (avatar == null) continue;
                    ImageView target = panel.findViewWithTag(
                            contributorAvatarTag(contributors.get(index)));
                    if (target != null) {
                        target.setImageBitmap(avatar);
                        target.setVisibility(View.VISIBLE);
                    }
                }
            });
        });
    }

    /** Displays bundled legal text plus cached repository-level licence declarations. */
    private void showLicences() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(this, 22), Ui.dp(this, 16),
                Ui.dp(this, 22), Ui.dp(this, 12));
        panel.addView(Ui.title(this, "Licences & source notices", 21),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 42)));

        StringBuilder content = new StringBuilder();
        content.append("DECKSCAPE\nCopyright © 2026 Paul Hepple\n\n")
                .append(readAssetText("LICENSE")).append("\n\n")
                .append(readAssetText("THIRD_PARTY_NOTICES.md")).append("\n\n")
                .append("LIVE REPOSITORY METADATA\n")
                .append("Repository licences may not cover individual collected wallpaper images.\n\n");
        RepositoryMetadata metadata = aboutMetadata;
        if (metadata == null || metadata.licenses.isEmpty()) {
            content.append("Licence metadata is not cached yet. Reopen About while online to refresh it.");
        } else {
            for (RepositoryMetadata.SourceLicense license : metadata.licenses.values()) {
                content.append(license.repositoryName).append(" — ")
                        .append(license.licenseName);
                if (!license.spdxId.isEmpty()) content.append(" (").append(license.spdxId).append(')');
                content.append('\n').append(license.pageUrl).append("\n\n");
            }
        }

        TextView text = Ui.text(this, content.toString(), 12, Ui.MUTED);
        text.setTextIsSelectable(true);
        text.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);
        text.setLinkTextColor(Ui.CYAN);
        text.setPadding(Ui.dp(this, 14), Ui.dp(this, 10),
                Ui.dp(this, 14), Ui.dp(this, 10));
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 10),
                Ui.DIVIDER, Ui.dp(this, 1)));
        scroll.addView(text, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 430)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Button done = Ui.button(this, "Done", true);
        actions.addView(done, new LinearLayout.LayoutParams(
                Ui.dp(this, 112), Ui.dp(this, 46)));
        panel.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 58)));
        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).create();
        done.setOnClickListener(view -> dialog.dismiss());
        dialog.show();
        styleWideDialog(dialog, 1_020);
    }

    private String readAssetText(String name) {
        try (java.io.InputStream input = getAssets().open(name);
             java.util.Scanner scanner = new java.util.Scanner(input, "UTF-8")
                     .useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        } catch (Exception exception) {
            return "Unable to load " + name + ".";
        }
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception exception) {
            Toast.makeText(this, "No browser is available.", Toast.LENGTH_LONG).show();
        }
    }

    private void addEqualDialogAction(LinearLayout row, Button button, boolean margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, Ui.dp(this, 46), 1f);
        if (margin) params.leftMargin = Ui.dp(this, 8);
        row.addView(button, params);
    }

    private String updateSummary() {
        if (updateState == null) return "UPDATES  •  Starting update check…";
        return "UPDATES  •  " + updateState.message;
    }

    /** Reflects background update progress without interrupting wallpaper browsing. */
    private void onUpdateStateChanged(UpdateManager.State value) {
        updateState = value;
        if (infoButton == null || isFinishing()) return;
        if (value.phase == UpdateManager.Phase.READY) {
            infoButton.setText(R.string.update_available);
            infoButton.setTextSize(13);
            infoButton.setTextColor(Ui.NAV);
            infoButton.setBackground(Ui.rounded(Ui.CYAN, Ui.dp(this, 12),
                    Ui.CYAN, Ui.dp(this, 1)));
            setStatus("Deckscape " + value.release.versionName + " is ready to install.");
        } else if (value.phase == UpdateManager.Phase.DOWNLOADING) {
            infoButton.setText(getString(R.string.update_progress, value.progress));
            infoButton.setTextSize(12);
            infoButton.setTextColor(Ui.CYAN);
            infoButton.setBackground(Ui.rounded(Ui.CYAN_DARK, Ui.dp(this, 12),
                    Ui.CYAN, Ui.dp(this, 1)));
        } else {
            infoButton.setText(R.string.info);
            infoButton.setTextSize(14);
            infoButton.setTextColor(Ui.TEXT);
            infoButton.setBackground(Ui.rounded(Ui.SURFACE_HIGH, Ui.dp(this, 12),
                    Ui.DIVIDER, Ui.dp(this, 1)));
        }
        if (infoUpdateStatus != null) {
            infoUpdateStatus.setText(updateSummary());
            infoUpdateStatus.setTextColor(value.phase == UpdateManager.Phase.READY
                    ? Ui.CYAN : value.phase == UpdateManager.Phase.ERROR ? Ui.CORAL : Ui.MUTED);
        }
        refreshUpdateDialog();
    }

    /** Shows release notes, download progress, verification state, and install consent. */
    private void showUpdateDialog() {
        UpdateManager.State current = updateState;
        if (current == null || current.release == null) {
            showInfo();
            return;
        }
        UpdateRelease release = current.release;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(this, 24), Ui.dp(this, 20),
                Ui.dp(this, 24), Ui.dp(this, 14));
        panel.addView(Ui.title(this, "Deckscape " + release.versionName, 22),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 42)));

        String notes = release.notes.isEmpty()
                ? "This release does not include additional notes." : release.notes;
        TextView notesView = Ui.text(this, notes, 13, Ui.MUTED);
        notesView.setPadding(Ui.dp(this, 14), Ui.dp(this, 10),
                Ui.dp(this, 14), Ui.dp(this, 10));
        ScrollView notesScroll = new ScrollView(this);
        notesScroll.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 10),
                Ui.DIVIDER, Ui.dp(this, 1)));
        notesScroll.addView(notesView, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(notesScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 210)));

        updateDialogStatus = Ui.text(this, current.message, 14, Ui.CYAN);
        panel.addView(updateDialogStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));
        updateDialogProgress = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        updateDialogProgress.setMax(100);
        updateDialogProgress.setProgress(current.progress);
        updateDialogProgress.setProgressTintList(
                android.content.res.ColorStateList.valueOf(Ui.CYAN));
        updateDialogProgress.setProgressBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Ui.CYAN_DARK));
        panel.addView(updateDialogProgress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 6)));

        TextView warning = Ui.text(this,
                "Android will ask you to approve installation. Updating may temporarily restore "
                        + "a head unit's stock wallpaper; reopen Deckscape and tap Setup needed if required.",
                13, Ui.CORAL);
        warning.setPadding(Ui.dp(this, 14), Ui.dp(this, 8),
                Ui.dp(this, 14), Ui.dp(this, 8));
        panel.addView(warning, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 64)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Button releasePage = Ui.actionButton(this, "Release page", false);
        Button later = Ui.actionButton(this, "Later", false);
        updateDialogAction = Ui.button(this, "Install update", true);
        addEqualDialogAction(actions, releasePage, false);
        addEqualDialogAction(actions, later, true);
        addEqualDialogAction(actions, updateDialogAction, true);
        panel.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 58)));

        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).create();
        updateDialog = dialog;
        releasePage.setOnClickListener(view -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl)));
            } catch (Exception exception) {
                Toast.makeText(this, "No browser is available.", Toast.LENGTH_LONG).show();
            }
        });
        later.setOnClickListener(view -> dialog.dismiss());
        updateDialogAction.setOnClickListener(view -> {
            UpdateManager.State latest = updateState;
            if (latest != null && latest.canInstall()) {
                beginUpdateInstall();
            } else if (latest != null && latest.phase == UpdateManager.Phase.ERROR) {
                updateManager.checkNow();
            }
        });
        dialog.setOnDismissListener(ignored -> {
            if (updateDialog == dialog) updateDialog = null;
            updateDialogStatus = null;
            updateDialogProgress = null;
            updateDialogAction = null;
        });
        dialog.show();
        styleWideDialog(dialog, 820);
        refreshUpdateDialog();
    }

    private void refreshUpdateDialog() {
        if (updateDialog == null || !updateDialog.isShowing() || updateState == null) return;
        if (updateDialogStatus != null) {
            updateDialogStatus.setText(updateState.message);
            updateDialogStatus.setTextColor(updateState.phase == UpdateManager.Phase.ERROR
                    ? Ui.CORAL : Ui.CYAN);
        }
        if (updateDialogProgress != null) {
            updateDialogProgress.setProgress(updateState.progress);
            updateDialogProgress.setVisibility(updateState.phase == UpdateManager.Phase.DOWNLOADING
                    ? View.VISIBLE : View.INVISIBLE);
        }
        if (updateDialogAction == null) return;
        if (updateState.canInstall()) {
            updateDialogAction.setText(R.string.install_update);
            updateDialogAction.setEnabled(true);
        } else if (updateState.phase == UpdateManager.Phase.ERROR) {
            updateDialogAction.setText(R.string.retry_update);
            updateDialogAction.setEnabled(true);
        } else {
            updateDialogAction.setText(updateState.phase == UpdateManager.Phase.CHECKING
                    ? R.string.checking_update : R.string.downloading_update);
            updateDialogAction.setEnabled(false);
        }
    }

    private void beginUpdateInstall() {
        if (updateState == null || !updateState.canInstall()) return;
        if (!UpdateInstaller.hasInstallPermission(this)) {
            pendingUpdateInstall = true;
            try {
                UpdateInstaller.requestInstallPermission(this);
            } catch (Exception exception) {
                pendingUpdateInstall = false;
                Toast.makeText(this, "Android's install-source settings are unavailable.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        launchVerifiedUpdate();
    }

    private void launchVerifiedUpdate() {
        if (updateState == null || !updateState.canInstall()) return;
        try {
            if (updateDialog != null) updateDialog.dismiss();
            UpdateInstaller.install(this, updateState.file);
        } catch (Exception exception) {
            Toast.makeText(this, "Android's package installer is unavailable.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void styleWideDialog(AlertDialog dialog, int widthDp) {
        styleDialog(dialog);
        Window window = dialog.getWindow();
        if (window != null) {
            int available = getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 48);
            window.setLayout(Math.min(available, Ui.dp(this, widthDp)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
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
