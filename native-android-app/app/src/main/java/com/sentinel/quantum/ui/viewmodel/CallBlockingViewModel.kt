package com.sentinel.quantum.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.quantum.security.CallBlocklistStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CallBlockingViewModel(
    private val blocklistStore: CallBlocklistStore
) : ViewModel() {
    private val _blockedItems = MutableStateFlow<List<String>>(emptyList())
    val blockedNumbers: StateFlow<List<String>> = _blockedItems.asStateFlow()

    fun blockNumber(number: String, reason: String) {
        if (number.isBlank()) return
        viewModelScope.launch {
            blocklistStore.addBlockedNumber(number.trim())
            if (!_blockedItems.value.contains(number.trim())) {
                _blockedItems.value = _blockedItems.value + number.trim()
            }
        }
    }

    fun unblockNumber(number: String) {
        viewModelScope.launch {
            _blockedItems.value = _blockedItems.value - number
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            blocklistStore.clearBlockedNumbers()
            _blockedItems.value = emptyList()
        }
    }
}
