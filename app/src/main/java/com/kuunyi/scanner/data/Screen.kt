package com.kuunyi.scanner.data

sealed class Screen {
    object EventPicker : Screen()
    object Scanner : Screen()
    object Result : Screen()
    object Settings : Screen()
}

enum class ScanMode { CONTINUOUS, TAP }
