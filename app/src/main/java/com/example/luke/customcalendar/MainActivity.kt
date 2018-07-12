package com.example.luke.customcalendar

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.example.luke.customcalendar.calendar.CalendarMainView
import com.example.luke.customcalendar.calendar.DefaultDayViewAdapter
import java.util.*

class MainActivity : AppCompatActivity() {

    lateinit var calendar: CalendarMainView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val nextYear = Calendar.getInstance()
        nextYear.add(Calendar.YEAR, 1)

        val lastYear = Calendar.getInstance()
        lastYear.add(Calendar.YEAR, -1)

        calendar = findViewById(R.id.calendar_view) as CalendarMainView

//        calendar.setCustomDayView(DefaultDayViewAdapter())
        val today = Calendar.getInstance()
        val dates = ArrayList<Date>()
        today.add(Calendar.DATE, 3)
        dates.add(today.time)
        today.add(Calendar.DATE, 5)
        dates.add(today.time)
        calendar.init(Date(), nextYear.time) //
                .withSelectedDates(dates)
    }
}
