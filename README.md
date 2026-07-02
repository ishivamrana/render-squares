# render-squares

An Android app that renders **10,000 colored squares** in a scrollable grid and lets you tap to remove them — built with a focus on smooth scrolling and minimal jank at scale.

## Overview

On launch, the app generates 10,000 squares grouped into rows. Each row contains a random number of squares (between 1 and 100). Every square has a random color from a fixed palette and a sequential index value displayed inside it.

Tapping a square removes it from its row. If that was the last square in the row, the entire row is removed from the list.

A **Janky Frames** counter at the top tracks UI frame drops using AndroidX JankStats, with a Reset button to clear the count.

## Demo

A screen recording of the final build is included in the repo:

**[Screen_recording_20260702_161950.webm](./Screen_recording_20260702_161950.webm)**

The video demonstrates:

- **Initial load** — 10,000 colored squares rendered across randomly sized rows
- **Smooth scrolling** — fast vertical fling through the full list with minimal janky frames (visible in the counter at the top)
- **Square removal** — tapping a square removes it instantly; removing the last square in a row collapses the entire row
- **JankStats counter** — stays low during scroll and interaction, reflecting the DiffUtil + shared `RecycledViewPool` optimizations described below

> Recorded on device after the final performance pass (`fix: removed janky frames`).

## How It Works

### Data generation

`MainViewModel` generates all 10,000 squares on a background thread (`Dispatchers.Default`) so the main thread stays free during startup. Squares are distributed across rows until none remain:

- Each row gets a random count between 1 and 100 (capped by remaining squares).
- Each square receives a unique `id`, a random color, and a global `value` index (0–9999).

The result is exposed to the UI as `LiveData<List<Row>>`.

### Display

The UI uses a **nested RecyclerView** layout:

```
Vertical RecyclerView (rows)
└── Horizontal RecyclerView (squares per row)
    └── Square TextView (32dp × 32dp)
```

- The outer list scrolls vertically through rows.
- Each row is a horizontal `RecyclerView` of square cells.
- Squares show their index value on a colored background.

### Interaction

When a square is tapped:

1. `MainViewModel.removeSquare(rowId, squareId)` updates the in-memory state under a lock.
2. If the row still has squares, only that row is updated.
3. If the row becomes empty, it is removed from the list entirely.
4. The adapter applies a **targeted UI update** (`notifyItemRemoved`) instead of rebinding the full list.

This keeps removals fast even with thousands of items on screen.

## Architecture


| Layer            | Responsibility                                                   |
| ---------------- | ---------------------------------------------------------------- |
| `MainActivity`   | Wires up the UI, observes `LiveData`, handles JankStats          |
| `MainViewModel`  | Generates data, manages state, handles square removal            |
| `RowAdapter`     | Vertical list of rows; hosts a horizontal `RecyclerView` per row |
| `SquareAdapter`  | Renders individual square cells and click handling               |
| `Row` / `Square` | Immutable data models                                            |


The app follows **MVVM**: the ViewModel owns state, the Activity observes it, and adapters handle rendering.

## Minimizing Janky Frames

Jank (dropped or delayed frames during scroll/interaction) was the main performance challenge with 10,000 items. The counter at the top of the screen helped measure each change. Below is the progression from the first working version to the current implementation.

### 1. Initial approach — high jank

The first version got the UI working but produced noticeably high janky frame counts when scrolling:

- Adapter updates used `**notifyDataSetChanged()`** instead of granular notifications. Every data change forced a **full list rebind**, so RecyclerView threw away diff information and rebound all visible ViewHolders.
- Each row inflated its squares **directly** (a single row ViewHolder holding all square views) rather than using a nested RecyclerView. As rows scrolled in and out, views were **created and destroyed repeatedly** with no cross-row reuse.
- There was **no shared `RecycledViewPool`** — each horizontal row kept its own isolated pool (or no pooling at all), so square ViewHolders could not be recycled when a row scrolled off screen and another row appeared.

Together, these caused heavy main-thread work on every scroll frame: layout, measure, bind, and garbage collection across thousands of items.

### 2. Introduced DiffUtil — fewer redundant updates

The first major improvement was replacing blanket `notifyDataSetChanged()` calls with `**DiffUtil**` in both `RowAdapter` and `SquareAdapter`.

```kotlin
val diffResult = DiffUtil.calculateDiff(RowDiffCallback(rows, newRows))
rows = newRows
diffResult.dispatchUpdatesTo(this)
```

**Why it helped:** DiffUtil compares the old and new lists on a background thread and emits only the minimal set of changes (`notifyItemInserted`, `notifyItemRemoved`, `notifyItemChanged`). RecyclerView can then animate or rebind **only what actually changed** instead of every visible item.

For square removal specifically, updates go further: `notifyItemRemoved(index)` is called directly on the affected square, avoiding even a DiffUtil pass during tap interactions.

### 3. Introduced a shared `RecycledViewPool` — reuse across rows

The next improvement was a **single shared pool** passed to every inner (horizontal) RecyclerView:

```kotlin
private val sharedRecycledViewPool = RecyclerView.RecycledViewPool().apply {
    setMaxRecycledViews(0, 10)
}

// In onCreateViewHolder for each row:
innerRecyclerView.setRecycledViewPool(sharedRecycledViewPool)
```

**Why it helped:** By default, each nested RecyclerView owns its own scrap heap. When a row scrolls off screen, its square ViewHolders are discarded. With a shared pool, those holders are **kept alive and handed to the next row** that scrolls into view — avoiding repeated `inflate()` calls during vertical scrolling.

The outer vertical RecyclerView also uses an enlarged pool (`setMaxRecycledViews(0, 100)`) so row ViewHolders are recycled efficiently.

### 4. Switched to nested RecyclerViews

The earlier flat row layout (commented out in `RowAdapter`) inflated all squares for a row at once. The current design uses:

```
Vertical RecyclerView (rows)
└── Horizontal RecyclerView (squares)  ← shared RecycledViewPool
    └── Square TextView (32dp)
```

Only **visible** squares exist as views at any time. A row with 80 squares no longer creates 80 views upfront — only the ~10–15 on screen are inflated.

### 5. Additional tuning (final pass)

After DiffUtil and the shared pool, a few more adjustments brought jank close to zero:


| Change                                     | Reason                                                               |
| ------------------------------------------ | -------------------------------------------------------------------- |
| `itemAnimator = null`                      | Default change animations are expensive at this scale                |
| `setHasFixedSize(true)`                    | Rows are always 32dp tall; skips remeasurement                       |
| `isItemPrefetchEnabled = false`            | Avoids prefetching work during fast flings                           |
| `setItemViewCacheSize(0)` on inner RV      | Relies on the shared pool instead of holding extra detached views    |
| `setItemViewCacheSize(2)` on outer RV      | Small cache for row holders without hoarding memory                  |
| Bind skipping in `SquareViewHolder`        | Skips `setBackgroundColor` / `setText` when `square.id` is unchanged |
| Background data generation                 | 10k squares built on `Dispatchers.Default`, not blocking `onCreate`  |
| `rowsRecyclerView.post { }` for first load | Defers the initial 10k-item insert until after the first layout pass |


### Measuring results

AndroidX **JankStats** (wired in `MainActivity`) counts frames that miss the display deadline. The Reset button lets you clear the counter and re-test after scrolling or tapping. This was used to validate each optimization above — DiffUtil and the shared `RecycledViewPool` produced the largest drops in janky frame count. See the [demo recording](./Screen_recording_20260702_161950.webm) for the end result.  
Also, the Trace file is included in the file.

## Tech Stack

- Kotlin
- AndroidX (AppCompat, RecyclerView, Lifecycle, Coroutines)
- MVVM with `ViewModel` + `LiveData`
- AndroidX Metrics (JankStats)
- Min SDK 24, Target SDK 36

## Project Structure

```
app/src/main/java/com/merabills/question3/
├── MainActivity.kt              # Entry point, RecyclerView setup, JankStats
├── adapter/
│   ├── RowAdapter.kt            # Vertical row list with nested horizontal RVs
│   └── SquareAdapter.kt         # Individual square cells
├── model/
│   ├── Row.kt
│   ├── Square.kt
│   └── SquareRemovalResult.kt
└── viewmodel/
    └── MainViewModel.kt         # Data generation and state management
```

## Getting Started

1. Open the project in Android Studio.
2. Sync Gradle.
3. Run on a device or emulator (API 24+).

## License

This project was built as a technical assessment submission.