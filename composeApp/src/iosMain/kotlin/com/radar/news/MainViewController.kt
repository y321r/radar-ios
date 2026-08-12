package com.radar.news

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Swift entry point — wired from ContentView.swift as `MainViewControllerKt.MainViewController()`. */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
