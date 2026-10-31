package com.dsh.tavern.data

object TokenEstimator {
    /**
     * 粗略估算 token：CJK 字符按 1 token，其他字符按 4 字符 1 token。
     * 只用于预算和 UI 显示，不要求精确。
     */
    fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        var cjk = 0
        var other = 0
        for (ch in text) {
            val code = ch.code
            val isCjk = code in 0x3400..0x9FFF ||
                code in 0xF900..0xFAFF ||
                code in 0x3000..0x303F ||
                code in 0xFF00..0xFFEF ||
                code in 0x3040..0x30FF
            if (isCjk) cjk++ else other++
        }
        return cjk + (other + 3) / 4
    }
}
