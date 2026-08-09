package com.network24.player.features.live.activity

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.internal.NavigationMenuView
import com.network24.player.R
import com.network24.player.core.base.BaseActivity
import com.network24.player.core.preferences.PreferenceManager
import com.network24.player.databinding.ActivityLiveCategoryBinding
import com.network24.player.features.dashboard.activity.DashboardActivity
import com.network24.player.features.live.adapter.CategoryAdapter
import com.network24.player.features.live.adapter.FavoriteCategoryAdapter
import com.network24.player.features.live.models.LiveCategory
import com.network24.player.features.live.repository.LiveRepository
import com.network24.player.features.live.repository.SyncCallback
import com.network24.player.features.login.activity.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class LiveCategoryActivity : BaseActivity() {

    private lateinit var binding: ActivityLiveCategoryBinding
    private lateinit var repository: LiveRepository
    private lateinit var prefs: PreferenceManager

    private val allCategories = mutableListOf<LiveCategory>()
    private val favoriteCategories = mutableListOf<LiveCategory>()

    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var favoriteAdapter: FavoriteCategoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveCategoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        registerDrawerBackHandler(binding.drawerLayout)

        prefs = PreferenceManager(this)
        repository = LiveRepository(this)

        setupDrawerAndMenu()
        setupRecyclerViews()
        setupSearch()

        ensureInitialSyncThenLoad()
    }

    private fun ensureInitialSyncThenLoad() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val categories = repository.getCategories(
                    server = prefs.getServer(),
                    username = prefs.getUsername(),
                    password = prefs.getPassword(),
                    forceRefresh = false
                )

                withContext(Dispatchers.Main) {
                    if (categories.isNotEmpty()) {
                        updateUIWithCategories(categories)
                    } else {
                        forceRefreshData(isInitialSync = true)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LiveCategoryActivity, e.message ?: "Initial load failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadCategoriesFromDB() {
        // Safe: called from Main Thread
        binding.edtSearch.clearFocus()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val categories = repository.getCategories(
                    server = prefs.getServer(),
                    username = prefs.getUsername(),
                    password = prefs.getPassword(),
                    forceRefresh = false
                )

                withContext(Dispatchers.Main) {
                    updateUIWithCategories(categories)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LiveCategoryActivity, e.message ?: "Unknown Error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateUIWithCategories(categories: List<LiveCategory>) {
        allCategories.clear()
        allCategories.addAll(categories)
        categoryAdapter.updateList(allCategories)

        val savedFavIds = getSavedFavorites()
        favoriteCategories.clear()
        for (category in allCategories) {
            if (savedFavIds.contains(category.category_id.toString())) {
                favoriteCategories.add(category)
            }
        }

        binding.txtCategoryCount.text = "${allCategories.size} Categories"
        favoriteAdapter.updateList(favoriteCategories)
        updateFavoritesSectionVisibility()

        binding.rvCategories.post {
            binding.rvCategories.postDelayed({
                binding.rvCategories.layoutManager?.findViewByPosition(0)?.requestFocus()
            }, 50)
        }
    }

    private var isRefreshing = false

    private fun forceRefreshData(isInitialSync: Boolean = false) {
        if (isRefreshing) return
        isRefreshing = true

        val msg = if (isInitialSync) "Downloading Categories for the first time…" else "Refreshing categories & channels…"

        runCallbackSyncWithLoader(
            loadingMessage = msg,
            successMessage = "Channels Updated Successfully!"
        ) { onSuccess, onError ->
            repository.syncAllData(
                server = prefs.getServer(),
                username = prefs.getUsername(),
                password = prefs.getPassword(),
                callback = object : SyncCallback {
                    override fun onSuccess() {
                        // ✅ FIX: Force UI thread update for callbacks
                        lifecycleScope.launch(Dispatchers.Main) {
                            isRefreshing = false
                            prefs.setLastSyncTime(System.currentTimeMillis())
                            onSuccess()
                            loadCategoriesFromDB()
                        }
                    }
                    override fun onError(message: String) {
                        // ✅ FIX: Force UI thread update for callbacks
                        lifecycleScope.launch(Dispatchers.Main) {
                            isRefreshing = false
                            onError("Failed to refresh: $message")
                        }
                    }
                }
            )
        }
    }

    private fun setupDrawerAndMenu() {
        binding.btnMore.setOnClickListener { openRightDrawer(binding.drawerLayout) }

        setupOptionalRightDrawerMenu(
            drawerLayout = binding.drawerLayout,
            navView = binding.rightNav
        ) { itemId ->
            when (itemId) {
                R.id.action_home -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                    true
                }
                R.id.action_refresh_all -> {
                    forceRefreshData()
                    true
                }
                R.id.action_refresh_guide -> {
                    refreshTvGuide()
                    true
                }
                R.id.action_settings -> true
                R.id.action_logout -> {
                    prefs.clear()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finishAffinity()
                    true
                }
                else -> false
            }
        }

        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                if (drawerView.id == binding.rightNav.id) {
                    binding.rightNav.post {
                        val menuView = binding.rightNav.getChildAt(0) as? NavigationMenuView
                        if (menuView != null) {
                            for (i in 0 until menuView.childCount) {
                                val child = menuView.getChildAt(i)
                                if (child.isFocusable) {
                                    child.requestFocus()
                                    break
                                }
                            }
                        }
                    }
                }
            }
        })
    }

    private fun setupRecyclerViews() {
        val columns = if (resources.configuration.smallestScreenWidthDp >= 600) 6 else 3
        binding.rvCategories.layoutManager = GridLayoutManager(this, columns)
        binding.rvFavorite.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        val snapHelper = object : LinearSnapHelper() {
            override fun calculateDistanceToFinalSnap(layoutManager: RecyclerView.LayoutManager, targetView: View): IntArray {
                val out = IntArray(2)
                val viewStart = targetView.left - layoutManager.getLeftDecorationWidth(targetView)
                out[0] = viewStart - layoutManager.paddingLeft
                out[1] = 0
                return out
            }
            override fun findSnapView(layoutManager: RecyclerView.LayoutManager): View? {
                if (layoutManager !is LinearLayoutManager) return null
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val firstView = layoutManager.findViewByPosition(0)
                if (firstVisible == 0 && firstView != null) {
                    val viewStart = firstView.left - layoutManager.getLeftDecorationWidth(firstView)
                    val distance = abs(viewStart - layoutManager.paddingLeft)
                    if (distance < firstView.width / 2) return null
                }
                var closestChild: View? = null
                var closestDistance = Int.MAX_VALUE
                for (i in 0 until layoutManager.childCount) {
                    val child = layoutManager.getChildAt(i) ?: continue
                    val viewStart = child.left - layoutManager.getLeftDecorationWidth(child)
                    val distance = abs(viewStart - layoutManager.paddingLeft)
                    if (distance < closestDistance) {
                        closestDistance = distance
                        closestChild = child
                    }
                }
                return closestChild
            }
        }
        snapHelper.attachToRecyclerView(binding.rvFavorite)

        categoryAdapter = CategoryAdapter(
            listener = { openCategory(it) },
            onLongClick = { addToFavorites(it) }
        )

        favoriteAdapter = FavoriteCategoryAdapter(
            columns = columns,
            listener = { openCategory(it) },
            onLongClick = { removeFromFavorites(it) }
        )

        binding.rvCategories.adapter = categoryAdapter
        binding.rvFavorite.adapter = favoriteAdapter
        binding.rvCategories.setHasFixedSize(true)
        binding.rvFavorite.setHasFixedSize(true)
    }

    private fun setupSearch() {
        binding.edtSearch.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    filter(s.toString())
                }
                override fun afterTextChanged(s: Editable?) {}
            }
        )
    }

    private fun filter(keyword: String) {
        // ✅ FIX: Null safety added for category_name
        val filtered = allCategories.filter { it.category_name.contains(keyword, true) }
        categoryAdapter.updateList(filtered)
    }

    private fun openCategory(category: LiveCategory) {
        val intent = Intent(this, ChannelListActivity::class.java)
        intent.putExtra("category_id", category.category_id)
        intent.putExtra("category_name", category.category_name)
        startActivity(intent)
    }

    private fun getSavedFavorites(): Set<String> {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        return sharedPreferences.getStringSet("fav_categories", emptySet()) ?: emptySet()
    }

    private fun saveFavoritesList(favIds: Set<String>) {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        sharedPreferences.edit().putStringSet("fav_categories", favIds).apply()
    }

    private fun addToFavorites(category: LiveCategory) {
        val currentFavs = getSavedFavorites().toMutableSet()
        val catId = category.category_id.toString()
        if (currentFavs.contains(catId)) {
            Toast.makeText(this, "${category.category_name} already in Favorites", Toast.LENGTH_SHORT).show()
            return
        }
        currentFavs.add(catId)
        saveFavoritesList(currentFavs)

        favoriteCategories.add(category)
        favoriteAdapter.updateList(favoriteCategories)
        updateFavoritesSectionVisibility()
        Toast.makeText(this, "${category.category_name} added to Favorites", Toast.LENGTH_SHORT).show()
    }

    private fun removeFromFavorites(category: LiveCategory) {
        val currentFavs = getSavedFavorites().toMutableSet()
        val catId = category.category_id.toString()
        currentFavs.remove(catId)
        saveFavoritesList(currentFavs)

        favoriteCategories.removeAll { it.category_id.toString() == catId }
        favoriteAdapter.updateList(favoriteCategories)
        updateFavoritesSectionVisibility()
        Toast.makeText(this, "${category.category_name} removed from Favorites", Toast.LENGTH_SHORT).show()
    }

    private fun updateFavoritesSectionVisibility() {
        val hasFav = favoriteCategories.isNotEmpty()
        binding.favoritesSection.visibility = if (hasFav) View.VISIBLE else View.GONE
        binding.txtFavoriteCount.text = "${favoriteCategories.size} Favorites"
    }

}
