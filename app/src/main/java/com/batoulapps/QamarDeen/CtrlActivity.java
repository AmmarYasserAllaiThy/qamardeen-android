package com.batoulapps.QamarDeen;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.batoulapps.QamarDeen.data.QamarDbAdapter;
import com.batoulapps.QamarDeen.utils.QamarTime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static com.batoulapps.QamarDeen.data.QamarConstants.PrayerType.LATE;
import static com.batoulapps.QamarDeen.data.QamarConstants.PrayerType.NOT_SET;
import static com.batoulapps.QamarDeen.data.QamarConstants.Prayers.ASR;
import static com.batoulapps.QamarDeen.data.QamarConstants.Prayers.DHUHR;
import static com.batoulapps.QamarDeen.data.QamarConstants.Prayers.FAJR;
import static com.batoulapps.QamarDeen.data.QamarConstants.Prayers.ISHA;
import static com.batoulapps.QamarDeen.data.QamarConstants.Prayers.MAGHRIB;
import static java.lang.String.valueOf;

public class CtrlActivity extends AppCompatActivity {
    private static final int TRANSPARENT_COLOR = Color.parseColor("#00ffffff");

    private TextView fajr_missed_tv;
    private TextView dhuhr_missed_tv;
    private TextView asr_missed_tv;
    private TextView maghrib_missed_tv;
    private TextView isha_missed_tv;
    private TextView sum_missed_tv;

    private EditText fajr_prayed_et;
    private EditText dhuhr_prayed_et;
    private EditText asr_prayed_et;
    private EditText maghrib_prayed_et;
    private EditText isha_prayed_et;

    private QamarDbAdapter mDatabaseAdapter;

    private static List<Long>
            missedFajr,
            missedDhuhr,
            missedAsr,
            missedMaghrib,
            missedIsha;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ctrl);

        fajr_missed_tv = findViewById(R.id.fajr_missed_tv);
        dhuhr_missed_tv = findViewById(R.id.dhuhr_missed_tv);
        asr_missed_tv = findViewById(R.id.asr_missed_tv);
        maghrib_missed_tv = findViewById(R.id.maghrib_missed_tv);
        isha_missed_tv = findViewById(R.id.isha_missed_tv);
        sum_missed_tv = findViewById(R.id.sum_missed_tv);

        fajr_prayed_et = findViewById(R.id.fajr_prayed_et);
        dhuhr_prayed_et = findViewById(R.id.dhuhr_prayed_et);
        asr_prayed_et = findViewById(R.id.asr_prayed_et);
        maghrib_prayed_et = findViewById(R.id.maghrib_prayed_et);
        isha_prayed_et = findViewById(R.id.isha_prayed_et);

        mDatabaseAdapter = new QamarDbAdapter(this);

        init();
    }

    private void init() {
        clear();

        final Cursor cursor = readPrayers();
        final HashMap<Long, List<Salah>> daysMap = getDaysMap(cursor);
        final HashMap<Long, List<Salah>> targetMap = getTargetMap(daysMap);

        int sum = calcMissedPrayers(targetMap);
        sum_missed_tv.setText(valueOf(sum));

        fajr_missed_tv.setText(valueOf(sum = missedFajr.size()));
        if (sum == 0) done(fajr_prayed_et);

        dhuhr_missed_tv.setText(valueOf(sum = missedDhuhr.size()));
        if (sum == 0) done(dhuhr_prayed_et);

        asr_missed_tv.setText(valueOf(sum = missedAsr.size()));
        if (sum == 0) done(asr_prayed_et);

        maghrib_missed_tv.setText(valueOf(sum = missedMaghrib.size()));
        if (sum == 0) done(maghrib_prayed_et);

        isha_missed_tv.setText(valueOf(sum = missedIsha.size()));
        if (sum == 0) done(isha_prayed_et);
    }

    private void clear() {
        missedFajr = new ArrayList<>();
        missedDhuhr = new ArrayList<>();
        missedAsr = new ArrayList<>();
        missedMaghrib = new ArrayList<>();
        missedIsha = new ArrayList<>();

        fajr_prayed_et.setText("");
        dhuhr_prayed_et.setText("");
        asr_prayed_et.setText("");
        maghrib_prayed_et.setText("");
        isha_prayed_et.setText("");
    }

    private Cursor readPrayers() {
        return mDatabaseAdapter.getPrayerEntries(
                QamarTime.getGMTTimeFromLocal(QamarTime.getTodayCalendar()) / 1000 - 1,
                1541332800 - 1);
    }

    private HashMap<Long, List<Salah>> getDaysMap(Cursor cursor) {
        HashMap<Long, List<Salah>> daysMap = new HashMap<>();

        if (cursor != null && cursor.moveToFirst()) do {
            long ts = cursor.getLong(1);
            int salah = cursor.getInt(2);
            int status = cursor.getInt(3);
            List<Salah> list = daysMap.get(ts);

            if (list == null) list = new ArrayList<>();

            list.add(new Salah(ts, salah, status));
            daysMap.put(ts, list);

        } while (cursor.moveToNext());

        return daysMap;
    }

    private HashMap<Long, List<Salah>> getTargetMap(HashMap<Long, List<Salah>> daysMap) {
        HashMap<Long, List<Salah>> targetMap = new HashMap<>();

        for (Map.Entry<Long, List<Salah>> entry : daysMap.entrySet()) {
            final Long ts = entry.getKey();
            final List<Salah> list = entry.getValue();

            if (list.size() < 5) {
                targetMap.put(ts, list);
                continue;
            }
            for (Salah salah : list)
                if (salah.getStatus() == NOT_SET) {
                    targetMap.put(ts, list);
                    break;
                }
        }

        return targetMap;
    }

    private int calcMissedPrayers(HashMap<Long, List<Salah>> targetMap) {
        for (Map.Entry<Long, List<Salah>> entry : targetMap.entrySet()) {
            final Long ts = entry.getKey();
            final List<Salah> day = entry.getValue();

            boolean isMissedFajr = true,
                    isMissedDhuhr = true,
                    isMissedAsr = true,
                    isMissedMaghrib = true,
                    isMissedIsha = true;

            for (Salah salah : day)
                switch (salah.getSalah()) {
                    case FAJR:
                        isMissedFajr = isMissedSalah(salah);
                        break;
                    case DHUHR:
                        isMissedDhuhr = isMissedSalah(salah);
                        break;
                    case ASR:
                        isMissedAsr = isMissedSalah(salah);
                        break;
                    case MAGHRIB:
                        isMissedMaghrib = isMissedSalah(salah);
                        break;
                    case ISHA:
                        isMissedIsha = isMissedSalah(salah);
                        break;
                }

            if (isMissedFajr) missedFajr.add(ts);
            if (isMissedDhuhr) missedDhuhr.add(ts);
            if (isMissedAsr) missedAsr.add(ts);
            if (isMissedMaghrib) missedMaghrib.add(ts);
            if (isMissedIsha) missedIsha.add(ts);
        }

        return missedFajr.size() + missedDhuhr.size() + missedAsr.size() + missedMaghrib.size() + missedIsha.size();
    }

    private boolean isMissedSalah(Salah salah) {
        return salah.getStatus() == NOT_SET;
    }

    private void done(EditText et) {
        et.setHint("Done");
        et.setEnabled(false);
        et.setFocusable(false);
        et.setBackgroundColor(TRANSPARENT_COLOR);
        getTvOf(et).setVisibility(View.GONE);
    }

    public TextView getTvOf(EditText et) {
        return et == fajr_prayed_et ? fajr_missed_tv :
                et == dhuhr_prayed_et ? dhuhr_missed_tv :
                        et == asr_prayed_et ? asr_missed_tv :
                                et == maghrib_prayed_et ? maghrib_missed_tv :
                                        et == isha_prayed_et ? isha_missed_tv :
                                                null;
    }

    public void save(View view) {
        final int fajrMissedNo = getIntValueOf(fajr_missed_tv);
        final int dhuhrMissedNo = getIntValueOf(dhuhr_missed_tv);
        final int asrMissedNo = getIntValueOf(asr_missed_tv);
        final int maghribMissedNo = getIntValueOf(maghrib_missed_tv);
        final int ishaMissedNo = getIntValueOf(isha_missed_tv);

        final int fajrPrayedNo = getIntValueOf(fajr_prayed_et);
        final int dhuhrPrayedNo = getIntValueOf(dhuhr_prayed_et);
        final int asrPrayedNo = getIntValueOf(asr_prayed_et);
        final int maghribPrayedNo = getIntValueOf(maghrib_prayed_et);
        final int ishaPrayedNo = getIntValueOf(isha_prayed_et);

        final StringBuilder sb = new StringBuilder();

        if (isValidPrayedNo(fajrPrayedNo, fajrMissedNo))
            sb.append(format(FAJR, fajrMissedNo, pray(FAJR, fajrPrayedNo)));

        if (isValidPrayedNo(dhuhrPrayedNo, dhuhrMissedNo))
            sb.append(format(DHUHR, dhuhrMissedNo, pray(DHUHR, dhuhrPrayedNo)));

        if (isValidPrayedNo(asrPrayedNo, asrMissedNo))
            sb.append(format(ASR, asrMissedNo, pray(ASR, asrPrayedNo)));

        if (isValidPrayedNo(maghribPrayedNo, maghribMissedNo))
            sb.append(format(MAGHRIB, maghribMissedNo, pray(MAGHRIB, maghribPrayedNo)));

        if (isValidPrayedNo(ishaPrayedNo, ishaMissedNo))
            sb.append(format(ISHA, ishaMissedNo, pray(ISHA, ishaPrayedNo)));

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.save) + " " + getString(R.string.prayers_tab))
                .setMessage(sb)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.done), (dialog, which) -> dialog.dismiss())
                .setNegativeButton(getString(R.string.copy), (dialog, which) -> {
                    copy(sb);
                    dialog.dismiss();
                })
                .show();

        init();
    }

    private int getIntValueOf(TextView v) {
        String s = v.getText().toString();
        return s.matches("[ ]*") ? 0 : Integer.parseInt(s);
    }

    private boolean isValidPrayedNo(int prayed, int missed) {
        return prayed > 0 && prayed <= missed;
    }

    private String format(int salah, int missed, List<Long> result) {
        final String name = salah == FAJR ? getString(R.string.fajr)
                : salah == DHUHR ? getString(R.string.dhuhr)
                : salah == ASR ? getString(R.string.asr)
                : salah == MAGHRIB ? getString(R.string.maghrib)
                : getString(R.string.isha);
        final StringBuilder format = new StringBuilder(String.format(
                Locale.getDefault(),
                "%n%s[%d] = %2d%n",
                name, missed, result.size()));

        for (long ts : result)
            format.append("- ").append(QamarTime.getDateFromMillis(ts * 1000)).append("\n");

        return format.toString();
    }

    private List<Long> pray(int salah, int prayersNo) {
        List<Long> list;

        switch (salah) {
            case DHUHR:
                list = missedDhuhr;
                break;
            case ASR:
                list = missedAsr;
                break;
            case MAGHRIB:
                list = missedMaghrib;
                break;
            case ISHA:
                list = missedIsha;
                break;
            default:
                list = missedFajr;
                break;
        }

        final List<Long> result = new ArrayList<>();
        final int id = 0;

        while (prayersNo-- > 0) {
            final long ts = list.get(id);

            if (mDatabaseAdapter.writePrayerEntry(ts, salah, LATE)) {
                result.add(ts);
                list.remove(id);
            }
        }
        return result;
    }

    private void copy(CharSequence sb) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("", sb);
        Objects.requireNonNull(clipboard).setPrimaryClip(clip);

        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
    }


    private static class Salah {
        private long ts;
        private int salah;
        private int status;

        Salah(long ts, int salah, int status) {
            this.ts = ts;
            this.salah = salah;
            this.status = status;
        }

        public long getTs() {
            return ts;
        }

        public int getSalah() {
            return salah;
        }

        public int getStatus() {
            return status;
        }
    }
}