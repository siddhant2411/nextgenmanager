package com.nextgenmanager.nextgenmanager.assets.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.assets.model.MachineEvent;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class MachineDtoValidationAndSerializationTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void machineEventRequestDTO_hasNotNullOnRequiredFields() throws NoSuchFieldException {
        Annotation[] annotations = MachineEventRequestDTO.class.getDeclaredField("machineId").getAnnotations();
        assertThat(annotationNames(annotations)).contains("NotNull");
    }

    @Test
    void machineProductionLogRequestDTO_hasValidationAnnotations() throws NoSuchFieldException {
        Annotation[] machineIdAnnotations = MachineProductionLogRequestDTO.class.getDeclaredField("machineId").getAnnotations();
        Annotation[] plannedQtyAnnotations = MachineProductionLogRequestDTO.class.getDeclaredField("plannedQuantity").getAnnotations();
        assertThat(annotationNames(machineIdAnnotations)).contains("NotNull");
        assertThat(annotationNames(plannedQtyAnnotations)).contains("PositiveOrZero");
    }

    @Test
    void machineStatusChangeRequestDTO_hasNotNullOnNewStatus() throws NoSuchFieldException {
        Annotation[] annotations = MachineStatusChangeRequestDTO.class.getDeclaredField("newStatus").getAnnotations();
        assertThat(annotationNames(annotations)).contains("NotNull");
    }

    @Test
    void serializesAndDeserializesEnumsInRequestJson() throws Exception {
        MachineEventRequestDTO dto = new MachineEventRequestDTO();
        dto.setMachineId(1L);
        dto.setEventType(MachineEvent.EventType.MAINTENANCE);
        dto.setStartTime(LocalDateTime.of(2026, 2, 25, 10, 30));
        dto.setSource(MachineEvent.Source.SYSTEM);

        String json = objectMapper.writeValueAsString(dto);
        assertThat(json).contains("MAINTENANCE").contains("SYSTEM");

        String statusJson = "{\"newStatus\":\"OUT_OF_SERVICE\",\"reason\":\"EOL\"}";
        MachineStatusChangeRequestDTO statusDTO = objectMapper.readValue(statusJson, MachineStatusChangeRequestDTO.class);
        assertThat(statusDTO.getNewStatus()).isEqualTo(MachineDetails.MachineStatus.OUT_OF_SERVICE);
    }

    private static java.util.List<String> annotationNames(Annotation[] annotations) {
        return Arrays.stream(annotations).map(a -> a.annotationType().getSimpleName()).toList();
    }
}
