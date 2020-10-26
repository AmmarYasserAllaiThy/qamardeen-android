package com.batoulapps.QamarDeen;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.batoulapps.QamarDeen.data.QamarConstants;
import com.batoulapps.QamarDeen.data.QamarDbAdapter;
import com.batoulapps.QamarDeen.data.ScoresHelper;
import com.batoulapps.QamarDeen.data.ScoresHelper.ScoreResult;
import com.batoulapps.QamarDeen.ui.widgets.GraphWidget;
import com.batoulapps.QamarDeen.ui.widgets.StatisticsWidget;
import com.batoulapps.QamarDeen.ui.widgets.TimeSelectorWidget;
import com.batoulapps.QamarDeen.utils.QamarTime;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Locale;

public class QamarGraphActivity extends AppCompatActivity
        implements ActionBar.TabListener, TimeSelectorWidget.TimeSelectedListener {

    public static final int GRAPH_PRAYER_TAB = 0;
    public static final int GRAPH_QURAN_TAB = 1;
    public static final int GRAPH_SADAQAH_TAB = 2;
    public static final int GRAPH_FASTING_TAB = 3;
    public static final int GRAPH_OVERVIEW_TAB = 4;

    // for save instance state
    private static final String LAST_SELECTED_TAB = "last_selected_tab";
    private static final String LAST_DATE_OPTION = "last_date_option";

    private int mCurrentTab = 0;
    private int mDateOption = 1;
    private int[] mDateOffsets = new int[]{7, 14, 30, 60, 90, 180, 365, -1};
    private int[] mTabs = new int[]{
            R.string.prayers_tab,
            R.string.quran_tab,
            R.string.sadaqah_tab,
            R.string.fasting_tab,
            R.string.overview_tab};

    private QamarDbAdapter mDatabaseAdapter;

    private ViewPager mGraphPager;
    private TextView mMinDate;
    private TextView mMaxDate;
    private GraphWidget mGraphWidget;
    private StatisticsWidget mStatisticsWidget;

    // async task
    private DataFetcher mDataFetchTask;

    private GraphPagerAdapter mGraphAdapter;
    private static DateFormat mDateFormat;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (prefs.getBoolean(QamarConstants.PreferenceKeys.USE_ARABIC, false)) {
            Resources resources = getResources();
            Configuration config = resources.getConfiguration();
            config.locale = new Locale("ar");
            resources.updateConfiguration(config, resources.getDisplayMetrics());
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.graph_layout);

        mDatabaseAdapter = new QamarDbAdapter(this);

        mGraphPager = findViewById(R.id.graph_pager);
        mGraphAdapter = new GraphPagerAdapter();
        mGraphPager.setAdapter(mGraphAdapter);
        mMinDate = findViewById(R.id.min_date);
        mMaxDate = findViewById(R.id.max_date);
        TimeSelectorWidget timeSelectorWidget = findViewById(R.id.time_selector);
        timeSelectorWidget.setTimeSelectedListener(this);

        ActionBar actionbar = getSupportActionBar();
        actionbar.setDisplayShowHomeEnabled(true);
        actionbar.setDisplayHomeAsUpEnabled(true);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
            actionbar.setTitle("");

        if (savedInstanceState != null) {
            mCurrentTab = savedInstanceState.getInt(LAST_SELECTED_TAB, 0);
            mDateOption = savedInstanceState.getInt(LAST_DATE_OPTION, 1);
            timeSelectorWidget.setSelectedPosition(mDateOption);
        }

        actionbar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        for (int i = 0; i < mTabs.length; i++) addTab(actionbar, i);

        updateLanguage();
        Calendar today = QamarTime.getTodayCalendar();
        mMaxDate.setText(mDateFormat.format(today.getTime()));
        drawGraph();
    }

    private void updateLanguage() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isArabic = prefs.getBoolean(QamarConstants.PreferenceKeys.USE_ARABIC, false);
        isArabic = isArabic || "ar".equals(Locale.getDefault().getLanguage());

        if (isArabic)
            mDateFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, new Locale("ar"));
        else mDateFormat = DateFormat.getDateInstance(DateFormat.DEFAULT);
    }

    private void addTab(ActionBar actionbar, int tabNumber) {
        ActionBar.Tab tab = actionbar.newTab();
        tab.setText(mTabs[tabNumber]);
        tab.setTag(tabNumber);
        tab.setTabListener(this);
        actionbar.addTab(tab, tabNumber, tabNumber == mCurrentTab);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(LAST_SELECTED_TAB, mCurrentTab);
        outState.putInt(LAST_DATE_OPTION, mDateOption);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (mDataFetchTask != null) {
            mDataFetchTask.cancel(true);
            mDataFetchTask = null;
        }

        mDatabaseAdapter.close();
        super.onDestroy();
    }

    @Override
    public void timeSelected(int position) {
        mDateOption = position;
        refreshData();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void refreshData() {
        if (mGraphWidget != null && mStatisticsWidget != null) {
            mGraphWidget.showProgressView();
            mStatisticsWidget.showProgressView();
        }
        drawGraph();
    }

    private void drawGraph() {
        Calendar today = QamarTime.getTodayCalendar();
        long maxTime = QamarTime.getGMTTimeFromLocal(today);
        long minTime = getGmtTimestamp(today);

        if (mDataFetchTask != null) mDataFetchTask.cancel(true);

        mDataFetchTask = new DataFetcher();
        mDataFetchTask.execute(maxTime, minTime);
    }

    public long getGmtTimestamp(Calendar localCal) {
        int delta = mDateOffsets[mDateOption];

        if (delta == -1) return 0;

        localCal.add(Calendar.DATE, -1 * delta);
        mMinDate.setText(mDateFormat.format(localCal.getTime()));
        return QamarTime.getGMTTimeFromLocal(localCal);
    }

    private class DataFetcher extends AsyncTask<Long, Void, ScoreResult> {

        @Override
        protected ScoreResult doInBackground(Long... params) {
            long maxDate = params[0] / 1000;
            long minDate = params[1] / 1000;

            switch (mCurrentTab) {
                case GRAPH_PRAYER_TAB:
                    return ScoresHelper.getPrayerScores(mDatabaseAdapter, maxDate, minDate);

                case GRAPH_QURAN_TAB:
                    return ScoresHelper.getQuranScores(mDatabaseAdapter, maxDate, minDate);

                case GRAPH_SADAQAH_TAB:
                    return ScoresHelper.getSadaqahScores(mDatabaseAdapter, maxDate, minDate);

                case GRAPH_FASTING_TAB:
                    return ScoresHelper.getFastingScores(mDatabaseAdapter, maxDate, minDate);

                case GRAPH_OVERVIEW_TAB:
                    return ScoresHelper.getOverallScores(mDatabaseAdapter, maxDate, minDate);
            }
            return null;
        }

        @Override
        protected void onPostExecute(ScoreResult result) {
            if (result != null && mGraphWidget != null) {
                mGraphWidget.renderGraph(result.scores);
                mStatisticsWidget.showStats(mCurrentTab, result.statistics, result.scores.size());

                /* at the last date option, we don't know the last date until
                 * after the query.  we cache it when we render the graph and
                 * thus ask the graph widget for it */
                if (mDateOption == (mDateOffsets.length - 1)) {
                    long lastDate = mGraphWidget.getMinimumDate();
                    mMinDate.setText(mDateFormat.format(lastDate));
                }
            }

            mDataFetchTask = null;
        }
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction transaction) {
        Integer tag = (Integer) tab.getTag();

        if (tag != null && tag != mCurrentTab) {
            mCurrentTab = tag;
            refreshData();
        }
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction transaction) {
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction transaction) {
    }

    public class GraphPagerAdapter extends PagerAdapter {
        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public Object instantiateItem(ViewGroup collection, int position) {
            if (position == 0) {
                mGraphWidget = new GraphWidget(QamarGraphActivity.this);
                collection.addView(mGraphWidget);
                return mGraphWidget;
            } else {
                mStatisticsWidget = new StatisticsWidget(QamarGraphActivity.this);
                collection.addView(mStatisticsWidget);
                return mStatisticsWidget;
            }
        }
    }
}