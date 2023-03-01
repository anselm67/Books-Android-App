package com.anselm.books.ui.sync

import android.accounts.Account
import android.net.Uri
import androidx.lifecycle.ViewModel

class SyncViewModel(
    var account: Account? = null,
    var displayName: String? = null,
    var photoUrl: Uri? = null,
    var inSync: Boolean = false,
): ViewModel()