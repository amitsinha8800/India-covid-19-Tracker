package com.amitsinha.covid_19tracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AbsListView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.work.*
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATED_IDENTITY_EQUALS")
class MainActivity : AppCompatActivity() {
    lateinit var stateAdapter: StateAdapter
    lateinit var progressLayout: RelativeLayout
    private lateinit var progressBar: ProgressBar

    @InternalCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        list.addHeaderView(LayoutInflater.from(this).inflate(R.layout.item_header, list, false))

        progressLayout = findViewById(R.id.progressLayout)
        progressBar = findViewById(R.id.progressBar)
        progressLayout.visibility = View.VISIBLE

        fetchResults()
        swipeToRefresh.setOnRefreshListener {
            fetchResults()
        }
        initWorker()
        list.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {}
            override fun onScroll(
                view: AbsListView,
                firstVisibleItem: Int,
                visibleItemCount: Int,
                totalItemCount: Int
            ) {
                if (list.getChildAt(0) != null) {
                    swipeToRefresh.isEnabled = list.firstVisiblePosition === 0 && list.getChildAt(0).getTop() === 0
                }
            }
        })


    }

    @InternalCoroutinesApi
    private fun initWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val notificationWorkRequest =
            PeriodicWorkRequestBuilder<NotificationWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "JOB_TAG",
            ExistingPeriodicWorkPolicy.KEEP,
            notificationWorkRequest
        )
    }

    private fun fetchResults() {
        GlobalScope.launch {

            val response = withContext(Dispatchers.IO) { Client.api.clone().execute() }
            if (response.isSuccessful) {
                swipeToRefresh.isRefreshing = false
                val data = Gson().fromJson(response.body?.string(), Response::class.java)
                launch(Dispatchers.Main) {
                    progressLayout.visibility = View.GONE
                    bindCombinedData(data.statewise[0])
                    bindSateWiseData(data.statewise.subList(0, data.statewise.size))
                }
            }

        }


    }


    private fun bindSateWiseData(subList: List<StatewiseItem>) {
        stateAdapter = StateAdapter(subList)
        list.adapter = stateAdapter

    }

    private fun bindCombinedData(data: StatewiseItem) {
        val lastUpdatedTime = data.lastupdatedtime
        val simpleDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
        lastUpdatedTv.text = "LastUpdated\n ${getTime(simpleDateFormat.parse(lastUpdatedTime))}"
        confirmedTv.text = data.confirmed
        recoveredTv.text = data.recovered
        activeTv.text = data.active
        deceasedTv.text = data.deaths

    }


}

fun getTime(past: Date): String {
    val now = Date()
    val seconds = TimeUnit.MILLISECONDS.toSeconds(now.time - past.time)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(now.time - past.time)
    val hours = TimeUnit.MILLISECONDS.toHours(now.time - past.time)

    return when {
        seconds < 60 -> {
            "Few seconds ago"
        }
        minutes < 60 -> {
            "$minutes minutes ago"
        }
        hours < 24 -> {
            "$hours hour ${minutes % 60} min ago"
        }
        else -> {
            SimpleDateFormat("dd/MM,yyyy HH:mm:ss").format(past).toString()
        }
    }
}