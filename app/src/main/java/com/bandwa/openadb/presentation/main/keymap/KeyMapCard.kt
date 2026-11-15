package com.bandwa.openadb.presentation.main.keymap

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandwa.openadb.presentation.ui.KeyCardShape
import com.bandwa.openadb.presentation.ui.keyCard
import com.bandwa.openadb.presentation.ui.keyDivider
import com.bandwa.openadb.presentation.ui.keyNoneHint
import com.bandwa.openadb.presentation.ui.keyStroke
import com.bandwa.openadb.presentation.ui.keyTextPrimary
import com.bandwa.openadb.presentation.ui.keyTextSecondary
import com.bandwa.openadb.presentation.ui.keyValue
import com.bandwa.openadb.util.PickApp
import com.google.accompanist.drawablepainter.rememberDrawablePainter

/**
 * 一条按键 = 一张卡片：标题 + 分隔线 + 「单击 / 长按」两格。
 *
 * 外观照抄按键映射软件的 `item_key_row`：一屏能看见十几行，点哪一格就改哪一格，
 * 比原来「一行按钮 + 一个横向图标条」直观。**没有长按的键只画一格**（用户明确：
 * 不支持长按的就不要显示长按），不摆一个灰掉的空格子。
 */
@Composable
fun KeyMapCard(
    row: KeyMapRow,
    onPickShort: () -> Unit,
    onPickLong: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(KeyCardShape)
            .background(keyCard())
            .border(1.dp, keyStroke(), KeyCardShape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = row.name,
                color = keyTextPrimary(),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                // 两列布局下宽度只有半屏，带括号注释的长名字（如「灯杆顶端（仅原功能为唤醒语音助手）」）
                // 必须允许折到第二行，截断会把括号里的条件说明吃掉。
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            trailing?.invoke()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(keyDivider())
        )

        if (row.listToggle) {
            // LIST 键没有「选应用」这回事：它就是个布尔（开 = 短按开关 360）。
            // 开关本体由调用方通过 trailing 放进标题行，这里只留一句说明。
            Text(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 14.dp),
                text = row.note,
                color = keyTextSecondary(),
                fontSize = 13.sp,
            )
        } else {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                KeyCell(
                    modifier = Modifier.weight(1f),
                    label = "单击",
                    app = row.shortApp,
                    onClick = onPickShort,
                )
                if (row.longProp != null) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(keyDivider())
                    )
                    KeyCell(
                        modifier = Modifier.weight(1f),
                        label = "长按",
                        app = row.longApp,
                        onClick = onPickLong,
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyCell(
    label: String,
    app: PickApp?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 16.dp),
    ) {
        Text(
            text = label,
            color = keyTextSecondary(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (app?.icon != null) {
                Image(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(24.dp),
                    painter = rememberDrawablePainter(drawable = app.icon),
                    contentDescription = app.label,
                )
            }
            Text(
                text = app?.label ?: "未设置（原功能）",
                color = if (app == null) keyNoneHint() else keyValue(),
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
