package com.merabills.question3

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.metrics.performance.JankStats
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.merabills.question3.adapter.RowAdapter
import com.merabills.question3.viewmodel.MainViewModel
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {
    //region Jank stats tracking - do not modify
    private lateinit var jankStatsTracker: JankStats
    private val totalJankyFrames = AtomicInteger(0)
    //endregion

    //shiv's code
    private lateinit var viewModel: MainViewModel
    private lateinit var rowAdapter: RowAdapter
    private lateinit var rowsRecyclerView: RecyclerView
    private var isFirstRowsEmission = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Your code goes here
        setupViewModel()
        setupRecyclerView()
        observeViewModel()

        //region Jank stats tracking - do not modify
        val totalJankyFramesLiveData = MutableLiveData(0)
        findViewById<Button>(R.id.resetButton).setOnClickListener {
            totalJankyFrames.set(0)
            totalJankyFramesLiveData.value = totalJankyFrames.get()
        }

        val jankStatsListener = JankStats.OnFrameListener { frameData ->
            if (frameData.isJank) totalJankyFramesLiveData.postValue(totalJankyFrames.incrementAndGet())
        }
        jankStatsTracker = JankStats.createAndTrack(window, jankStatsListener)

        val jankStatsTextView = findViewById<TextView>(R.id.jankStatsTextView)
        totalJankyFramesLiveData.observe(this) { jankyFrames ->
            jankStatsTextView.text = getString(R.string.jank_stats, jankyFrames)
        }
        //endregion
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
    }

    private fun setupRecyclerView() {
        rowAdapter = RowAdapter { rowId, squareId ->
            val removal = viewModel.removeSquare(rowId, squareId)
            removal?.let { rowAdapter.applyRemoval(it) }
        }

        findViewById<RecyclerView>(R.id.rcv_rows).apply {
            rowsRecyclerView = this
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                initialPrefetchItemCount = 0
                isItemPrefetchEnabled = false
            }
            // Outer RecyclerView scrolls vertically; each row draws squares directly.
            adapter = rowAdapter
            // Critical for performance: every row has the same 32dp height.
            setHasFixedSize(true)
            setItemViewCacheSize(2)
            recycledViewPool.setMaxRecycledViews(0, 100)
            itemAnimator = null
        }
    }

    private fun observeViewModel() {
        viewModel.rows.observe(this) { rows ->
            if (isFirstRowsEmission) {
                isFirstRowsEmission = false
                rowsRecyclerView.post { rowAdapter.setRows(rows) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        //region Jank stats tracking - do not modify
        if (::jankStatsTracker.isInitialized) {
            jankStatsTracker.isTrackingEnabled = true
        }
        //endregion
    }

    override fun onPause() {
        super.onPause()

        //region Jank stats tracking - do not modify
        if (::jankStatsTracker.isInitialized) {
            jankStatsTracker.isTrackingEnabled = false
        }
        //endregion
    }
}
