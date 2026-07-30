package ir.sabou.inventory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.sabou.inventory.data.repository.DashboardRepository
import ir.sabou.inventory.data.repository.DashboardSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(repository: DashboardRepository) : ViewModel() {
    val state: StateFlow<DashboardSnapshot> = repository.snapshot.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardSnapshot(),
    )

    companion object {
        fun factory(repository: DashboardRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(DashboardViewModel::class.java))
                    return DashboardViewModel(repository) as T
                }
            }
    }
}

