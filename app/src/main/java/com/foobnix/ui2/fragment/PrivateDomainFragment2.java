package com.foobnix.ui2.fragment;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.util.Pair;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.dao2.FileMeta;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.TintUtil;
import com.foobnix.pdf.info.view.MyProgressBar;
import com.foobnix.ui2.fast.FastScrollRecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PrivateDomainFragment2 extends UIFragment<PrivateDomainApiService.Novel> {
    public static final Pair<Integer, Integer> PAIR = new Pair<>(R.string.private_domain, R.drawable.glyphicons_217_lock);

    private EditText searchInput;
    private ImageView searchBtn;
    private ImageView settingsBtn;
    private HorizontalScrollView categoriesScroll;
    private LinearLayout categoriesLayout;
    private FastScrollRecyclerView recyclerView;
    private MyProgressBar myProgressBar;

    private List<PrivateDomainApiService.Category> categories = new ArrayList<>();
    private List<PrivateDomainApiService.Novel> novels = new ArrayList<>();
    private String selectedCategoryId = "";
    private String searchQuery = "";
    private int currentPage = 1;
    private int totalPages = 1;
    private boolean hasMore = true;
    private boolean isLoading = false;

    private NovelAdapter novelAdapter;

    @Override
    public Pair<Integer, Integer> getNameAndIconRes() {
        return PAIR;
    }

    @Override
    public void notifyFragment() {
    }

    @Override
    public void resetFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View inflate = inflater.inflate(R.layout.fragment_private_domain, container, false);

        searchInput = inflate.findViewById(R.id.searchInput);
        searchBtn = inflate.findViewById(R.id.searchBtn);
        settingsBtn = inflate.findViewById(R.id.settingsBtn);
        categoriesScroll = inflate.findViewById(R.id.categoriesScroll);
        categoriesLayout = inflate.findViewById(R.id.categoriesLayout);
        recyclerView = inflate.findViewById(R.id.recyclerView);
        myProgressBar = inflate.findViewById(R.id.myProgressBar);

        PrivateDomainApiService.init(getActivity());

        setupRecyclerView();
        setupSearch();
        setupSettings();
        loadCategories();

        return inflate;
    }

    private void setupRecyclerView() {
        novelAdapter = new NovelAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setAdapter(novelAdapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                if (!isLoading && hasMore &&
                        (visibleItemCount + firstVisibleItemPosition) >= totalItemCount &&
                        firstVisibleItemPosition >= 0) {
                    loadNextPage();
                }
            }
        });
    }

    private void setupSearch() {
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setShape(GradientDrawable.RECTANGLE);
        searchBg.setCornerRadius(Dips.dpToPx(8));
        searchBg.setColor(TintUtil.color);
        searchBg.setStroke(Dips.dpToPx(2), TintUtil.color);
        searchInput.setBackgroundDrawable(searchBg);
        searchInput.setTextColor(Color.WHITE);
        searchInput.setHintTextColor(Color.WHITE);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim();
            }
        });

        searchBtn.setOnClickListener(v -> {
            currentPage = 1;
            hasMore = true;
            novels.clear();
            novelAdapter.notifyDataSetChanged();
            loadNovels();
        });
    }

    private void setupSettings() {
        settingsBtn.setColorFilter(Color.WHITE);
        settingsBtn.setOnClickListener(v -> {
            showSettingsDialog();
        });
    }

    private void showSettingsDialog() {
        View dialogView = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_private_domain_settings, null);

        final EditText inputHost = dialogView.findViewById(R.id.inputHost);
        final EditText inputPort = dialogView.findViewById(R.id.inputPort);
        final EditText inputUsername = dialogView.findViewById(R.id.inputUsername);
        final EditText inputPassword = dialogView.findViewById(R.id.inputPassword);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        Button btnSave = dialogView.findViewById(R.id.btnSave);

        inputHost.setText(PrivateDomainApiService.getHost());
        inputPort.setText(PrivateDomainApiService.getPort());
        inputUsername.setText(PrivateDomainApiService.getUsername());
        inputPassword.setText(PrivateDomainApiService.getPassword());

        GradientDrawable dialogBg = new GradientDrawable();
        dialogBg.setShape(GradientDrawable.RECTANGLE);
        dialogBg.setCornerRadius(Dips.dpToPx(16));
        dialogBg.setColor(getResources().getColor(R.color.grey_800, null));
        dialogView.setBackgroundDrawable(dialogBg);

        GradientDrawable hostBg = new GradientDrawable();
        hostBg.setShape(GradientDrawable.RECTANGLE);
        hostBg.setCornerRadius(Dips.dpToPx(4));
        hostBg.setColor(getResources().getColor(R.color.grey_800, null));
        hostBg.setStroke(Dips.dpToPx(2), TintUtil.color);
        inputHost.setBackgroundDrawable(hostBg);

        GradientDrawable portBg = new GradientDrawable();
        portBg.setShape(GradientDrawable.RECTANGLE);
        portBg.setCornerRadius(Dips.dpToPx(4));
        portBg.setColor(getResources().getColor(R.color.grey_800, null));
        portBg.setStroke(Dips.dpToPx(2), TintUtil.color);
        inputPort.setBackgroundDrawable(portBg);

        GradientDrawable usernameBg = new GradientDrawable();
        usernameBg.setShape(GradientDrawable.RECTANGLE);
        usernameBg.setCornerRadius(Dips.dpToPx(4));
        usernameBg.setColor(getResources().getColor(R.color.grey_800, null));
        usernameBg.setStroke(Dips.dpToPx(2), TintUtil.color);
        inputUsername.setBackgroundDrawable(usernameBg);

        GradientDrawable passwordBg = new GradientDrawable();
        passwordBg.setShape(GradientDrawable.RECTANGLE);
        passwordBg.setCornerRadius(Dips.dpToPx(4));
        passwordBg.setColor(getResources().getColor(R.color.grey_800, null));
        passwordBg.setStroke(Dips.dpToPx(2), TintUtil.color);
        inputPassword.setBackgroundDrawable(passwordBg);

        GradientDrawable cancelBtnBg = new GradientDrawable();
        cancelBtnBg.setShape(GradientDrawable.RECTANGLE);
        cancelBtnBg.setCornerRadius(Dips.dpToPx(8));
        cancelBtnBg.setColor(TintUtil.color);
        btnCancel.setBackgroundDrawable(cancelBtnBg);

        GradientDrawable saveBtnBg = new GradientDrawable();
        saveBtnBg.setShape(GradientDrawable.RECTANGLE);
        saveBtnBg.setCornerRadius(Dips.dpToPx(8));
        saveBtnBg.setColor(TintUtil.color);
        btnSave.setBackgroundDrawable(saveBtnBg);

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getActivity());
        builder.setView(dialogView);

        final android.app.AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
        });

        btnSave.setOnClickListener(v -> {
            final String host = inputHost.getText().toString().trim();
            final String port = inputPort.getText().toString().trim();
            final String username = inputUsername.getText().toString().trim();
            final String password = inputPassword.getText().toString().trim();

            if (host.isEmpty() || port.isEmpty()) {
                Toast.makeText(getActivity(), R.string.host_or_port_empty, Toast.LENGTH_SHORT).show();
                return;
            }

            showProgress(true);
            new Thread(() -> {
                PrivateDomainApiService.saveConfig(host, port, username, password);

                final PrivateDomainApiService.LoginResponse[] loginResponse = new PrivateDomainApiService.LoginResponse[1];
                if (!username.isEmpty() && !password.isEmpty()) {
                    loginResponse[0] = PrivateDomainApiService.login(username, password);
                }

                getActivity().runOnUiThread(() -> {
                    showProgress(false);
                    if (loginResponse[0] != null) {
                        if (loginResponse[0].code == 0) {
                            Toast.makeText(getActivity(), R.string.login_success, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getActivity(), R.string.login_failed, Toast.LENGTH_SHORT).show();
                        }
                    }
                    dialog.dismiss();
                    refreshData();
                });
            }).start();
        });

        dialog.show();
    }

    private void refreshData() {
        categories.clear();
        novels.clear();
        selectedCategoryId = "";
        currentPage = 1;
        hasMore = true;
        novelAdapter.notifyDataSetChanged();
        loadCategories();
    }

    private void loadCategories() {
        showProgress(true);
        new Thread(() -> {
            try {
                List<PrivateDomainApiService.Category> result = PrivateDomainApiService.getCategories();
                categories.clear();
                categories.addAll(result);

                getActivity().runOnUiThread(() -> {
                    showCategories();
                    if (!categories.isEmpty()) {
                        selectedCategoryId = categories.get(0).id;
                        currentPage = 1;
                        hasMore = true;
                        novels.clear();
                        loadNovels();
                    }
                    showProgress(false);
                });
            } catch (Exception e) {
                LOG.e(e);
                getActivity().runOnUiThread(() -> {
                    showProgress(false);
                    Toast.makeText(getActivity(), R.string.failed_to_load_categories, Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void showCategories() {
        categoriesLayout.removeAllViews();

        for (PrivateDomainApiService.Category category : categories) {
            TextView tagView = new TextView(getActivity());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, Dips.dpToPx(8), 0);
            tagView.setLayoutParams(params);

            int paddingHorizontal = Dips.dpToPx(16);
            int paddingVertical = Dips.dpToPx(6);
            tagView.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(Dips.dpToPx(20));
            bg.setColor(TintUtil.color);
            tagView.setBackgroundDrawable(bg);

            tagView.setText(category.name);
            tagView.setTextSize(14);
            tagView.setTextColor(Color.WHITE);

            tagView.setOnClickListener(v -> {
                selectedCategoryId = category.id;
                currentPage = 1;
                hasMore = true;
                novels.clear();

                for (int i = 0; i < categoriesLayout.getChildCount(); i++) {
                    View child = categoriesLayout.getChildAt(i);
                    if (child instanceof TextView) {
                        GradientDrawable childBg = new GradientDrawable();
                        childBg.setShape(GradientDrawable.RECTANGLE);
                        childBg.setCornerRadius(Dips.dpToPx(20));
                        childBg.setColor(TintUtil.color);
                        child.setBackgroundDrawable(childBg);
                        ((TextView) child).setTextColor(Color.WHITE);
                    }
                }

                loadNovels();
            });

            categoriesLayout.addView(tagView);
        }
    }

    private void updateCategoryStyle(TextView view, boolean isSelected) {
        if (isSelected) {
            view.setBackgroundColor(TintUtil.color);
            view.setTextColor(Color.WHITE);
        } else {
            view.setBackgroundResource(R.drawable.bg_search_second);
            view.setTextColor(Color.WHITE);
        }
    }

    private void loadNovels() {
        loadNovels(currentPage);
    }

    private void loadNovels(int page) {
        showProgress(true);
        isLoading = true;

        new Thread(() -> {
            try {
                PrivateDomainApiService.NovelResponse response = PrivateDomainApiService.getNovels(
                        page, 10, searchQuery, selectedCategoryId);

                getActivity().runOnUiThread(() -> {
                    if (response != null && response.novels != null) {
                        if (page == 1) {
                            novels.clear();
                        }
                        novels.addAll(response.novels);
                        totalPages = (response.total + 9) / 10;
                        hasMore = page < totalPages;
                        novelAdapter.notifyDataSetChanged();
                    } else {
                        if (page == 1) {
                            novels.clear();
                            novelAdapter.notifyDataSetChanged();
                            Toast.makeText(getActivity(), R.string.no_novels_found, Toast.LENGTH_SHORT).show();
                        }
                    }
                    showProgress(false);
                    isLoading = false;
                });
            } catch (Exception e) {
                LOG.e(e);
                getActivity().runOnUiThread(() -> {
                    showProgress(false);
                    isLoading = false;
                    if (page == 1) {
                        novels.clear();
                        novelAdapter.notifyDataSetChanged();
                        Toast.makeText(getActivity(), R.string.failed_to_load_novels, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private void loadNextPage() {
        if (isLoading || !hasMore) return;
        currentPage++;
        loadNovels(currentPage);
    }

    private void showProgress(boolean show) {
        if (myProgressBar != null) {
            myProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void openNovelDetail(String novelId, String title) {
        showProgress(true);
        new Thread(() -> {
            try {
                File saveDir = new File(getActivity().getFilesDir(), "novels");
                if (!saveDir.exists()) {
                    saveDir.mkdirs();
                }

                File downloadedFile = PrivateDomainApiService.downloadNovel(Integer.parseInt(novelId), title, saveDir);

                if (downloadedFile != null && downloadedFile.exists()) {
                    getActivity().runOnUiThread(() -> {
                        showProgress(false);
                        ExtUtils.openFile(getActivity(), new FileMeta(downloadedFile.getPath()));
                    });
                } else {
                    getActivity().runOnUiThread(() -> {
                        showProgress(false);
                        Toast.makeText(getActivity(), R.string.failed_to_load_content, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                LOG.e(e);
                getActivity().runOnUiThread(() -> {
                    showProgress(false);
                    Toast.makeText(getActivity(), R.string.failed_to_load_content, Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void showNovelContent(PrivateDomainApiService.NovelContentResponse content) {
        getActivity().runOnUiThread(() -> {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getActivity());
            builder.setTitle(content.title);
            builder.setMessage(content.content);
            builder.setPositiveButton(R.string.ok, null);
            builder.show();
        });
    }

    @Override
    public void onTintChanged() {
        if (categoriesLayout != null) {
            for (int i = 0; i < categoriesLayout.getChildCount(); i++) {
                View child = categoriesLayout.getChildAt(i);
                if (child instanceof TextView) {
                    TextView tv = (TextView) child;
                    if (categories.get(i).id.equals(selectedCategoryId)) {
                        tv.setBackgroundColor(TintUtil.color);
                    }
                }
            }
        }
    }

    private class NovelAdapter extends RecyclerView.Adapter<NovelAdapter.ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_novel, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            PrivateDomainApiService.Novel novel = novels.get(position);
            holder.title.setText(novel.title);
            holder.author.setText(novel.author);
        }

        @Override
        public int getItemCount() {
            return novels.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            TextView author;

            ViewHolder(View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.novelTitle);
                author = itemView.findViewById(R.id.novelAuthor);

                itemView.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        PrivateDomainApiService.Novel novel = novels.get(pos);
                        openNovelDetail(novel.id, novel.title);
                    }
                });
            }
        }
    }
}
