package com.batoulapps.QamarDeen.ui.fragments;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.view.View;
import android.view.ViewGroup;

import com.batoulapps.QamarDeen.QamarDeenActivity;
import com.batoulapps.QamarDeen.R;
import com.batoulapps.QamarDeen.data.QamarDbAdapter;
import com.batoulapps.QamarDeen.ui.helpers.QamarFragment;
import com.batoulapps.QamarDeen.ui.helpers.QamarListAdapter;
import com.batoulapps.QamarDeen.ui.widgets.PrayerBoxesHeaderLayout;
import com.batoulapps.QamarDeen.ui.widgets.PrayerBoxesLayout;
import com.batoulapps.QamarDeen.ui.widgets.PrayerBoxesLayout.SalahClickListener;
import com.batoulapps.QamarDeen.utils.QamarTime;

public class PrayerFragment extends QamarFragment {
   
   private boolean mIsGenderMale = true;
   private boolean mIsExtendedMode = false;
   private AsyncTask<Integer, Void, Boolean> mWritingTask = null;
   
   public static int[] PRAYER_SELECTOR_IMAGES_M = new int[]{
      R.drawable.prayer_group_with_voluntary_m,
      R.drawable.prayer_group_m,
      R.drawable.prayer_alone_with_voluntary_m,
      R.drawable.prayer_alone_m,
      R.drawable.prayer_late,
      R.drawable.prayer_notset
   };
   
   public static int[] PRAYER_SELECTOR_IMAGES_F = new int[]{
      R.drawable.prayer_group_with_voluntary_f,
      R.drawable.prayer_group_f,
      R.drawable.prayer_alone_with_voluntary_f,
      R.drawable.prayer_alone_f,
      R.drawable.prayer_late,
      R.drawable.prayer_notset,
      R.drawable.prayer_excused
   };
   
   public static PrayerFragment newInstance(){
      return new PrayerFragment();
   }
   
   @Override
   public void onResume() {
      super.onResume();
      
      // TODO set mIsGenderMale and mIsExtendedMode from preferences
   }

   @Override
   public int getHeaderLayout(){
      return R.layout.prayer_hdr;
   }
   
   @Override
   protected QamarListAdapter createAdapter(Context context){
      return new PrayerListAdapter(context);
   }
   
   @Override
   protected AsyncTask<Long, Void, Cursor> getDataReadingTask(){
      return new ReadPrayerDataTask();
   }
   
   /**
    * AsyncTask that asynchronously gets prayer data from the database
    * and updates the cursor accordingly.
    */
   private class ReadPrayerDataTask extends AsyncTask<Long, Void, Cursor> {
      
      @Override
      protected Cursor doInBackground(Long... params){
         long maxDate = params[0];
         long minDate = params[1];
         
         QamarDeenActivity activity =
               (QamarDeenActivity)PrayerFragment.this.getActivity();
         QamarDbAdapter adapter = activity.getDatabaseAdapter();
         return adapter.getPrayerEntries(maxDate / 1000, minDate / 1000);
      }
      
      @Override
      protected void onPostExecute(Cursor cursor){
         if (cursor != null){
            if (cursor.moveToFirst()){
               Map<Long, int[]> data = new HashMap<Long, int[]>();
               do {
                  long timestamp = cursor.getLong(1) * 1000;
                  int prayer = cursor.getInt(2);
                  int status = cursor.getInt(3);
                  
                  // time calculations
                  Calendar gmtCal = QamarTime.getGMTCalendar();
                  gmtCal.setTimeInMillis(timestamp);
                  long localTimestamp = QamarTime.getLocalTimeFromGMT(gmtCal);
                  
                  // get or make columns for the data
                  int[] columns = data.get(localTimestamp);
                  if (columns == null){ columns = new int[7]; }
                  
                  columns[prayer] = status;
                  data.put(localTimestamp, columns);
               }
               while (cursor.moveToNext());
               
               if (!data.isEmpty()){
                  // set the data in the adapter
                  ((PrayerListAdapter)mListAdapter).addDayData(data);
                  mListAdapter.notifyDataSetChanged();
               }
            }
            cursor.close();
            mReadData = true;
         }
         else { mReadData = false; }
         
         mLoadingTask = null;
      }
   }
      
   private void popupSalahBox(View anchorView, int currentRow, int salah){
      int[] elems = ((PrayerListAdapter)mListAdapter).getDataItem(currentRow);
      int sel = 0;
      if (elems != null){ sel = elems[salah]; }
      
      // TODO - read from shared prefs
      int[] imageIds = mIsGenderMale? PRAYER_SELECTOR_IMAGES_M :
         PRAYER_SELECTOR_IMAGES_F;
      int options = mIsGenderMale? R.array.prayer_options_m :
         R.array.prayer_options_f;
      mPopupHelper.showPopup(this, anchorView, currentRow, salah, sel,
            options, R.array.prayer_values, imageIds);
   }
   
   @Override
   public void onItemSelected(int row, int salah, int selection){
      long ts = -1;
      
      // get the row of the selection
      Object dateObj = mListView.getItemAtPosition(row);
      if (dateObj == null){ return; }
      
      // get the timestamp corresponding to the row
      Date date = (Date)dateObj;
      Calendar cal = QamarTime.getTodayCalendar();
      cal.setTime(date);
      ts = QamarTime.getGMTTimeFromLocal(cal);
      
      if (mWritingTask != null){
         mWritingTask.cancel(true);
      }
      mWritingTask = new WritePrayerDataTask(ts);
      mWritingTask.execute(row, salah, selection);
   }
   
   private class WritePrayerDataTask extends AsyncTask<Integer, Void, Boolean>{
      private long mTimestamp = -1;
      private int mSelectedRow = -1;
      private int mSalah = -1;
      private int mSelectionValue = -1;

      public WritePrayerDataTask(long timestamp){
         mTimestamp = timestamp;
      }
      
      @Override
      protected Boolean doInBackground(Integer... params) {
         mSelectedRow = params[0];
         mSalah = params[1];
         mSelectionValue = params[2];
         
         QamarDeenActivity activity =
               (QamarDeenActivity)PrayerFragment.this.getActivity();
         QamarDbAdapter adapter = activity.getDatabaseAdapter();
         return adapter.writePrayerEntry(mTimestamp / 1000,
               mSalah, mSelectionValue);
      }
      
      @Override
      protected void onPostExecute(Boolean result) {
         if (result != null && result == true){
            // calculate the local timestamp
            Calendar gmtCal = QamarTime.getGMTCalendar();
            gmtCal.setTimeInMillis(mTimestamp);
            long localTimestamp = QamarTime.getLocalTimeFromGMT(gmtCal);

            // update the list adapter with the data
            ((PrayerListAdapter)mListAdapter)
               .addOneSalahData(localTimestamp, mSalah, mSelectionValue);
            
            boolean refreshed = false;
            
            // attempt to refresh just this one list item
            int start = mListView.getFirstVisiblePosition();
            int end = mListView.getLastVisiblePosition();
            if (mSelectedRow >= start && mSelectedRow <= end){
               View view = mListView.getChildAt(mSelectedRow - start);
               if (view != null){
                  mListAdapter.getView(mSelectedRow, view, mListView);
                  refreshed = true;
               }
            }
            
            if (!refreshed){
               // if we can't, refresh everything
               mListAdapter.notifyDataSetChanged();
            }
         }
         mWritingTask = null;
      }
   }
   
   private class PrayerListAdapter extends QamarListAdapter {
      private Map<Long, int[]> mDataMap = new HashMap<Long, int[]>();

      public PrayerListAdapter(Context context){
         super(context);
      }
      
      @Override
      public void requestData(Long maxDate, Long minDate){
         requestRangeData(maxDate, minDate);
      }
      
      public int[] getDataItem(int position){
         Date date = (Date)getItem(position);
         if (date != null){
            return mDataMap.get(date.getTime());
         }
         return null;
      }
      
      public void addDayData(Map<Long, int[]> data){
         mDataMap.putAll(data);
      }
      
      public void addOneSalahData(long when, int salah, int val){
         int[] data = mDataMap.get(when);
         if (data == null){
            data = new int[7];
         }
         data[salah] = val;
         mDataMap.put(when, data);
      }
      
      @Override
      public View getView(int position, View convertView, ViewGroup parent){
         ViewHolder holder;
         Date date = (Date)getItem(position);
         
         if (convertView == null){
            ViewHolder h = new ViewHolder();
            convertView = mInflater.inflate(R.layout.prayer_layout, null);
            populateDayInfoInHolder(h, convertView, R.id.prayer_hdr);
            
            h.boxes = (PrayerBoxesLayout)convertView
                  .findViewById(R.id.prayer_boxes);
            
            h.boxes.setGenderIsMale(mIsGenderMale);
            h.boxes.setExtendedMode(mIsExtendedMode);
            PrayerBoxesHeaderLayout headerBoxes =
                  (PrayerBoxesHeaderLayout)convertView
                  .findViewById(R.id.prayer_header_boxes);
            headerBoxes.setExtendedMode(mIsExtendedMode);
            
            holder = h;
            convertView.setTag(holder);
         }
         else { holder = (ViewHolder)convertView.getTag(); }
         
         // initialize generic row stuff (date, header, etc)
         initializeRow(holder, date, position);
         
         // set the salah data
         int[] prayerStatus = mDataMap.get(date.getTime());
         if (prayerStatus != null){
            holder.boxes.setPrayerSquares(prayerStatus);
         }
         else { holder.boxes.clearPrayerSquares(); }
         
         final int currentRow = position;
         holder.boxes.setSalahClickListener(new SalahClickListener(){
            
            @Override
            public void onSalahClicked(View view, final int salah){
               scrollListToPosition(mListView, currentRow, mHeaderHeight);
               
               final View v = view;
               view.postDelayed(
                     new Runnable(){
                        public void run(){ 
                           popupSalahBox(v, currentRow, salah);
                        } 
                     }, 50);
            }
         });
   
         return convertView;
      }
      
      @Override
      public void configurePinnedHeader(View v, int position, int alpha) {
         super.configurePinnedHeader(v, position, alpha);
         if (alpha == 255){
            PrayerBoxesHeaderLayout hdr =
                  (PrayerBoxesHeaderLayout)v
                  .findViewById(R.id.prayer_header_boxes);
            hdr.setBackgroundResource(R.color.pinned_hdr_background);
            hdr.setExtendedMode(mIsExtendedMode);
            hdr.showSalahLabels();
         }
      }
      
      class ViewHolder extends QamarViewHolder {
         PrayerBoxesLayout boxes;
      }
   }
}
