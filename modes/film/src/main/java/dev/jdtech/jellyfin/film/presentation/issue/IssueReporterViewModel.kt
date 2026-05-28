package dev.jdtech.jellyfin.film.presentation.issue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.jellyseerr.JellyseerrRepository
import dev.jdtech.jellyfin.jellyseerr.model.CreateJellyseerrIssue
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.repository.JellyfinRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class IssueReporterViewModel
@Inject
constructor(
    private val jellyseerrRepository: JellyseerrRepository,
    private val jellyfinRepository: JellyfinRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(IssueReporterState())
    val state = _state.asStateFlow()

    private val eventChannel = Channel<IssueReporterEvent>()
    val events = eventChannel.receiveAsFlow()

    private var currentKey: String? = null

    fun loadForMovie(movie: FindroidMovie) {
        loadTarget(key = "movie-${movie.id}") {
            val tmdbId = movie.tmdbId ?: return@loadTarget unresolvedTarget(movie.name, IssueReportMediaType.MOVIE)
            val details = jellyseerrRepository.getMovieDetails(tmdbId)
            IssueReportTarget(
                title = movie.name,
                seerrMediaId = details.seerrMediaId,
                mediaType = IssueReportMediaType.MOVIE,
            )
        }
    }

    fun loadForShow(show: FindroidShow) {
        loadTarget(key = "show-${show.id}") {
            val tmdbId = show.tmdbId ?: return@loadTarget unresolvedTarget(show.name, IssueReportMediaType.SERIES)
            val details = jellyseerrRepository.getTvDetails(tmdbId)
            IssueReportTarget(
                title = show.name,
                seerrMediaId = details.seerrMediaId,
                mediaType = IssueReportMediaType.SERIES,
                seasons = details.seasons,
                defaultSeasonNumber = details.seasons.maxOfOrNull { it.seasonNumber },
                defaultEpisodeNumber = 0,
            )
        }
    }

    fun loadForSeason(season: FindroidSeason) {
        loadTarget(key = "season-${season.id}") {
            val show = jellyfinRepository.getShow(season.seriesId)
            val tmdbId =
                season.tmdbId ?: show.tmdbId
                    ?: return@loadTarget unresolvedTarget(season.seriesName, IssueReportMediaType.SERIES)
            val details = jellyseerrRepository.getTvDetails(tmdbId)
            IssueReportTarget(
                title = season.seriesName,
                seerrMediaId = details.seerrMediaId,
                mediaType = IssueReportMediaType.SERIES,
                seasons = details.seasons,
                defaultSeasonNumber = season.indexNumber,
                defaultEpisodeNumber = 0,
            )
        }
    }

    fun loadForEpisode(episode: FindroidEpisode) {
        loadTarget(key = "episode-${episode.id}") {
            val show = jellyfinRepository.getShow(episode.seriesId)
            val tmdbId =
                episode.tmdbId ?: show.tmdbId
                    ?: return@loadTarget unresolvedTarget(episode.seriesName, IssueReportMediaType.SERIES)
            val details = jellyseerrRepository.getTvDetails(tmdbId)
            IssueReportTarget(
                title = episode.seriesName,
                seerrMediaId = details.seerrMediaId,
                mediaType = IssueReportMediaType.SERIES,
                seasons = details.seasons,
                defaultSeasonNumber = episode.parentIndexNumber,
                defaultEpisodeNumber = episode.indexNumber,
            )
        }
    }

    fun onIssueButtonClick() {
        val current = _state.value
        val target = current.target
        if (target?.seerrMediaId == null) {
            sendMessage("Issue reporting is unavailable for this item.")
            return
        }

        _state.value =
            if (current.openIssues.isNotEmpty()) {
                current.copy(showExistingIssues = true, showReportDialog = false)
            } else {
                current.copy(showReportDialog = true, showExistingIssues = false)
            }
    }

    fun onReportNewClick() {
        _state.value = _state.value.copy(showReportDialog = true, showExistingIssues = false)
    }

    fun onIssueSelected(issueId: Int) {
        viewModelScope.launch {
            val existingIssue = _state.value.openIssues.firstOrNull { it.id == issueId }
            _state.value =
                _state.value.copy(
                    selectedIssue = existingIssue,
                    showExistingIssues = true,
                    showReportDialog = false,
                )
        }
    }

    fun dismissDialogs() {
        _state.value =
            _state.value.copy(
                showExistingIssues = false,
                showReportDialog = false,
                selectedIssue = null,
            )
    }

    fun submit(form: IssueReportForm) {
        val target = _state.value.target ?: return
        val mediaId = target.seerrMediaId ?: return
        val message = form.message.trim()
        if (message.isBlank()) {
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, errorMessage = null)
            try {
                jellyseerrRepository.createIssue(
                    CreateJellyseerrIssue(
                        mediaId = mediaId,
                        type = form.type,
                        message = message,
                        problemSeason = if (target.isSeries) form.seasonNumber else 0,
                        problemEpisode = if (target.isSeries) form.episodeNumber else 0,
                    )
                )
                val issues = jellyseerrRepository.getOpenIssues(mediaId)
                _state.value =
                    _state.value.copy(
                        isSubmitting = false,
                        openIssues = issues,
                        showReportDialog = false,
                        showExistingIssues = false,
                    )
                eventChannel.send(IssueReporterEvent.Message("Issue submitted."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSubmitting = false, errorMessage = e.message)
                eventChannel.send(IssueReporterEvent.Message(e.message ?: "Failed to submit issue."))
            }
        }
    }

    private fun loadTarget(
        key: String,
        block: suspend () -> IssueReportTarget,
    ) {
        if (currentKey == key || _state.value.isLoading) {
            return
        }

        viewModelScope.launch {
            currentKey = key
            _state.value = IssueReporterState(isLoading = true)
            try {
                val target = block()
                val issues =
                    target.seerrMediaId?.let { jellyseerrRepository.getOpenIssues(it) }.orEmpty()
                _state.value = IssueReporterState(target = target, openIssues = issues)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value =
                    IssueReporterState(
                        target = null,
                        errorMessage = e.message,
                    )
                currentKey = null
            }
        }
    }

    private fun unresolvedTarget(title: String, mediaType: IssueReportMediaType): IssueReportTarget {
        return IssueReportTarget(
            title = title,
            seerrMediaId = null,
            mediaType = mediaType,
        )
    }

    private fun sendMessage(message: String) {
        viewModelScope.launch { eventChannel.send(IssueReporterEvent.Message(message)) }
    }
}
