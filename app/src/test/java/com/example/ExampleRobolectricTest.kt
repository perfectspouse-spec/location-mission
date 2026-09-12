package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.TaskLocationEntity
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.LocalizationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
  fun `create task location entity with title, description, and priority`() {
    val task = TaskLocationEntity(
      title = "Süreyya Operası Bilet Teslimi",
      description = "Gişeden cuma günkü rezerve biletleri al",
      priority = "HIGH",
      placeName = "Kadıköy Süreyya Tiyatrosu",
      category = "Tiyatro / Kültür",
      latitude = 40.9897,
      longitude = 29.0289,
      taskDescription = "Gişeden cuma günkü rezerve biletleri al"
    )
    assertEquals("Süreyya Operası Bilet Teslimi", task.displayTitle)
    assertEquals("Gişeden cuma günkü rezerve biletleri al", task.displayDescription)
    assertEquals("HIGH", task.priority)
    assertEquals("Kadıköy Süreyya Tiyatrosu", task.placeName)
    assertEquals("Tiyatro / Kültür", task.category)
    assertFalse(task.isCompleted)
    assertEquals(100, task.radiusMeters)
  }

  @Test
  fun `verify localization manager turkish and english strings`() {
    val tr = LocalizationManager.getStrings(AppLanguage.TURKISH)
    val en = LocalizationManager.getStrings(AppLanguage.ENGLISH)

    assertEquals("Konum Görev Yöneticisi", tr.appTitle)
    assertEquals("Location Task Manager", en.appTitle)

    assertTrue(tr.priorityHigh.contains("Yüksek"))
    assertTrue(en.priorityHigh.contains("High"))

    assertTrue(tr.enterKeyNewEntry.contains("Yeni Giriş"))
    assertTrue(en.enterKeyNewEntry.contains("New Entry"))
  }

  @Test
  fun `verify all supported languages and default language is english`() {
    val languages = AppLanguage.values()
    assertEquals(5, languages.size)
    assertEquals(AppLanguage.ENGLISH, languages.first())

    languages.forEach { lang ->
      val strings = LocalizationManager.getStrings(lang)
      assertTrue(strings.appTitle.isNotBlank())
      assertTrue(strings.settings.isNotBlank())
      assertTrue(strings.language.isNotBlank())
      assertTrue(strings.tabTasks.isNotBlank())
      assertTrue(strings.tabMap.isNotBlank())
      assertTrue(strings.tabArchive.isNotBlank())
    }

    val es = LocalizationManager.getStrings(AppLanguage.SPANISH)
    assertEquals("Administrador de Tareas por Ubicación", es.appTitle)

    val de = LocalizationManager.getStrings(AppLanguage.GERMAN)
    assertEquals("Standort Aufgabenplaner", de.appTitle)

    val fr = LocalizationManager.getStrings(AppLanguage.FRENCH)
    assertEquals("Gestionnaire de Tâches par Emplacement", fr.appTitle)
  }

  @Test
  fun `verify place not found error message is localized correctly`() {
    val tr = LocalizationManager.getStrings(AppLanguage.TURKISH)
    val trMsg = tr.placeNotFoundMessage("Bilinmeyen Yer")
    assertTrue(trMsg.contains("Bilinmeyen Yer"))
    assertTrue(trMsg.contains("bulunamadı"))

    val en = LocalizationManager.getStrings(AppLanguage.ENGLISH)
    val enMsg = en.placeNotFoundMessage("Unknown Place")
    assertTrue(enMsg.contains("Unknown Place"))
    assertTrue(enMsg.contains("could not be found"))
  }
}
