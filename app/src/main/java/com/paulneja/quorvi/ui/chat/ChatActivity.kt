package com.paulneja.quorvi.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.paulneja.quorvi.ops.CoreClient
import com.paulneja.quorvi.ui.design.QuorviDesign
import com.paulneja.quorvi.ui.operations.PolicyViewModel
import com.paulneja.quorvi.ui.theme.QuorviTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ChatActivity : ComponentActivity() {
    @Inject lateinit var core: CoreClient
    private val vm: ChatViewModel by viewModels()
    private val policy: PolicyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) intent.getStringExtra(EXTRA_CHAT)?.let { vm.open(it) }
        if (savedInstanceState == null) intent.getStringExtra(EXTRA_DRAFT)?.let { vm.seedDraft(it) }
        val prompt = if (savedInstanceState == null) intent.getStringExtra(EXTRA_PROMPT) else null
        setContent { QuorviTheme { QuorviDesign { ChatApp(vm, policy, prompt, ::finish) } } }
    }

    override fun onStart() {
        super.onStart()
        core.attach()
        core.setVisible(true)
    }

    override fun onStop() {
        core.setVisible(false)
        core.detach()
        super.onStop()
    }

    companion object {
        const val EXTRA_CHAT = "chat"
        const val EXTRA_PROMPT = "prompt"
        const val EXTRA_DRAFT = "draft"

        fun open(context: Context, prompt: String? = null, chat: String? = null, draft: String? = null) {
            val intent = Intent(context, ChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            draft?.let { intent.putExtra(EXTRA_DRAFT, it) }
            prompt?.let { intent.putExtra(EXTRA_PROMPT, it) }
            chat?.let { intent.putExtra(EXTRA_CHAT, it) }
            context.startActivity(intent)
        }
    }
}
