package com.anselm.books.ui.sync

import android.accounts.AccountManager
import android.accounts.AccountManagerCallback
import android.accounts.AccountManagerFuture
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.BooksApplication.Reporter
import com.anselm.books.GlideApp
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.databinding.FragmentSyncBinding
import com.anselm.books.ui.widgets.BookFragment
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val SIMPLE_DATE_FORMAT by lazy {
    val format = SimpleDateFormat("EEE, MMM d HH:mm:ss", Locale.US)
    format.timeZone = TimeZone.getDefault()
    format
}

class SyncFragment: BookFragment() {
    private var _binding: FragmentSyncBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SyncViewModel by viewModels()
    private val signInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestProfile()
            .build()
        GoogleSignIn.getClient(requireActivity(), gso)
    }
    private val accountManager by lazy {
        AccountManager.get(requireContext())
    }

    private var logInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> handleSignData(result.data) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentSyncBinding.inflate(inflater)
        if (viewModel.account == null) {
            silentSignIn()
        } else {
            bindWithAccount()
        }

        binding.idLogoutButton.setOnClickListener {
            signInClient.signOut().addOnCompleteListener {
                viewModel.account = null
                binding.idSyncButton.text = getString(R.string.sync_login)
                binding.idSyncButton.setOnClickListener { logIn() }
            }
            signInClient.revokeAccess().addOnCompleteListener {
                Log.d(TAG, "Fully logged out.")
            }
        }
        handleMenu()
        displayStatus()
        return binding.root
    }

    private fun displayStatus() {
        binding.idSyncButton.isEnabled = ! viewModel.inSync
        val config = SyncConfig.get()
        binding.idLastSyncDate.text = if (config.hasSynced()) {
            SIMPLE_DATE_FORMAT.format(config.lastSync())
        } else {
            getString(R.string.no_sync_available)
        }
    }

    private fun bindWithAccount() {
        check(viewModel.account != null)
        binding.idUserDisplayName.text = viewModel.displayName
        GlideApp.with(app.applicationContext)
            .load(viewModel.photoUrl)
            .fallback(R.drawable.user_profile)
            .into(binding.idUserPhoto)
        binding.idSyncButton.text = getString(R.string.sync_do_sync)
        binding.idSyncButton.setOnClickListener {
            auth()
        }
        binding.idLogoutButton.isEnabled = true
    }

    private fun bindWithoutAccount() {
        binding.idSyncButton.text = getString(R.string.sync_login)
        binding.idSyncButton.setOnClickListener { logIn() }
        binding.idLogoutButton.isEnabled = false
    }

    private var authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "result: $result.")
        auth(fromIntent = true)
    }

    // https://developer.android.com/training/id-auth/authenticate
    private fun auth(fromIntent: Boolean = false) {
        val account = viewModel.account!!
        val options = Bundle()
        // "https://www.googleapis.com/auth/drive.file",
        accountManager.getAuthToken(
            account,
            "oauth2: https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.appdata",
            options,
            requireActivity(),
            object : AccountManagerCallback<Bundle> {
                override fun run(result: AccountManagerFuture<Bundle>?) {
                    if (result != null && result.result != null) {
                        val bundle = result.result
                        // If the bundle has an intent, we need to run it.
                        val intent = bundle.get(AccountManager.KEY_INTENT) as? Intent
                        if (intent != null && ! fromIntent) {
                            authLauncher.launch(intent)
                        } else {
                            withToken(bundle.getString(AccountManager.KEY_AUTHTOKEN)!!)
                        }
                    } else {
                        Log.d(TAG, "Auth failed, user feedback needed.")
                    }
                }
            },
            Looper.myLooper()?.let {
                Handler(it, object : Handler.Callback {
                    override fun handleMessage(msg: Message): Boolean {
                        Log.d(TAG, "An error occurred: $msg")
                        return true
                    }

                })
            }
        )
    }

    private var job: SyncJob? = null
    private var reporter: Reporter? = null
    private fun withToken(authToken: String) {
        //Displays the progress dialog, and sets up the progress reporter.
        reporter = app.openReporter(
            getString(R.string.sync_started),
            isIndeterminate = false
        ) { job?.cancel() }
        binding.idSyncButton.isEnabled = false
        // Proceeds. With care cause we might noo longer be in this fragment when the job completes.
        viewModel.inSync = true
        job = SyncDrive(authToken, reporter!!).sync { finishedJob, syncFailed ->
            viewModel.inSync = false
            reporter?.close()
            // We want to help GC a bit by nullifying job and progressReporter.
            val isCancelled = finishedJob.isCancelled
            job = null
            reporter = null
            app.applicationScope.launch {
                val totalCount = app.repository.getTotalCount()
                app.postOnUiThread {
                    app.title = app.getString(R.string.book_count, totalCount)
                }
            }
            app.postOnUiThread {
                if (isCancelled) {
                    app.toast(app.getString(R.string.sync_cancelled))
                } else if (syncFailed) {
                    app.toast(app.getString(R.string.sync_failed))
                } else {
                    app.toast(app.getString(R.string.sync_success))
                }
                if ( ! this@SyncFragment.isDetached && _binding != null) {
                    displayStatus()
                }
            }
        }
    }

    private fun silentSignIn() {
        signInClient.silentSignIn().addOnCompleteListener {
            if (it.exception == null && it.result != null) {
                val account = it.result
                viewModel.account = account.account
                viewModel.photoUrl = account.photoUrl
                viewModel.displayName = account.displayName
                bindWithAccount()
            } else {
                Log.e(TAG, "silentSignIn.onCompleteListener: failed.", it.exception)
                bindWithoutAccount()
            }
        }.addOnFailureListener {
            Log.e(TAG, "silentSignIn.onFailureListener: failed.", it)
            bindWithoutAccount()
        }.addOnCanceledListener {
            bindWithoutAccount()
        }
    }

    private fun logIn() {
        logInLauncher.launch(signInClient.signInIntent)
    }

    private fun handleSignData(data: Intent?) {
        GoogleSignIn.getSignedInAccountFromIntent(data)
            .addOnCompleteListener {
                if (it.isSuccessful && it.result != null) {
                    viewModel.photoUrl = it.result.photoUrl
                    viewModel.displayName = it.result.displayName
                    viewModel.account = it.result.account!!
                    bindWithAccount()
                    binding.idSyncButton.text = getString(R.string.sync_do_sync)
                    binding.idSyncButton.setOnClickListener { auth() }
                } else {
                    // authentication failed
                    Log.e(TAG, "Failed to log in.", it.exception)
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
