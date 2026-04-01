package com.tardfyou.paperlens.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DefaultProfilePresetTest {
    @Test
    fun elderPreset_prioritizesReadability() {
        val preset = DefaultProfile.Elder.toPreset()

        assertThat(preset.fontScale).isEqualTo(1.35f)
        assertThat(preset.lineHeightScale).isEqualTo(1.25f)
        assertThat(preset.contrastMode).isEqualTo(ContrastMode.High)
        assertThat(preset.speechRate).isEqualTo(0.95f)
    }

    @Test
    fun studentPreset_keepsBalancedDefaults() {
        val preset = DefaultProfile.Student.toPreset()

        assertThat(preset.fontScale).isEqualTo(1.1f)
        assertThat(preset.lineHeightScale).isEqualTo(1.15f)
        assertThat(preset.contrastMode).isEqualTo(ContrastMode.Standard)
        assertThat(preset.speechRate).isEqualTo(1.05f)
    }
}
