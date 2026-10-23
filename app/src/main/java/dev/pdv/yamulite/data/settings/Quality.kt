package dev.pdv.yamulite.data.settings

enum class Quality(val bitrate: Int, val label: String) {
    Low(64, "منخفضة (64 kbps)"),
    Normal(128, "متوسطة (128 kbps)"),
    High(192, "عالية (192 kbps)"),
    Best(320, "قصوى (320 kbps)"),
}
