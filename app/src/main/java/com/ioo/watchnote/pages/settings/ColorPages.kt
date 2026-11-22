package com.ioo.watchnote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Picker
import androidx.wear.compose.material3.RadioButtonDefaults
import androidx.wear.compose.material3.SplitRadioButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimePickerDefaults
import androidx.wear.compose.material3.rememberPickerState

/** 0xFFxxxxxxL 形式的颜色值转 Compose Color。 */
private fun cnColor(argb: Long): Color = Color(argb and 0xFFFFFFFFL)

/** 卡片底色上该用深字还是浅字。 */
private fun cnOnColor(bg: Color): Color =
    if (bg.luminance() > 0.45f) Color(0xFF14100E) else Color(0xFFF8F4ED)

/** 两个颜色按比例混合（t=0 取 a，t=1 取 b）。 */
private fun blend(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f
)

/** 一个色系的代表色：取最鲜艳的那一支，太暗的抬到看得见的亮度。 */
private fun famColor(fam: CnFamily): Color {
    val list = fam.colors.map { cnColor(it.argb) }
    fun sat(c: Color): Float {
        val mx = maxOf(c.red, c.green, c.blue)
        val mn = minOf(c.red, c.green, c.blue)
        return if (mx <= 0.001f) 0f else (mx - mn) / mx
    }
    val best = list.maxByOrNull { sat(it) } ?: list.first()
    return accentFromColor(best).primary
}

/** 一张颜色卡片：整块底色就是它自己的颜色，名字在上面，右侧是单选控件（选中 = 使用中）。 */
@Composable
private fun ColorCard(
    cardColor: Color,
    label: String,
    trailing: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val onCard = cnOnColor(cardColor)
    val split = blend(cardColor, onCard, 0.18f)
    val colors = RadioButtonDefaults.splitRadioButtonColors(
        selectedContainerColor = cardColor,
        selectedContentColor = onCard,
        selectedSecondaryContentColor = onCard,
        selectedSplitContainerColor = split,
        selectedControlColor = onCard,
        unselectedContainerColor = cardColor,
        unselectedContentColor = onCard,
        unselectedSecondaryContentColor = onCard,
        unselectedSplitContainerColor = split,
        unselectedControlColor = onCard
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        SplitRadioButton(
            selected = active,
            onSelectionClick = { Haptics.tap(); onClick() },
            selectionContentDescription = if (active) "使用中" else "使用这个颜色",
            onContainerClick = { Haptics.tap(); onClick() },
            containerClickLabel = "使用这个颜色",
            modifier = Modifier.weight(1f),
            shape = squircle(24.dp),
            colors = colors,
            secondaryLabel = {
                Text(trailing, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            label = {
                Text(
                    label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
            }
        )
    }
}

/** 小节标题（预设色 / 中国传统色）。 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 2.dp)
    )
}

/**
 * 主题颜色主页面：顶部显示当前用的是哪个颜色，
 * 下面是 5 个预设色 + 中国传统色的 15 个主色系（一个色系一页，红归红绿归绿）。
 */
@Composable
internal fun ColorListPage(
    currentPreset: Int,
    customArgb: Int,
    onPreset: (Int) -> Unit,
    onOpenSeries: (Int) -> Unit,
    onBack: () -> Unit
) {
    val usingCustom = customArgb != 0
    val usingName = if (usingCustom) {
        cnColorNameOf(customArgb) ?: UiPrefsState.customAccentName.ifBlank { "自定义颜色" }
    } else {
        ACCENT_THEMES.getOrElse(currentPreset) { ACCENT_THEMES[0] }.name
    }
    val usingSeries = remember(customArgb) { cnSeriesIndexOf(customArgb) }
    SubPageListFrame(onBack = onBack, fishEye = false) {
        item(key = "color-title") {
            Fish { PageTitle("主题颜色", "使用中：" + usingName) }
        }
        item(key = "color-preset-label") { Fish { SectionLabel("预设色") } }
        ACCENT_THEMES.forEachIndexed { i, theme ->
            item(key = "preset-" + i) {
                Fish {
                    ColorCard(
                        cardColor = theme.primary,
                        label = theme.name,
                        trailing = if (!usingCustom && i == currentPreset) "使用中" else "预设",
                        active = !usingCustom && i == currentPreset,
                        onClick = { onPreset(i) }
                    )
                }
            }
        }
        item(key = "color-series-label") {
            Fish { SectionLabel("中国传统色 · " + CN_SERIES.size + " 个色系 526 色") }
        }
        CN_SERIES.forEachIndexed { i, fam ->
            item(key = "series-" + i) {
                Fish {
                    ColorCard(
                        cardColor = famColor(fam),
                        label = fam.label,
                        trailing = if (i == usingSeries) "使用中" else fam.colors.size.toString() + " 色",
                        active = i == usingSeries,
                        onClick = { onOpenSeries(i) }
                    )
                }
            }
        }
    }
}

/** 某个细分系列里的滚轮选色，✓ 应用后就变成整套主题的强调色。 */
@Composable
internal fun ColorWheelPage(
    seriesIndex: Int,
    selectedArgb: Int,
    onPick: (Int, String) -> Unit,
    onBack: () -> Unit
) {
    val family = CN_SERIES.getOrElse(seriesIndex) { CN_SERIES[0] }
    val colors = family.colors
    val initialIndex = remember(seriesIndex, selectedArgb) {
        if (selectedArgb == 0) 0
        else colors.indexOfFirst { it.argb == (selectedArgb.toLong() and 0xFFFFFFFFL) }.coerceAtLeast(0)
    }
    val pickerState = rememberPickerState(
        initialNumberOfOptions = colors.size,
        initiallySelectedIndex = initialIndex,
        shouldRepeatOptions = false
    )
    val confirmColors = TimePickerDefaults.timePickerColors()
    val selectedIndex = pickerState.selectedOptionIndex.coerceIn(0, colors.size - 1)
    val selected = colors[selectedIndex]
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Picker(
            state = pickerState,
            contentDescription = { selected.name },
            modifier = Modifier.fillMaxSize(),
            rotaryScrollableBehavior = null,
            verticalSpacing = 8.dp,
            option = { index ->
                val c = colors[index]
                val picked = index == selectedIndex
                val cardBg = cnColor(c.argb)
                val onCard = cnOnColor(cardBg)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(
                        modifier = Modifier
                            .width(144.dp)
                            .height(48.dp)
                            .clip(squircle(20.dp))
                            .background(cardBg)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            c.name,
                            color = onCard,
                            fontSize = 15.sp,
                            fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                        if (picked) {
                            Spacer(Modifier.width(5.dp))
                            Text("✓", color = onCard, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(72.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.background, Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(96.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                    )
                )
        )
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(family.label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Text(
                selected.name,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { Haptics.tap(); onBack() },
            contentAlignment = Alignment.Center
        ) {
            Text("←", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp)
        }
        EdgeButton(
            onClick = {
                Haptics.tap()
                onPick((selected.argb and 0xFFFFFFFFL).toInt(), selected.name)
            },
            modifier = Modifier.align(Alignment.BottomCenter),
            buttonSize = EdgeButtonSize.Small,
            colors = ButtonDefaults.buttonColors(
                contentColor = confirmColors.confirmButtonContentColor,
                containerColor = confirmColors.confirmButtonContainerColor
            )
        ) {
            Text("✓", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}
