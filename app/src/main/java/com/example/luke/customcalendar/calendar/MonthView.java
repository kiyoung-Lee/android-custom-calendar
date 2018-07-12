package com.example.luke.customcalendar.calendar;

import android.content.Context;
import android.graphics.Typeface;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.luke.customcalendar.R;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MonthView extends LinearLayout {

    private MonthViewListener monthViewListener;
    private TextView title;
    private CalendarGridView grid;
    private View dayNamesHeaderRowView;


    public MonthView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public static MonthView create(ViewGroup parent, LayoutInflater inflater,
       DateFormat weekdayNameFormat, MonthViewListener monthViewListener, Calendar today,
       int dayBackgroundResId, int dayTextColorResId, int titleTextStyle, boolean displayHeader,
       int headerTextColor, boolean displayDayNamesHeaderRowView, DayViewAdapter adapter){

        final MonthView view = (MonthView) inflater.inflate(R.layout.month, parent, false);

        // Set the views
        view.title = new TextView(new ContextThemeWrapper(view.getContext(), titleTextStyle));
        view.grid = view.findViewById(R.id.calendar_grid);
        view.dayNamesHeaderRowView = view.findViewById(R.id.day_names_header_row);

        // Add the month title as the first child of MonthView
        view.addView(view.title, 0);

        view.setDayViewAdapter(adapter);
        view.setDayTextColor(dayTextColorResId);
        view.setDisplayHeader(displayHeader);
        view.setHeaderTextColor(headerTextColor);
        view.setDayBackground(dayBackgroundResId);

        int firstDayOfWeek = today.getFirstDayOfWeek();
        final CalendarRowView headerRow = (CalendarRowView) view.grid.getChildAt(0);

        if (displayDayNamesHeaderRowView) {
            final int originalDayOfWeek = today.get(Calendar.DAY_OF_WEEK);
            for (int offset = 0; offset < 7; offset++) {
                today.set(Calendar.DAY_OF_WEEK, getDayOfWeek(firstDayOfWeek, offset));
                final TextView textView = (TextView) headerRow.getChildAt(offset);
                textView.setText(weekdayNameFormat.format(today.getTime()));
            }
            today.set(Calendar.DAY_OF_WEEK, originalDayOfWeek);
        } else {
            view.dayNamesHeaderRowView.setVisibility(View.GONE);
        }

        view.monthViewListener = monthViewListener;

        return view;
    }

    private static int getDayOfWeek(int firstDayOfWeek, int offset) {
        int dayOfWeek = firstDayOfWeek + offset;
        return dayOfWeek;
    }

    public void init(MonthDescriptor month, List<List<MonthCellDescriptor>> cells) {

        title.setText(month.getLabel());
        NumberFormat numberFormatter = NumberFormat.getNumberInstance(Locale.US);

        final int numRows = cells.size();
        grid.setNumRows(numRows);

        for (int i = 0; i < 6; i++) {
            CalendarRowView weekRow = (CalendarRowView) grid.getChildAt(i + 1);
            weekRow.setListener(monthViewListener);

            if (i < numRows) {
                weekRow.setVisibility(VISIBLE);
                List<MonthCellDescriptor> week = cells.get(i);
                for (int c = 0; c < week.size(); c++) {
                    MonthCellDescriptor cell = week.get(c);
                    CalendarCellView cellView = (CalendarCellView) weekRow.getChildAt(c);

                    String cellDate = numberFormatter.format(cell.getValue());
                    if (!cellView.getDayOfMonthTextView().getText().equals(cellDate)) {
                        cellView.getDayOfMonthTextView().setText(cellDate);
                    }
                    cellView.setEnabled(cell.isCurrentMonth());
                    cellView.setSelectable(cell.isSelectable());
                    cellView.setSelected(cell.isSelected());
                    cellView.setCurrentMonth(cell.isCurrentMonth());
                    cellView.setToday(cell.isToday());
                    cellView.setRangeState(cell.getRangeState());
                    cellView.setHighlighted(cell.isHighlighted());
                    cellView.setTag(cell);
                }
            } else {
                weekRow.setVisibility(GONE);
            }
        }
    }

    public void setDayBackground(int resId) {
        grid.setDayBackground(resId);
    }

    public void setDayTextColor(int resId) {
        grid.setDayTextColor(resId);
    }

    public void setDayViewAdapter(DayViewAdapter adapter) {
        grid.setDayViewAdapter(adapter);
    }

    public void setDisplayHeader(boolean displayHeader) {
        grid.setDisplayHeader(displayHeader);
    }

    public void setHeaderTextColor(int color) {
        grid.setHeaderTextColor(color);
    }

    public interface MonthViewListener {
        void handleClick(MonthCellDescriptor cell);
    }
}
