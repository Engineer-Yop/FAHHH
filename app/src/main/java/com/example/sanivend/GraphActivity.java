package com.example.sanivend;

import android.os.Bundle;
import android.widget.Button;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import java.util.ArrayList;
import java.util.List;

public class GraphActivity extends BaseActivity {

    private SimpleLineGraphView lineGraph;
    private Button btnWeekly, btnMonthly, btnAnnually;
    private Button catAll, catNapkins, catWipes, catTissues, catSanitizers;
    private List<Button> timeframeButtons = new ArrayList<>();
    private List<Button> categoryButtons = new ArrayList<>();
    
    private AnalyticsViewModel viewModel;

    // Redefined labels as per your request
    private final String[] hourlyLabels = {
        "12am", "1am", "2am", "3am", "4am", "5am", "6am", "7am", "8am", "9am", "10am", "11am",
        "12pm", "1pm", "2pm", "3pm", "4pm", "5pm", "6pm", "7pm", "8pm", "9pm", "10pm", "11pm"
    };
    private final String[] weeklyLabels = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"}; 
    private final String[] monthlyLabels = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

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
        
        // Timeframe buttons
        btnWeekly = findViewById(R.id.btn_weekly);
        btnMonthly = findViewById(R.id.btn_monthly);
        btnAnnually = findViewById(R.id.btn_annually);
        
        // Today (hourly) · This Week (Sun–Sat) · This Year (Jan–Dec)
        btnAnnually.setText("Today");
        btnWeekly.setText("This Week");
        btnMonthly.setText("This Year");
        btnAnnually.setTag(SalesBuckets.TODAY);
        btnWeekly.setTag(SalesBuckets.WEEK);
        btnMonthly.setTag(SalesBuckets.YEAR);
        timeframeButtons.add(btnAnnually);
        timeframeButtons.add(btnWeekly);
        timeframeButtons.add(btnMonthly);

        // Category buttons
        catAll = findViewById(R.id.cat_all);
        catNapkins = findViewById(R.id.cat_napkins);
        catWipes = findViewById(R.id.cat_wipes);
        catTissues = findViewById(R.id.cat_tissues);
        catSanitizers = findViewById(R.id.cat_sanitizers);
        
        categoryButtons.add(catAll);
        categoryButtons.add(catNapkins);
        categoryButtons.add(catWipes);
        categoryButtons.add(catTissues);
        categoryButtons.add(catSanitizers);

        setupViewModel();
        setupFilters();
        
        // Default view: this year (Jan - Dec)
        setMode(SalesBuckets.YEAR, monthlyLabels);
        setCategory(null);
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(AnalyticsViewModel.class);
        viewModel.setDevice(DeviceSession.id(this));
        
        viewModel.getChartData().observe(this, data -> {
            if (data != null) {
                lineGraph.setData(data);
            }
        });
    }

    private void setupFilters() {
        btnAnnually.setOnClickListener(v -> setMode(SalesBuckets.TODAY, hourlyLabels));
        btnWeekly.setOnClickListener(v -> setMode(SalesBuckets.WEEK, weeklyLabels));
        btnMonthly.setOnClickListener(v -> setMode(SalesBuckets.YEAR, monthlyLabels));

        catAll.setOnClickListener(v -> setCategory(null));
        catNapkins.setOnClickListener(v -> setCategory("Sanitary Napkins"));
        catWipes.setOnClickListener(v -> setCategory("Wet Wipes"));
        catTissues.setOnClickListener(v -> setCategory("Facial Tissues"));
        catSanitizers.setOnClickListener(v -> setCategory("Hand Sanitizers"));
    }

    private void setMode(String mode, String[] labels) {
        lineGraph.setLabels(labels);
        viewModel.setMode(mode);
        
        for (Button btn : timeframeButtons) {
            btn.setSelected(mode.equals(btn.getTag()));
        }
    }

    private void setCategory(String category) {
        viewModel.setCategory(category);
        catAll.setSelected(category == null);
        catNapkins.setSelected("Sanitary Napkins".equals(category));
        catWipes.setSelected("Wet Wipes".equals(category));
        catTissues.setSelected("Facial Tissues".equals(category));
        catSanitizers.setSelected("Hand Sanitizers".equals(category));
    }
}
