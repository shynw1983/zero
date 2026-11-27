package com.zero.todo.domain.model

import androidx.test.core.app.ApplicationProvider
import com.zero.todo.data.preferences.SharedPrefsUserPreferencesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderPathFormatterTest {

    private fun sampleFolders(): List<Folder> = listOf(
        Folder(id = "r_personal", name = "個人"),
        Folder(id = "c_daily", parentId = "r_personal", name = "DAILY"),
        Folder(id = "r_shimizu", name = "清水店", colorHex = ProjectFolderColors.SAGE),
        Folder(id = "c_shimizu_keiri", parentId = "r_shimizu", name = "経理"),
        Folder(id = "r_marukyu", name = "丸九", colorHex = ProjectFolderColors.SLATE_BLUE),
        Folder(id = "c_consult", parentId = "r_marukyu", name = "コンサルティング"),
        Folder(id = "c_marukyu_keiri", parentId = "c_consult", name = "経理"),
        Folder(id = "c_store_branch", parentId = "r_marukyu", name = "直営店"),
        Folder(id = "c_store_order", parentId = "c_store_branch", name = "発注"),
        Folder(id = "c_shimizu_order", parentId = "r_shimizu", name = "発注"),
        Folder(id = "c_direct_order", parentId = "r_marukyu", name = "発注")
    )

    @Test
    fun smartDisambiguateKeepsUniqueLeafConciseAndDisambiguatesCollisions() {
        val index = FolderPathFormatter.buildIndex(sampleFolders())

        val daily = index.badgeFor("c_daily", FolderPathDisplayMode.SMART_DISAMBIGUATE)
        assertEquals(null, daily?.prefix)
        assertEquals("DAILY", daily?.leaf)
        assertNull(daily?.colorHex)

        val shimizuKeiri = index.badgeFor("c_shimizu_keiri", FolderPathDisplayMode.SMART_DISAMBIGUATE)
        assertEquals("清水店/", shimizuKeiri?.prefix)
        assertEquals("経理", shimizuKeiri?.leaf)
        assertEquals(ProjectFolderColors.SAGE, shimizuKeiri?.colorHex)

        val marukyuKeiri = index.badgeFor("c_marukyu_keiri", FolderPathDisplayMode.SMART_DISAMBIGUATE)
        assertEquals("丸九/…/", marukyuKeiri?.prefix)
        assertEquals("経理", marukyuKeiri?.leaf)
        assertEquals(ProjectFolderColors.SLATE_BLUE, marukyuKeiri?.colorHex)

        // Two deep/mixed branches under the same root ("丸九") sharing "発注" fall back to full disambiguation
        val storeOrder = index.badgeFor("c_store_order", FolderPathDisplayMode.SMART_DISAMBIGUATE)
        assertEquals("丸九/直営店/発注", storeOrder?.fullText)
    }

    @Test
    fun rootAndLeafVsFullPathFormatting() {
        val index = FolderPathFormatter.buildIndex(sampleFolders())

        val rootLeaf = index.badgeFor("c_marukyu_keiri", FolderPathDisplayMode.ROOT_AND_LEAF)
        assertEquals("丸九/…/経理", rootLeaf?.fullText)

        val rootLeafUnique = index.badgeFor("c_daily", FolderPathDisplayMode.ROOT_AND_LEAF)
        assertEquals("個人/DAILY", rootLeafUnique?.fullText)

        val full = index.badgeFor("c_marukyu_keiri", FolderPathDisplayMode.FULL_PATH)
        assertEquals("丸九/コンサルティング/経理", full?.fullText)
    }

    @Test
    fun relativeTruncationStripsOpenParentPrefix() {
        val index = FolderPathFormatter.buildIndex(sampleFolders())

        val relSmart = index.badgeFor(
            folderId = "c_marukyu_keiri",
            mode = FolderPathDisplayMode.SMART_DISAMBIGUATE,
            relativeToFolderId = "r_marukyu"
        )
        assertEquals("コンサルティング/経理", relSmart?.fullText)

        val relDirect = index.badgeFor(
            folderId = "c_marukyu_keiri",
            mode = FolderPathDisplayMode.ROOT_AND_LEAF,
            relativeToFolderId = "c_consult"
        )
        assertNull(relDirect?.prefix)
        assertEquals("経理", relDirect?.leaf)
    }

    @Test
    fun monolithBadgeAndCycleProtection() {
        val folders = sampleFolders() + listOf(
            Folder(id = "cyc_a", parentId = "cyc_b", name = "CycleA"),
            Folder(id = "cyc_b", parentId = "cyc_a", name = "CycleB")
        )
        val index = FolderPathFormatter.buildIndex(folders)

        val monolithSmart = index.monolithBadgeFor("c_marukyu_keiri", FolderPathDisplayMode.SMART_DISAMBIGUATE)
        assertEquals("丸九 · コンサルティング · 経理", monolithSmart?.fullText)

        val monolithRootLeaf = index.monolithBadgeFor("c_marukyu_keiri", FolderPathDisplayMode.ROOT_AND_LEAF)
        assertEquals("丸九 · … · 経理", monolithRootLeaf?.fullText)

        // Should terminate safely without StackOverflowError
        val cyclicBadge = index.badgeFor("cyc_a", FolderPathDisplayMode.FULL_PATH)
        assertEquals("CycleA", cyclicBadge?.leaf)
    }

    @Test
    fun folderPathDisplayModePersistsInSharedPrefs() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences(SharedPrefsUserPreferencesRepository.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()

        val repo1 = SharedPrefsUserPreferencesRepository(ctx)
        assertEquals(FolderPathDisplayMode.SMART_DISAMBIGUATE, repo1.preferences.value.folderPathDisplayMode)

        repo1.setFolderPathDisplayMode(FolderPathDisplayMode.ROOT_AND_LEAF)
        val repo2 = SharedPrefsUserPreferencesRepository(ctx)
        assertEquals(FolderPathDisplayMode.ROOT_AND_LEAF, repo2.preferences.value.folderPathDisplayMode)
    }
}
