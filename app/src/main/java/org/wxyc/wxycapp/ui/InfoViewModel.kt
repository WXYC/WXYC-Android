package org.wxyc.wxycapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.wxyc.wxycapp.requestline.RequestLineClient
import javax.inject.Inject

@HiltViewModel
class InfoViewModel @Inject constructor(
    private val requestLineClient: RequestLineClient,
) : ViewModel() {

    /**
     * The outcome of the last request, held until the UI reports showing it.
     *
     * Deliberately state rather than an event stream: InfoScreen is page 1 of a
     * HorizontalPager with the default beyondViewportPageCount, so swiping back
     * to the player disposes its collector. A request-o-matic round trip can
     * take 20 s, far longer than the old ~200 ms Slack webhook, so a listener
     * who swipes away mid-flight is ordinary rather than rare — and an emission
     * with no collector is dropped, leaving no toast at all. Holding the value
     * lets the toast appear when they come back.
     */
    private val _requestStatus = MutableStateFlow<String?>(null)
    val requestStatus: StateFlow<String?> = _requestStatus.asStateFlow()

    fun makeRequest(requestText: String) {
        if (requestText.isBlank()) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _requestStatus.value = when (val result = requestLineClient.send(requestText)) {
                RequestLineClient.Result.Sent -> "Request sent!"
                is RequestLineClient.Result.Failed -> "Failed to send: ${result.statusCode}"
                is RequestLineClient.Result.NetworkError -> "Network error"
            }
        }
    }

    /** Clears the held status so returning to the screen doesn't re-toast it. */
    fun onRequestStatusShown() {
        _requestStatus.value = null
    }
}
