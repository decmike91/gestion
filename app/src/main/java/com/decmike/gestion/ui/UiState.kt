package com.decmike.gestion.ui

import androidx.compose.runtime.mutableStateListOf
import com.decmike.gestion.data.DuplicateGroup
import com.decmike.gestion.data.FileQuery
import kotlinx.coroutines.flow.MutableStateFlow

/** État d'interface conservé pendant la navigation. */
object UiState {
    val fileQuery = MutableStateFlow(FileQuery())
    val duplicates = mutableStateListOf<DuplicateGroup>()
    val dupSelection = mutableStateListOf<String>()
    val audioSearch = MutableStateFlow("")
}
