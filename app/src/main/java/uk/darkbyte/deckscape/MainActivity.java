package uk.darkbyte.deckscape;

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
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Landscape-first catalog browser and controller for Deckscape's live-wallpaper service.
 * Network and disk work is delegated to a bounded executor; UI mutations return to the main
 * thread and are guarded by request generations when navigation supersedes an older request.
 */
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
    private UpdateManager updateManager;
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
        previewCache = new PreviewCache(this);
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
    protected void onDestroy() {
        requestGeneration++;
        io.shutdownNow();
        previewCache.close();
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

        modeButton = Ui.button(this, "Set up", true);
        modeButton.setOnClickListener(view -> {
            if (isWallpaperActive()) showSlideshowLibrary();
            else showActivationGuide();
        });
        LinearLayout.LayoutParams activateParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 112), Ui.dp(this, 48));
        activateParams.leftMargin = Ui.dp(this, 8);
        top.addView(modeButton, activateParams);

        infoButton = Ui.button(this, "Info", false);
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
                Ui.dp(this, 82), Ui.dp(this, 48));
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
        if (!WallpaperRules.canInstall(item)) {
            Toast.makeText(this, "This file exceeds Deckscape's safe size limit.",
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
                        setStatus("Now showing " + item.name
                                + ". Other downloads remain in the slideshow.");
                        Toast.makeText(this, "Now showing • slideshow unchanged",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        setStatus("Added " + item.name
                                + " to the slideshow. Tap Set up to enable Deckscape.");
                        Toast.makeText(this, "Added to slideshow • tap Set up",
                                Toast.LENGTH_SHORT).show();
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
        activeIndicator.setText(active ? "DECKSCAPE ON" : "SETUP NEEDED");
        activeIndicator.setTextColor(active ? Ui.CYAN : Ui.CORAL);
        activeIndicator.setBackground(Ui.rounded(active ? Ui.CYAN_DARK : Ui.SURFACE_HIGH,
                Ui.dp(this, 12), active ? Ui.CYAN : Ui.CORAL, Ui.dp(this, 1)));
        modeButton.setText(active ? "Slideshow" : "Set up");
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

    /** Shows every downloaded wallpaper that participates in automatic or manual rotation. */
    private void showSlideshowLibrary() {
        List<File> files = WallpaperStore.list(this);
        File current = WallpaperStore.current(this, files);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(this, 22), Ui.dp(this, 18),
                Ui.dp(this, 22), Ui.dp(this, 12));

        panel.addView(Ui.title(this, "Slideshow wallpapers", 22),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 42)));

        String summary = files.isEmpty()
                ? "Download wallpapers to include them in the slideshow."
                : "All " + files.size() + " downloaded wallpapers are included automatically. "
                        + "Show now changes the current image without removing the others.";
        TextView explanation = Ui.text(this, summary, 13, Ui.MUTED);
        explanation.setMaxLines(2);
        panel.addView(explanation, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 58)));

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

        SlideshowGridAdapter adapter = new SlideshowGridAdapter(
                this, previewCache, files, current, file -> {
                    WallpaperStore.select(this, file);
                    sendBroadcast(new Intent(WallpaperEngineService.ACTION_LIBRARY_CHANGED)
                            .setPackage(getPackageName()));
                    gridAdapter.refreshLibraryState(isWallpaperActive());
                    setStatus("Now showing " + WallpaperStore.displayName(file)
                            + ". Other downloaded wallpapers remain in the slideshow.");
                    Toast.makeText(this, "Wallpaper changed • slideshow unchanged",
                            Toast.LENGTH_SHORT).show();
                });
        slideshow.setAdapter(adapter);
        panel.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 360)));

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

    /** Opens a cached, bandwidth-saving preview without downloading or selecting the original. */
    private void showWallpaperPreview(CatalogItem item) {
        RepositorySource source = activeSource;
        if (source == null) return;

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(this, 22), Ui.dp(this, 18),
                Ui.dp(this, 22), Ui.dp(this, 12));

        TextView title = Ui.title(this, item.name, 20);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        panel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 38)));

        String note = item.isGif()
                ? "Animated preview • original GIF is cached temporarily for playback"
                : "Optimised 16:9 preview • original downloads only when you choose Download";
        TextView description = Ui.text(this, note, 12, Ui.MUTED);
        panel.addView(description, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 30)));

        FrameLayout previewFrame = new FrameLayout(this);
        previewFrame.setBackground(Ui.rounded(Ui.BACKGROUND, Ui.dp(this, 12),
                Ui.DIVIDER, Ui.dp(this, 1)));
        previewFrame.setClipToOutline(true);

        ImageView image = null;
        AnimatedGifView animation = null;
        if (item.isGif()) {
            animation = new AnimatedGifView(this);
            animation.setContentDescription("Animated preview of " + item.name);
            previewFrame.addView(animation, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setContentDescription("Preview of " + item.name);
            previewFrame.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        TextView loading = Ui.title(this, "LOADING PREVIEW", 12);
        loading.setTextColor(Ui.MUTED);
        loading.setGravity(Gravity.CENTER);
        previewFrame.addView(loading, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 420));
        frameParams.topMargin = Ui.dp(this, 8);
        panel.addView(previewFrame, frameParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Button close = Ui.button(this, "Close", false);
        actions.addView(close, new LinearLayout.LayoutParams(
                Ui.dp(this, 104), Ui.dp(this, 46)));
        panel.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 58)));

        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).create();
        close.setOnClickListener(view -> dialog.dismiss());
        dialog.setOnShowListener(ignored -> {
            styleDialog(dialog);
            Window window = dialog.getWindow();
            if (window != null) {
                int available = getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 48);
                window.setLayout(Math.min(available, Ui.dp(this, 900)),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();

        if (item.isGif()) {
            AnimatedGifView target = animation;
            previewCache.requestGif(source, item, (movie, error) -> {
                if (!dialog.isShowing()) return;
                if (movie != null) {
                    target.setMovie(movie);
                    loading.setVisibility(View.GONE);
                } else {
                    loading.setText(error == null ? "GIF PREVIEW UNAVAILABLE" : error);
                }
            });
        } else {
            ImageView target = image;
            previewCache.request(source, item, (bitmap, error) -> {
                if (!dialog.isShowing()) return;
                if (bitmap != null) {
                    target.setImageBitmap(bitmap);
                    loading.setVisibility(View.GONE);
                } else {
                    loading.setText(error == null ? "PREVIEW UNAVAILABLE" : error);
                }
            });
        }
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
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(this, 24), Ui.dp(this, 20),
                Ui.dp(this, 24), Ui.dp(this, 14));
        panel.addView(Ui.title(this, "About " + AppMetadata.DISPLAY_NAME, 22),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 42)));

        String message = String.format(Locale.ROOT,
                AppMetadata.versionLabel() + "\n\n"
                        + "• Every download joins the slideshow automatically\n"
                        + "• Static and animated previews remain in a bounded local cache\n"
                        + "• No account, analytics, advertising, location or storage permission\n\n"
                        + "Preview cache: %.1f MB / 96 MB\n"
                        + "Downloaded library: %.1f MB\n\n"
                        + "Deckscape checks its official GitHub releases once per day. New APKs "
                        + "download automatically over any internet connection, including mobile "
                        + "data, then wait for you to approve installation.",
                previewMb, libraryMb);
        TextView copy = Ui.text(this, message, 13, Ui.MUTED);
        copy.setLineSpacing(Ui.dp(this, 3), 1f);
        panel.addView(copy, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 268)));

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
        Button clear = Ui.actionButton(this, "Clear previews", false);
        Button saver = Ui.actionButton(this, previewCache.isDataSaverEnabled()
                ? "Data saver: on" : "Data saver: off", false);
        Button check = Ui.actionButton(this, updateState != null && updateState.release != null
                ? "View update" : "Check updates", true);
        Button done = Ui.button(this, "Done", true);
        addEqualDialogAction(actions, clear, false);
        addEqualDialogAction(actions, saver, true);
        addEqualDialogAction(actions, check, true);
        addEqualDialogAction(actions, done, true);
        panel.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 58)));

        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).create();
        infoDialog = dialog;
        clear.setOnClickListener(view -> {
            previewCache.clear();
            gridAdapter.notifyDataSetChanged();
            setStatus("Preview cache cleared.");
            dialog.dismiss();
        });
        saver.setOnClickListener(view -> {
            boolean enabled = !previewCache.isDataSaverEnabled();
            previewCache.setDataSaverEnabled(enabled);
            saver.setText(enabled ? "Data saver: on" : "Data saver: off");
            setStatus("Preview data saver " + (enabled ? "enabled." : "disabled."));
        });
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
        styleWideDialog(dialog, 900);
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
                        + "a head unit's stock wallpaper; reopen Deckscape and tap Set up if needed.",
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
