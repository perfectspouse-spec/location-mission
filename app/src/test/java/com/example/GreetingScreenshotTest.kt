package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.TaskLocationEntity
import com.example.ui.components.TaskCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val sampleTask = TaskLocationEntity(
      id = 1,
      placeName = "Kadıköy Süreyya Tiyatrosu",
      category = "Tiyatro / Kültür",
      latitude = 40.9897,
      longitude = 29.0289,
      address = "Bahariye Cad. No:29, Kadıköy",
      taskDescription = "Gişeden rezerve tiyatro biletlerini al",
      radiusMeters = 100
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        TaskCard(
          task = sampleTask,
          isSelected = true,
          userLatitude = 40.9900,
          userLongitude = 29.0290,
          onSelect = {},
          onToggleComplete = {},
          onEdit = {},
          onDelete = {},
          onSimulateArrival = {},
          onShowRoute = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
