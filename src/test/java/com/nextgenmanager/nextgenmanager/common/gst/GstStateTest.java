package com.nextgenmanager.nextgenmanager.common.gst;

import com.nextgenmanager.nextgenmanager.purchase.service.GstResolver;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class GstStateTest {

    private static final Path SEED = Path.of("src/main/resources/db/migration/V180__gst_state_master.sql");

    @Test
    void theSeededTableAndTheEnumAreTheSameList() throws IOException {
        Map<String, String> seeded = new LinkedHashMap<>();
        Matcher row = Pattern.compile("\\('(\\d{2})', '([^']+)'\\)")
                .matcher(Files.readString(SEED, StandardCharsets.UTF_8));
        while (row.find()) {
            seeded.put(row.group(1), row.group(2));
        }

        Map<String, String> coded = new LinkedHashMap<>();
        for (GstState s : GstState.values()) {
            coded.put(s.getCode(), s.getDisplayName());
        }

        // A state added to one and not the other is a foreign-key failure waiting for its first order.
        assertThat(seeded).isEqualTo(coded);
    }

    @Test
    void aCodeIsFoundWhateverSpaceSurroundsIt() {
        assertThat(GstState.fromCode(" 24 ")).isEqualTo(GstState.GUJARAT);
        assertThat(GstState.fromCode("GJ")).isNull();
        assertThat(GstState.fromCode(null)).isNull();
    }

    @Test
    void aNameIsFoundHoweverItWasTyped() {
        assertThat(GstState.fromName("gujarat ")).isEqualTo(GstState.GUJARAT);
        assertThat(GstState.fromName("Jammu and Kashmir")).isEqualTo(GstState.JAMMU_KASHMIR);
        assertThat(GstState.fromName("TAMILNADU")).isEqualTo(GstState.TAMIL_NADU);
        // The current state, not the one the code was taken from in 2014.
        assertThat(GstState.fromName("Andhra Pradesh")).isEqualTo(GstState.ANDHRA_PRADESH);
        assertThat(GstState.fromName("Gujrat")).isNull();
    }

    @Test
    void onlyARealStateCodeIsReadOffAGstin() {
        assertThat(GstState.codeFromGstin("27ABCDE1234F1Z5")).isEqualTo("27");
        assertThat(GstState.codeFromGstin("00ABCDE1234F1Z5")).isNull();
        assertThat(GstState.codeFromGstin("URP")).isNull();
        assertThat(GstState.codeFromGstin(null)).isNull();
    }

    @Test
    void aCodeThatIsNotAStateIsRefusedByName() {
        assertThat(GstState.requireCode("  ", "Ship-to state code")).isNull();
        assertThat(GstState.requireCode("24", "Ship-to state code")).isEqualTo("24");
        assertThatThrownBy(() -> GstState.requireCode("GJ", "Ship-to state code"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ship-to state code")
                .hasMessageContaining("GJ");
    }

    @Test
    void aPartysStoredStateFollowsItsGstinThenTheChoiceThenTheAddress() {
        // The GSTIN is the registration, so it outranks a state picked by hand.
        assertThat(GstResolver.stateCodeToStore("24AAAAA0000A1Z5", "27", "Kerala")).isEqualTo("24");
        assertThat(GstResolver.stateCodeToStore(null, "27", "Kerala")).isEqualTo("27");
        assertThat(GstResolver.stateCodeToStore(" ", null, "Kerala")).isEqualTo("32");
        assertThat(GstResolver.stateCodeToStore(null, null, "Somewhere")).isNull();
    }

    @Test
    void aStoredCodeThatIsNotAStateIsIgnoredInFavourOfTheGstin() {
        assertThat(GstResolver.stateCodeOf("GJ", "24AAAAA0000A1Z5")).isEqualTo("24");
        assertThat(GstResolver.stateCodeOf("27", "24AAAAA0000A1Z5")).isEqualTo("27");
        assertThat(GstResolver.stateCodeOf(null, null)).isNull();
    }
}
