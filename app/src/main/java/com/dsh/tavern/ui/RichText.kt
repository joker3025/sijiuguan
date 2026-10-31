package com.dsh.tavern.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 轻量 Markdown 渲染（不引第三方库）：
 * 粗体 **...**、斜体 *...*、行内代码 `...`、引用 >、代码块 ```、标题 #。
 * 其余内容按纯文本逐行显示，够角色扮演对话使用。
 */
@Composable
fun RichText(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    modifier: Modifier = Modifier
) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val codeBg = MaterialTheme.colorScheme.surface
    val lines = text.split("\n")
    Column(modifier) {
        var inCode = false
        val codeBuffer = StringBuilder()
        lines.forEach { line ->
            if (line.trimStart().startsWith("```")) {
                if (inCode) {
                    CodeBlock(codeBuffer.toString(), codeBg, color)
                    codeBuffer.clear()
                }
                inCode = !inCode
                return@forEach
            }
            if (inCode) {
                if (codeBuffer.isNotEmpty()) codeBuffer.append('\n')
                codeBuffer.append(line)
                return@forEach
            }
            if (line.isBlank()) {
                Spacer(Modifier.height(4.dp))
                return@forEach
            }
            val heading = when {
                line.startsWith("### ") -> 3
                line.startsWith("## ") -> 2
                line.startsWith("# ") -> 1
                else -> 0
            }
            if (heading > 0) {
                Text(
                    text = line.substring(heading + 1),
                    style = style.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
                    color = color
                )
                return@forEach
            }
            val quote = line.trimStart().startsWith(">")
            val content = if (quote) line.trimStart().removePrefix(">").trimStart() else line
            Text(
                text = inlineAnnotated(content, color),
                style = if (quote) style.copy(fontStyle = FontStyle.Italic) else style,
                color = if (quote) secondary else color
            )
        }
        if (inCode && codeBuffer.isNotEmpty()) {
            CodeBlock(codeBuffer.toString(), codeBg, color)
        }
    }
}

@Composable
private fun CodeBlock(content: String, background: Color, textColor: Color) {
    Surface(
        color = background.copy(alpha = 0.55f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = content.trimEnd(),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = textColor,
            modifier = Modifier.padding(8.dp)
        )
    }
}

private fun inlineAnnotated(text: String, color: Color): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", index + 2)
                if (end > index + 2) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(index + 2, end))
                    pop()
                    index = end + 2
                } else {
                    append("**")
                    index += 2
                }
            }
            text[index] == '`' -> {
                val end = text.indexOf('`', index + 1)
                if (end > index + 1) {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = color.copy(alpha = 0.08f)
                        )
                    )
                    append(text.substring(index + 1, end))
                    pop()
                    index = end + 1
                } else {
                    append('`')
                    index++
                }
            }
            text[index] == '*' -> {
                val end = text.indexOf('*', index + 1)
                if (end > index + 1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(index + 1, end))
                    pop()
                    index = end + 1
                } else {
                    append('*')
                    index++
                }
            }
            else -> {
                append(text[index])
                index++
            }
        }
    }
}
