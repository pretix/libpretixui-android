package eu.pretix.libpretixui.android.setup

import android.Manifest
import android.app.Dialog
import android.app.ProgressDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.method.TextKeyListener
import android.text.method.TextKeyListener.Capitalize
import android.util.Log
import android.view.KeyEvent
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDialog
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.pretix.libpretixsync.setup.SetupBadRequestException
import eu.pretix.libpretixsync.setup.SetupBadResponseException
import eu.pretix.libpretixsync.setup.SetupException
import eu.pretix.libpretixsync.setup.SetupServerErrorException
import eu.pretix.libpretixui.android.R
import eu.pretix.libpretixui.android.databinding.FragmentSetupBinding
import eu.pretix.libpretixui.android.scanning.HardwareScanner
import eu.pretix.libpretixui.android.scanning.ScanReceiver
import eu.pretix.libpretixui.android.scanning.ScannerView
import eu.pretix.libpretixui.android.scanning.ScannerView.ResultHandler
import eu.pretix.libpretixui.android.scanning.defaultToScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.lang.Exception
import javax.net.ssl.SSLException

interface SetupCallable {
    fun setup(url: String, token: String)
    fun onSucessfulSetup()
    fun onGenericSetupException(e: Exception)
}

class SetupFragment : Fragment() {
    companion object {
        const val ARG_DEFAULT_HOST = "default_host"
    }

    private var _binding: FragmentSetupBinding? = null
    private val binding get() = _binding!!
    private var defaultHost: String? = null
    private var useCamera = true
    var lastScanTime = 0L
    var lastScanValue = ""
    private var currentOpenAlert: AppCompatDialog? = null
    private var tkl = TextKeyListener(Capitalize.NONE, false)
    private var keyboardEditable = Editable.Factory.getInstance().newEditable("")
    private var ongoingSetup = false
    private val LOG_TAG = this::class.java.name

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val hardwareScanner = HardwareScanner(object : ScanReceiver {
        override fun scanResult(result: String) {
            handleScan(result)
        }
    })

    private val scannerResultHandler = object : ResultHandler {
        override fun handleResult(rawResult: ScannerView.Result) {
            if (lastScanValue == rawResult.text && lastScanTime > System.currentTimeMillis() - 3000) {
                return
            }
            lastScanValue = rawResult.text
            lastScanTime = System.currentTimeMillis()
            handleScan(rawResult.text)
        }
    }

    fun handleScan(result: String) {
        try {
            val jd = JSONObject(result)
            if (jd.has("version")) {
                alert(R.string.setup_error_legacy_qr_code)
                return
            }
            if (!jd.has("handshake_version")) {
                alert(R.string.setup_error_invalid_qr_code)
                return
            }
            if (jd.getInt("handshake_version") > 1) {
                alert(R.string.setup_error_version_too_high)
                return
            }
            if (!jd.has("url") || !jd.has("token")) {
                alert(R.string.setup_error_invalid_qr_code)
                return
            }
            initialize(jd.getString("url"), jd.getString("token"))
        } catch (e: JSONException) {
            alert(R.string.setup_error_invalid_qr_code)
            return
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        defaultHost = requireArguments().getString(ARG_DEFAULT_HOST, "")
        useCamera = !defaultToScanner()

        binding.btSwitchCamera.setOnClickListener {
            hardwareScanner.stop(requireContext())
            useCamera = true
            binding.llHardwareScan.visibility = View.GONE
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                binding.llCameraPermission.visibility = View.GONE
                binding.scannerView.setResultHandler(scannerResultHandler)
                binding.scannerView.startCamera()
            } else {
                binding.llCameraPermission.visibility = View.VISIBLE
            }
        }

        val menuHost: MenuHost = requireActivity() as MenuHost
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_setup_lib, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_manual -> {
                        showManualSetupDialog()
                        return true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        val requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (!isGranted) {
                    binding.llCameraPermission.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), getString(R.string.setup_camera_permission_needed), Toast.LENGTH_SHORT).show()
                } else {
                    binding.llCameraPermission.visibility = View.GONE
                    binding.scannerView.startCamera()
                    if (useCamera) {
                        binding.scannerView.setResultHandler(scannerResultHandler)
                        binding.scannerView.startCamera()
                    }
                }
            }
        binding.btCameraPermission.setOnClickListener {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        if (useCamera) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                binding.llCameraPermission.visibility = View.VISIBLE
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                binding.llCameraPermission.visibility = View.GONE
            }
        } else {
            binding.llCameraPermission.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        if (useCamera) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                binding.llCameraPermission.visibility = View.GONE
                binding.scannerView.setResultHandler(scannerResultHandler)
                binding.scannerView.startCamera()
            } else {
                binding.llCameraPermission.visibility = View.VISIBLE
            }
        }
        binding.llHardwareScan.visibility = if (useCamera) View.GONE else View.VISIBLE
        hardwareScanner.start(requireContext())
    }

    override fun onPause() {
        super.onPause()
        if (useCamera && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            binding.scannerView.stopCamera()
        }
        hardwareScanner.stop(requireContext())
    }

    fun showManualSetupDialog() {
        childFragmentManager.setFragmentResultListener(
            ManualSetupDialogFragment.KEY,
            viewLifecycleOwner,
            { _, bundle ->
                val url = bundle.getString(ManualSetupDialogFragment.RESULT_URL)
                val token = bundle.getString(ManualSetupDialogFragment.RESULT_TOKEN)
                if (url != null && token != null) {
                    initialize(url, token)
                }
                childFragmentManager.clearFragmentResultListener(ManualSetupDialogFragment.KEY)
            })

        val args = bundleOf(ManualSetupDialogFragment.ARG_DEFAULT_URL to defaultHost)
        childFragmentManager.beginTransaction()
            .add(ManualSetupDialogFragment::class.java, args, "MANUAL_SETUP_DIALOG")
            .commit()
    }

    // NOTE: this needs manually be called in the Activity
    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.action == KeyEvent.ACTION_UP) {
                handleScan(keyboardEditable.toString())
                keyboardEditable.clear()
            }
            return true
        }
        val processed = when (event.action) {
            KeyEvent.ACTION_DOWN -> tkl.onKeyDown(null, keyboardEditable, event.keyCode, event)
            KeyEvent.ACTION_UP -> tkl.onKeyUp(null, keyboardEditable, event.keyCode, event)
            else -> tkl.onKeyOther(null, keyboardEditable, event)
        }
        if (processed) {
            return true
        }
        return false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initialize(url: String, token: String) {
        if (ongoingSetup) {
            Log.w(LOG_TAG, "Ongoing setup. Discarding initialize with ${url} / ${token}.")
            return
        }
        ongoingSetup = true

        val pdialog = ProgressDialog(requireContext()).apply {
            isIndeterminate = true
            setMessage(getString(R.string.setup_progress))
            setTitle(R.string.setup_progress)
            setCanceledOnTouchOutside(false)
            setCancelable(false)
        }

        fun resume() {
            pdialog.dismiss()
            ongoingSetup = false
        }

        pdialog.show()

        runBlocking {
            val bgScope = CoroutineScope(Dispatchers.IO)
            try {
                bgScope.async {
                    // FIXME: propagate useCamera back to appconfig
                    (requireActivity() as SetupCallable).setup(url, token)
                }.await()
                (requireActivity() as SetupCallable).onSucessfulSetup()
            } catch (e: SetupBadRequestException) {
                e.printStackTrace()
                if (parentFragmentManager.isDestroyed) {
                    return@runBlocking
                }
                resume()
                alert(R.string.setup_error_request)
            } catch (e: SSLException) {
                e.printStackTrace()
                if (parentFragmentManager.isDestroyed) {
                    return@runBlocking
                }
                resume()
                alert(R.string.setup_error_ssl)
            } catch (e: IOException) {
                e.printStackTrace()
                if (parentFragmentManager.isDestroyed) {
                    return@runBlocking
                }
                resume()
                alert(R.string.setup_error_io)
            } catch (e: SetupServerErrorException) {
                if (parentFragmentManager.isDestroyed) {
                    return@runBlocking
                }
                resume()
                alert(R.string.setup_error_server)
            } catch (e: SetupBadResponseException) {
                e.printStackTrace()
                if (parentFragmentManager.isDestroyed) {
                    return@runBlocking
                }
                resume()
                alert(R.string.setup_error_response)
            } catch (e: SetupException) {
                e.printStackTrace()
                if (parentFragmentManager.isDestroyed) {
                    return@runBlocking
                }
                resume()
                alert(e.message ?: "Unknown error")
            } catch (e: Exception) {
                e.printStackTrace()
                (requireActivity() as SetupCallable).onGenericSetupException(e)
                if (parentFragmentManager.isDestroyed) {
                    return@runBlocking
                }
                resume()
                alert(e.message ?: "Unknown error")
            }
        }
    }

    fun alert(id: Int) { alert(getString(id)) }
    fun alert(message: CharSequence) {
        if (currentOpenAlert != null) {
            currentOpenAlert!!.dismiss()
        }
        currentOpenAlert = MaterialAlertDialogBuilder(requireContext()).setMessage(message).create()
        currentOpenAlert!!.show()
    }
}

// having the manual setup alert as a dialog fragment
// allows it to keep its input after rotating the device
class ManualSetupDialogFragment : DialogFragment() {
    companion object {
        const val KEY = "ManualSetupDialogFragment"
        const val ARG_DEFAULT_URL = "default_url"
        const val RESULT_URL = "url"
        const val RESULT_TOKEN = "token"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val defaultUrl = arguments?.getString(ARG_DEFAULT_URL, "")

        val builder = MaterialAlertDialogBuilder(requireActivity())
        val view = layoutInflater.inflate(R.layout.dialog_setup_manual, null)
        val inputUri = view.findViewById<EditText>(R.id.input_uri)
        if (!defaultUrl.isNullOrEmpty()) {
            inputUri.setText(defaultUrl)
        }
        val inputToken = view.findViewById<EditText>(R.id.input_token)
        builder.setView(view)
        builder.setPositiveButton(R.string.ok) { dialog, _ ->
            dialog.dismiss()
            parentFragmentManager.setFragmentResult(
                KEY,
                bundleOf(
                    RESULT_URL to inputUri.text.toString(),
                    RESULT_TOKEN to inputToken.text.toString()
                )
            )
        }
        builder.setNegativeButton(android.R.string.cancel) { dialog, _ ->
            dialog.cancel()
        }
        return builder.create()
    }
}