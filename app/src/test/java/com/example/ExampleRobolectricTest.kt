package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.TaskLocationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Konum Görev Yöneticisi", appName)
  }

  @Test
  fun `create task location entity default values`() {
    val task = TaskLocationEntity(
      placeName = "Kadıköy Süreyya Tiyatrosu",
      category = "Tiyatro / Kültür",
      latitude = 40.9897,
      longitude = 29.0289,
      taskDescription = "Gişeden biletleri teslim al"
    )
    assertEquals("Kadıköy Süreyya Tiyatrosu", task.placeName)
    assertEquals("Tiyatro / Kültür", task.category)
    assertFalse(task.isCompleted)
    assertEquals(100, task.radiusMeters)
  }
}
