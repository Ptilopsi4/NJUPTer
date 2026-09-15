package com.example.njupter.ui.settings

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.njupter.R
import com.example.njupter.data.import.JwxtEndpoints
import com.example.njupter.data.import.SemesterRange
import com.example.njupter.data.import.SemesterRangeParser
import com.example.njupter.ui.theme.NJUPTerTheme
import java.net.URI
import org.json.JSONTokener

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun JwxtImportScreen(
    isActive: Boolean = true,
    onBack: () -> Unit,
    onSemesterInfoObtained: (SemesterRange?) -> Unit = {},
    onTimetableHtmlObtained: (String) -> Unit
) {
    val defaultTitle = stringResource(R.string.jwxt_login_title)
    var title by remember(defaultTitle) { mutableStateOf(defaultTitle) }
    var loadProgress by remember { mutableIntStateOf(-1) }

    // 为了防止多次触发成功回调
    var isSuccess by remember { mutableStateOf(false) }
    var hasRequestedTimetable by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (loadProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { loadProgress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (LocalInspectionMode.current) {
                Text(
                    text = "WebView is not available in Preview",
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true

                            // 需要支持 Cookie
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            webViewClient = object : WebViewClient() {
                                private var extractionAttempts = 0
                                private var semesterAttempts = 0

                                private fun tryExtractTimetableHtml(view: WebView) {
                                    if (isSuccess) return

                                    view.evaluateJavascript(EXTRACT_TIMETABLE_HTML_SCRIPT) { result ->
                                        val html = decodeJavascriptString(result)
                                        if (!html.isNullOrBlank() && !isSuccess) {
                                            isSuccess = true
                                            onTimetableHtmlObtained(html)
                                        } else if (
                                            !isSuccess &&
                                            extractionAttempts < MAX_EXTRACTION_ATTEMPTS
                                        ) {
                                            extractionAttempts++
                                            view.postDelayed(
                                                { tryExtractTimetableHtml(view) },
                                                EXTRACTION_RETRY_DELAY_MS
                                            )
                                        } else if (!isSuccess) {
                                            // 让 ViewModel 进入明确的解析错误状态，避免页面无反馈。
                                            isSuccess = true
                                            onTimetableHtmlObtained("")
                                        }
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    view?.title?.let { title = it }

                                    url?.let { currentUrl ->
                                        val uri = runCatching { URI(currentUrl) }.getOrNull()
                                        val isNewJwxt = uri?.host.equals(
                                            "jwglxt.njupt.edu.cn",
                                            ignoreCase = true
                                        )
                                        val isTimetable = isNewJwxt &&
                                            uri?.path?.startsWith(
                                                JwxtEndpoints.TIMETABLE_PATH
                                            ) == true

                                        if (isTimetable && view != null && !isSuccess) {
                                            extractionAttempts = 0
                                            tryExtractTimetableHtml(view)
                                        } else if (
                                            isNewJwxt &&
                                            uri?.path == "/sso/ddlogin"
                                        ) {
                                            // 登录页意味着会话已过期或首次进入，允许下次到教务页时重新跳课表
                                            hasRequestedTimetable = false
                                        } else if (
                                            isNewJwxt &&
                                            !hasRequestedTimetable
                                        ) {
                                            hasRequestedTimetable = true
                                            extractSemesterInfoThenLoadTimetable(view)
                                        }
                                    }
                                }

                                // 首次进入教务内页时轮询主页日历 widget（onPageFinished 时日历可能尚未渲染，
    // 且首个页面可能是不含日历的跳转中间页），拿到学期数据或重试耗尽后再跳课表页。
    private fun extractSemesterInfoThenLoadTimetable(view: WebView?) {
        if (view == null) return
        view.evaluateJavascript(EXTRACT_SEMESTER_INFO_SCRIPT) { result ->
            val raw = decodeJavascriptString(result)
            val range = raw?.let { SemesterRangeParser.parse(it) }
            Log.d(TAG, "semester extract attempt=$semesterAttempts raw=${raw?.take(160)} range=$range")
            when {
                range != null -> {
                    onSemesterInfoObtained(range)
                    view.loadUrl(JwxtEndpoints.TIMETABLE_URL)
                }
                semesterAttempts < MAX_SEMESTER_EXTRACTION_ATTEMPTS -> {
                    semesterAttempts++
                    view.postDelayed(
                        { extractSemesterInfoThenLoadTimetable(view) },
                        SEMESTER_EXTRACTION_RETRY_DELAY_MS
                    )
                }
                else -> {
                    onSemesterInfoObtained(null)
                    view.loadUrl(JwxtEndpoints.TIMETABLE_URL)
                }
            }
        }
    }

    override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    return super.shouldOverrideUrlLoading(view, request)
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                }
                            }

                            loadUrl(JwxtEndpoints.LOGIN_URL)
                        }
                    },
                    update = { webView ->
                        if (isActive) webView.onResume() else webView.onPause()
                    },
                    onRelease = { webView ->
                        webView.stopLoading()
                        webView.destroy()
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun JwxtImportScreenPreview() {
    NJUPTerTheme {
        JwxtImportScreen(
            onBack = {},
            onTimetableHtmlObtained = { _ -> }
        )
    }
}

private fun decodeJavascriptString(result: String?): String? {
    if (result.isNullOrBlank() || result == "null") return null
    return runCatching { JSONTokener(result).nextValue() as? String }.getOrNull()
}

private const val MAX_EXTRACTION_ATTEMPTS = 20
private const val EXTRACTION_RETRY_DELAY_MS = 250L

private const val MAX_SEMESTER_EXTRACTION_ATTEMPTS = 10
private const val SEMESTER_EXTRACTION_RETRY_DELAY_MS = 300L
private const val TAG = "JwxtImport"

// ES5 写法，兼容旧版系统 WebView（不使用 ?. 与展开语法）
private val EXTRACT_SEMESTER_INFO_SCRIPT = """
    (function () {
      var tables = document.querySelectorAll('table');
      var table = null;
      for (var i = 0; i < tables.length; i++) {
        if (tables[i].querySelector('#rcStr')) { table = tables[i]; break; }
      }
      if (!table) return null;
      var header = table.querySelector('th[colspan]');
      var title = header ? header.textContent : '';
      var weekThs = table.querySelectorAll('thead tr:nth-child(3) th');
      var weeks = [];
      for (var j = 0; j < weekThs.length; j++) {
        var n = parseInt(weekThs[j].textContent, 10);
        if (!isNaN(n)) weeks.push(n);
      }
      return title + '\n' + weeks.join(',');
    })()
""".trimIndent()

private val EXTRACT_TIMETABLE_HTML_SCRIPT = """
    (() => {
      const documents = [document];
      for (const frame of document.querySelectorAll('iframe')) {
        try {
          if (frame.contentDocument) documents.push(frame.contentDocument);
        } catch (_) {}
      }
      const timetableDocument = documents.find(
        candidate => candidate && candidate.querySelector('#kblist_table')
      );
      return timetableDocument ? timetableDocument.documentElement.outerHTML : null;
    })()
""".trimIndent()
