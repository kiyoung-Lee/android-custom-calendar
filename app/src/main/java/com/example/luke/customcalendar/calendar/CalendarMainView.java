package com.example.luke.customcalendar.calendar;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;

import com.example.luke.customcalendar.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static java.util.Calendar.DATE;
import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.DAY_OF_WEEK;
import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MILLISECOND;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.SECOND;
import static java.util.Calendar.YEAR;


public class CalendarMainView extends ListView{

    private final CalendarMainView.MonthAdapter adapter;
    private final List<MonthDescriptor> months = new ArrayList<>();
    private final MonthView.MonthViewListener monthViewListener = new CellClickedListener();
    private final IndexedLinkedHashMap<String, List<List<MonthCellDescriptor>>> cells = new IndexedLinkedHashMap<>();
    private final List<MonthCellDescriptor> selectedCells = new ArrayList<>();
    private final List<Calendar> selectedCals = new ArrayList<>();
    private final List<Calendar> highlightedCals = new ArrayList<>();

    private DayViewAdapter dayViewAdapter = new DefaultDayViewAdapter();
    private Calendar minCal;
    private Calendar maxCal;
    private Calendar monthCounter;
    private DateFormat fullDateFormat;
    private DateFormat weekdayNameFormat;
    private Calendar today;
    private int dayBackgroundResId;
    private int dayTextColorResId;
    private int titleTextStyle;
    private boolean displayHeader;
    private int headerTextColor;
    private boolean displayAlwaysDigitNumbers;
    private boolean displayDayNamesHeaderRow;

    private final StringBuilder monthBuilder = new StringBuilder(50);
    private Formatter monthFormatter;


    public CalendarMainView(Context context, AttributeSet attrs) {
        super(context, attrs);

        Resources res = context.getResources();
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CalendarPickerView);
        final int bg = a.getColor(R.styleable.CalendarPickerView_android_background,
                res.getColor(R.color.calendar_bg));

        dayBackgroundResId = a.getResourceId(R.styleable.CalendarPickerView_tsquare_dayBackground,
                R.drawable.calendar_bg_selector);
        dayTextColorResId = a.getResourceId(R.styleable.CalendarPickerView_tsquare_dayTextColor,
                R.color.calendar_text_selector);
        titleTextStyle = a.getResourceId(R.styleable.CalendarPickerView_tsquare_titleTextStyle,
                R.style.CalendarTitle);
        displayHeader = a.getBoolean(R.styleable.CalendarPickerView_tsquare_displayHeader, true);
        headerTextColor = a.getColor(R.styleable.CalendarPickerView_tsquare_headerTextColor,
                res.getColor(R.color.calendar_text_active));
        displayDayNamesHeaderRow =
                a.getBoolean(R.styleable.CalendarPickerView_tsquare_displayDayNamesHeaderRow, true);

        a.recycle();

        adapter = new MonthAdapter();
        setDivider(null);
        setDividerHeight(0);
        setBackgroundColor(bg);
        setCacheColorHint(bg);
        today = Calendar.getInstance();

        weekdayNameFormat = new SimpleDateFormat(context.getString(R.string.day_name_format));
    }

    public Initializer init(Date minDate, Date maxDate) {
        return init(minDate, maxDate, TimeZone.getDefault(), Locale.getDefault());
    }

    public Initializer init(Date minDate, Date maxDate, TimeZone timeZone, Locale locale) {
        if (minDate == null || maxDate == null) {
            throw new IllegalArgumentException(
                    "minDate and maxDate must be non-null.  " + dbg(minDate, maxDate));
        }
        if (minDate.after(maxDate)) {
            throw new IllegalArgumentException(
                    "minDate must be before maxDate.  " + dbg(minDate, maxDate));
        }
        if (locale == null) {
            throw new IllegalArgumentException("Locale is null.");
        }
        if (timeZone == null) {
            throw new IllegalArgumentException("Time zone is null.");
        }

        // Make sure that all calendar instances use the same time zone and locale.
        today = Calendar.getInstance(timeZone, locale);
        minCal = Calendar.getInstance(timeZone, locale);
        maxCal = Calendar.getInstance(timeZone, locale);
        monthCounter = Calendar.getInstance(timeZone, locale);
        for (MonthDescriptor month : months) {
            month.setLabel(formatMonthDate(month.getDate()));
        }
        weekdayNameFormat =
                new SimpleDateFormat(getContext().getString(R.string.day_name_format), locale);
        weekdayNameFormat.setTimeZone(timeZone);
        fullDateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, locale);
        fullDateFormat.setTimeZone(timeZone);
        monthFormatter = new Formatter(monthBuilder, locale);

        selectedCals.clear();
        selectedCells.clear();

        // Clear previous state.
        cells.clear();
        months.clear();
        minCal.setTime(minDate);
        maxCal.setTime(maxDate);
        setMidnight(minCal);
        setMidnight(maxCal);

        // maxDate is exclusive: bump back to the previous day so if maxDate is the first of a month,
        // we don't accidentally include that month in the view.
        maxCal.add(MINUTE, -1);

        // Now iterate between minCal and maxCal and build up our list of months to show.
        monthCounter.setTime(minCal.getTime());
        final int maxMonth = maxCal.get(MONTH);
        final int maxYear = maxCal.get(YEAR);
        while ((monthCounter.get(MONTH) <= maxMonth // Up to, including the month.
                || monthCounter.get(YEAR) < maxYear) // Up to the year.
                && monthCounter.get(YEAR) < maxYear + 1) { // But not > next yr.
            Date date = monthCounter.getTime();
            MonthDescriptor month =
                    new MonthDescriptor(monthCounter.get(MONTH), monthCounter.get(YEAR),
                            date, formatMonthDate(date));
            cells.put(monthKey(month), getMonthCells(month, monthCounter));
            months.add(month);
            monthCounter.add(MONTH, 1);
        }

        validateAndUpdate();

        return new Initializer();
    }

    private static String dbg(Date minDate, Date maxDate) {
        return "minDate: " + minDate + "\nmaxDate: " + maxDate;
    }

    private String formatMonthDate(Date date) {
        int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR
                | DateUtils.FORMAT_NO_MONTH_DAY;

        String dateFormatted;
        if (displayAlwaysDigitNumbers) {
            StringBuilder sb = new StringBuilder();
            SimpleDateFormat sdfMonth = new SimpleDateFormat(getContext()
                    .getString(R.string.month_only_name_format));
            SimpleDateFormat sdfYear = new SimpleDateFormat(getContext()
                    .getString(R.string.year_only_format), Locale.ENGLISH);
            dateFormatted = sb.append(sdfMonth.format(date.getTime())).append(" ")
                    .append(sdfYear.format(date.getTime())).toString();
        } else {
            // Format date using the new Locale
            dateFormatted = DateUtils.formatDateRange(getContext(), monthFormatter,
                    date.getTime(), date.getTime(), flags, TimeZone.getDefault().getID()).toString();
        }
        // Call setLength(0) on StringBuilder passed to the Formatter constructor to not accumulate
        // the results
        monthBuilder.setLength(0);

        // Restore default Locale to avoid generating any side effects
        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(defaultLocale);

        return dateFormatted;
    }

    List<List<MonthCellDescriptor>> getMonthCells(MonthDescriptor month, Calendar startCal) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(startCal.getTime());
        List<List<MonthCellDescriptor>> cells = new ArrayList<>();
        cal.set(DAY_OF_MONTH, 1);
        int firstDayOfWeek = cal.get(DAY_OF_WEEK);
        int offset = cal.getFirstDayOfWeek() - firstDayOfWeek;
        if (offset > 0) {
            offset -= 7;
        }
        cal.add(Calendar.DATE, offset);

        Calendar minSelectedCal = minDate(selectedCals);
        Calendar maxSelectedCal = maxDate(selectedCals);

        while ((cal.get(MONTH) < month.getMonth() + 1 || cal.get(YEAR) < month.getYear()) //
                && cal.get(YEAR) <= month.getYear()) {
            List<MonthCellDescriptor> weekCells = new ArrayList<>();
            cells.add(weekCells);
            for (int c = 0; c < 7; c++) {
                Date date = cal.getTime();
                boolean isCurrentMonth = cal.get(MONTH) == month.getMonth();
                boolean isSelected = isCurrentMonth && containsDate(selectedCals, cal);
                boolean isSelectable =
                        isCurrentMonth && betweenDates(cal, minCal, maxCal);
                boolean isToday = sameDate(cal, today);
                boolean isHighlighted = containsDate(highlightedCals, cal);
                int value = cal.get(DAY_OF_MONTH);

                RangeState rangeState = RangeState.NONE;
                if (selectedCals.size() > 1) {
                    if (sameDate(minSelectedCal, cal)) {
                        rangeState = RangeState.FIRST;
                    } else if (sameDate(maxDate(selectedCals), cal)) {
                        rangeState = RangeState.LAST;
                    } else if (betweenDates(cal, minSelectedCal, maxSelectedCal)) {
                        rangeState = RangeState.MIDDLE;
                    }
                }

                weekCells.add(
                        new MonthCellDescriptor(date, isCurrentMonth, isSelectable, isSelected, isToday,
                                isHighlighted, value, rangeState));
                cal.add(DATE, 1);
            }
        }
        return cells;
    }

    private Calendar minDate(List<Calendar> selectedCals) {
        if (selectedCals == null || selectedCals.size() == 0) {
            return null;
        }
        Collections.sort(selectedCals);
        return selectedCals.get(0);
    }

    private Calendar maxDate(List<Calendar> selectedCals) {
        if (selectedCals == null || selectedCals.size() == 0) {
            return null;
        }
        Collections.sort(selectedCals);
        return selectedCals.get(selectedCals.size() - 1);
    }

    private boolean containsDate(List<Calendar> selectedCals, Calendar cal) {
        for (Calendar selectedCal : selectedCals) {
            if (sameDate(cal, selectedCal)) {
                return true;
            }
        }
        return false;
    }

    private static boolean betweenDates(Calendar cal, Calendar minCal, Calendar maxCal) {
        final Date date = cal.getTime();
        return betweenDates(date, minCal, maxCal);
    }

    static boolean betweenDates(Date date, Calendar minCal, Calendar maxCal) {
        final Date min = minCal.getTime();
        return (date.equals(min) || date.after(min)) // >= minCal
                && date.before(maxCal.getTime()); // && < maxCal
    }


    public class Initializer {

        public Initializer withSelectedDate(Date selectedDates) {
            return withSelectedDates(Collections.singletonList(selectedDates));
        }

        public Initializer withSelectedDates(Collection<Date> selectedDates) {
            if (selectedDates.size() > 2) {
                throw new IllegalArgumentException(
                        "RANGE mode only allows two selectedDates.  You tried to pass " + selectedDates.size());
            }

            if (selectedDates != null) {
                for (Date date : selectedDates) {
                    selectDate(date);
                }
            }
            scrollToSelectedDates();

            validateAndUpdate();
            return this;
        }
    }

    public boolean selectDate(Date date) {
        return selectDate(date, false);
    }

    public boolean selectDate(Date date, boolean smoothScroll) {
        validateDate(date);

        MonthCellWithMonthIndex monthCellWithMonthIndex = getMonthCellWithIndexByDate(date);
        if (monthCellWithMonthIndex == null) {
            return false;
        }
        boolean wasSelected = doSelectDate(date, monthCellWithMonthIndex.cell);
        if (wasSelected) {
            scrollToSelectedMonth(monthCellWithMonthIndex.monthIndex, smoothScroll);
        }
        return wasSelected;
    }

    private void validateDate(Date date) {
        if (date == null) {
            throw new IllegalArgumentException("Selected date must be non-null.");
        }
        if (date.before(minCal.getTime()) || date.after(maxCal.getTime())) {
            throw new IllegalArgumentException(String.format(
                    "SelectedDate must be between minDate and maxDate."
                            + "%nminDate: %s%nmaxDate: %s%nselectedDate: %s", minCal.getTime(), maxCal.getTime(),
                    date));
        }
    }

    private static class MonthCellWithMonthIndex {
        MonthCellDescriptor cell;
        int monthIndex;

        MonthCellWithMonthIndex(MonthCellDescriptor cell, int monthIndex) {
            this.cell = cell;
            this.monthIndex = monthIndex;
        }
    }

    private MonthCellWithMonthIndex getMonthCellWithIndexByDate(Date date) {
        Calendar searchCal = Calendar.getInstance();
        searchCal.setTime(date);
        String monthKey = monthKey(searchCal);
        Calendar actCal = Calendar.getInstance();

        int index = cells.getIndexOfKey(monthKey);
        List<List<MonthCellDescriptor>> monthCells = cells.get(monthKey);
        for (List<MonthCellDescriptor> weekCells : monthCells) {
            for (MonthCellDescriptor actCell : weekCells) {
                actCal.setTime(actCell.getDate());
                if (sameDate(actCal, searchCal) && actCell.isSelectable()) {
                    return new MonthCellWithMonthIndex(actCell, index);
                }
            }
        }
        return null;
    }

    private boolean sameDate(Calendar cal, Calendar selectedDate) {
        return cal.get(MONTH) == selectedDate.get(MONTH)
                && cal.get(YEAR) == selectedDate.get(YEAR)
                && cal.get(DAY_OF_MONTH) == selectedDate.get(DAY_OF_MONTH);
    }

    private void scrollToSelectedMonth(final int selectedIndex) {
        scrollToSelectedMonth(selectedIndex, false);
    }

    private void scrollToSelectedMonth(final int selectedIndex, final boolean smoothScroll) {
        post(new Runnable() {
            @Override public void run() {
                if (smoothScroll) {
                    smoothScrollToPosition(selectedIndex);
                } else {
                    setSelection(selectedIndex);
                }
            }
        });
    }

    private void scrollToSelectedDates() {
        Integer selectedIndex = null;
        Integer todayIndex = null;
        Calendar today = Calendar.getInstance();
        for (int c = 0; c < months.size(); c++) {
            MonthDescriptor month = months.get(c);
            if (selectedIndex == null) {
                for (Calendar selectedCal : selectedCals) {
                    if (sameMonth(selectedCal, month)) {
                        selectedIndex = c;
                        break;
                    }
                }
                if (selectedIndex == null && todayIndex == null && sameMonth(today, month)) {
                    todayIndex = c;
                }
            }
        }
        if (selectedIndex != null) {
            scrollToSelectedMonth(selectedIndex);
        } else if (todayIndex != null) {
            scrollToSelectedMonth(todayIndex);
        }
    }

    private boolean sameMonth(Calendar cal, MonthDescriptor month) {
        return (cal.get(MONTH) == month.getMonth() && cal.get(YEAR) == month.getYear());
    }

    private class MonthAdapter extends BaseAdapter{

        private final LayoutInflater inflater;

        public MonthAdapter() {
            this.inflater = LayoutInflater.from(getContext());;
        }

        @Override
        public int getCount() {
            return months.size();
        }

        @Override
        public Object getItem(int position) {
            return months.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            MonthView monthView = (MonthView) convertView;

            if(monthView == null || monthView.getTag(R.id.day_view_adapter_class).equals(dayViewAdapter.getClass()) == false){
                monthView = MonthView.create(parent, inflater, weekdayNameFormat, monthViewListener, today, dayBackgroundResId, dayTextColorResId,
                        titleTextStyle, displayHeader, headerTextColor, displayDayNamesHeaderRow, dayViewAdapter);

                monthView.setTag(R.id.day_view_adapter_class, dayViewAdapter.getClass());
            }

            monthView.init(months.get(position), cells.getValueAtIndex(position));

            return monthView;
        }
    }

    private class CellClickedListener implements MonthView.MonthViewListener {
        @Override public void handleClick(MonthCellDescriptor cell) {
            Date clickedDate = cell.getDate();
            doSelectDate(clickedDate, cell);
        }
    }

    private boolean doSelectDate(Date date, MonthCellDescriptor cell) {
        Calendar newlySelectedCal = Calendar.getInstance();
        newlySelectedCal.setTime(date);
        // Sanitize input: clear out the hours/minutes/seconds/millis.
        setMidnight(newlySelectedCal);

        // Clear any remaining range state.
        for (MonthCellDescriptor selectedCell : selectedCells) {
            selectedCell.setRangeState(RangeState.NONE);
        }

        if (selectedCals.size() > 1) {
            // We've already got a range selected: clear the old one.
            clearOldSelections();
        } else if (selectedCals.size() == 1 && newlySelectedCal.before(selectedCals.get(0))) {
            // We're moving the start of the range back in time: clear the old start date.
            clearOldSelections();
        }

        if (date != null) {
            // Select a new cell.
            if (selectedCells.size() == 0 || !selectedCells.get(0).equals(cell)) {
                selectedCells.add(cell);
                cell.setSelected(true);
            }
            selectedCals.add(newlySelectedCal);

            if (selectedCells.size() > 1) {
                // Select all days in between start and end.
                Date start = selectedCells.get(0).getDate();
                Date end = selectedCells.get(1).getDate();
                selectedCells.get(0).setRangeState(RangeState.FIRST);
                selectedCells.get(1).setRangeState(RangeState.LAST);

                int startMonthIndex = cells.getIndexOfKey(monthKey(selectedCals.get(0)));
                int endMonthIndex = cells.getIndexOfKey(monthKey(selectedCals.get(1)));
                for (int monthIndex = startMonthIndex; monthIndex <= endMonthIndex; monthIndex++) {
                    List<List<MonthCellDescriptor>> month = cells.getValueAtIndex(monthIndex);
                    for (List<MonthCellDescriptor> week : month) {
                        for (MonthCellDescriptor singleCell : week) {
                            if (singleCell.getDate().after(start)
                                    && singleCell.getDate().before(end)
                                    && singleCell.isSelectable()) {
                                singleCell.setSelected(true);
                                singleCell.setRangeState(RangeState.MIDDLE);
                                selectedCells.add(singleCell);
                            }
                        }
                    }
                }
            }
        }

        // Update the adapter.
        validateAndUpdate();
        return date != null;
    }

    private void clearOldSelections() {
        for (MonthCellDescriptor selectedCell : selectedCells) {
            // De-select the currently-selected cell.
            selectedCell.setSelected(false);
        }
        selectedCells.clear();
        selectedCals.clear();
    }

    private void setMidnight(Calendar cal) {
        cal.set(HOUR_OF_DAY, 0);
        cal.set(MINUTE, 0);
        cal.set(SECOND, 0);
        cal.set(MILLISECOND, 0);
    }

    private String monthKey(Calendar cal) {
        return cal.get(YEAR) + "-" + cal.get(MONTH);
    }

    private String monthKey(MonthDescriptor month) {
        return month.getYear() + "-" + month.getMonth();
    }

    public void setCustomDayView(DayViewAdapter dayViewAdapter) {
        this.dayViewAdapter = dayViewAdapter;
        if (null != adapter) {
            adapter.notifyDataSetChanged();
        }
    }

    private void validateAndUpdate() {
        if (getAdapter() == null) {
            setAdapter(adapter);
        }
        adapter.notifyDataSetChanged();
    }


}
