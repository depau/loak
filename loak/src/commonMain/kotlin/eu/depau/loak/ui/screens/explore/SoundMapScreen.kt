package eu.depau.loak.ui.screens.explore

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import eu.depau.loak.ui.components.sheets.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.repositories.AudioMuseRepository
import eu.depau.loak.domain.repositories.SoundMapPoint
import eu.depau.loak.generated.resources.*
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Play
import eu.depau.loak.icons.outlined.Add
import eu.depau.loak.icons.outlined.Fit
import eu.depau.loak.icons.outlined.InstantMix
import eu.depau.loak.icons.outlined.Lasso
import eu.depau.loak.icons.outlined.PlaylistAdd
import eu.depau.loak.icons.outlined.Queue
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.screens.alchemy.SaveSongsSheet
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.math.hypot

/** A style on the map: AudioMuse-AI's top tags, the most common ones by name, the rest Other. */
class MapStyle(val key: String, val label: String, val color: Color)

private val knownStyles = listOf(
	"electronic" to "Electronic", "pop" to "Pop", "rock" to "Rock", "dance" to "Dance",
	"jazz" to "Jazz", "hip-hop" to "Hip-hop", "indie" to "Indie", "rnb" to "R&B"
)
private val palette = listOf(
	Color(0xFF5B6CF0), Color(0xFFE0569B), Color(0xFFD9534F), Color(0xFF9C5BD6),
	Color(0xFFE0A030), Color(0xFF2E9E8F), Color(0xFF6BAF3A), Color(0xFFB0705A)
)
private const val OTHER = ""

/** The known styles this map has, by size, then Other. */
fun soundMapStyles(points: List<SoundMapPoint>): List<MapStyle> {
	val counts = points.groupingBy { it.style.lowercase() }.eachCount()
	return knownStyles.mapIndexedNotNull { i, (key, label) ->
		MapStyle(key, label, palette[i]).takeIf { (counts[key] ?: 0) > 0 }
	}.sortedByDescending { counts[it.key] } + MapStyle(OTHER, "Other", Color(0xFF8A8F99))
}

fun styleOf(style: String, styles: List<MapStyle>) = styles.firstOrNull { it.key == style.lowercase() } ?: styles.last()

/** Even-odd ray casting: is [p] inside the [polygon]? */
fun inPolygon(p: Offset, polygon: List<Offset>): Boolean {
	var inside = false
	var j = polygon.lastIndex
	for (i in polygon.indices) {
		val a = polygon[i]
		val b = polygon[j]
		if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
		j = i
	}
	return inside
}

/**
 * The library laid out by how it sounds (AudioMuse-AI's map): pinch or +/− to zoom, tap a dot
 * for its song, or Select area and draw around some dots to play or save them.
 */
@Composable
fun SoundMapScreen() {
	val repository = koinInject<AudioMuseRepository>()
	val player = koinInject<MediaPlayerViewModel>()
	val scope = rememberCoroutineScope()
	val points by produceState<List<SoundMapPoint>?>(null) { value = runCatching { repository.soundMap() }.getOrDefault(emptyList()) }
	val styles = remember(points) { soundMapStyles(points.orEmpty()) }
	var highlighted by remember { mutableStateOf<MapStyle?>(null) }
	var canvasSize by remember { mutableStateOf(Size.Zero) }
	var scale by remember { mutableStateOf(1f) }
	var offset by remember { mutableStateOf(Offset.Zero) }
	var selecting by remember { mutableStateOf(false) }
	var lasso by remember { mutableStateOf(emptyList<Offset>()) }
	var selection by remember { mutableStateOf(emptyList<SoundMapPoint>()) }
	var tapped by remember { mutableStateOf<SoundMapPoint?>(null) }
	var saving by remember { mutableStateOf<List<DomainSong>?>(null) }
	val title = stringResource(Res.string.title_sound_map)

	val all = points.orEmpty()
	val bounds = remember(all) {
		if (all.isEmpty()) null else listOf(all.minOf { it.x }, all.maxOf { it.x }, all.minOf { it.y }, all.maxOf { it.y })
	}
	// map → screen: fit the whole map with a margin, then zoom and pan
	fun toScreen(p: SoundMapPoint): Offset {
		val (minX, maxX, minY, maxY) = bounds ?: return Offset.Zero
		val pad = 24f
		val base = Offset(
			pad + (p.x - minX) / (maxX - minX) * (canvasSize.width - 2 * pad),
			pad + (maxY - p.y) / (maxY - minY) * (canvasSize.height - 2 * pad)
		)
		return base * scale + offset
	}
	fun zoom(by: Float, around: Offset = Offset(canvasSize.width / 2, canvasSize.height / 2)) {
		val next = (scale * by).coerceIn(1f, 40f)
		offset = (offset - around) * (next / scale) + around
		scale = next
	}
	suspend fun songsOf(list: List<SoundMapPoint>) = repository.songs(list.map { it.id })

	Scaffold(topBar = { NestedTopBar(title = { Text(title) }) }) { innerPadding ->
		Column(Modifier.padding(innerPadding).fillMaxSize()) {
			LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				items(styles, key = { it.key }) { style ->
					FilterChip(
						selected = highlighted == style,
						onClick = { highlighted = if (highlighted == style) null else style },
						label = { Text(style.label) },
						leadingIcon = { Canvas(Modifier.size(10.dp)) { drawCircle(style.color) } }
					)
				}
			}
			if (points == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
			Box(Modifier.weight(1f).fillMaxWidth()) {
				Canvas(
					Modifier.fillMaxSize()
						.onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
						.pointerInput(selecting) {
							if (selecting) detectDragGestures(
								onDragStart = { lasso = listOf(it); selection = emptyList() },
								onDrag = { change, _ -> lasso = lasso + change.position },
								onDragEnd = { selection = all.filter { inPolygon(toScreen(it), lasso) } }
							) else detectTransformGestures { centroid, pan, z, _ ->
								zoom(z, centroid)
								offset += pan
							}
						}
						.pointerInput(all, scale, offset) {
							detectTapGestures(onDoubleTap = { zoom(2f, it) }) { pos ->
								tapped = all.minByOrNull { hypot(toScreen(it).x - pos.x, toScreen(it).y - pos.y) }
									?.takeIf { val s = toScreen(it); hypot(s.x - pos.x, s.y - pos.y) < 24.dp.toPx() }
							}
						}
				) {
					val radius = (2.5.dp.toPx() * kotlin.math.sqrt(scale)).coerceAtMost(7.dp.toPx())
					val chosen = selection.mapTo(HashSet()) { it.id }
					all.groupBy { styleOf(it.style, styles) }.forEach { (style, group) ->
						val dim = (highlighted != null && highlighted != style) || (chosen.isNotEmpty())
						drawPoints(group.map(::toScreen), PointMode.Points, style.color.copy(alpha = if (dim) 0.2f else 0.9f), radius * 2, StrokeCap.Round)
					}
					if (chosen.isNotEmpty()) all.filter { it.id in chosen }.groupBy { styleOf(it.style, styles) }.forEach { (style, group) ->
						drawPoints(group.map(::toScreen), PointMode.Points, style.color, radius * 2, StrokeCap.Round)
					}
					if (lasso.size > 1) drawPath(
						Path().apply { moveTo(lasso[0].x, lasso[0].y); lasso.drop(1).forEach { lineTo(it.x, it.y) }; close() },
						Color.Gray, style = Stroke(2.dp.toPx())
					)
					tapped?.let { drawCircle(Color.White, radius * 2.5f, toScreen(it), style = Stroke(2.dp.toPx())) }
				}
				Column(Modifier.align(Alignment.TopEnd).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
					SmallFloatingActionButton(onClick = { zoom(1.5f) }) { Icon(Icons.Outlined.Add, stringResource(Res.string.action_zoom_in)) }
					SmallFloatingActionButton(onClick = { zoom(1 / 1.5f) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
					SmallFloatingActionButton(onClick = { scale = 1f; offset = Offset.Zero }) {
						Icon(Icons.Outlined.Fit, stringResource(Res.string.action_fit_map))
					}
				}
				// over the map, so the map keeps its size and the lasso stays on its dots
				Column(Modifier.align(Alignment.BottomEnd).fillMaxWidth(), horizontalAlignment = Alignment.End) {
				ExtendedFloatingActionButton(
					onClick = { selecting = !selecting; lasso = emptyList() },
					modifier = Modifier.padding(16.dp),
					icon = { Icon(Icons.Outlined.Lasso, null) },
					text = { Text(stringResource(if (selecting) Res.string.label_selecting else Res.string.action_select_area)) },
					containerColor = if (selecting) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
				)
			if (selection.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
				Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Row(verticalAlignment = Alignment.CenterVertically) {
						Text(stringResource(Res.string.info_area_songs, selection.size), Modifier.weight(1f))
						TextButton(onClick = { selection = emptyList(); lasso = emptyList() }) { Text(stringResource(Res.string.action_clear)) }
					}
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
						OutlinedButton(onClick = { scope.launch { saving = songsOf(selection) } }) {
							Icon(Icons.Outlined.PlaylistAdd, null, Modifier.size(18.dp))
							Spacer(Modifier.size(6.dp))
							Text(stringResource(Res.string.action_save_as_playlist))
						}
						Button(onClick = { player.playMix(title) { songsOf(selection) } }) {
							Icon(Icons.Filled.Play, null, Modifier.size(18.dp))
							Spacer(Modifier.size(6.dp))
							Text(stringResource(Res.string.action_play_these, selection.size))
						}
					}
				}
			}
		
				}
			}
		}
	}

	tapped?.let { point ->
		ModalBottomSheet(onDismissRequest = { tapped = null }) {
			val song by produceState<DomainSong?>(null, point) { value = songsOf(listOf(point)).firstOrNull() }
			ListItem(
				colors = ListItemDefaults.colors(containerColor = Color.Transparent),
				leadingContent = { CoverArt(coverArtId = song?.coverArtId, modifier = Modifier.size(56.dp)) },
				headlineContent = { Text(point.title) },
				supportingContent = { Text(listOf(point.artist, styleOf(point.style, styles).label).joinToString(" · ")) }
			)
			Row(Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				Button(onClick = { song?.let(player::playNow); tapped = null }, enabled = song != null) {
					Icon(Icons.Filled.Play, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text(stringResource(Res.string.action_play))
				}
				OutlinedButton(onClick = { player.playInstantMix(point.id, point.title, song); tapped = null }) {
					Icon(Icons.Outlined.InstantMix, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text(stringResource(Res.string.action_instant_mix))
				}
				OutlinedButton(onClick = { song?.let { player.addToQueueSingle(it) }; tapped = null }, enabled = song != null) {
					Icon(Icons.Outlined.Queue, null, Modifier.size(18.dp))
				}
			}
		}
	}
	saving?.let { songs -> SaveSongsSheet(title, songs) { saving = null } }
}
