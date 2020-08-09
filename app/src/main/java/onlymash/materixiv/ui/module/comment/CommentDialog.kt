package onlymash.materixiv.ui.module.comment

import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import onlymash.materixiv.app.Keys
import onlymash.materixiv.data.action.ActionComment
import onlymash.materixiv.data.api.PixivAppApi
import onlymash.materixiv.data.db.entity.Token
import onlymash.materixiv.data.repository.NetworkState
import onlymash.materixiv.data.repository.comment.CommentRepositoryImpl
import onlymash.materixiv.databinding.DialogCommentBinding
import onlymash.materixiv.extensions.getViewModel
import onlymash.materixiv.ui.module.common.TokenBottomSheetDialog
import org.kodein.di.instance
import retrofit2.HttpException

class CommentDialog : TokenBottomSheetDialog<DialogCommentBinding>() {

    companion object {
        fun create(illustId: Long): CommentDialog {
            return CommentDialog().apply {
                arguments = Bundle().apply {
                    putLong(Keys.ILLUST_ID, illustId)
                }
            }
        }
    }

    private val pixivAppApi by instance<PixivAppApi>()

    private var illustId: Long = 0
    private var token: Token? = null
    private lateinit var commentViewModel: CommentViewModel
    private lateinit var commentApapter: CommentAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        illustId = arguments?.getLong(Keys.ILLUST_ID) ?: 0
    }

    override fun onCreateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): DialogCommentBinding {
        return DialogCommentBinding.inflate(inflater, container, false)
    }

    override fun onCreateViewModel() {
        super.onCreateViewModel()
        commentViewModel = getViewModel(CommentViewModel(CommentRepositoryImpl(pixivAppApi)))
    }

    override fun onBaseViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.root.minimumHeight = getWindowHeight() / 2
        commentApapter = CommentAdapter()
        binding.commentList.adapter = commentApapter
        commentApapter.addLoadStateListener { handleNetworkState(it) }
        commentViewModel.comments.observe(viewLifecycleOwner, Observer {
            commentApapter.submitData(lifecycle, it)
        })
        binding.retryButton.setOnClickListener { commentApapter.retry() }
    }

    private fun handleNetworkState(loadStates: CombinedLoadStates) {
        val refresh = loadStates.refresh
        val append = loadStates.append
        val isEmptyList = commentApapter.itemCount == 0
        if (refresh is LoadState.Error || append is LoadState.Error) {
            val error = if (refresh is LoadState.Error) {
                refresh.error
            } else {
                (append as LoadState.Error).error
            }
            handleException(error)
        } else {
            binding.retryButton.isVisible = false
            binding.commentList.isVisible = true
        }
        val isLoading = refresh is LoadState.Loading || append is LoadState.Loading
        binding.progressBar.isVisible = isLoading && isEmptyList
    }

    private fun handleException(error: Throwable) {
        val token = token ?: return
        if (error is HttpException && error.code() == 400) {
            binding.retryButton.isVisible = false
            binding.progressBar.isVisible = false
            refreshToken(
                uid = token.uid,
                refreshToken = token.data.refreshToken,
                deviceToken = token.data.deviceToken
            )
        } else {
            binding.commentList.isVisible = false
            binding.progressBar.isVisible = false
            binding.retryButton.isVisible = true
        }
    }

    override fun onTokenLoaded(token: Token) {
        this.token = token
        commentViewModel.show(ActionComment(token.auth, illustId))
    }

    override fun onLoginStateChange(state: NetworkState?) {

    }

    override fun onRefreshStateChange(state: NetworkState?) {

    }

    private fun getWindowHeight(): Int {
        val displayMetrics = DisplayMetrics()
        activity?.windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        return displayMetrics.heightPixels
    }
}