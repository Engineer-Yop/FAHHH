package com.example.sanivend;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Sales Performance: items sold Weekly (Sun–Sat), Monthly (each day of this month)
 * or Annually (Jan–Dec), for all products or one category.
 */
public class GraphActivity extends BaseActivity {

    private SimpleLineGraphView lineGraph;
    private TextView totalView;
    private Button btnWeekly, btnMonthly, btnAnnually;
    private Button catAll, catNapkins, catWipes, catTissues, catSanitizers;
    private final List<Button> timeframeButtons = new ArrayList<>();

    private AnalyticsViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        if (!DeviceSession.requireDevice(this)) return;
        setContentView(R.layout.activity_graph);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        lineGraph = findViewById(R.id.line_graph);
        totalView = findViewById(R.id.tv_period_total);

        btnWeekly = findViewById(R.id.btn_weekly);
        btnMonthly = findViewById(R.id.btn_monthly);
        btnAnnually = findViewById(R.id.btn_annually);
        btnWeekly.setTag(SalesBuckets.WEEK);
        btnMonthly.setTag(SalesBuckets.MONTH);
        btnAnnually.setTag(SalesBuckets.YEAR);
        timeframeButtons.add(btnWeekly);
        timeframeButtons.add(btnMonthly);
        timeframeButtons.add(btnAnnually);

        catAll = findViewById(R.id.cat_all);
        catNapkins = findViewById(R.id.cat_napkins);
        catWipes = findViewById(R.id.cat_wipes);
        catTissues = findViewById(R.id.cat_tissues);
        catSanitizers = findViewById(R.id.cat_sanitizers);

        setupViewModel();
        setupFilters();

        setMode(SalesBuckets.WEEK);
        setCategory(null);
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(AnalyticsViewModel.class);
        viewModel.setDevice(DeviceSession.id(this));
        viewModel.getChartData().observe(this, data -> {
            if (data != null) lineGraph.setData(data);
        });
        viewModel.getTotals().observe(this, text -> {
            if (text != null) totalView.setText(text);
        });
    }

    private void setupFilters() {
        btnWeekly.setOnClickListener(v -> setMode(SalesBuckets.WEEK));
        btnMonthly.setOnClickListener(v -> setMode(SalesBuckets.MONTH));
        btnAnnually.setOnClickListener(v -> setMode(SalesBuckets.YEAR));

        catAll.setOnClickListener(v -> setCategory(null));
        catNapkins.setOnClickListener(v -> setCategory(ProductCatalog.NAPKINS));
        catWipes.setOnClickListener(v -> setCategory(ProductCatalog.WIPES));
        catTissues.setOnClickListener(v -> setCategory(ProductCatalog.TISSUES));
        catSanitizers.setOnClickListener(v -> setCategory(ProductCatalog.SANITIZERS));
    }

    private void setMode(String mode) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        lineGraph.setLabels(SalesBuckets.labels(mode, today));
        viewModel.setMode(mode);
        for (Button btn : timeframeButtons) {
            btn.setSelected(mode.equals(btn.getTag()));
        }
    }

    private void setCategory(String category) {
        viewModel.setCategory(category);
        catAll.setSelected(category == null);
        catNapkins.setSelected(ProductCatalog.NAPKINS.equals(category));
        catWipes.setSelected(ProductCatalog.WIPES.equals(category));
        catTissues.setSelected(ProductCatalog.TISSUES.equals(category));
        catSanitizers.setSelected(ProductCatalog.SANITIZERS.equals(category));
    }
}
