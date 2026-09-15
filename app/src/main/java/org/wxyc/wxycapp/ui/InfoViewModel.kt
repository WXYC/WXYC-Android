package org.wxyc.wxycapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.wxyc.wxycapp.requestline.RequestLineClient
import javax.inject.Inject

@HiltViewModel
class InfoViewModel @Inject constructor(
    private val requestLineClient: RequestLineClient,
) : ViewModel() {

    private val _requestStatus = MutableSharedFlow<String>()
    val requestStatus: SharedFlow<String> = _requestStatus.asSharedFlow()

    fun makeRequest(requestText: String) {
        if (requestText.isBlank()) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val status = when (val result = requestLineClient.send(requestText)) {
                RequestLineClient.Result.Sent -> "Request sent!"
                is RequestLineClient.Result.Failed -> "Failed to send: ${result.statusCode}"
                is RequestLineClient.Result.NetworkError -> "Network error"
            }
            _requestStatus.emit(status)
        }
    }
}
